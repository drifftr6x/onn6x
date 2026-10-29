# onn6x Launcher

Custom Android TV / Google TV home launcher for an Onn 4K Pro (Android 14). Kotlin + Jetpack Compose for TV.
Package `com.example.onnlauncher`. Current version 1.1.0 (versionCode 3). Repo: github.com/drifftr6x/onn6x.

## Environment
- Windows PC. Android Studio project at `C:\onn-launcher`. Box is reached over ADB wireless at `11.1.1.87:5555`.
- platform-tools: `C:\Users\operator\Downloads\platform-tools-latest-windows\platform-tools`.
- Always install the RELEASE apk (a debug apk has versionCode 1 and fails with INSTALL_FAILED_VERSION_DOWNGRADE):
  ```
  .\gradlew.bat assembleRelease
  adb install -r "C:\onn-launcher\app\build\outputs\apk\release\app-release.apk"
  ```
  Use the full path; a relative path gives "No such file".
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for every GitHub release (tag v1.0.0 already exists).
- No tests. Verify by building and trying it on the box.

## Stack
Compose BOM 2024.09.00, tv-material 1.0.0, material-icons-extended (R8 strips unused icons), OkHttp 4.12.0 (NAS sync only).
State: `AndroidViewModel` (`LauncherViewModel`) exposing `StateFlow<UiState>`. Settings in SharedPreferences via `LauncherPrefs`.

## Source map (`app/src/main/java/com/example/onnlauncher`)
- `MainActivity` – host, fonts/typography, dispatches keys to the VM, `playTrailer`, launch helpers, APK install.
- `HomeScreen` – rows, hero (full-screen backdrop + info), What's playing bar, My list, screensaver (`BackdropShow`).
- `Dialogs` – Settings, quick menu, title dialog (trailer / my list / where to watch), editor PIN dialog.
- `LauncherViewModel` – almost all logic: rows, recents, watchlist, TMDB rows, editor start/stop (1 h, wake + Wi-Fi locks), NAS sync scheduling.
- `HomeOverrideService` – AccessibilityService: bounces the stock launcher to ours, double Home press = quick menu, remap keys, sleep timer, Back-from-trailer return.
- `WebEditorServer` + `assets/editor.html` – phone editor (see below).
- `NowPlayingService` – NotificationListener + MediaSession for the What's playing bar.
- `TmdbClient`, `WeatherClient`, `CryptoClient`, `SmartHome` (Home Assistant), `WallpaperSync` (NAS), `Dedupe` (content hash), `Wallpaper`, `TileImages`, `Theme`, `ThemePattern`, `Fonts`, `Options`, `DefaultRows`, `DeepLinks`, `KeyBus`.

## Things that are easy to break
- **Trailers**: `playTrailer` opens the regular YouTube TV app (`com.google.android.youtube.tv`) with `NEW_TASK | CLEAR_TASK`, and skips any package containing "kids". The user's YouTube profile must NOT be Kids. Back returns to the launcher via `HomeOverrideService.armTrailerReturn()` (10 min window, 500 ms delay). Removing CLEAR_TASK was tried and reverted; leave it.
- **Quick menu on wake**: Home pressed shortly after wake must not open the quick menu (`homeIsWakeUp()`, `WAKE_QUIET_MS` = 60 s; double press passes `EXTRA_DOUBLE_PRESS`). The box does not emit screen on/off events, so this is key-idle based.
- **Home key** is never delivered to apps; only the accessibility service sees it.
- **Secrets** (TMDB key, HA token, NAS password) live in prefs, are excluded from backup export, and are kept across restore (`SECRET_KEYS`).
- **Phone editor server** is a hand-written HTTP server on raw sockets (ports 8080-8090, PIN in `X-Pin` header, or `pin=` query for the two image endpoints `/api/icon` and `/api/wallpaper/thumb`). Uploads go through `/api/upload` in 1 MB pieces with `probe`, `offset`, `total` and a 409 resync; partial files are `name.part`. `editor.html` is a single vanilla-JS page (helper `h()`, `api()`, `uploadFile()`); it is loaded from assets, so edit it as plain HTML/JS.
- **Duplicates**: same filename overwrites; identical content under another name is dropped (`Dedupe.findDuplicate`) for wallpaper uploads and NAS sync.
- **NAS wallpapers**: WebDAV (PROPFIND), plain HTML index, or a single image URL. Not SMB. Files are stored as `net_<name>`, max 60 files / 20 MB each, stale ones removed. Auto-sync every 6 h.
- Google Photos is not supported (Library API restricted since 2025-03-31).
- Google TV limits: the system screensaver cannot be replaced; ours is an in-app idle screen.

## One-time setup on the box
- What's playing: `adb shell cmd notification allow_listener com.example.onnlauncher/com.example.onnlauncher.NowPlayingService`
- Accessibility service ("Home override") must be enabled for Home bounce, double press, key remaps and trailer Back.
- TMDB key goes in the phone editor, Look tab (free key from themoviedb.org; either the v3 key or the long Bearer token works).
- Optional cleanup of old screensaver experiments:
  ```
  adb shell settings delete secure screensaver_components
  adb shell settings put secure screensaver_activate_on_sleep 0
  adb shell settings put system screen_off_timeout 600000
  ```

## Release
`git add . && git commit && git push`, then a GitHub Release with a new `vX.Y.Z` tag and `app-release.apk` attached (rename to `onn6x-launcher-X.Y.Z.apk`). `*.apk`, `build/`, `local.properties` are git-ignored.

## Ideas not done
Kids profile with PIN, SMB support.
