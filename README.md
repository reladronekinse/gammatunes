# GammaTunes

An unofficial Android client for YouTube Music.

The app ships with a small backend that runs directly on the device (via
[Chaquopy](https://chaquo.com/chaquopy/)) to talk to YouTube Music, so no
separate server is required to use the app. A standalone version of the same
backend is also included under `backend/`, in case you'd rather run it on a
PC or server instead of on-device.

## Project structure

```
android/    Android client (Kotlin, Jetpack Compose)
backend/    Standalone FastAPI backend (optional, PC/server use)
```

## Building

### Android app

Requirements: Android Studio (or the command-line Android SDK/build-tools),
a JDK 17, and Python 3.11 on the **build machine** (needed by the Chaquopy
plugin to resolve `ytmusicapi` / `yt-dlp` into the APK). No Python install is
required on the phone.

```bash
cd android
./gradlew assembleDebug
```

The resulting APK will be at
`android/app/build/outputs/apk/debug/app-debug.apk`.

### Standalone backend (optional)

```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

To point the app at an external backend instead of the on-device one, change
`LocalBackend.BASE_URL` in
`android/app/src/main/java/com/gammatunes/app/backend/LocalBackend.kt`.

## Features (0.2)

- Embedded Python backend (ytmusicapi + yt-dlp) via Chaquopy
- Material 3 UI with Liquid Glass surfaces
- Search (artists + tracks), artist/album detail
- Playback via ExoPlayer + MediaSession foreground service (notification controls)
- Queue next/previous, repeat modes, seek
- Offline downloads (tracks and full albums)
- Browser-header login for likes and library playlists
- Account screen (liked songs, library playlists)
- Appearance settings (cover style, seek bar, accents)
- EN / RU localization

## Acknowledgments

GammaTunes builds on the work of several open-source projects:

- [ytmusicapi](https://github.com/sigma67/ytmusicapi) — unofficial YouTube
  Music API used for search, artist, and album metadata.
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — used to resolve playable audio
  stream URLs.
- [FastAPI](https://github.com/fastapi/fastapi), [Uvicorn](https://github.com/encode/uvicorn),
  and [Pydantic](https://github.com/pydantic/pydantic) — power the standalone
  backend.
- [Chaquopy](https://chaquo.com/chaquopy/) — embeds a Python interpreter in
  the Android app so the backend can run on-device.
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and the
  AndroidX libraries (Lifecycle, Navigation, Media3/ExoPlayer) — the app's UI
  toolkit and media playback stack.
- [Retrofit](https://github.com/square/retrofit) and [OkHttp](https://github.com/square/okhttp)
  (Square) — networking.
- [Coil](https://github.com/coil-kt/coil) — image loading in Compose.
- [Gson](https://github.com/google/gson) — JSON parsing (via Retrofit's
  converter).

This is an unofficial, community project and is not affiliated with,
endorsed by, or sponsored by Google or YouTube.

## License

Licensed under the Apache License, Version 2.0 — see [LICENSE](LICENSE) for
the full text.
