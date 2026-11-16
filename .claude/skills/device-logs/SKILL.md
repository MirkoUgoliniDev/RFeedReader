---
name: device-logs
description: Raccoglie e riassume logcat, stato WorkManager/JobScheduler e file scaricati dal Fire 7 per capire un crash o un comportamento anomalo. Usala quando l'utente dice che l'app si è bloccata, chiusa o non scarica.
---

# Raccolta log dal Fire 7

```bash
adb devices
adb shell ps | grep rfeedreader                       # processo vivo?
adb logcat -d -v time > "$TMP/rfeed-logcat.txt"       # dump completo del buffer
grep -iE "AndroidRuntime|FATAL|ANR|rfeedreader|WM-|ExoPlayer|SSL|Certificate|SocketTimeout|UnknownHost" "$TMP/rfeed-logcat.txt" | tail -n 150
adb shell dumpsys jobscheduler | grep -B2 -A6 rfeedreader
adb shell dumpsys activity services it.mirkougolinidev.rfeedreader | head -n 60
adb shell ls /data/anr/ 2>/dev/null                   # tracce ANR (potrebbe servire root)
```

Build debug: `adb shell "run-as it.mirkougolinidev.rfeedreader ls -l files/episodes"` per vedere `.part` e file finali.

Usa `$TMP` (o la scratchpad della sessione) per i file temporanei, non la cartella del progetto.
Riassumi in italiano: cosa è successo, eccezione principale con stack ridotto alle righe del package, ipotesi di causa con `file:riga`.
