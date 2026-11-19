# Registro delle decisioni

Decisioni prese durante lo sviluppo notturno, da rivedere. Ogni voce indica il **contesto**,
la **decisione** e le **alternative scartate**. Quelle marcate ⚠️ meritano un tuo parere esplicito.

## D1 – Clienti e personale nella stessa tabella `utente`
- **Contesto**: il piano prevedeva tabelle separate `Utente` e `Cliente`.
- **Decisione**: un'unica tabella `utente` con ruolo `STAFF` o `CLIENTE`; telefono e consenso privacy stanno lì.
- **Perché**: ogni cliente ha comunque un account; due tabelle 1:1 aggiungono join e casi limite senza benefici.
- **Alternativa scartata**: tabella `cliente` separata (utile solo con clienti senza account, vedi D3).

## D2 – Il personale coincide con le "unità lavorative"
- **Decisione**: niente tabella `operatore`; ogni utente `STAFF` attivo è un'unità lavorativa con il suo orario.
- **Perché**: tu e il collaboratore siete sia utenti sia operatori. Se un giorno ci fosse uno staff che non lavora in officina, basterà non dargli ore di manutenzione.

## D3 – Clienti creati dal negozio (aggiornata in fase 10, vedi D43)
- **Contesto**: un cliente può presentarsi in negozio senza essersi registrato.
- **Decisione**: lo staff crea il cliente con nome, email e telefono; l'account nasce con una password casuale che nessuno conosce. Il cliente la imposta con "Password dimenticata?" (link via email, fase 5).
- **Alternativa scartata**: password provvisoria comunicata a voce (insicura e scomoda).
- **Risolto in fase 10**: l'email ora è facoltativa per i clienti creati dal negozio (D43).

## D4 – Rimozione di una bici = archiviazione
- **Decisione**: la bici sparisce dall'elenco ma resta nel database.
- **Perché**: lo storico dei lavori deve restare consultabile.

## D5 – Fuso orario e "oggi"
- **Decisione**: tutto il calcolo di date e orari usa `Europe/Rome` tramite un `Clock` configurabile.

## D6 – Informativa privacy in bozza ⚠️ (aggiornata in fase 10, vedi D44)
- **Decisione**: pagina `/privacy` con un testo generico e la casella obbligatoria in registrazione (si salva data e ora del consenso).
- **Da fare a cura tua**: completare titolare del trattamento, indirizzo e contatti, possibilmente con il tuo consulente.

## D7 – Telefono obbligatorio per i clienti
- **Perché**: è il canale più rapido quando la bici è pronta o serve un'autorizzazione per un lavoro extra. Formato libero (fisso o cellulare, con o senza +39).

## D8 – Password: minimo 8 caratteri, nessun'altra regola
- **Perché**: le regole complicate (maiuscole, simboli…) spingono a password prevedibili; la lunghezza conta di più. Il blocco dopo 5 tentativi errati protegge dagli attacchi.

## D9 – Il catalogo non si cancella, si disattiva
- **Perché**: le voci sono usate nei lavori passati. Una voce disattivata non viene più proposta. Il catalogo nasce con 16 interventi comuni e tempi indicativi da adattare ai tuoi.

## D10 – Chi gestisce cosa
| Funzione | Titolare | Collaboratore | Cliente |
|---|---|---|---|
| Clienti e loro bici | ✅ | ✅ | solo le proprie bici |
| Disattivare un cliente | ✅ | – | – |
| Personale, catalogo, orari settimanali | ✅ | – | – |
| Lavori, calendario, assenze, chiusure | ✅ | ✅ | annuncia e segue i propri lavori |

## D11 – Un solo lavoro aperto per bici
- **Decisione**: vincolo nel database (indice unico parziale). Se il cliente vuole aggiungere qualcosa, lo dice in negozio e lo staff aggiunge l'intervento al lavoro esistente.
- **Perché**: evita doppioni e confusione nel calendario.

## D12 – Annuncio con sola descrizione ⚠️
- **Decisione**: se il cliente scrive solo una nota senza scegliere interventi, si crea una riga "Da valutare in negozio" da 30 minuti, che lo staff sostituisce dopo aver visto la bici.
- **Perché**: la pianificazione ha bisogno di una stima; 30 minuti è prudente per una diagnosi. Cambiabile.

## D13 – I tempi non sono mostrati al cliente (confermato dal titolare)
- **Decisione**: il cliente vede interventi, stato e data prevista, non i minuti stimati. Le durate compaiono solo nelle schermate del personale.
- **Garanzia**: test automatici verificano che modulo di annuncio, pagina della bici, elenco bici ed esportazione dei dati non contengano durate.
- **Perché**: i tempi sono uno strumento interno di pianificazione e potrebbero essere letti come impegni o prezzi.

