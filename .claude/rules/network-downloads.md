---
paths:
  - "app/src/main/java/**/core/network/**"
  - "app/src/main/java/**/core/download/**"
---

# Rete e download: niente stalli

Ogni operazione di rete deve poter **finire** in un tempo limitato, anche su un Fire 7 con Wi‑Fi debole.

- Sempre `connectTimeout` e `readTimeout`. Ricorda che `readTimeout` vale per la singola `read()`: un server che
  manda pochi byte alla volta non scade mai. Per i feed serve anche una scadenza complessiva; per gli episodi
  un controllo di "nessun progresso da N secondi".
- La cancellazione delle coroutine non interrompe una `read()` bloccata: quando il job viene cancellato bisogna chiamare
  `connection.disconnect()` (o chiudere lo stream) da un altro thread, ad esempio con `invokeOnCompletion`/`suspendCancellableCoroutine`.
- `instanceFollowRedirects` non segue redirect tra protocolli diversi (http → https e viceversa), frequenti con
  tracker podcast (podtrac, op3, chartable, CDN). Gestire i redirect a mano con un limite (es. 10 salti).
- Errori transitori (timeout, IOException, HTTP 408/429/5xx) → `Result.retry()` con backoff, non `failure()`.
  Errori definitivi (404, 410, MIME non audio, spazio insufficiente) → `failure()` con messaggio italiano.
- Download lunghi: preferire la ripresa con `Range: bytes=<n>-` se il server risponde `206`, mantenendo il `.part`.
- Lo stato in Room (`QUEUED`/`DOWNLOADING`) deve sempre tornare a uno stato finale, anche se il processo muore:
  riconciliare con `WorkManager.getWorkInfosForUniqueWork` all'avvio.
- Aggiornamenti di progresso in DB al massimo ogni ~1 s: ogni scrittura rifà emettere i Flow osservati dalla UI.
- Mai I/O di rete o di file su `Dispatchers.Main`.
- TLS: vedi `LegacyTrust`; non disattivare mai la verifica.
