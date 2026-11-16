---
name: bump-dependency
description: Aggiorna (o aggiunge) una libreria in app/build.gradle.kts verificando che regga minSdk 22 tramite il manifest merger. Usala per qualsiasi cambio di versione di dipendenze o plugin.
argument-hint: <libreria> <versione>
---

# Aggiornare una dipendenza senza rompere API 22

Il minSdk di una libreria AndroidX non sta nel POM: l'unica verifica affidabile è il manifest merger.

1. Leggi il tetto attuale in `.claude/CLAUDE.md` (sezione Stack). Se la versione richiesta lo supera, avvisa che quasi
   certamente fallirà e chiedi se procedere comunque come prova.
2. Modifica **una sola** dipendenza alla volta in `app/build.gradle.kts`.
3. Verifica:
   ```bash
   ./gradlew :app:processDebugMainManifest
   ```
   Errore tipico: `uses-sdk:minSdkVersion 22 cannot be smaller than version N declared in library [...]`.
   Se fallisce: ripristina la versione precedente e annota la libreria che blocca (può essere una transitiva).
4. Se passa: `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
5. Room/KSP: Room 2.6.x rompe con Kotlin 2.4 (`unexpected jvm signature V`), Room 2.8.x richiede API > 22. Resta su 2.7.2.
6. Coil: restare su 2.x (package `coil.compose`); Coil 3 richiede API 23.
7. Aggiorna la tabella Stack in `.claude/CLAUDE.md` se il tetto cambia.

Riporta: versione provata, esito del merger, eventuale libreria bloccante con livello API richiesto.
