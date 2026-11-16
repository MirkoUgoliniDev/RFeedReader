# RFeedReader — Specifica di progetto e istruzioni per Claude Code / Codex

Versione: 1.1  
Data: 12 novembre 2026  
Percorso progetto previsto: `C:\Lavori\Android\RFeedReader`

## 1. Obiettivo

Realizzare un'app Android nativa per sottoscrivere feed podcast RSS/Atom, consultare gli episodi, scaricarli e riprodurli online oppure offline.

Il riferimento funzionale è AntennaPod, ma RFeedReader deve avere una UX più essenziale, progettata soprattutto per l'utilizzo in palestra e per essere leggibile senza occhiali.

Priorità assolute:
1. Leggibilità immediata.
2. Controlli audio grandi e raggiungibili con una mano.
3. Riproduzione affidabile in background.
4. Download e ascolto offline.
5. Pochi passaggi per le operazioni comuni.

## 2. Direzione visuale approvata come concept

- Tema scuro predefinito: fondo quasi nero, superfici antracite.
- Colore di accento: verde brillante/turchese.
- Testo primario bianco, testo secondario grigio chiaro.
- Contrasto elevato e gerarchia tipografica netta.
- Titoli principali indicativamente 28–32 sp; titoli episodi 22–26 sp; testo secondario 18–20 sp.
- Target touch dei controlli principali almeno 64 dp; preferibilmente 72–88 dp per Play/Pausa.
- Spaziatura generosa; evitare icone ambigue e azioni nascoste.
- Supportare font scaling Android e TalkBack.
- Non usare testo piccolo per informazioni necessarie all'ascolto.

Il concept grafico comprende sei schermate: Home/mini-player, elenco Podcast, Player completo, Aggiungi Podcast, Download, Impostazioni. È una direzione visuale, non una specifica pixel-perfect: verificare contrasto, spaziature, dimensioni e accessibilità durante l'implementazione.

## 3. Navigazione

Barra inferiore con quattro destinazioni:
- Home
- Podcast
- Download
- Impostazioni

Il player compatto deve rimanere accessibile quando è in corso una riproduzione. Toccandolo si apre il Player completo.

## 4. Schermate e comportamento

### 4.1 Home
- Sezione “In riproduzione” con copertina, titolo e podcast.
- Controlli essenziali: indietro 30 s, Play/Pausa, avanti 30 s.
- Accesso rapido a velocità, timer e coda.
- Se non c'è una riproduzione attiva, mostrare nuovi episodi e accesso all'aggiunta di un feed.
- Non riempire la schermata con statistiche o contenuti decorativi.

### 4.2 Podcast
- Elenco delle sottoscrizioni con copertina, nome e numero di episodi non ascoltati.
- Azione evidente “+” per aggiungere un feed.
- Dettaglio podcast con descrizione, elenco episodi, aggiornamento feed e gestione sottoscrizione.
- Ordinamento semplice: più recenti prima.

### 4.3 Player completo
- Copertina e titolo ben visibili.
- Barra di avanzamento con tempo trascorso e rimanente.
- Controlli grandi: -30 s, Play/Pausa, +30 s.
- Selettore velocità facilmente raggiungibile.
- Timer spegnimento facilmente raggiungibile.
- Accesso alla coda.
- Gestione di errori, buffering e fine episodio con feedback chiaro.
- Ricordare la posizione per ogni episodio.

### 4.4 Aggiungi Podcast
- Campo URL grande e facilmente selezionabile.
- Pulsante “Aggiungi” evidente.
- Supportare incolla da appunti.
- Validare URL e contenuto prima di creare la sottoscrizione.
- Mostrare errori comprensibili: URL non valido, rete non disponibile, feed non riconosciuto, feed senza episodi.
- Riconoscere RSS 2.0 e Atom.

### 4.5 Download
- Elenco download in corso, in attesa, completati e falliti.
- Progresso, dimensione quando disponibile, annullamento e nuovo tentativo.
- Accesso alla riproduzione offline.
- Non avviare download duplicati dello stesso episodio.
- Mostrare chiaramente lo spazio occupato.

