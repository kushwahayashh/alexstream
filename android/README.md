# AlexStream — Android TV (hybrid WebView + native ExoPlayer)

A native Android TV app, modelled on the ALEX architecture:

- **UI is bundled in the APK** (`app/src/main/assets/` = the web app),
  loaded instantly from local storage — no network round-trip for the UI.
- **Browse/API networking is JavaScript.** The bundled WebView UI uses normal
  `fetch()` for TMDB proxy and `/api/*` calls to the hosted Modal backend.
- **Video plays in a native ExoPlayer** (`PlayerActivity` / `PlayerScreen`,
  ported from ALEX) with D-pad controls, seek, and track menus — not HTML5
  `<video>`. External English subtitles from `/api/subtitles` are sideloaded.

## The one thing you must set

Put your deployed **Modal backend URL** in
`app/src/main/java/com/alexstream/tv/BackendConfig.kt`:

```kotlin
const val BASE_URL = "https://your-app--whatever.modal.run"
```

That's the only required edit. It's used by the bundled JS (via
`AndroidBridge.backendBase()`) and by native subtitle fetching, so set it once
here.

TMDB browsing works without the backend; search/resolve/streaming need it.

## Build

```bash
cd android
./gradlew assembleDebug      # APK -> app/build/outputs/apk/debug/app-debug.apk
```

(Gradle wrapper jar is committed. Or open `android/` in Android Studio.)

## Install on a TV / emulator

```bash
adb connect <tv-ip>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

Appears in the Android TV Apps row (LEANBACK_LAUNCHER).

## How a stream flows

1. Web UI (in WebView) browses TMDB, resolves a title to a ShowBox id, and
   lists quality options with JavaScript `fetch()` calls to the Modal backend.
2. Selecting a quality calls `AndroidBridge.play(url, ext, title, fid)`.
3. `PlayerActivity` opens, fetches `/api/subtitles?fid=…` natively, and plays
   the stream in ExoPlayer with subtitles sideloaded.

## Notes / current scope

- **Resume position is intentionally off** (no Continue-Watching row in the UI
  yet). `PlaybackProgressStore` is included but the player is launched with a
  blank `mediaPath`, so nothing is tracked. Flip this on later by passing a
  stable id as `mediaPath`.
- The stream URL handed to ExoPlayer is the backend's already-proxied
  `proxiedUrl`, so there's no second proxy layer.
- `minSdk 28` (matches the Media3/Compose player stack); covers all modern
  Android TV devices.
- Lucide icons and Google Fonts still load from CDNs in the web UI; they
  degrade gracefully if offline.
