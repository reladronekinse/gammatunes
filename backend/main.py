from __future__ import annotations

import re
import time
from typing import Any
from urllib.parse import parse_qs, urlparse

import yt_dlp
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from ytmusicapi import YTMusic

app = FastAPI(title="YTM Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

yt = YTMusic()


class Track(BaseModel):
    videoId: str
    title: str
    artist: str
    album: str | None = None
    thumbnail: str | None = None
    durationSeconds: int | None = None
    artistId: str | None = None


class SearchResponse(BaseModel):
    results: list[Track]


class Album(BaseModel):
    albumId: str
    title: str
    thumbnail: str | None = None
    year: str | None = None


class Artist(BaseModel):
    artistId: str
    name: str
    thumbnail: str | None = None
    albums: list[Album] = []


class ArtistSearchResponse(BaseModel):

    artists: list[Artist]


class AlbumTracksResponse(BaseModel):
    albumId: str
    title: str
    thumbnail: str | None = None
    tracks: list[Track]


class StreamResponse(BaseModel):
    videoId: str
    streamUrl: str
    mimeType: str
    bitrate: int
    httpHeaders: dict[str, str] = {}


_THUMBNAIL_SIZE = 544
_THUMBNAIL_SIZE_RE = re.compile(r"=w\d+-h\d+")


def _upscale_thumbnail(url: str | None) -> str | None:
    if not url:
        return None
    if "googleusercontent.com" in url and _THUMBNAIL_SIZE_RE.search(url):
        return _THUMBNAIL_SIZE_RE.sub(f"=w{_THUMBNAIL_SIZE}-h{_THUMBNAIL_SIZE}", url)
    return url


def _pick_thumbnail(thumbnails: list[dict[str, Any]] | None) -> str | None:
    if not thumbnails:
        return None
    return _upscale_thumbnail(thumbnails[-1].get("url"))


def _to_track(item: dict[str, Any]) -> Track | None:
    video_id = item.get("videoId")
    if not video_id:
        return None
    artists = item.get("artists") or []
    artist_name = artists[0]["name"] if artists else item.get("artist", "Unknown")
    artist_id = None
    if artists:
        artist_id = artists[0].get("id") or artists[0].get("browseId")
    duration = item.get("duration_seconds")
    return Track(
        videoId=video_id,
        title=item.get("title", "Unknown"),
        artist=artist_name,
        album=(item.get("album") or {}).get("name") if isinstance(item.get("album"), dict) else None,
        thumbnail=_pick_thumbnail(item.get("thumbnails")),
        durationSeconds=duration,
        artistId=artist_id,
    )


def _to_album(item: dict[str, Any]) -> Album | None:
    browse_id = item.get("browseId")
    if not browse_id:
        return None
    return Album(
        albumId=browse_id,
        title=item.get("title", "Unknown"),
        thumbnail=_pick_thumbnail(item.get("thumbnails")),
        year=item.get("year"),
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/search", response_model=SearchResponse)
def search(q: str = Query(..., min_length=1), limit: int = 25) -> SearchResponse:
    try:
        raw = yt.search(q, filter="songs", limit=limit)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"YTMusic search failed: {exc}") from exc

    tracks = [t for t in (_to_track(item) for item in raw) if t is not None]
    return SearchResponse(results=tracks)


@app.get("/search/artists", response_model=ArtistSearchResponse)
def search_artists(q: str = Query(..., min_length=1), limit: int = 20) -> ArtistSearchResponse:
    try:
        raw = yt.search(q, filter="artists", limit=limit)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"YTMusic artist search failed: {exc}") from exc

    artists: list[Artist] = []
    for item in raw:
        browse_id = item.get("browseId")
        if not browse_id:
            continue
        artists.append(
            Artist(
                artistId=browse_id,
                name=item.get("artist") or item.get("title") or "Unknown",
                thumbnail=_pick_thumbnail(item.get("thumbnails")),
            )
        )
    return ArtistSearchResponse(artists=artists)


@app.get("/artists/{artist_id}", response_model=Artist)
def artist_detail(artist_id: str) -> Artist:
    try:
        details = yt.get_artist(artist_id)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"YTMusic artist fetch failed: {exc}") from exc

    albums_section = details.get("albums") or {}
    raw_albums = albums_section.get("results") or []

    params = albums_section.get("params")
    albums_browse_id = albums_section.get("browseId")
    if params and albums_browse_id:
        try:
            full_albums = yt.get_artist_albums(albums_browse_id, params, limit=None)
        except TypeError:
            full_albums = yt.get_artist_albums(albums_browse_id, params)
        except Exception as exc:
            full_albums = None
            print(f"[ytm-backend] get_artist_albums fallback to first page: {exc!r}")
        if full_albums:
            raw_albums = full_albums

    albums = [a for a in (_to_album(item) for item in raw_albums) if a is not None]
    return Artist(
        artistId=artist_id,
        name=details.get("name") or "Unknown",
        thumbnail=_pick_thumbnail(details.get("thumbnails")),
        albums=albums,
    )


@app.get("/albums/{album_id}", response_model=AlbumTracksResponse)
def album_tracks(album_id: str) -> AlbumTracksResponse:
    try:
        album = yt.get_album(album_id)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"YTMusic album fetch failed: {exc}") from exc

    album_thumbnail = _pick_thumbnail(album.get("thumbnails"))
    album_title = album.get("title", "Unknown")

    tracks: list[Track] = []
    for item in album.get("tracks", []):
        track = _to_track(item)
        if track is None:
            continue
        if track.thumbnail is None:
            track = track.model_copy(update={"thumbnail": album_thumbnail})
        if track.album is None:
            track = track.model_copy(update={"album": album_title})
        tracks.append(track)

    return AlbumTracksResponse(
        albumId=album_id,
        title=album_title,
        thumbnail=album_thumbnail,
        tracks=tracks,
    )


_STREAM_CACHE_SAFETY_SECONDS = 300
_stream_cache: dict[str, tuple[float, StreamResponse]] = {}


def _stream_expiry(stream_url: str) -> float:
    try:
        expire = parse_qs(urlparse(stream_url).query).get("expire", [None])[0]
        if expire:
            return float(expire) - _STREAM_CACHE_SAFETY_SECONDS
    except Exception:
        pass
    return time.time() + 3600


def _extract_stream(video_id: str) -> StreamResponse:
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
            raise HTTPException(status_code=404, detail="No audio stream found")
        fmt = max(audio_formats, key=lambda f: f.get("abr") or 0)
        stream_url = fmt["url"]

    http_headers = dict(fmt.get("http_headers") or info.get("http_headers") or {})

    result = StreamResponse(
        videoId=video_id,
        streamUrl=stream_url,
        mimeType=fmt.get("ext", "m4a"),
        bitrate=int(fmt.get("abr") or 0),
        httpHeaders=http_headers,
    )
    _stream_cache[video_id] = (_stream_expiry(stream_url), result)
    return result


@app.get("/stream/{video_id}", response_model=StreamResponse)
def stream(video_id: str) -> StreamResponse:
    try:
        return _extract_stream(video_id)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Stream extraction failed: {exc}") from exc