### 4.6 Impostazioni
- Aspetto e dimensione testo.
- Preferenze di download (solo Wi-Fi, se attivato dall'utente).
- Salti avanti/indietro configurabili.
- Velocità predefinita.
- Timer.
- Gestione archiviazione.
- Importazione/esportazione OPML in una fase successiva.
- Informazioni e versione app.

## 5. Stack tecnologico proposto

- Kotlin.
- Jetpack Compose e Material 3 personalizzato.
- AndroidX Media3 ExoPlayer per la riproduzione.
- MediaSessionService per playback in background e controlli di sistema.
- Room per persistenza locale.
- WorkManager per lavori differibili e download pianificati, valutando il vincolo di download lunghi e le API Android più adatte.
- Kotlin Coroutines e Flow.
- Hilt per Dependency Injection.
- Coil per immagini.

Prima di introdurre librerie, verificare le versioni stabili compatibili tra loro e con il Gradle/Android Gradle Plugin del progetto. Non fissare versioni obsolete senza verifica.

Target iniziale: Android 10 o superiore, da confermare in base ai dispositivi previsti.

## 6. Architettura

Adottare una separazione MVVM con livelli:
- `presentation`: schermate Compose, componenti UI e ViewModel.
- `domain`: modelli, interfacce repository e use case.
- `data`: repository, datasource, mapping e implementazioni.
- `core`: database, rete, player, download, utilità comuni.

Struttura indicativa:

```text
app/src/main/java/<package>/
  core/
    database/
    network/
    player/
    download/
    common/
  data/
    local/
    remote/
    repository/
    model/
  domain/
    model/
    repository/
    usecase/
  feature/
    home/
    subscriptions/
    episodes/
    player/
    downloads/
    settings/
  navigation/
  MainActivity.kt
```

Adattare il package e la struttura alle convenzioni già presenti nel repository, evitando una riorganizzazione distruttiva.

## 7. Modello dati minimo

### Podcast
- id locale.
- feedUrl canonico.
- titolo.
- descrizione.
- immagine URL.
- sito web, se disponibile.
- data ultimo aggiornamento.
- stato/errore ultimo aggiornamento.

### Episode
- id locale.
- podcastId.
- guid del feed.
- titolo e descrizione.
- enclosureUrl.
- MIME type e lunghezza, se disponibili.
- data pubblicazione.
- durata, se disponibile.
- immagine.
- stato ascolto.
- posizione riproduzione.
- stato download e percorso locale.

Usare una chiave stabile: podcastId + GUID quando disponibile; in assenza di GUID applicare una strategia documentata basata sui metadati dell'enclosure. Gestire aggiornamenti senza duplicare episodi.

## 8. Feed RSS/Atom

- Supportare RSS 2.0 e Atom.
- Leggere enclosure audio e MIME type.
- Leggere metadati iTunes/Podcast quando presenti.
- Gestire date in formati diversi.
- Gestire descrizioni HTML in modo sicuro.
- Gestire redirect HTTP, timeout, TLS e risposte non valide.
- Impostare User-Agent identificabile.
- Non bloccare il thread UI.
- Non assumere che durata, copertina o GUID siano sempre presenti.
- Conservare il feed URL originale e l'URL finale dopo redirect, se utile.
- Prevedere aggiornamento manuale; pianificazione automatica in una fase successiva.

## 9. Riproduzione

Usare Media3 ExoPlayer. Implementare un servizio MediaSessionService per:
- playback in background;
- notifica multimediale e controlli schermata bloccata;
- headset e Bluetooth;
- audio focus e interruzioni;
- ripresa dopo ricreazione del processo, per quanto consentito dal sistema;
- coda e cambio episodio.

Salvare periodicamente la posizione e al cambio di episodio/uscita, evitando scritture eccessive. La UI deve osservare uno stato di riproduzione unico e coerente.

Velocità iniziali suggerite: 0.8x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x, 2.5x. Consentire configurazione.

## 10. Download e offline

- Download con stato osservabile.
- Supportare annullamento e retry.
- Scrivere su file temporaneo e finalizzare solo a download completato.
- Gestire spazio insufficiente, rete interrotta e risposte HTTP non valide.
- Valutare supporto HTTP Range/resume solo se il server lo consente.
- Verificare MIME type e dimensione quando disponibili.
- Proteggere da path non sicuri: i nomi file devono essere generati dall'app.
- Associare il file locale all'episodio nel database.
- La riproduzione deve preferire il file locale quando scaricato.
- Definire una politica di cancellazione esplicita; non eliminare automaticamente contenuti senza impostazione dell'utente.

## 11. Permessi, privacy e robustezza

- Richiedere solo i permessi strettamente necessari.
- Usare storage app-specifico per i download, evitando permessi di archiviazione generici.
- Dichiarare correttamente il foreground service per la riproduzione secondo la versione Android.
- Gestire notifiche secondo le regole della versione Android.
- Nessun account o cloud obbligatorio.
- Nessuna telemetria non necessaria.
- Non caricare URL o cronologia di ascolto verso servizi esterni.

## 12. Piano di sviluppo incrementale

### Fase 0 — Ispezione
- Analizzare repository e stato Git.
- Leggere Gradle, manifest, package e codice esistente.
- Verificare se il progetto è vuoto o già avviato.
- Produrre una breve relazione e un piano di modifica.
- Non sovrascrivere file esistenti prima di averli compresi.

### Fase 1 — Fondamenta UI
- Tema e tipografia.
- Navigazione a quattro sezioni.
- Componenti riutilizzabili e preview Compose.
- Dati mock temporanei.
- Verifica su emulatori/dimensioni diverse.

### Fase 2 — Feed e persistenza
- Room e migrazioni.
- Parser RSS/Atom.
- Aggiunta feed e lista podcast.
- Importazione episodi e deduplicazione.
- Test con feed validi, incompleti e malformati.

### Fase 3 — Player
- Media3 e MediaSessionService.
- Player UI, coda e seek.
- Persistenza posizione.
- Background, notifiche, audio focus e Bluetooth.

### Fase 4 — Download
- Download, progressi, annullamento e retry.
- Riproduzione offline.
- Gestione spazio e file mancanti.

### Fase 5 — Rifinitura
- Accessibilità e font scaling.
- Stati vuoti, caricamento ed errori.
- Test su dispositivi reali.
- OPML e aggiornamenti automatici solo dopo MVP stabile.

## 13. Criteri di accettazione MVP

- L'utente aggiunge un URL RSS/Atom valido.
- Il feed viene interpretato e salvato.
- Gli episodi sono elencati senza duplicati.
- L'utente avvia e mette in pausa un episodio.
- Il playback continua con app in background.
- I controlli di sistema funzionano.
- La posizione viene ripristinata.
- Un episodio viene scaricato, annullato e riprodotto offline.
- Gli errori sono comunicati in modo comprensibile.
- I controlli principali sono utilizzabili con una mano e leggibili senza occhiali.
- Il progetto compila e i test pertinenti passano.

## 14. Prompt operativo da dare a Claude Code o Codex

Agisci come sviluppatore Android senior. Devi lavorare nel repository `C:\Lavori\Android\RFeedReader` e seguire questa specifica.

Prima di scrivere codice:
1. Ispeziona l'intero progetto e identifica lo stato attuale.
2. Controlla Gradle, SDK, manifest, package, dipendenze e test.
3. Rileva eventuali decisioni già presenti e preservale se valide.
4. Riporta una sintesi dell'architettura attuale e un piano concreto.

Poi implementa una fase alla volta. Inizia dalla Fase 0 e proponi la Fase 1. Non realizzare tutto in un'unica generazione.

Regole:
- Non cancellare o sostituire codice esistente senza motivazione.
- Non inventare API o versioni delle dipendenze: verifica la compatibilità.
- Mantieni separazione dei livelli e codice testabile.
- Non lasciare pulsanti finti o azioni senza comportamento: se una funzione non è ancora implementata, indicarla chiaramente come non disponibile.
- Dopo ogni fase esegui build e test disponibili, correggi gli errori e riporta i risultati.
- Aggiorna questa specifica o una documentazione tecnica quando cambiano le decisioni.
- Mantieni come criterio guida la leggibilità in palestra e l'affidabilità del player.

**Primo incarico:** esegui soltanto l'ispezione (Fase 0), presenta la relazione e il piano di intervento. Attendi conferma prima di modificare file.
