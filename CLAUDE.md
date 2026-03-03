# Maestro Android

YouTube music player for Android with MCP server integration. Companion to the Maestro desktop Electron app.

## Architecture

```
[YouTube] ←→ [maestro-server (yt-dlp, port 29171)] ←→ [Maestro Android (ExoPlayer)]
                                                            ↑
                                                    [MCP clients (port 29170)]
```

### maestro-server (Python)
Lightweight FastAPI service in `server/` wrapping yt-dlp for YouTube search and stream URL extraction.

| Endpoint | Description |
|----------|-------------|
| `GET /health` | Health check |
| `GET /search?q=&limit=5` | YouTube search → track list |
| `GET /extract?id=VIDEO_ID` | Extract CDN stream URL |

### Android App
Kotlin + Jetpack Compose, Material 3 dark theme matching desktop (`#1a1a2e` bg, `#e94560` primary).

#### Package Structure
```
com.maestro.android/
├── MaestroApp.kt              # Application: notification channel, PlayerController init, MCP server start
├── MainActivity.kt            # Single activity, Compose setContent
├── data/
│   ├── model/Models.kt        # Track, PlayerState, PlaybackState, LoopMode
│   ├── remote/MaestroApi.kt   # Ktor client → maestro-server
│   └── datastore/AppDataStore.kt  # DataStore persistence (queue, history, volume, loop, server URL)
├── player/
│   ├── PlayerController.kt    # Singleton: state management, queue, history, playback callbacks
│   └── PlaybackService.kt     # MediaSessionService + ExoPlayer, foreground notification, background playback
├── mcp/
│   ├── McpServer.kt           # Ktor Netty server on port 29170 (SSE + streamable HTTP transports)
│   └── McpTools.kt            # 11 MCP tools matching desktop
├── ui/
│   ├── theme/Theme.kt         # Material 3 dark color scheme
│   ├── screen/MainScreen.kt   # Tab layout (Search + Queue) + NowPlayingBar
│   ├── component/             # SearchPanel, QueuePanel, NowPlayingBar, TrackItem
│   └── viewmodel/PlayerViewModel.kt
└── util/DurationFormat.kt
```

#### Key Design
- **PlayerController**: Singleton managing all state via `StateFlow<PlayerState>`. Callbacks (`onPlayUrl`, `onPause`, etc.) are set by `PlaybackService` to bridge to ExoPlayer.
- **PlaybackService**: `MediaSessionService` subclass — ExoPlayer with audio focus, `handleAudioBecomingNoisy`, foreground notification via MediaSession. Background playback supported.
- **MCP Server**: Ktor Netty on port 29170. JSON-RPC over SSE (`/sse` + `/messages`) and streamable HTTP (`/mcp`). Same 11 tools as desktop.
- **Persistence**: DataStore Preferences for queue, history, volume, loop mode, server URL. Survives app restart.

#### MCP Tools
`play_music`, `pause_music`, `resume_music`, `skip_music`, `stop_music`, `add_to_queue`, `remove_from_queue`, `get_music_state`, `set_volume`, `get_recently_played`, `search_music`

## Development

```bash
# Server
cd server && pip install -r requirements.txt && python main.py

# Android
# Open root in Android Studio, sync Gradle, run on device/emulator
```

## Dependencies
- **Server**: FastAPI, uvicorn, yt-dlp
- **Android**: Jetpack Compose (BOM), Media3 ExoPlayer + MediaSession, Ktor (client + server), kotlinx-serialization, DataStore, Coil
