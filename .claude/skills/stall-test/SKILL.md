---
name: stall-test
description: Batteria di prove sul Fire 7 per scoprire stalli nei download e nell'aggiornamento dei feed (rete che cade, kill del processo, cancellazione, Wi-Fi only, rimozione durante il download). Usala dopo modifiche a DownloadWorker, DownloadScheduler, FeedParser o PodcastRepository.
---

# Prove anti-stallo sul dispositivo

Prerequisito: build **debug** installata (skill `build-install`), così si può leggere il DB.

## Strumenti

- I download di Radio 24 (~25 MB) finiscono in ~10 s: per annullare a metà usa tocchi a coordinate fisse,
  non `uiautomator dump` (impiega 2–3 s). In Git Bash serve `MSYS_NO_PATHCONV=1` per i percorsi `/sdcard/...`.
- Crash nativo noto: SIGSEGV su `RenderThread` in `libhwui.so` (ricorsione nel teardown dei RenderNode, Android 5.1 + layer
  Compose basati su View sotto API 23). Raro; se ricompare, raccogli il tombstone con `adb logcat -d | grep I/DEBUG`.

```bash
# Log utili (Android 5.1: niente --pid)
adb logcat -c && adb logcat -v time | grep -iE "rfeedreader|WM-|AndroidRuntime"

# Stato dei job WorkManager (JobScheduler su API 22)
adb shell dumpsys jobscheduler | grep -A3 rfeedreader

# Stato download nel DB (solo debug)
adb shell "run-as it.mirkougolinidev.rfeedreader ls -l files/episodes"
adb exec-out run-as it.mirkougolinidev.rfeedreader cat databases/rfeedreader.db > "$TMP/rfeed.db"
# poi: sqlite3 "$TMP/rfeed.db" "select title, downloadState, downloadedBytes from episodes where downloadState != 'NOT_DOWNLOADED';"
# Attenzione: con WAL attivo copiare anche rfeedreader.db-wal, altrimenti i dati recenti mancano.
```

## Scenari (per ognuno: atteso → osservato)

1. **Rete che cade a metà.** Avvia un download lungo, poi togli il Wi‑Fi. Su questa ROM `svc wifi disable` da adb **non ha effetto** (senza root): chiedi all'utente di spegnere il Wi‑Fi dal dispositivo. Atteso: entro un tempo
   limitato l'episodio esce da `DOWNLOADING` (retry o `FAILED` con messaggio); niente notifica eterna.
   Riattiva con `svc wifi enable`: il download riparte o si può riprovare.
2. **Rete lenta / server che non risponde.** Usa un hotspot con segnale debole o un feed verso un host che non risponde.
   Atteso: errore leggibile, la UI non resta in caricamento infinito.
3. **Kill del processo durante il download.** Avvia il download, premi Home, `adb shell am kill it.mirkougolinidev.rfeedreader`.
   Riapri l'app. Atteso: nessun episodio resta per sempre in `QUEUED`/`DOWNLOADING`; nessun `.part` orfano a lungo.
   (Non usare `am force-stop`: cancella i job di WorkManager e falsa la prova.)
4. **Annulla durante il download.** Atteso: stato `NOT_DOWNLOADED`, `.part` cancellato, notifica sparita subito.
5. **Wi‑Fi only cambiato dopo l'accodamento.** Accoda su rete mobile/hotspot con Wi‑Fi only attivo, poi disattivalo.
   Atteso: il download parte (oppure la UI spiega perché aspetta).
6. **Rimozione episodio/podcast durante il download.** Atteso: worker fermato, nessun file residuo, nessun crash.
7. **Refresh del feed durante il download.** Atteso: il download continua e lo stato non viene azzerato.
8. **Molti download insieme** (10+). Atteso: la UI resta fluida, progresso aggiornato, nessun ANR.
9. **Redirect http→https** (feed/episodi con tracker tipo podtrac/op3). Atteso: download riuscito.
10. **Host Let's Encrypt** (es. anchor.fm). Atteso: funziona grazie a `LegacyTrust`.

Riporta una tabella scenario / esito / note, con estratti di log brevi per i fallimenti.
