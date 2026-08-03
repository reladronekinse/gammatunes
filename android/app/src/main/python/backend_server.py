"""
Встроенный бэкенд, работающий ПРЯМО НА ТЕЛЕФОНЕ через Chaquopy.

Это функциональный аналог backend/main.py (который остаётся в репозитории
для запуска на ПК/сервере, если он вам понадобится отдельно), но переписанный
на стандартной библиотеке (http.server) вместо FastAPI/uvicorn/pydantic —
это снижает риск проблем совместимости пакетов при сборке под Android.

Слушает только 127.0.0.1 (localhost телефона) — наружу из телефона порт
не торчит, приложение обращается к нему как к обычному HTTP API.

Запускается не напрямую, а через Kotlin: см.
android/.../backend/LocalBackend.kt, который вызывает start(port) в фоновом
потоке при старте приложения (Application.onCreate).
"""

from __future__ import annotations

import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import yt_dlp
from ytmusicapi import YTMusic, setup

# --------------------------------------------------------------------------- #
# YTMusic — ленивая инициализация + browser auth для лайков/библиотеки
# --------------------------------------------------------------------------- #



def _android_files_dir() -> str:
    """Каталог files/ приложения — auth-файл переживает перезапуск процесса."""
    try:
        from com.chaquo.python import Python
        ctx = Python.getPlatform().getApplication()
        return str(ctx.getFilesDir().getAbsolutePath())
    except Exception:
        import tempfile
        return tempfile.gettempdir()


def _auth_file_default() -> str:
    import os
    return os.path.join(_android_files_dir(), "ytm_browser_auth.json")


def _extract_sapisid(cookie: str) -> str:
    """Достаём __Secure-3PAPISID или SAPISID из cookie-строки."""
    # SimpleCookie иногда ломается на значениях с запятыми — парсим вручную.
    found: dict[str, str] = {}
    for part in cookie.split(";"):
        part = part.strip()
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        found[k.strip()] = v.strip()
    for name in ("__Secure-3PAPISID", "SAPISID"):
        if name in found and found[name]:
            return found[name]
    raise ValueError(
        "В Cookie нет __Secure-3PAPISID/SAPISID. "
        "Нужна полная сессия YouTube (не гость)."
    )


_yt_lock = threading.Lock()
_yt_instance: YTMusic | None = None
_auth_json: str | None = None  # JSON browser headers (для ответа клиенту)
_auth_file_path: str | None = None  # путь к файлу auth для YTMusic()
_auth_account_hint: str | None = None


def _get_yt() -> YTMusic:
    global _yt_instance, _auth_file_path
    with _yt_lock:
        if _yt_instance is None:
            if not _auth_file_path:
                import os
                candidate = _auth_file_default()
                if os.path.isfile(candidate):
                    _auth_file_path = candidate
            if _auth_file_path:
                try:
                    _yt_instance = YTMusic(_auth_file_path)
                except Exception as exc:
                    print(f"[ytm-backend] auth YTMusic(file) failed, fallback anonymous: {exc!r}")
                    _yt_instance = YTMusic()
            elif _auth_json:
                # Legacy: только если файл ещё не записан — через tempfile.
                import os
                import tempfile
                try:
                    fd, path = tempfile.mkstemp(prefix="ytm_browser_", suffix=".json")
                    with os.fdopen(fd, "w", encoding="utf-8") as f:
                        f.write(_auth_json)
                    _auth_file_path = path
                    _yt_instance = YTMusic(path)
                except Exception as exc:
                    print(f"[ytm-backend] auth YTMusic failed, fallback anonymous: {exc!r}")
                    _yt_instance = YTMusic()
            else:
                _yt_instance = YTMusic()
        return _yt_instance


def _reset_yt() -> None:
    """Сбросить клиент после смены/сброса auth."""
    global _yt_instance
    with _yt_lock:
        _yt_instance = None