## D14 – "Bici pronta" chiude tutti gli interventi
- **Decisione**: segnare la bici pronta segna come fatti anche gli interventi rimasti. Se un intervento non è stato fatto, va rimosso prima.
- **Perché**: un clic in meno nel caso comune; "Riapri" rimedia agli errori.

## D15 – Il cliente annulla solo prima del check-in
- **Perché**: dopo l'arrivo in negozio la bici è fisicamente da voi e l'annullamento va gestito a voce.

## D16 – Nomi degli stati
`Annunciato → In negozio → Pianificato → In lavorazione → Pronta → Ritirata` (+ `Annullato`).
"In lavorazione" scatta da sé quando si segna il primo intervento come fatto.

## D17 – Orario settimanale "tipo" + eccezioni
- **Decisione**: ogni persona ha un orario settimanale fisso diviso in fasce **Vendita** (banco) e **Officina**. Le variazioni (giorno libero occasionale, ferie, malattia) si registrano come **assenze di giornata intera**; le chiusure del negozio come **chiusure**.
- **Perché**: copre il 95% dei casi con pochissimo inserimento dati. Una persona che il martedì fa solo vendita ha semplicemente solo fasce Vendita quel giorno.
- **Non gestito (backlog)**: assenze di mezza giornata e orari diversi per stagione (es. estate). Oggi si cambia l'orario tipo quando serve.

## D18 – Festività nazionali chiuse in automatico ⚠️
- **Decisione**: Capodanno, Epifania, Pasqua e Pasquetta, 25 aprile, 1° maggio, 2 giugno, Ferragosto, Ognissanti, Immacolata, Natale e Santo Stefano sono giorni di chiusura senza doverli inserire. Il patrono va aggiunto a mano.
- **Se il negozio apre nei festivi**: impostare `BOFF_FESTIVI_CHIUSI=false` e inserire a mano solo le chiusure reali.

## D19 – Assenze e chiusure gestibili anche dal collaboratore
- **Perché**: hai chiesto che in tua assenza si occupi lui di tutto. L'orario tipo invece resta del titolare (è una decisione organizzativa).

## D20 – Pianificazione automatica "greedy" invece di un ottimizzatore ⚠️
- **Decisione**: algoritmo semplice e prevedibile, scritto da zero e coperto da test: ogni intervento va nel **primo spazio libero di officina** di chiunque, in quest'ordine di priorità:
  1. lavori già **in lavorazione** (si finisce ciò che è iniziato);
  2. lavori **urgenti**;
  3. **data promessa** al cliente più vicina;
  4. **ordine di arrivo** della bici.
- **Perché**: con 2 persone e qualche decina di bici a settimana un ottimizzatore (es. Timefold) non dà benefici visibili ma rende i risultati meno prevedibili. Resta un'evoluzione possibile.
- **Regole di dettaglio**: gli interventi della stessa bici sono in sequenza (mai due persone in parallelo sulla stessa bici); un intervento lungo si spezza su più fasce/giorni (pezzi non inferiori a 15 minuti); si pianifica dal quarto d'ora successivo ad "adesso" e mai prima dell'arrivo della bici; orizzonte di 90 giorni.

## D21 – Il calendario si ricalcola da solo
- **Quando**: dopo ogni modifica che lo influenza (check-in, interventi aggiunti/rimossi/fatti, durate, urgenza, data promessa, orari, assenze, chiusure), ogni ora (il tempo passa e ciò che non è stato fatto slitta in avanti) e con il pulsante **Ripianifica**.
- **Gli spostamenti manuali vincono**: un intervento trascinato (o spostato con un tocco su telefono) viene **fissato** (lucchetto) e il ricalcolo non lo tocca; il resto dello stesso lavoro viene messo **dopo** la parte fissata. "Sblocca" lo restituisce al calcolo automatico.
- **Effetto collaterale accettato**: gli interventi non fissati possono cambiare orario quando arriva un lavoro più urgente. È il comportamento voluto (il piano è sempre aggiornato); per "congelare" qualcosa basta fissarlo.

## D22 – Cosa vede il cliente
- La **data prevista** di consegna, aggiornata dalla pianificazione. Non vede orari né chi lavora sulla bici.

## D23 – Spostamento manuale: primo spazio libero del giorno scelto
- Trascinando su una persona/giorno, l'intervento va nel **primo spazio libero di officina abbastanza lungo** di quella giornata. Se non c'è spazio l'app lo dice. Non si sceglie l'orario esatto (scelta per semplicità: in backlog).

