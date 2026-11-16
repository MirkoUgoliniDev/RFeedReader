# RFeedReader — istruzioni di progetto

App Android nativa (Kotlin + Jetpack Compose) per ascoltare podcast RSS/Atom, pensata per la palestra:
testi grandi, controlli grandi, uso con una mano. Lingua del progetto: **italiano** (UI, commenti, messaggi di errore, documentazione).

## Vincoli non negoziabili

1. **Offline First.** Si riproducono solo episodi `DOWNLOADED`, con file in `filesDir/episodes` verificato da
   `OfflineFileVerifier`. Mai URL remoti a ExoPlayer, mai streaming di ripiego. Vedi `.claude/rules/offline-first.md`.
2. **minSdk 22.** Il dispositivo reale è un Amazon Fire 7 (KFFOWI) con LineageOS 12 = Android 5.1.1, armeabi-v7a.
   Ogni API, libreria o versione deve reggere API 22. Vedi `.claude/rules/api22-compat.md`.
3. **Mai disattivare la verifica TLS.** Le CA mancanti (ISRG Root X1) si aggiungono in modo additivo tramite
   `core/network/LegacyTrust.kt`, per HttpURLConnection e per l'OkHttpClient di Coil.

## Stack (tetto massimo per API 22, non alzare senza verifica)

AGP 9.5.1, Kotlin/Compose plugin 2.4.10, KSP 2.3.11, compileSdk/targetSdk 37, JDK 17, Gradle 9.8.1.
compose-bom 2024.06.00 · activity-compose 1.9.0 · navigation-compose 2.7.7 · lifecycle 2.7.0 (max 2.8.2) ·
Room **2.7.2** (unica che regge API 22 e KSP moderno) · Media3 1.3.1 · WorkManager 2.9.0 · Coil **2.7.0** (non Coil 3) ·
OkHttp 4.12.0 · desugar_jdk_libs 2.1.5 (per `java.time`).

Fa fede `app/build.gradle.kts`. `README.md` è in inglese (repository pubblico, licenza Apache 2.0); il resto della documentazione resta in italiano.

## Struttura

- `core/network` — `FeedParser` (HttpURLConnection + XmlPullParser), `OpmlDocument`, `LegacyTrust`, refresh periodico.
- `core/download` — `DownloadScheduler` (WorkManager, unique work `episode-download-<id>`), `DownloadWorker` (file `.part` → rename), `OfflineFileVerifier`.
- `core/database` — Room `rfeedreader.db` **v3** (migrazioni 1→2→3, mai `fallbackToDestructiveMigration`), `PodcastDao`, entità. Stati download: `NOT_DOWNLOADED`, `QUEUED`, `DOWNLOADING`, `DOWNLOADED`, `FAILED`.
- **Concorrenza dei download** (non rompere): ogni accodamento genera un `downloadToken`; tutte le scritture del worker sono UPDATE condizionati al token; `DownloadScheduler` serializza DB+WorkManager con un `Mutex` di processo in `NonCancellable`; i file portano token (e id esecuzione) nel nome; la finalizzazione (controllo token + rename + UPDATE) è una transazione; `reconcile()` all'avvio rimette in coda i lavori morti e cancella i file senza proprietario.
- `core/player` — `OfflinePlaybackService` (Media3 MediaSessionService).
- `data/repository/PodcastRepository` — unico punto d'accesso ai dati; DI manuale in `RFeedReaderApplication`.
- `ui` — Compose: Home, Podcast, Dettaglio, Player, Download, Impostazioni.

## Comandi

```bash
./gradlew :app:assembleDebug            # build di debug
./gradlew :app:testDebugUnitTest        # test JVM (OfflineFileVerifier, OPML)
./gradlew :app:assembleRelease          # minify + shrink, firmata con la debug keystore locale
./gradlew :app:processDebugMainManifest # verifica minSdk delle dipendenze (manifest merger)
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -v time | grep -iE "rfeedreader|WM-|AndroidRuntime|ExoPlayer"
```

Su Android 5.1 `logcat --pid` e `pidof` non esistono: filtrare con grep. Il Fire 7 in Windows compare come
dispositivo **WPD/MTP** (VID_1949), non nella classe USB.

## Modo di lavorare

- Dopo ogni modifica al codice: `./gradlew :app:assembleDebug` e, se tocchi download/verifier/OPML, `:app:testDebugUnitTest`.
- Prima di dichiarare "fatto" su rete/download, pensa al Fire 7: rete lenta, CPU debole, Android 5.1.
- I messaggi d'errore per l'utente sono in italiano, brevi e azionabili.
- Agenti specializzati in `.claude/agents/`, procedure ripetibili in `.claude/skills/`.
