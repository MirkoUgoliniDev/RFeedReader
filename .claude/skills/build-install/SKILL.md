---
name: build-install
description: Compila RFeedReader e lo installa sul Fire 7 collegato via adb (debug o release ottimizzata), poi avvia l'app. Usala quando l'utente chiede di provare, installare o lanciare l'app sul dispositivo.
---

# Build e installazione sul Fire 7

1. Verifica il dispositivo:
   ```bash
   adb devices
   ```
   Se la lista è vuota: chiedi all'utente di collegare il Fire 7 e abilitare il debug USB. In Windows compare come WPD/MTP.

2. Scegli la variante:
   - **debug** (default per il debugging, permette `run-as` e lettura del DB):
     ```bash
     ./gradlew :app:assembleDebug
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```
   - **release** (minify + shrink, più veloce sul Fire 7, firmata con la debug keystore locale):
     ```bash
     ./gradlew :app:assembleRelease
     adb install -r app/build/outputs/apk/release/app-release.apk
     ```
   Passare da debug a release con la stessa chiave funziona con `-r`. Se l'installazione fallisce per firma diversa
   (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) **chiedi prima** di disinstallare: si perdono episodi scaricati e iscrizioni.

3. Avvia:
   ```bash
   adb shell am start -n it.mirkougolinidev.rfeedreader/.MainActivity
   ```

4. Controlla crash all'avvio:
   ```bash
   adb logcat -d -v time | grep -iE "AndroidRuntime|FATAL|rfeedreader" | tail -n 60
   ```

Riporta: variante installata, esito, eventuali eccezioni.
