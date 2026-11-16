# RFeedReader

An offline-first podcast player for Android, built with Kotlin and Jetpack Compose.

RFeedReader was designed for the gym: large text, large touch targets and one-handed use.
It subscribes to RSS/Atom podcast feeds, downloads episodes in the background and plays
**only files that are fully downloaded and verified on the device**. There is no streaming
and no fallback to remote URLs, so playback never stalls because of a weak connection.

It also runs on old hardware: the reference device is an Amazon Fire 7 (2015) running
LineageOS 12 (Android 5.1.1, API 22).

> The app UI is in Italian.

## Features

- RSS 2.0 and Atom feeds, with redirect handling and episode de-duplication
- Background downloads with WorkManager, optional Wi-Fi-only mode, resumable and crash-safe
- Offline playback with Media3 (ExoPlayer + MediaSession), background audio and lock-screen controls
- Playback position saved and restored, sleep timer
- OPML import and export of subscriptions
- Optional periodic feed refresh
- Everything stays on the device: no accounts, no cloud, no telemetry

## Offline-first rule

An episode can be played only when **all** of these are true:

1. its state in the database is `DOWNLOADED`;
2. its file lives in the app's private `filesDir/episodes` folder;
3. the file exists and is not empty (checked by `OfflineFileVerifier`).

Downloads are written to a temporary `.part` file and renamed only after the HTTP response,
MIME type and size have been validated. `QUEUED`, `DOWNLOADING`, `FAILED` and
`NOT_DOWNLOADED` episodes are never handed to the player.

## Requirements

| | |
|---|---|
| Minimum Android version | 5.1 (API 22) |
| Target / compile SDK | 37 |
| JDK | 17 |
| Gradle | 9.8.1 (wrapper included) |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.4.10 |

Library versions are capped so that they still support API 22 (for example Coil 2.x instead
of Coil 3, Room 2.7.2, Media3 1.3.1). `app/build.gradle.kts` is the source of truth.

### Older devices and TLS

Android 5.1 does not trust the Let's Encrypt root (ISRG Root X1). RFeedReader adds it to the
system trust store at runtime (`core/network/LegacyTrust.kt`) for both `HttpURLConnection`
and Coil's OkHttp client. Certificate validation is never disabled.

## Build

Open the project in Android Studio and wait for Gradle sync, or use the command line:

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:assembleRelease        # minified release APK
```

On Windows use `gradlew.bat` instead of `./gradlew`.

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> The release build is signed with the local **debug** keystore
> (`~/.android/debug.keystore`). This makes it easy to install an optimized build on a
> test device, but the result is not suitable for distribution. Configure your own signing
> key before publishing.

## Project structure

```
app/src/main/java/it/mirkougolinidev/rfeedreader/
├── core/
│   ├── common/      preferences, default feeds
│   ├── database/    Room database, DAO, entities, migrations
│   ├── download/    DownloadScheduler, DownloadWorker, OfflineFileVerifier
│   ├── network/     FeedParser, OPML, LegacyTrust, periodic refresh
│   └── player/      OfflinePlaybackService (Media3)
├── data/repository/ PodcastRepository, the single entry point for data
└── ui/              Compose screens: Home, Podcast, Details, Player, Downloads, Settings
```

More details in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and in the project
specification [`docs/RFeedReader_Specifica_Progetto_v1.1.md`](docs/RFeedReader_Specifica_Progetto_v1.1.md)
(both in Italian).

## Contributing

Issues and pull requests are welcome. Before opening a pull request, please:

- run `./gradlew :app:assembleDebug :app:testDebugUnitTest`;
- make sure any new API or library still works on API 22;
- keep the offline-first rule: never pass a remote URL to the player;
- write user-facing strings in Italian, consistent with the rest of the UI.

## License

Copyright 2026 Mirko Ugolini.

Licensed under the [Apache License, Version 2.0](LICENSE).
