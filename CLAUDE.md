# Maestro Android

YouTube music player for Android with MCP server integration. Companion to the Maestro desktop Electron app.

## Architecture

```
[YouTube] ←→ [NewPipeExtractor (in-process)] ←→ [Maestro Android (ExoPlayer)]
                                                       ↑
                                               [MCP clients (port 29170)]
```

YouTube search and stream-URL extraction run **in-process** via NewPipeExtractor —
there is no separate server. (Earlier versions used a Python `maestro-server`
wrapping yt-dlp; that has been removed.) When YouTube changes break playback,
bump the `NewPipeExtractor` version in `app/build.gradle.kts`.

### Android App
Kotlin + Jetpack Compose, Material 3 dark theme matching desktop (`#1a1a2e` bg, `#e94560` primary).

#### Package Structure
```
com.maestro.android/
├── MaestroApp.kt              # Application: notification channel, PlayerController init, MCP server start
├── MainActivity.kt            # Single activity, Compose setContent
├── data/
│   ├── model/Models.kt        # Track, PlayerState, PlaybackState, LoopMode
│   ├── remote/MaestroApi.kt   # NewPipeExtractor wrapper (search + stream extraction)
│   ├── remote/NewPipeOkHttpDownloader.kt  # OkHttp-backed NewPipe Downloader
│   └── datastore/AppDataStore.kt  # DataStore persistence (queue, history, saved library, volume, loop)
├── player/
│   ├── PlayerController.kt    # Singleton: state management, queue, history, offline library, playback callbacks
│   └── PlaybackService.kt     # MediaSessionService + ExoPlayer, permanent audio cache, foreground notification
├── mcp/
│   ├── McpServer.kt           # Ktor Netty server on port 29170 (SSE + streamable HTTP transports)
│   └── McpTools.kt            # 11 MCP tools matching desktop
├── ui/
│   ├── theme/Theme.kt         # Material 3 dark color scheme
│   ├── screen/MainScreen.kt   # Tab layout (Search + Saved + Queue) + NowPlayingBar
│   ├── component/             # SearchPanel, SavedPanel, QueuePanel, NowPlayingBar, TrackItem
│   └── viewmodel/PlayerViewModel.kt
└── util/DurationFormat.kt
```

#### Key Design
- **PlayerController**: Singleton managing all state via `StateFlow<PlayerState>`. Callbacks (`onPlayUrl`, `onPause`, etc.) are set by `PlaybackService` to bridge to ExoPlayer.
- **PlaybackService**: `MediaSessionService` subclass — ExoPlayer with audio focus, `handleAudioBecomingNoisy`, foreground notification via MediaSession. Background playback supported.
- **Offline cache**: ExoPlayer `SimpleCache` under `filesDir/audio_cache` with `NoOpCacheEvictor` — every played track is kept on disk permanently. `PlaybackService.fullyCachedTrackIds()` reports which tracks are completely downloaded; `PlayerController` skips network extraction and plays straight from cache when a track is fully cached, so saved songs play with no internet.
- **MCP Server**: Ktor Netty on port 29170. JSON-RPC over SSE (`/sse` + `/messages`) and streamable HTTP (`/mcp`). Same 11 tools as desktop.
- **Persistence**: DataStore Preferences for queue, history, saved library, volume, loop mode. Survives app restart.

#### MCP Tools
`play_music`, `pause_music`, `resume_music`, `skip_music`, `stop_music`, `add_to_queue`, `remove_from_queue`, `get_music_state`, `set_volume`, `get_recently_played`, `search_music`

## Development

```bash
# Android — open root in Android Studio, sync Gradle, run on device/emulator.
# No server to run: YouTube extraction is in-process via NewPipeExtractor.
```

## Dependencies
- Jetpack Compose (BOM), Media3 ExoPlayer + MediaSession + Cache, NewPipeExtractor, OkHttp, Ktor (server), kotlinx-serialization, DataStore, Coil
