# Architettura RFeedReader

## Struttura attuale

- `core/database`: Room, DAO e modelli persistenti di podcast ed episodi.
- `core/network`: client HTTP e parser RSS 2.0 / Atom basato su XML Pull Parser.
- `core/download`: coda WorkManager, download su file temporaneo, verifica e finalizzazione locale.
- `core/player`: ExoPlayer e MediaSessionService per playback in background.
- `core/common`: preferenze locali.
- `data/repository`: accesso ai dati e query dedicate agli episodi riproducibili.
- `ui`: schermate Compose, navigazione, controlli player e tema.

Il progetto usa Kotlin, Jetpack Compose, Material 3, Room, WorkManager, Media3 e Coil 2 per le copertine (Coil 3 richiede API 23). L'iniezione delle dipendenze è gestita da un contenitore applicativo semplice; non è necessaria una libreria DI per l'MVP.

## Regola Offline First

La query della libreria riproducibile restituisce solo episodi con stato `DOWNLOADED`, percorso nel folder privato `filesDir/episodes` e file presente con dimensione maggiore di zero. `OfflinePlaybackService` ricontrolla database, percorso e file a ogni richiesta del MediaSession e costruisce un `MediaItem` con URI `file://`. Non passa URL remoti a ExoPlayer e non abilita lo streaming come fallback.

Il download scrive in un file `.part`; aggiorna lo stato a `DOWNLOADED` e associa il percorso solo dopo risposta HTTP valida, controllo del MIME type, verifica della dimensione quando fornita e rinomina completata. `QUEUED`, `DOWNLOADING`, `FAILED` e `NOT_DOWNLOADED` non sono riproducibili.

## Dati e aggiornamento

- `Podcast.feedUrl` conserva l'indirizzo aggiunto e `finalFeedUrl` quello dopo i redirect.
- Gli episodi sono deduplicati per podcast e chiave stabile: GUID del feed o hash di enclosure, data e titolo se il GUID manca.
- Il refresh aggiorna i metadati dell'episodio senza sovrascrivere stato ascolto, posizione e download locale.
- La posizione viene salvata periodicamente e al termine del servizio; l'ultimo episodio e lo stato di riproduzione consentono il ripristino quando il processo viene ricreato.
- Preferenze e database rimangono locali; non sono configurati account, cloud o telemetria.

## Dipendenze e build

Il repository include il Gradle Wrapper (Gradle 9.8.1). Il progetto supporta Android 5.1 (API 22) e versioni successive e richiede JDK 17 e Android SDK 37 per la build. Strumenti: AGP 9.5.1, Kotlin/Compose 2.4.10, KSP 2.3.11. Le librerie sono limitate alle ultime versioni che reggono API 22: compose-bom 2024.06.00, Navigation Compose 2.7.7, Lifecycle 2.7.0, Room 2.7.2, Media3 1.3.1, WorkManager 2.9.0, Coil 2.7.0, OkHttp 4.12.0; `java.time` è fornito dal desugaring (desugar_jdk_libs 2.1.5). Le CA mancanti su Android 5.1 (ISRG Root X1) vengono aggiunte da `core/network/LegacyTrust.kt` senza disattivare la verifica TLS. Fa fede `app/build.gradle.kts`. I test unitari coprono l'ammissione dei soli file locali non vuoti, con dimensioni coerenti e interni alla cartella privata dell'app.

## Verifica successiva

OPML e aggiornamenti automatici sono disponibili e facoltativi. Prima di una pubblicazione restano da eseguire prove end-to-end con feed reali e download interrotti, modalità aereo dopo il download, migrazioni dei dati dopo il primo rilascio e verifiche su più dispositivi.