## D24 – Notifiche: cosa, a chi, come
| Evento | Destinatario | Canale |
|---|---|---|
| Nuovo lavoro annunciato da un cliente | personale | push |
| Bici arrivata in negozio (check-in di un lavoro annunciato) | cliente | push |
| Prima pianificazione: "pronta indicativamente il …" | cliente | push |
| Bici pronta | cliente | push, **email se il cliente non ha notifiche attive** |
- **Non** si notificano i semplici spostamenti di data dovuti ai ricalcoli: sarebbero troppi. Il cliente vede sempre la data aggiornata nell'app.
- Invio **asincrono dopo il salvataggio**: un servizio push lento non blocca mai l'interfaccia.

## D25 – Web Push con il modulo ufficiale di Vaadin
- **Decisione**: `flow-webpush` (Vaadin) invece di librerie di terzi: il service worker generato da Vaadin gestisce già la ricezione. Le chiavi VAPID sono generate al primo avvio e salvate nel database (devono restare stabili).
- **Limite noto**: su iPhone le push funzionano solo con l'app aggiunta alla schermata Home (iOS ≥ 16.4). Il riquadro "Attiva le notifiche" rimanda alla guida `/installa`.

## D26 – Email facoltative
- Senza SMTP l'app funziona; le email (recupero password, "bici pronta" di riserva, invito ai clienti creati al banco) vengono solo scritte nel log. **In produzione è fortemente consigliato configurarle.**

## D27 – Recupero password
- Link monouso valido 2 ore; nel database solo l'hash SHA-256; massimo 3 richieste l'ora per account; risposta identica che l'email esista o meno (non si rivela chi è cliente).
- Lo stesso meccanismo serve per i **clienti creati al banco** (pulsante "Invia link per la password" nella scheda cliente).

## D28 – Messa online con Docker Compose + Caddy
- Un solo server: app, PostgreSQL, Caddy (HTTPS automatico) e backup giornaliero. Semplice da gestire per un negozio; costi 5–10 €/mese. Vedi `docs/DEPLOY.md`.
- **Alternativa scartata**: PaaS (Render, Railway…): più comodi ma più costosi con database gestito, e i dati restano meno sotto controllo.

## D29 – Test dell'interfaccia "senza browser"
- **Decisione**: Vaadin browserless-test (open source) invece di Playwright in CI: i test girano nella JVM in pochi secondi, senza installare un browser. Playwright resta per le verifiche visive durante lo sviluppo.

## D30 – Cancellazione account = anonimizzazione ⚠️
- **Decisione**: nome, email, telefono e note scritte dal cliente vengono cancellati; bici e lavori restano nello storico del negozio senza collegamento alla persona. Non si può eliminare l'account con un lavoro aperto.
- **Perché**: rispetta il diritto all'oblio senza perdere i dati di lavoro del negozio (ore, interventi). Da confermare con il tuo consulente privacy.

## D31 – Un ricalcolo per azione
- Più modifiche nella stessa operazione (es. "intervento fatto" che fa partire il lavoro) producono un solo ricalcolo del calendario.

## D32 – Assenze di parte della giornata
- Un'assenza di **un solo giorno** può avere un orario (es. 15:00–17:00); per più giorni resta a giornata intera. Massimo un'assenza per persona al giorno.

## D33 – Spostamento a orario preciso solo dal dialog
- Il trascinamento resta "primo spazio libero" (gesto veloce); per un orario preciso si tocca l'intervento e si compila "Alle".

## D34 – Ricerca rapida per lo staff
- Cerca per nome/cognome (in qualsiasi ordine), email, telefono, marca e modello della bici. Non cerca nel numero di telaio (backlog, se utile).

