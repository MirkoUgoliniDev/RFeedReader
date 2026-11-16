---
paths:
  - "app/src/main/java/**/core/player/**"
  - "app/src/main/java/**/core/download/**"
  - "app/src/main/java/**/data/**"
  - "app/src/main/java/**/core/database/**"
  - "app/src/main/java/**/ui/**"
---

# Regola Offline First

- La libreria riproducibile contiene **solo** episodi `DOWNLOADED` il cui file passa `OfflineFileVerifier.verifiedFile`:
  file esistente, non vuoto, lunghezza == `downloadedBytes`, dentro `filesDir/episodes` (canonical path).
- `OfflinePlaybackService` costruisce `MediaItem` solo con URI `file://` e ricontrolla il file a ogni richiesta.
  Nessun `enclosureUrl` deve mai arrivare a ExoPlayer, neanche come fallback.
- Il download scrive su `<id>.part` e fa `renameTo` sul file finale solo a trasferimento completo e verificato;
  lo stato passa a `DOWNLOADED` solo dopo il rename.
- Il `length` dell'enclosure RSS **non** è affidabile (Radio 24 manda 1000 per ogni episodio): usarlo solo come stima,
  mai come criterio di validità. Fa fede il `Content-Length` HTTP, e in sua assenza i byte effettivamente scritti.
- Il refresh del feed aggiorna i metadati ma non tocca `downloadState`, `localPath`, `downloadedBytes`, `positionMs`, `isPlayed`.
- Ogni modifica a questi punti richiede di aggiornare/estendere `OfflineFileVerifierTest`.