def _parse_headers_raw(raw: str) -> dict:
    """Превращает текст Request Headers из DevTools или JSON в dict для YTMusic."""
    raw = (raw or "").strip()
    if not raw:
        raise ValueError("empty headers")

    raw = raw.strip()
    # Уже JSON (файл browser.json / headers_auth.json)
    if raw.startswith("{"):
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise ValueError("auth JSON must be an object")
        return data

    # Формат DevTools: "Name: value" по строкам
    headers: dict[str, str] = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.lower().startswith(":"):
            continue
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            continue
        # Нормализуем известные ключи
        lk = key.lower()
        if lk == "cookie":
            headers["Cookie"] = value
        elif lk == "authorization":
            headers["Authorization"] = value
        elif lk == "x-goog-authuser":
            headers["X-Goog-AuthUser"] = value
        elif lk == "x-origin":
            headers["x-origin"] = value
        elif lk == "user-agent":
            headers["User-Agent"] = value
        elif lk == "content-type":
            headers["Content-Type"] = value
        elif lk == "accept":
            headers["Accept"] = value
        elif lk == "accept-language":
            headers["Accept-Language"] = value
        else:
            headers[key] = value

    if "Cookie" not in headers and "cookie" not in {k.lower() for k in headers}:
        # Возможно пришла голая cookie-строка без префикса "Cookie:"
        if "SID=" in raw or "SAPISID=" in raw or "__Secure-3PSID=" in raw:
            headers["Cookie"] = raw.strip()
        else:
            raise ValueError("В заголовках нет Cookie — без него вход невозможен")

    headers.setdefault("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
    headers.setdefault("Accept", "*/*")
    headers.setdefault("Accept-Language", "en-US,en;q=0.5")
    headers.setdefault("Content-Type", "application/json")
    headers.setdefault("X-Goog-AuthUser", "0")
    headers.setdefault("x-origin", "https://music.youtube.com")
    return headers


def _set_auth_from_raw(raw: str) -> dict:
    """Browser-auth для ytmusicapi 1.11+.

    Критично: determine_auth_type() по умолчанию возвращает OAUTH_CUSTOM_CLIENT.
    Тип BROWSER выставляется ТОЛЬКО если в headers есть Authorization
    со строкой SAPISIDHASH. Без этого YTMusic() падает с
    "oauth JSON provided... oauth_credentials not provided".

    Поэтому из Cookie достаём SAPISID / __Secure-3PAPISID, собираем
    Authorization через get_authorization() и пишем browser.json на диск.
    """
    global _auth_json, _auth_account_hint, _auth_file_path
    import os
    import tempfile

    raw = (raw or "").strip()
    if not raw:
        raise ValueError("empty headers")

    # 1) Получаем dict headers
    headers: dict
    if raw.startswith("{"):
        parsed = json.loads(raw)
        if not isinstance(parsed, dict):
            raise ValueError("auth JSON must be an object")
        if "access_token" in parsed or "refresh_token" in parsed:
            raise ValueError(
                "Это OAuth JSON. Нужен browser auth: Cookie + headers с music.youtube.com."
            )
        headers = {str(k): str(v) for k, v in parsed.items()}
    else:
        try:
            # setup() из ytmusicapi умеет DevTools-текст
            setup_out = setup(headers_raw=raw)
            headers = json.loads(setup_out) if isinstance(setup_out, str) else dict(setup_out)
            headers = {str(k): str(v) for k, v in headers.items()}
        except Exception as exc:
            print(f"[ytm-backend] setup() failed ({exc!r}), using local parser")
            headers = {str(k): str(v) for k, v in _parse_headers_raw(raw).items()}

    # Case-insensitive lookup
    def _get(h: dict, *names: str) -> str | None:
        lower = {k.lower(): v for k, v in h.items()}
        for n in names:
            if n.lower() in lower:
                return lower[n.lower()]
        return None

    cookie = _get(headers, "Cookie", "cookie")
    if not cookie:
        raise ValueError("В заголовках нет Cookie")

    # Нужен SAPISID / __Secure-3PAPISID в cookie
    if "SAPISID=" not in cookie and "__Secure-3PAPISID=" not in cookie:
        raise ValueError(
            "В Cookie нет SAPISID/__Secure-3PAPISID — войдите в аккаунт на music.youtube.com "
            "и скопируйте headers заново (нужна полная сессия, не гость)."
        )

    # Обязательные browser-поля
    headers.setdefault("Accept", "*/*")
    headers.setdefault("Content-Type", "application/json")
    headers.setdefault("X-Goog-AuthUser", "0")
    headers.setdefault("x-origin", "https://music.youtube.com")
    if not _get(headers, "User-Agent", "user-agent"):
        headers["User-Agent"] = (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    # Нормализуем ключ Cookie
    for k in list(headers.keys()):
        if k.lower() == "cookie" and k != "Cookie":
            headers["Cookie"] = headers.pop(k)
            break
    else:
        headers["Cookie"] = cookie

    # 2) Authorization с SAPISIDHASH — иначе auth_type = OAUTH
    origin = _get(headers, "x-origin", "origin") or "https://music.youtube.com"
    from ytmusicapi.helpers import get_authorization
    sapisid = _extract_sapisid(cookie)
    auth_header = get_authorization(f"{sapisid} {origin}")
    headers["Authorization"] = auth_header
    print(f"[ytm-backend] Authorization generated, sapisid_prefix={sapisid[:8]}...")

    # Убедимся, что ключ Authorization с большой буквы (CaseInsensitiveDict ок, но файл читаемее)
    for k in list(headers.keys()):
        if k.lower() == "authorization" and k != "Authorization":
            headers["Authorization"] = headers.pop(k)

    auth_str = json.dumps(headers, ensure_ascii=False)
    print(f"[ytm-backend] browser auth keys={sorted(headers.keys())}")

    # 3) Файл в files/ приложения → YTMusic(path)
    path = _auth_file_default()
    with open(path, "w", encoding="utf-8") as f:
        f.write(auth_str)
    print(f"[ytm-backend] auth written to {path}")

    try:
        test = YTMusic(path)
    except Exception as exc:
        print(f"[ytm-backend] YTMusic(path) failed: {exc!r}")
        raise ValueError(f"YTMusic auth init failed: {exc}") from exc

    # Жёсткая проверка: YouTube должен отдать лайки, а не экран Sign in.
    try:
        liked = test.get_liked_songs(limit=1)
    except Exception as exc:
        print(f"[ytm-backend] liked check failed: {exc!r}")
        msg = str(exc)
        if "Sign in" in msg or "Looking for what you" in msg or "messageRenderer" in msg:
            raise ValueError(
                "YouTube не принял сессию (ответ Sign in). "
                "Скопируйте Request Headers из Chrome на ПК: "
                "music.youtube.com → F12 → Network → browse → Request Headers "
                "(нужны Cookie с __Secure-3PAPISID и Authorization)."
            ) from exc
        raise ValueError(
            "Сессия не работает для лайков. Проверьте Cookie/Authorization. "
            f"Деталь: {type(exc).__name__}: {msg[:180]}"
        ) from exc

    if not isinstance(liked, dict) or "tracks" not in liked:
        raise ValueError(
            "YouTube вернул неожиданный ответ вместо списка лайков. "
            "Сессия, скорее всего, гостевая — войдите через headers из Chrome."
        )

    tracks = liked.get("tracks") or []
    count_hint = len(tracks)
    _auth_account_hint = f"Лайки доступны (проверка: {count_hint}+)"

    _auth_json = auth_str
    _auth_file_path = path
    _reset_yt()
    return {"ok": True, "accountName": _auth_account_hint, "authJson": auth_str}



def _clear_auth() -> None:
    global _auth_json, _auth_account_hint, _auth_file_path
    _auth_json = None
    _auth_account_hint = None
    if _auth_file_path:
        try:
            import os
            os.unlink(_auth_file_path)
        except Exception:
            pass
    _auth_file_path = None
    _reset_yt()


# --------------------------------------------------------------------------- #
# Вспомогательное — те же преобразования, что и в backend/main.py
# --------------------------------------------------------------------------- #

# БАГ, который здесь был: код брал thumbnails[-1] и считал, что это
# "самая большая" картинка — по факту это просто последний элемент списка,
# который для многих карточек (особенно у артистов и в некоторых ответах
# поиска) содержит превью всего 60x60/120x120 — именно столько отдаёт
# конкретный эндпоинт YTMusic для этого типа карточки, а не потому что
# большего не существует. Сам CDN (lh3.googleusercontent.com) отдаёт любой
# размер по параметру в самом URL (=w..-h..-...) независимо от того, какой
# размер пришёл в ответе API — поэтому вместо того чтобы доверять размеру
# из ответа, принудительно переписываем его на достаточно большой.
_THUMBNAIL_SIZE = 544
_THUMBNAIL_SIZE_RE = re.compile(r"=w\d+-h\d+")


def _upscale_thumbnail(url: str | None) -> str | None:
    if not url:
        return None
    if "googleusercontent.com" in url and _THUMBNAIL_SIZE_RE.search(url):
        return _THUMBNAIL_SIZE_RE.sub(f"=w{_THUMBNAIL_SIZE}-h{_THUMBNAIL_SIZE}", url)
    return url


def _pick_thumbnail(thumbnails):
    if not thumbnails:
        return None
    return _upscale_thumbnail(thumbnails[-1].get("url"))


def _to_track(item: dict) -> dict | None:
    video_id = item.get("videoId")
    if not video_id:
        return None
    artists = item.get("artists") or []
    artist_name = artists[0]["name"] if artists else item.get("artist", "Unknown")
    artist_id = None
    if artists:
        artist_id = artists[0].get("id") or artists[0].get("browseId")
    album = item.get("album")
    album_name = None
    album_id = None
    if isinstance(album, dict):
        album_name = album.get("name")
        album_id = album.get("id") or album.get("browseId")
    return {
        "videoId": video_id,
        "title": item.get("title", "Unknown"),
        "artist": artist_name,
        "album": album_name,
        "albumId": album_id,
        "thumbnail": _pick_thumbnail(item.get("thumbnails")),
        "durationSeconds": item.get("duration_seconds"),
        "artistId": artist_id,
    }


def _to_album(item: dict) -> dict | None:
    browse_id = item.get("browseId")
    if not browse_id:
        return None
    return {
        "albumId": browse_id,
        "title": item.get("title", "Unknown"),
        "thumbnail": _pick_thumbnail(item.get("thumbnails")),
        "year": item.get("year"),
    }


def _artist_detail(browse_id: str, fallback_name: str | None = None, fallback_thumbnail=None) -> dict | None:
    try:
        details = _get_yt().get_artist(browse_id)
    except Exception:
        return None

    albums_section = details.get("albums") or {}
    raw_albums = albums_section.get("results") or []

    # get_artist() отдаёт только первую страницу альбомов + continuation-
    # токен `params`, если у артиста их больше. ВАЖНО: get_artist_albums()
    # нужно вызывать с собственным browseId секции "Альбомы"
    # (albums_section["browseId"]), а не с browse_id самого артиста — это
    # отдельный browse-эндпоинт ("посмотреть все альбомы"). Раньше здесь по
    # ошибке передавался browse_id артиста, запрос падал, и ошибка тихо
    # проглатывалась в except — поэтому оставалась только первая страница.
    params = albums_section.get("params")
    albums_browse_id = albums_section.get("browseId")
    if params and albums_browse_id:
        try:
            full_albums = _get_yt().get_artist_albums(albums_browse_id, params, limit=None)
        except TypeError:
            try:
                full_albums = _get_yt().get_artist_albums(albums_browse_id, params)
            except Exception as exc:
                full_albums = None
                print(f"[ytm-backend] get_artist_albums fallback to first page: {exc!r}")
        except Exception as exc:
            full_albums = None
            print(f"[ytm-backend] get_artist_albums fallback to first page: {exc!r}")
        if full_albums:
            raw_albums = full_albums

    albums = [a for a in (_to_album(i) for i in raw_albums) if a]

    # Синглы / EP — отдельная секция в get_artist(). Подтягиваем так же,
    # как альбомы (с continuation через get_artist_albums, если есть).
    singles_section = details.get("singles") or {}
    raw_singles = list(singles_section.get("results") or [])
    s_params = singles_section.get("params")
    s_browse_id = singles_section.get("browseId")
    if s_params and s_browse_id:
        try:
            full_singles = _get_yt().get_artist_albums(s_browse_id, s_params, limit=None)
        except TypeError:
            try:
                full_singles = _get_yt().get_artist_albums(s_browse_id, s_params)
            except Exception as exc:
                full_singles = None
                print(f"[ytm-backend] get_artist singles fallback: {exc!r}")
        except Exception as exc:
            full_singles = None
            print(f"[ytm-backend] get_artist singles fallback: {exc!r}")
        if full_singles:
            raw_singles = full_singles
    singles = [a for a in (_to_album(i) for i in raw_singles) if a]

    return {
        "artistId": browse_id,
        "name": details.get("name") or fallback_name or "Unknown",
        "thumbnail": _upscale_thumbnail(fallback_thumbnail) or _pick_thumbnail(details.get("thumbnails")),
        "albums": albums,
        "singles": singles,
    }


# БАГ, который здесь был: раньше эта функция кэшировалась через
# @lru_cache(maxsize=256) без TTL. Ссылки на аудиопоток от YouTube —
# подписанные и живут ограниченное время (обычно несколько часов), после
# чего сервер начинает отвечать на них ошибкой, и трек перестаёт грузиться.
# lru_cache этого не знал и продолжал отдавать один и тот же протухший URL
# до перезапуска приложения. Решение — кэш с TTL: срок годности берём из
# параметра `expire` в самом URL (с небольшим запасом), а как истёк —
# извлекаем ссылку заново.
_STREAM_CACHE_SAFETY_SECONDS = 300
_stream_cache: dict[str, tuple[float, dict]] = {}
_stream_cache_lock = threading.Lock()


def _stream_expiry(stream_url: str) -> float:
    try:
        expire = parse_qs(urlparse(stream_url).query).get("expire", [None])[0]
        if expire:
            return float(expire) - _STREAM_CACHE_SAFETY_SECONDS
    except Exception:
        pass
    return time.time() + 3600


def _extract_stream(video_id: str) -> dict:
    with _stream_cache_lock:
        cached = _stream_cache.get(video_id)
        if cached is not None:
            expires_at, cached_result = cached
            if time.time() < expires_at:
                return cached_result

    ydl_opts = {
        # progressive audio preferred (easier to download fully)
        "format": "bestaudio[protocol^=http][protocol!=m3u8_native]/bestaudio/best",
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "extractor_args": {"youtube": {"player_client": ["android", "web"]}},
    }
    url = f"https://music.youtube.com/watch?v={video_id}"
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)

    if "url" in info:
        stream_url = info["url"]
        fmt = info
    else:
        formats = info.get("formats", [])
        audio_formats = [f for f in formats if f.get("acodec") != "none"]
        if not audio_formats:
            raise ValueError("No audio stream found")
        fmt = max(audio_formats, key=lambda f: f.get("abr") or 0)
        stream_url = fmt["url"]

    # БАГ (обрыв воспроизведения через ~15 секунд): googlevideo-ссылка от
    # yt-dlp подписана с привязкой к заголовкам запроса (в первую очередь
    # User-Agent). Если клиент запрашивает поток с другими заголовками,
    # YouTube не блокирует запрос сразу, а начинает жёстко троттлить
    # соединение через несколько секунд после старта — так и выглядит как
    # "трек обрывается". Прокидываем клиенту точные заголовки yt-dlp.
    http_headers = dict(fmt.get("http_headers") or info.get("http_headers") or {})

    result = {
        "videoId": video_id,
        "streamUrl": stream_url,
        "mimeType": fmt.get("ext", "m4a"),
        "bitrate": int(fmt.get("abr") or 0),
        "httpHeaders": http_headers,
    }
    with _stream_cache_lock:
        _stream_cache[video_id] = (_stream_expiry(stream_url), result)
    return result


# --------------------------------------------------------------------------- #
# HTTP-обработчик
# --------------------------------------------------------------------------- #


def _download_audio_file(video_id: str) -> tuple[str, str, str]:
    """Скачивает аудио на диск. Сначала yt-dlp, при ошибке — stream URL + requests."""
    import os
    import shutil

    work = os.path.join(_android_files_dir(), "ytm_dl_tmp", video_id)
    if os.path.isdir(work):
        shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work, exist_ok=True)

    url = f"https://music.youtube.com/watch?v={video_id}"
    outtmpl = os.path.join(work, f"{video_id}.%(ext)s")
    last_err: Exception | None = None

    # --- попытка 1: yt-dlp пишет файл сам ---
    try:
        ydl_opts = {
            "format": "bestaudio/best",
            "outtmpl": outtmpl,
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "noprogress": True,
            "restrictfilenames": True,
            # android-клиент часто стабильнее на телефоне
            "extractor_args": {"youtube": {"player_client": ["android", "web"]}},
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            filepath = ydl.prepare_filename(info)
            if not os.path.isfile(filepath) or os.path.getsize(filepath) < 1024:
                candidates = [
                    os.path.join(work, name)
                    for name in os.listdir(work)
                    if os.path.isfile(os.path.join(work, name))
                ]
                if not candidates:
                    raise ValueError("yt-dlp не создал файл")
                filepath = max(candidates, key=lambda p: os.path.getsize(p))
            if os.path.getsize(filepath) < 1024:
                raise ValueError(f"файл слишком маленький: {os.path.getsize(filepath)} байт")
            ext = (info.get("ext") or os.path.splitext(filepath)[1].lstrip(".") or "m4a").lower()
            mime = {
                "m4a": "audio/mp4",
                "mp4": "audio/mp4",
                "webm": "audio/webm",
                "opus": "audio/webm",
                "mp3": "audio/mpeg",
            }.get(ext, "application/octet-stream")
            print(f"[ytm-backend] yt-dlp downloaded {video_id}: {os.path.getsize(filepath)} bytes")
            return filepath, mime, f"{video_id}.{ext}"
    except Exception as exc:
        last_err = exc
        print(f"[ytm-backend] yt-dlp download failed: {exc!r}, try stream fallback")

    # --- попытка 2: взять signed URL и скачать requests-ом ---
    try:
        stream = _extract_stream(video_id)
        stream_url = stream["streamUrl"]
        headers = dict(stream.get("httpHeaders") or {})
        if "User-Agent" not in headers and "user-agent" not in headers:
            headers["User-Agent"] = (
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
        ext = (stream.get("mimeType") or "m4a").split(";")[0].strip().lower()
        if "/" in ext:
            # mime like audio/mp4
            ext = "m4a" if "mp4" in ext else ("webm" if "webm" in ext else "m4a")
        filepath = os.path.join(work, f"{video_id}.{ext}")
        import urllib.request
        req = urllib.request.Request(stream_url, headers=headers)
        with urllib.request.urlopen(req, timeout=300) as resp, open(filepath, "wb") as out:
            while True:
                chunk = resp.read(64 * 1024)
                if not chunk:
                    break
                out.write(chunk)
        size = os.path.getsize(filepath)
        if size < 1024:
            raise ValueError(f"stream fallback too small: {size}")
        mime = {
            "m4a": "audio/mp4",
            "mp4": "audio/mp4",
            "webm": "audio/webm",
            "mp3": "audio/mpeg",
        }.get(ext, "application/octet-stream")
        print(f"[ytm-backend] stream-fallback downloaded {video_id}: {size} bytes")
        return filepath, mime, f"{video_id}.{ext}"
    except Exception as exc2:
        raise RuntimeError(
            f"download failed (yt-dlp: {last_err}; stream: {exc2})"
        ) from exc2


def _to_playlist(item: dict) -> dict | None:
    if not isinstance(item, dict):
        return None
    pid = item.get("playlistId") or item.get("browseId") or item.get("id")
    title = item.get("title") or item.get("name")
    if not pid or not title:
        return None
    thumbs = item.get("thumbnails") or []
    thumb = _pick_thumbnail(thumbs) if thumbs else None
    count = item.get("count")
    try:
        count_i = int(count) if count is not None else None
    except Exception:
        count_i = None
    return {
        "playlistId": pid,
        "title": title,
        "thumbnail": thumb,
        "count": count_i,
    }



class _Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args) -> None:  # noqa: A002
        pass  # не засоряем logcat стандартными access-логами

    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error_json(self, status: int, message: str) -> None:
        self._send_json(status, {"detail": message})

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        try:
            if path == "/health":
                self._send_json(200, {"status": "ok"})
                return

            if path == "/search/artists":
                # Поиск отдаёт карточки артистов (не треков).
                q = (qs.get("q") or [""])[0]
                if not q:
                    self._send_error_json(400, "q is required")
                    return
                limit = int((qs.get("limit") or ["20"])[0])
                try:
                    raw = _get_yt().search(q, filter="artists", limit=limit)
                except Exception as exc:
                    self._send_error_json(502, f"YTMusic artist search failed: {exc}")
                    return

                artists = []
                for item in raw:
                    browse_id = item.get("browseId")
                    if not browse_id:
                        continue
                    artists.append({
                        "artistId": browse_id,
                        "name": item.get("artist") or item.get("title") or "Unknown",
                        "thumbnail": _pick_thumbnail(item.get("thumbnails")),
                        "albums": [],
                    })
                self._send_json(200, {"artists": artists})
                return

            if path.startswith("/artists/"):
                # Полная карточка артиста с альбомами — подгружается по тапу
                # на карточку из результатов поиска.
                artist_id = path[len("/artists/"):]
                if not artist_id:
                    self._send_error_json(400, "artistId is required")
                    return
                artist = _artist_detail(artist_id)
                if artist is None:
                    self._send_error_json(502, "YTMusic artist fetch failed")
                    return
                self._send_json(200, artist)
                return

            if path.startswith("/albums/"):
                album_id = path[len("/albums/"):]
                if not album_id:
                    self._send_error_json(400, "albumId is required")
                    return
                try:
                    album = _get_yt().get_album(album_id)
                except Exception as exc:
                    self._send_error_json(502, f"YTMusic album fetch failed: {exc}")
                    return

                album_thumbnail = _pick_thumbnail(album.get("thumbnails"))
                album_title = album.get("title", "Unknown")

                tracks = []
                for item in album.get("tracks", []):
                    track = _to_track(item)
                    if not track:
                        continue
                    # Треки внутри альбома обычно не несут собственную
                    # обложку/название альбома — подставляем с уровня альбома.
                    if track.get("thumbnail") is None:
                        track["thumbnail"] = album_thumbnail
                    if track.get("album") is None:
                        track["album"] = album_title
                    if track.get("albumId") is None:
                        track["albumId"] = album_id
                    tracks.append(track)

                self._send_json(200, {
                    "albumId": album_id,
                    "title": album_title,
                    "thumbnail": album_thumbnail,
                    "tracks": tracks,
                })
                return

            if path == "/search":
                q = (qs.get("q") or [""])[0]
                if not q:
                    self._send_error_json(400, "q is required")
                    return
                limit = int((qs.get("limit") or ["25"])[0])
                try:
                    raw = _get_yt().search(q, filter="songs", limit=limit)
                except Exception as exc:
                    self._send_error_json(502, f"YTMusic search failed: {exc}")
                    return
                tracks = [t for t in (_to_track(i) for i in raw) if t]
                self._send_json(200, {"results": tracks})
                return

            if path.startswith("/stream/"):
                video_id = path[len("/stream/"):]
                if not video_id:
                    self._send_error_json(400, "videoId is required")
                    return
                try:
                    result = _extract_stream(video_id)
                except Exception as exc:
                    self._send_error_json(502, f"Stream extraction failed: {exc}")
                    return
                self._send_json(200, result)
                return

            if path == "/auth/status":
                self._send_json(200, {
                    "loggedIn": _auth_json is not None,
                    "accountName": _auth_account_hint,
                })
                return

            if path == "/liked":
                if _auth_json is None:
                    self._send_error_json(401, "Not authenticated")
                    return
                limit = int((qs.get("limit") or ["100"])[0])
                try:
                    liked = _get_yt().get_liked_songs(limit=limit)
                except Exception as exc:
                    import traceback
                    print(f"[ytm-backend] get_liked_songs failed:\n{traceback.format_exc()}")
                    msg = str(exc)
                    if "Sign in" in msg or "Looking for what you" in msg or "messageRenderer" in msg:
                        self._send_error_json(
                            401,
                            "YouTube не принял сессию (Sign in). "
                            "Выйдите и войдите через headers из Chrome на ПК "
                            "(Cookie с __Secure-3PAPISID).",
                        )
                    else:
                        # Не тащим весь dict в UI — обрезаем
                        short = msg.replace("\n", " ")[:220]
                        self._send_error_json(
                            502,
                            f"get_liked_songs failed: {type(exc).__name__}: {short}",
                        )
                    return
                raw_tracks = liked.get("tracks") if isinstance(liked, dict) else (liked or [])
                tracks = [t for t in (_to_track(i) for i in (raw_tracks or [])) if t]
                self._send_json(200, {"results": tracks})
                return

            self._send_error_json(404, "Not found")
        except Exception as exc:  # страховка — сервер не должен падать целиком
            try:
                self._send_error_json(500, str(exc))
            except Exception:
                pass

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path
        try:
            length = int(self.headers.get("Content-Length") or "0")
            body = self.rfile.read(length) if length > 0 else b""
            payload = {}
            if body:
                try:
                    payload = json.loads(body.decode("utf-8"))
                except Exception:
                    self._send_error_json(400, "Invalid JSON body")
                    return

            if path == "/playlists":
                if _auth_json is None and not _auth_file_path:
                    self._send_error_json(401, "Not authenticated")
                    return
                limit = 50
                try:
                    qs = parse_qs(urlparse(self.path).query)
                    if "limit" in qs:
                        limit = max(1, min(100, int(qs["limit"][0])))
                except Exception:
                    pass
                try:
                    raw = _get_yt().get_library_playlists(limit=limit)
                except Exception as exc:
                    print(f"[ytm-backend] get_library_playlists failed: {exc!r}")
                    self._send_error_json(502, f"get_library_playlists failed: {exc}")
                    return
                items = raw if isinstance(raw, list) else (raw.get("results") or raw.get("playlists") or [])
                playlists = [p for p in (_to_playlist(i) for i in items) if p]
                self._send_json(200, {"playlists": playlists})
                return

            if path.startswith("/playlists/"):
                if _auth_json is None and not _auth_file_path:
                    self._send_error_json(401, "Not authenticated")
                    return
                playlist_id = path[len("/playlists/"):]
                if not playlist_id:
                    self._send_error_json(400, "playlistId required")
                    return
                limit = 100
                try:
                    qs = parse_qs(urlparse(self.path).query)
                    if "limit" in qs:
                        limit = max(1, min(500, int(qs["limit"][0])))
                except Exception:
                    pass
                try:
                    data = _get_yt().get_playlist(playlist_id, limit=limit)
                except Exception as exc:
                    print(f"[ytm-backend] get_playlist failed: {exc!r}")
                    self._send_error_json(502, f"get_playlist failed: {exc}")
                    return
                tracks_raw = data.get("tracks") if isinstance(data, dict) else []
                tracks = [t for t in (_to_track(i) for i in (tracks_raw or [])) if t]
                title = (data.get("title") if isinstance(data, dict) else None) or playlist_id
                self._send_json(200, {
                    "playlistId": playlist_id,
                    "title": title,
                    "tracks": tracks,
                })
                return

            if path.startswith("/download/"):
                video_id = path[len("/download/"):]
                if not video_id or "/" in video_id:
                    self._send_error_json(400, "videoId required")
                    return
                try:
                    filepath, mime, filename = _download_audio_file(video_id)
                except Exception as exc:
                    import traceback
                    print(f"[ytm-backend] download failed:\n{traceback.format_exc()}")
                    self._send_error_json(502, f"download failed: {exc}")
                    return
                import os
                import shutil
                try:
                    size = os.path.getsize(filepath)
                    if size < 1024:
                        self._send_error_json(502, f"downloaded file too small: {size}")
                        return
                    self.send_response(200)
                    self.send_header("Content-Type", mime)
                    self.send_header("Content-Length", str(size))
                    self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
                    self.send_header("Connection", "close")
                    self.end_headers()
                    with open(filepath, "rb") as f:
                        while True:
                            chunk = f.read(64 * 1024)
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                    try:
                        self.wfile.flush()
                    except Exception:
                        pass
                    print(f"[ytm-backend] /download/{video_id} sent {size} bytes")
                finally:
                    try:
                        shutil.rmtree(os.path.dirname(filepath), ignore_errors=True)
                    except Exception:
                        pass
                return

            if path == "/auth/login":
                raw = (
                    payload.get("headersRaw")
                    or payload.get("headers")
                    or payload.get("authJson")
                    or ""
                )
                # Иногда клиент шлёт уже готовый dict headers
                if not raw and isinstance(payload.get("cookie"), str):
                    raw = "Cookie: " + payload["cookie"]
                if not raw:
                    self._send_error_json(400, "headersRaw is required")
                    return
                if not isinstance(raw, str):
                    try:
                        raw = json.dumps(raw)
                    except Exception:
                        self._send_error_json(400, "headersRaw must be a string")
                        return
                try:
                    result = _set_auth_from_raw(raw)
                    self._send_json(200, result)
                except Exception as exc:
                    print(f"[ytm-backend] /auth/login error: {exc!r}")
                    self._send_error_json(400, f"Auth failed: {exc}")
                return

            if path == "/auth/logout":
                _clear_auth()
                self._send_json(200, {"ok": True})
                return

            if path == "/rate":
                if _auth_json is None:
                    self._send_error_json(401, "Not authenticated")
                    return
                video_id = payload.get("videoId") or ""
                rating = (payload.get("rating") or "LIKE").upper()
                if not video_id:
                    self._send_error_json(400, "videoId is required")
                    return
                if rating not in ("LIKE", "DISLIKE", "INDIFFERENT"):
                    self._send_error_json(400, "rating must be LIKE, DISLIKE or INDIFFERENT")
                    return
                try:
                    _get_yt().rate_song(video_id, rating)
                    self._send_json(200, {"ok": True, "videoId": video_id, "rating": rating})
                except Exception as exc:
                    self._send_error_json(502, f"rate_song failed: {exc}")
                return

            self._send_error_json(404, "Not found")
        except Exception as exc:
            try:
                self._send_error_json(500, str(exc))
            except Exception:
                pass


# --------------------------------------------------------------------------- #
# Запуск
# --------------------------------------------------------------------------- #

_server: ThreadingHTTPServer | None = None
_server_lock = threading.Lock()


def start(port: int = 8765) -> None:
    """
    Запускает сервер и блокирует текущий поток (serve_forever).
    Вызывать из фонового потока со стороны Kotlin, не из UI-потока.
    Повторный вызов безопасен — если сервер уже поднят, просто выходит.
    """
    global _server
    with _server_lock:
        if _server is not None:
            return
        _server = ThreadingHTTPServer(("127.0.0.1", port), _Handler)
    _server.serve_forever()