## D35 – Foto nel database ⚠️
- **Decisione**: le foto sono salvate nel database PostgreSQL, non su disco.
- **Perché**: un solo posto da salvare (il backup giornaliero le include già), nessun permesso di cartelle da gestire sul server.
- **Costo**: il database cresce (ordine di qualche GB l'anno). Se diventasse un problema: ridimensionamento automatico delle foto o archiviazione su disco/S3.

## D36 – Limiti foto
- Cliente: 3 foto per lavoro (bastano per mostrare il problema). Negozio: fino a 10 in totale (es. prima/dopo). Massimo 8 MB ciascuna.

## D37 – Statistiche essenziali, senza grafici complessi
- Indicatori numerici e un elenco a barre degli interventi più richiesti. Niente librerie di grafici: sono domande semplici ("quanto lavoro, quanto ci mettiamo, rispettiamo le promesse?").
- **Non misurato**: il tempo reale di lavoro (l'app conosce solo i tempi stimati). Se interessasse, si può aggiungere un "tempo effettivo" quando si segna un intervento come fatto.

## D38 – Funzionalità opzionali spente di default (richiesta del titolare)
- Pagina *Impostazioni → Funzionalità* (solo titolare): quattro interruttori, effetto immediato, nessun cambiamento finché non si attivano.

## D39 – Approvazione dei lavori extra
- Il negozio propone un intervento in più (dal catalogo o libero) con un **motivo** visibile al cliente. Il cliente riceve una notifica (o un'email) e risponde con **Approva** / **No, grazie**.
- In attesa di risposta l'intervento **non entra in calendario** e la bici **non può essere segnata pronta**; se rifiutato resta visibile barrato e non viene fatto.
- Il cliente vede descrizione e motivo, **mai la durata**. Il personale riceve una notifica con la risposta.

## D40 – Promemoria di manutenzione
- Ogni giorno alle 10:12 (ora del negozio) un promemoria ai clienti la cui bici è stata **ritirata** da più dei mesi impostati (default 12). Un solo promemoria per ritiro; nessuno se c'è già un lavoro aperto.
- Il cliente può disattivarli dal profilo ("Ricevi i promemoria per il tagliando"). ⚠️ Conviene citarli nell'informativa privacy.

## D41 – Tempo effettivo
- Quando un intervento è fatto si può indicare il tempo reale; togliendo la spunta il dato si cancella. Le Statistiche confrontano tempo standard e reale per voce del catalogo (scarto %).
- Il calendario continua a usare i tempi standard: il tempo reale serve a correggere il catalogo, non a ripianificare.

## D42 – Avviso di officina piena
- Occupazione = minuti pianificati / ore di officina disponibili, da adesso ai prossimi 7 giorni. Oltre la soglia (default 85%) il cliente vede un avviso generico nella pagina della bici e nel modulo di annuncio. Mai numeri, percentuali o orari.

## D43 – Clienti senza app
- **Contesto**: chi entra in negozio o non vuole usare l'app deve comunque avere lavori, bici e storico.
- **Decisione**: stessa tabella `utente`, con **email facoltativa solo per i clienti creati dal negozio** (vincolo nel database: il personale deve averla; chi si registra da solo pure, perché è il nome utente). Un cliente senza email non può accedere: è un "cliente senza app".
- Cliente e bici si creano **direttamente dalla finestra *Nuovo lavoro***, senza passare da *Clienti*. Basta nome, cognome e telefono.
- Il negozio vede il badge **"Senza app"** (elenco clienti, ricerca, pannello del lavoro) e, quando la bici è pronta, il promemoria **"chiamalo"** con il numero.
- Notifiche, email e promemoria per questi clienti semplicemente non partono (nessun errore).
- Approvazione extra (se attiva): per loro la casella "Chiedi l'approvazione" parte spenta; se la si usa, il negozio registra la risposta data a voce con **"Ha detto sì / Ha detto no"**.
- Se in seguito il cliente vuole l'app: *Modifica* → si aggiunge l'email (una sola volta; poi non si cambia dal negozio) → *Invia link* per scegliere la password. Storico e bici restano.
- **Alternativa scartata**: tabella `cliente` separata dall'account (D1): duplicava ricerca, bici e storico e complicava il passaggio da "senza app" a "con app".

## D44 – Privacy per chi non usa l'app
- **Informativa al banco**: creando un cliente dal negozio (da *Clienti* o da *Nuovo lavoro*) il personale deve spuntare "Ho mostrato o consegnato al cliente l'informativa privacy" (con link per aprirla). Il servizio rifiuta la creazione senza conferma e registra la data.
- **Diritti esercitati in negozio**: nella scheda cliente, per tutto il personale, *Scarica i dati del cliente* (stesso file JSON del profilo) ed *Elimina i dati del cliente* (con conferma). Si usa solo su richiesta del cliente; non possibile con un lavoro aperto. L'operazione è registrata nel log.
- **Cancellazione**: stessa anonimizzazione di D30. Ora si registra la data (`eliminato_il`) e l'email viene tolta del tutto invece di essere sostituita da un indirizzo fittizio. I clienti eliminati spariscono da elenco e ricerche; aprendo il loro indirizzo si vede solo "Cliente eliminato".
- **Informativa (versione 2)**: aggiunti clienti senza app, foto, notifiche (fornitori push), promemoria (legittimo interesse, disattivabili), fornitori di hosting ed email, conservazione con storico anonimo, come esercitare i diritti senza app (in negozio, telefono, email; entro 30 giorni), reclamo al Garante.
- ⚠️ **Da completare prima della messa online**: ragione sociale, indirizzo, P. IVA e contatti del negozio; nomi dei fornitori di hosting ed email. Il testo resta una bozza da far verificare da un consulente.
