

from __future__ import annotations

import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import yt_dlp
from ytmusicapi import YTMusic, setup

def _android_files_dir() -> str:

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

    found: dict[str, str] = {}
    for part in cookie.split(";"):
        part = part.strip()
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        found[k.strip()] = v.strip()

    for name in ("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID"):
        if name in found and found[name]:
            return found[name]
    raise ValueError(
        "В Cookie нет SAPISID/__Secure-3PAPISID. "
        "Нужна полная сессия YouTube (не гость)."
    )

_yt_lock = threading.Lock()
_yt_instance: YTMusic | None = None
_auth_json: str | None = None
_auth_file_path: str | None = None
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

    global _yt_instance
    with _yt_lock:
        _yt_instance = None

def _parse_headers_raw(raw: str) -> dict:

    raw = (raw or "").strip()
    if not raw:
        raise ValueError("empty headers")

    raw = raw.strip()

    if raw.startswith("{"):
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise ValueError("auth JSON must be an object")
        return data

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

    global _auth_json, _auth_account_hint, _auth_file_path
    import os
    import tempfile

    raw = (raw or "").strip()
    if not raw:
        raise ValueError("empty headers")

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

            setup_out = setup(headers_raw=raw)
            headers = json.loads(setup_out) if isinstance(setup_out, str) else dict(setup_out)
            headers = {str(k): str(v) for k, v in headers.items()}
        except Exception as exc:
            print(f"[ytm-backend] setup() failed ({exc!r}), using local parser")
            headers = {str(k): str(v) for k, v in _parse_headers_raw(raw).items()}

    def _get(h: dict, *names: str) -> str | None:
        lower = {k.lower(): v for k, v in h.items()}
        for n in names:
            if n.lower() in lower:
                return lower[n.lower()]
        return None

    cookie = _get(headers, "Cookie", "cookie")
    if not cookie:
        raise ValueError("В заголовках нет Cookie")

    if "SAPISID=" not in cookie and "__Secure-3PAPISID=" not in cookie and "__Secure-1PAPISID=" not in cookie:
        raise ValueError(
            "В Cookie нет SAPISID/__Secure-3PAPISID — войдите в аккаунт на music.youtube.com "
            "и дождитесь загрузки страницы (или вставьте headers из Chrome на ПК)."
        )

    headers.setdefault("Accept", "*/*")
    headers.setdefault("Content-Type", "application/json")
    headers.setdefault("X-Goog-AuthUser", "0")
    headers.setdefault("x-origin", "https://music.youtube.com")
    if not _get(headers, "User-Agent", "user-agent"):
        headers["User-Agent"] = (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

    for k in list(headers.keys()):
        if k.lower() == "cookie" and k != "Cookie":
            headers["Cookie"] = headers.pop(k)
            break
    else:
        headers["Cookie"] = cookie

    origin = _get(headers, "x-origin", "origin") or "https://music.youtube.com"
    from ytmusicapi.helpers import get_authorization
    sapisid = _extract_sapisid(cookie)
    auth_header = get_authorization(f"{sapisid} {origin}")
    headers["Authorization"] = auth_header
    print(f"[ytm-backend] Authorization generated, sapisid_prefix={sapisid[:8]}...")

    for k in list(headers.keys()):
        if k.lower() == "authorization" and k != "Authorization":
            headers["Authorization"] = headers.pop(k)
        if k.lower() == "x-goog-authuser" and k != "X-Goog-AuthUser":
            headers["X-Goog-AuthUser"] = headers.pop(k)

    path = _auth_file_default()

    def _write_and_open(authuser: str):
        headers["X-Goog-AuthUser"] = str(authuser)

        headers["Authorization"] = get_authorization(f"{sapisid} {origin}")
        auth_str_local = json.dumps(headers, ensure_ascii=False)
        with open(path, "w", encoding="utf-8") as f:
            f.write(auth_str_local)
        return auth_str_local, YTMusic(path)

    def _session_works(yt: "YTMusic") -> tuple[bool, str]:

        last_err = ""
        signs_of_guest = ("Sign in", "Looking for what you", "messageRenderer")

        try:
            liked = yt.get_liked_songs(limit=1)
            if isinstance(liked, dict) and "tracks" in liked:
                n = len(liked.get("tracks") or [])
                return True, f"Лайки OK ({n}+)"
            last_err = f"liked unexpected: {type(liked).__name__}"
        except Exception as exc:
            last_err = str(exc)
            print(f"[ytm-backend] liked check: {exc!r}")
            if any(s in last_err for s in signs_of_guest):
                return False, last_err

        try:
            pls = yt.get_library_playlists(limit=5)
            if isinstance(pls, list) and len(pls) > 0:
                return True, f"Плейлисты OK ({len(pls)})"

            if isinstance(pls, list) and last_err == "":

                pass
        except Exception as exc:
            last_err = str(exc)
            print(f"[ytm-backend] library playlists check: {exc!r}")
            if any(s in last_err for s in signs_of_guest):
                return False, last_err

        try:
            hist = yt.get_history()
            if isinstance(hist, list) and len(hist) > 0:
                return True, f"История OK ({len(hist)})"
        except Exception as exc:
            last_err = str(exc)
            print(f"[ytm-backend] history check: {exc!r}")

        return False, last_err or "нет подтверждения авторизованной сессии"

    preferred = str(_get(headers, "X-Goog-AuthUser", "x-goog-authuser") or "0")
    authuser_order = [preferred] + [str(i) for i in range(5) if str(i) != preferred]

    last_detail = ""
    auth_str = ""
    for au in authuser_order:
        try:
            auth_str, test = _write_and_open(au)
            print(f"[ytm-backend] trying authuser={au} keys={sorted(headers.keys())}")
            ok, detail = _session_works(test)
            if ok:
                _auth_account_hint = "Signed in"
                _auth_json = auth_str
                _auth_file_path = path
                _reset_yt()
                return {"ok": True, "accountName": _auth_account_hint, "authJson": auth_str}
            last_detail = detail
        except Exception as exc:
            last_detail = str(exc)
            print(f"[ytm-backend] authuser={au} failed: {exc!r}")

    msg = last_detail or "unknown"
    if "Sign in" in msg or "Looking for what you" in msg or "messageRenderer" in msg:
        raise ValueError(
            "YouTube не принял сессию (ответ Sign in). "
            "WebView-cookies часто недостаточны. Надёжный способ: "
            "на ПК Chrome → music.youtube.com → F12 → Network → browse → "
            "Copy request headers → в приложении «Sign in with headers»."
        )
    raise ValueError(
        "Сессия не принята YouTube. "
        f"Деталь: {msg[:200]}"
    )

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

    songs_section = details.get("songs") or {}
    raw_songs = list(songs_section.get("results") or [])
    songs_browse = songs_section.get("browseId")
    if songs_browse:
        try:
            pl = _get_yt().get_playlist(songs_browse, limit=None)
            pl_tracks = pl.get("tracks") if isinstance(pl, dict) else None
            if pl_tracks:
                raw_songs = list(pl_tracks)
        except TypeError:
            try:
                pl = _get_yt().get_playlist(songs_browse)
                pl_tracks = pl.get("tracks") if isinstance(pl, dict) else None
                if pl_tracks:
                    raw_songs = list(pl_tracks)
            except Exception as exc:
                print(f"[ytm-backend] get_playlist songs fallback: {exc!r}")
        except Exception as exc:
            print(f"[ytm-backend] get_playlist songs fallback: {exc!r}")

    seen_vids = set()
    songs = []
    for i in raw_songs:
        t = _to_track(i)
        if not t:
            continue
        vid = t.get("videoId")
        if not vid or vid in seen_vids:
            continue
        seen_vids.add(vid)
        songs.append(t)

    return {
        "artistId": browse_id,
        "name": details.get("name") or fallback_name or "Unknown",
        "thumbnail": _upscale_thumbnail(fallback_thumbnail) or _pick_thumbnail(details.get("thumbnails")),
        "albums": albums,
        "singles": singles,
        "songs": songs,
    }

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

def _download_audio_file(video_id: str) -> tuple[str, str, str]:

    import os
    import shutil

    work = os.path.join(_android_files_dir(), "ytm_dl_tmp", video_id)
    if os.path.isdir(work):
        shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work, exist_ok=True)

    url = f"https://music.youtube.com/watch?v={video_id}"
    outtmpl = os.path.join(work, f"{video_id}.%(ext)s")
    last_err: Exception | None = None

    try:
        ydl_opts = {
            "format": "bestaudio/best",
            "outtmpl": outtmpl,
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "noprogress": True,
            "restrictfilenames": True,

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

    def log_message(self, format: str, *args) -> None:
        pass

    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error_json(self, status: int, message: str) -> None:
        self._send_json(status, {"detail": message})

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        try:
            if path == "/health":
                self._send_json(200, {"status": "ok"})
                return

            if path == "/search/artists":

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
                seen_album_vids = set()
                for item in album.get("tracks", []) or []:
                    if not isinstance(item, dict):
                        continue
                    track = _to_track(item)
                    if not track:
                        continue
                    vid = track.get("videoId")
                    if not vid or vid in seen_album_vids:
                        continue
                    seen_album_vids.add(vid)

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

            if path == "/lyrics":

                qs = parse_qs(parsed.query)
                title = (qs.get("title") or [""])[0].strip()
                artist = (qs.get("artist") or [""])[0].strip()
                album = (qs.get("album") or [""])[0].strip()
                try:
                    duration = int(float((qs.get("duration") or ["0"])[0]))
                except Exception:
                    duration = 0
                if not title:
                    self._send_error_json(400, "title required")
                    return
                try:
                    import urllib.request
                    import urllib.parse

                    def _get(url: str) -> str | None:
                        req = urllib.request.Request(
                            url,
                            headers={
                                "User-Agent": "GammaTunes/0.3 (Android; lrclib)",
                                "Accept": "application/json",
                            },
                        )
                        with urllib.request.urlopen(req, timeout=12) as resp:
                            return resp.read().decode("utf-8", errors="replace")

                    def _enc(s: str) -> str:
                        return urllib.parse.quote(s or "")

                    candidates = []
                    if artist:
                        if duration > 0:
                            candidates.append(
                                f"https://lrclib.net/api/get?artist_name={_enc(artist)}"
                                f"&track_name={_enc(title)}&album_name={_enc(album)}&duration={duration}"
                            )
                        candidates.append(
                            f"https://lrclib.net/api/search?artist_name={_enc(artist)}&track_name={_enc(title)}"
                        )
                    candidates.append(f"https://lrclib.net/api/search?q={_enc((artist + ' ' + title).strip())}")
                    candidates.append(f"https://lrclib.net/api/search?track_name={_enc(title)}")

                    best_synced = None
                    best_plain = None
                    best_dur_diff = 10**9

                    for url in candidates:
                        try:
                            body = _get(url)
                        except Exception as exc:
                            print(f"[ytm-backend] lyrics fetch {url}: {exc!r}")
                            continue
                        if not body:
                            continue
                        body = body.strip()
                        items = []
                        try:
                            if body.startswith("["):
                                items = json.loads(body)
                            else:
                                items = [json.loads(body)]
                        except Exception:
                            continue
                        if not isinstance(items, list):
                            continue
                        for it in items:
                            if not isinstance(it, dict):
                                continue
                            synced = it.get("syncedLyrics") or ""
                            plain = it.get("plainLyrics") or ""
                            d = it.get("duration") or 0
                            try:
                                d = float(d)
                            except Exception:
                                d = 0
                            diff = abs(d - duration) if duration > 0 and d > 0 else 9999
                            if synced and (best_synced is None or diff < best_dur_diff):
                                best_synced = synced
                                best_dur_diff = diff
                            if plain and best_plain is None:
                                best_plain = plain

                    if best_synced:
                        self._send_json(200, {
                            "ok": True,
                            "synced": True,
                            "source": "lrclib",
                            "lrc": best_synced,
                        })
                        return
                    if best_plain:
                        self._send_json(200, {
                            "ok": True,
                            "synced": False,
                            "source": "lrclib",
                            "plain": best_plain,
                        })
                        return
                    self._send_json(404, {"ok": False, "error": "No lyrics found"})
                except Exception as exc:
                    import traceback
                    print(f"[ytm-backend] /lyrics failed:\n{traceback.format_exc()}")
                    self._send_error_json(502, f"lyrics failed: {exc}")
                return

            if path == "/liked":
                if _auth_json is None and not _auth_file_path:
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

            if path == "/playlists":
                if _auth_json is None and not _auth_file_path:
                    self._send_error_json(401, "Not authenticated")
                    return
                limit = 50
                try:
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
                if not playlist_id or playlist_id == "add":
                    self._send_error_json(400, "playlistId required")
                    return
                limit = 100
                try:
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

            self._send_error_json(404, "Not found")
        except Exception as exc:
            try:
                self._send_error_json(500, str(exc))
            except Exception:
                pass

    def do_POST(self) -> None:
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

            if path == "/playlists/add":
                if _auth_json is None:
                    self._send_error_json(401, "Not authenticated")
                    return
                playlist_id = payload.get("playlistId") or ""
                video_id = payload.get("videoId") or ""
                if not playlist_id or not video_id:
                    self._send_error_json(400, "playlistId and videoId are required")
                    return
                try:
                    _get_yt().add_playlist_items(playlist_id, [video_id], duplicates=False)
                    self._send_json(200, {
                        "ok": True,
                        "playlistId": playlist_id,
                        "videoId": video_id,
                    })
                except TypeError:
                    try:
                        _get_yt().add_playlist_items(playlist_id, [video_id])
                        self._send_json(200, {
                            "ok": True,
                            "playlistId": playlist_id,
                            "videoId": video_id,
                        })
                    except Exception as exc:
                        self._send_error_json(502, f"add_playlist_items failed: {exc}")
                except Exception as exc:
                    self._send_error_json(502, f"add_playlist_items failed: {exc}")
                return

            self._send_error_json(404, "Not found")
        except Exception as exc:
            try:
                self._send_error_json(500, str(exc))
            except Exception:
                pass

_server: ThreadingHTTPServer | None = None
_server_lock = threading.Lock()

def start(port: int = 8765) -> None:

    global _server
    with _server_lock:
        if _server is not None:
            return
        _server = ThreadingHTTPServer(("127.0.0.1", port), _Handler)
    _server.serve_forever()
