from __future__ import annotations

import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import yt_dlp
from ytmusicapi import YTMusic


_yt_lock = threading.Lock()
_yt_instance: YTMusic | None = None
_auth_json: str | None = None
_auth_account_hint: str | None = None


def _get_yt() -> YTMusic:
    global _yt_instance
    with _yt_lock:
        if _yt_instance is None:
            if _auth_json:
                try:
                    _yt_instance = YTMusic(_auth_json)
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
        raise ValueError("В заголовках нет Cookie — без него вход невозможен")

    headers.setdefault("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
    headers.setdefault("Accept", "*/*")
    headers.setdefault("Accept-Language", "en-US,en;q=0.5")
    headers.setdefault("Content-Type", "application/json")
    headers.setdefault("X-Goog-AuthUser", "0")
    headers.setdefault("x-origin", "https://music.youtube.com")
    return headers


def _set_auth_from_raw(raw: str) -> dict:
    global _auth_json, _auth_account_hint
    headers = _parse_headers_raw(raw)
    auth_str = json.dumps(headers)
    test = YTMusic(auth_str)
    try:
        liked = test.get_liked_songs(limit=1)
        tracks = liked.get("tracks") if isinstance(liked, dict) else liked
        count_hint = len(tracks) if tracks else 0
        _auth_account_hint = f"Лайки доступны (проверка: {count_hint}+)"
    except Exception as exc:
        _auth_account_hint = "Сессия принята"
        print(f"[ytm-backend] liked check: {exc!r}")

    _auth_json = auth_str
    _reset_yt()
    return {"ok": True, "accountName": _auth_account_hint, "authJson": auth_str}


def _clear_auth() -> None:
    global _auth_json, _auth_account_hint
    _auth_json = None
    _auth_account_hint = None
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
    return {
        "videoId": video_id,
        "title": item.get("title", "Unknown"),
        "artist": artist_name,
        "album": album.get("name") if isinstance(album, dict) else None,
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
    return {
        "artistId": browse_id,
        "name": details.get("name") or fallback_name or "Unknown",
        "thumbnail": _upscale_thumbnail(fallback_thumbnail) or _pick_thumbnail(details.get("thumbnails")),
        "albums": albums,
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
        "format": "bestaudio/best",
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
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
                for item in album.get("tracks", []):
                    track = _to_track(item)
                    if not track:
                        continue
                    if track.get("thumbnail") is None:
                        track["thumbnail"] = album_thumbnail
                    if track.get("album") is None:
                        track["album"] = album_title
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
                    self._send_error_json(502, f"get_liked_songs failed: {exc}")
                    return
                raw_tracks = liked.get("tracks") if isinstance(liked, dict) else (liked or [])
                tracks = [t for t in (_to_track(i) for i in (raw_tracks or [])) if t]
                self._send_json(200, {"results": tracks})
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

            if path == "/auth/login":
                raw = payload.get("headersRaw") or payload.get("headers") or ""
                if not raw:
                    self._send_error_json(400, "headersRaw is required")
                    return
                try:
                    result = _set_auth_from_raw(raw)
                    self._send_json(200, result)
                except Exception as exc:
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


_server: ThreadingHTTPServer | None = None
_server_lock = threading.Lock()


def start(port: int = 8765) -> None:
    global _server
    with _server_lock:
        if _server is not None:
            return
        _server = ThreadingHTTPServer(("127.0.0.1", port), _Handler)
    _server.serve_forever()
