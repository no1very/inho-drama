# InhoDrama · NAS Short-Drama Streaming System

A short-drama streaming service you host on your own NAS. The interaction model follows the popular "Hongguo" short-drama apps: **swipe up/down on the home feed → tap an episode to play → auto-play the next episode when one finishes, with playback progress synced across devices**. It runs entirely inside your LAN and **does not depend on any external metadata (scraper) service**.

## Features

- **Three media sources**: local directories / WebDAV (Alist, Synology, etc.) / your existing **Jellyfin** library (reuses posters, descriptions, and durations)
- **Web H5 frontend**: one-click toggle between "video feed ↔ poster wall" home modes, fullscreen vertical swipe playback, episode selection, search, continue-watching, and a web player with prev/next controls (direct mp4 playback; Jellyfin mkv sources fall back to automatic HLS transcoding). The built frontend is **embedded into the backend binary** — delivered as a single container
- **Native Android app**: Kotlin + Jetpack Compose (**no WebView**) + Media3/ExoPlayer, dual-mode home, vertical swipe to switch episodes, prev/next buttons, auto-continue playback, and external SRT subtitles
- **Cross-device progress sync**: progress is stored server-side in SQLite keyed by "account + show + episode"; start an episode in the browser and continue right where you left off in the app. Progress survives re-scans and source changes. **Accidental-touch junk records are automatically ignored by the server, and finished shows disappear from the home page automatically** (still visible in history)
- **Zero scraper dependency**: show titles and episode counts come from file-naming conventions; covers use `poster.jpg` inside the directory; when no poster exists, ffmpeg captures a random video frame or generates a placeholder; `tvshow.nfo` descriptions are optionally read
- **Multi-architecture Docker**: x86_64 + ARM64 (buildx cross-compilation; no QEMU needed when building on an ARM NAS), single image with optional ffmpeg
- **Built-in admin panel**: add/edit/scan all three media sources, view scan status, and register users from the web UI

## Directory Layout

```
inho-drama/
├── docker-compose.yml        # one-command deployment
├── Dockerfile                # multi-stage multi-arch build (frontend + backend → single image)
├── docs/
│   ├── api.md                # HTTP API contract (the shared spec for all three clients)
│   ├── deploy.md             # deployment docs (compose / env vars / reverse proxy / backup & upgrade)
│   ├── naming.md             # file-naming conventions (episodes / covers / subtitles / encoding)
│   ├── webdav.md             # WebDAV setup guide (Alist / Synology / QNAP / OMV)
│   ├── jellyfin.md           # Jellyfin setup guide (API Key / HLS transcoding / permissions)
│   └── android.md            # Android app build & packaging guide
├── server/                   # Go backend (pure-Go SQLite driver, no CGO)
│   ├── main.go
│   ├── webdist/              # H5 build output embedded at build time
│   └── internal/
│       ├── api/              # routes / auth / streaming proxy / covers / subtitles / source mgmt
│       ├── store/            # SQLite storage layer
│       ├── source/           # media-source scanners + episode parsing
│       ├── scanner/          # scan queue & status
│       └── ffmpeg/           # optional frame-capture covers
├── web/                      # Vue 3 + Vite H5 frontend
└── android/                  # Kotlin Jetpack Compose Android app
```

## Quick Start

```bash
cd inho-drama
docker compose up -d --build
# open http://<NAS_IP>:8654 in your browser
# default account: admin / admin (change it as soon as possible)
```

Then go to "Me → Media Source Management" and add a source (local directory `/media` / WebDAV / Jellyfin). Once the automatic scan finishes, you can start watching.

The Android app source lives in `android/`; build steps are in [docs/android.md](docs/android.md).

## Documentation

| Doc | Contents |
|---|---|
| [Deployment](docs/deploy.md) | Compose deployment, env vars, multi-arch build & push, reverse proxy, backup & upgrade, FAQ |
| [File Naming](docs/naming.md) | Directory structure, episode naming, covers/descriptions/subtitles, web-friendly encoding |
| [WebDAV](docs/webdav.md) | Alist / Synology / QNAP / OMV address & credential setup, troubleshooting |
| [Jellyfin](docs/jellyfin.md) | Getting an API Key, user isolation, HLS transcoding, troubleshooting |
| [Android Build](docs/android.md) | Android Studio / CLI build, signing, common issues |
| [API Contract](docs/api.md) | All endpoints & fields (read this to write your own client) |

## Tech Stack

- **Backend**: Go 1.23 + `modernc.org/sqlite` (pure-Go SQLite, no CGO, naturally cross-architecture) + standard library `net/http`; WebDAV client `studio-b12/gowebdav`; bcrypt passwords; token auth
- **H5**: Vue 3 + Vite + vue-router(hash) + pinia + hls.js (fallback); the build output is `embed`ded into the binary
- **Android**: Kotlin 2.0 + Compose BOM + Navigation + Media3 1.4 + Retrofit + kotlinx-serialization + Coil + DataStore
- **Streaming**: local `http.ServeFile`; WebDAV/Jellyfin proxied server-side with `Range/206` passthrough; Jellyfin web-unfriendly containers get a 302 redirect to `master.m3u8` for transcoding

## AI Development Statement

This project is developed **entirely by AI**: from requirements analysis, architecture design, backend (Go), H5 frontend (Vue3), Android client (Kotlin/Jetpack Compose), testing, and documentation, to **open-sourcing and version releases on GitHub (including pushing to GitHub)** — all of it is carried out by AI.

## Disclaimer

This project only provides private media management capabilities. Please make sure the content you connect is media for which **you hold legal rights**; do not use it to distribute copyright-protected content.
