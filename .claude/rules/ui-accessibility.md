---
paths:
  - "app/src/main/java/**/ui/**"
---

# UI: leggibile senza occhiali, usabile con una mano

Dalla specifica (`docs/RFeedReader_Specifica_Progetto_v1.1.md`):

- Tema scuro predefinito, accento verde/turchese, testo primario bianco, secondario grigio chiaro, contrasto alto.
- Titoli schermata 28–32 sp, titoli episodio 22–26 sp, testo secondario 18–20 sp. Niente testo piccolo per info d'ascolto.
- Target touch dei controlli principali ≥ 64 dp; Play/Pausa 72–88 dp.
- Supportare font scaling e TalkBack: ogni `Icon`/`IconButton` ha `contentDescription` in italiano.
- Azioni evidenti, niente gesti nascosti; navigazione inferiore Home / Podcast / Download / Impostazioni.
- Fire 7 = schermo 1024×600, CPU lenta: `LazyColumn` con `key`, niente lavoro pesante in composizione,
  raccogliere i Flow con `collectAsStateWithLifecycle`.
- Mostrare chiaramente gli stati di download (in coda, in corso con percentuale, fallito con motivo e "Riprova").
