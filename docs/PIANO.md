# Piano di progetto – Boff Bike

## Obiettivo
Web app mobile (PWA, senza store) per gestire clienti, biciclette, lavori di officina e la
pianificazione della manutenzione su 2 unità lavorative, separando le ore di vendita da quelle di officina.

## Decisioni prese
1. Il cliente **annuncia solo i lavori**; la data di lavorazione la decide il negozio.
2. **Niente preventivi** né prezzi: per ogni intervento contano solo tipo e durata.
3. Il collaboratore ha **gli stessi poteri operativi** del titolare e vede tutto il calendario.
   Solo il titolare gestisce la configurazione (utenti, orari, catalogo).
4. **Magazzino** escluso per ora.
5. Notifiche **Web Push** (PWA) con email come riserva. Su iPhone richiedono iOS ≥ 16.4
   e l'app aggiunta alla schermata Home.

## Due esperienze d'uso
| | Cliente | Staff (titolare e collaboratore) |
|---|---|---|
| Dispositivo principale | **Telefono** | **Mac in ufficio** (monitor grande), ma usabile anche da telefono in negozio |
| Impostazione | Mobile-first: una colonna, pulsanti grandi, barra in alto senza menu laterale | Desktop-first: menu laterale fisso, pagine a tutta larghezza su più colonne |
| Schermate chiave | Le mie bici, annuncia lavoro, stato del lavoro | Bacheca con indicatori, calendario settimanale a colonne (una per persona), elenco lavori con dettaglio affiancato |

Accorgimenti per il Mac:
- L'app si installa come **app desktop**: Safari → File → "Aggiungi al Dock" (macOS Sonoma o successivi),
  oppure Chrome/Edge → "Installa app". Si apre in una finestra sua, con le notifiche push di macOS.
- **Calendario settimanale** con trascinamento dei lavori tra giorni e persone (fasi 3–4).
- **Elenchi con dettaglio affiancato**: si sceglie un lavoro a sinistra e si modifica a destra, senza cambiare pagina.
- Tabelle con filtri, ordinamento e **scorciatoie da tastiera** per le azioni frequenti (nuovo lavoro, cerca cliente).
- Sotto i 1100 px di larghezza le stesse schermate si impilano in una colonna sola (telefono e tablet).

## Ruoli
| Ruolo | Chi | Permessi |
|---|---|---|
| STAFF (+ TITOLARE) | Titolare | Tutto, inclusa la configurazione |
| STAFF | Collaboratore | Clienti, bici, lavori, calendario, assenze |
| CLIENTE | Clienti | Le proprie bici, annuncio lavori, stato dei lavori |

## Flusso di un lavoro
`ANNUNCIATO → IN NEGOZIO (check-in) → PIANIFICATO → IN LAVORAZIONE → PRONTO → RITIRATO` (+ `ANNULLATO`)

## Modello dati
```
Utente(id, email, password_hash, nome, cognome, ruolo[STAFF|CLIENTE], titolare, attivo)
Cliente(id, utente_id, telefono, consenso_privacy, note)
Bicicletta(id, proprietario_id, marca*, modello*, tipologia*, elettrica, taglia, colore, anno, numero_telaio, note, archiviata)
  tipologia: CITTA | STRADA | GRAVEL | MTB | TREKKING | EBIKE | BAMBINO | ALTRO   (* obbligatori)
TipoIntervento(id, nome, durata_standard_min, attivo)
Lavoro(id, bicicletta_id, stato, data_checkin, data_pronta_prevista, priorita, note_cliente, note_interne)
RigaLavoro(id, lavoro_id, tipo_intervento_id, descrizione_libera, durata_min, completata)
Operatore(id, utente_id, nome)
OrarioSettimanale(id, operatore_id, giorno_sett, ora_inizio, ora_fine, tipo_slot[VENDITA|MANUTENZIONE])
Assenza(id, operatore_id, data_inizio, data_fine, motivo)
EccezioneCalendario(id, data, chiuso, note)
Assegnazione(id, riga_lavoro_id, operatore_id, data, ora_inizio, ora_fine, bloccata)
PushSubscription(id, utente_id, endpoint, chiavi, creata_il)
```

## Pianificazione
1. Capacità di officina per persona e giorno = slot MANUTENZIONE − assenze − chiusure − assegnazioni bloccate.
2. Lavori ordinati per data promessa, poi priorità, poi data di check-in.
3. MVP: assegnazione greedy al primo slot libero (i lavori lunghi si possono spezzare).
4. Evoluzione: Timefold Solver con vincoli (niente sovrapposizioni, date promesse, carico bilanciato).
5. Le assegnazioni spostate a mano possono essere **bloccate**: lo scheduler non le tocca.

## Stack
Java 21 · Spring Boot 4.1 · Vaadin 25 (PWA) · Spring Security · PostgreSQL · Flyway ·
Web Push (VAPID) · Spring Mail · JUnit 5 + Testcontainers · Docker

## Roadmap
| Fase | Contenuto | Stato |
|---|---|---|
| 0. Setup | Progetto, DB, login, ruoli, PWA, CI | ✅ completata |
| 1. Anagrafiche | Registrazione cliente, profilo, bici del cliente, clienti e bici per lo staff, catalogo interventi, personale | ✅ completata |
| 2. Lavori | Annuncio del cliente, check-in, stati, lavoro al banco, interventi e tempi, storico bici | ✅ completata |
| 3. Calendario | Orari settimanali vendita/officina, assenze, chiusure, festività automatiche, vista settimanale | ✅ completata |
| 4. Pianificazione | Pianificazione automatica, calendario con interventi trascinabili, blocco manuale, oggi in officina, carico settimanale | ✅ completata |
| 5. Notifiche e deploy | Web Push + email, recupero password, guida "Installa l'app", Docker Compose + Caddy HTTPS + backup | ✅ completata |
| 6. Robustezza | Test interfaccia in CI, un ricalcolo per azione, scarica dati / elimina account | ✅ completata |
| 7. Backlog | Assenze di mezza giornata, orario esatto negli spostamenti, ricerca rapida | in corso |
