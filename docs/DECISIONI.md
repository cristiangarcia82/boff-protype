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

## D18 – Festività chiuse in automatico ⚠️ (aggiornata da D51: Canton Grigioni)
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

## D21 – Il calendario si ricalcola da solo (superata da D48: ora è facoltativo)
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

## D45 – Accesso senza password e Mac condiviso (confermato dal titolare)
- **Codice via email (6 cifre)**, da *Accedi* → *Ricevi un codice via email*. Scelto il codice e non un link: sull'iPhone l'app installata e Safari non condividono l'accesso, un link si aprirebbe in Safari.
  - Vale 10 minuti, una volta sola, massimo 5 tentativi; un nuovo codice annulla il precedente; massimo 5 invii l'ora e uno ogni 30 secondi. Nel database solo l'hash.
  - Il codice passa dallo stesso modulo di login della password (una "password" di 6 cifre è un codice: le password hanno almeno 8 caratteri). Valgono quindi le stesse protezioni: blocco dopo 5 errori, cambio di sessione, "resta collegato".
  - Risposta identica che l'email esista o no.
- **Passkey (Face ID / Touch ID / impronta / Windows Hello)** con il supporto WebAuthn di Spring Security. Si attivano dal riquadro proposto dopo l'accesso o dal *Profilo*, dove si vedono e si rimuovono i dispositivi. Il server conserva solo la chiave pubblica.
  - ⚠️ Le passkey sono legate al dominio (`BOFF_URL_PUBBLICO`): se il dominio cambia vanno riattivate.
- **Durata dell'accesso per ruolo**: clienti 30 giorni; **personale 12 ore**, perché il Mac del negozio è condiviso fra titolare e collaboratore. Anche con la scheda sempre aperta la sessione del personale scade dopo 12 ore dall'accesso.
- **Mac condiviso, da sapere**: se titolare e collaboratore usano lo **stesso utente di macOS**, le passkey di entrambi stanno nello stesso portachiavi e Touch ID non distingue chi appoggia il dito: chiunque sia registrato su quel Mac può scegliere l'una o l'altra. Per una separazione vera, creare due utenti di macOS (passaggio rapido tra utenti). In alternativa: "Esci" a fine turno.
- Account disattivati o bloccati non entrano né con il codice né con la passkey. La cancellazione dei dati elimina anche passkey e codici.
- **Alternative scartate**: login con Google/Apple (account sviluppatore a pagamento, fornitori in più), codici via SMS (costo per messaggio, meno sicuri).

## D46 – Calendario: tre visualizzazioni da confrontare
- Richiesta del titolare: il calendario era poco leggibile (un blocco per ogni intervento, nomi tagliati, orari di vendita e officina in evidenza quanto i lavori).
- Il selettore *Visualizzazione* propone tre modi, per una demo e per scegliere il migliore con chi lo usa:
  - **Compatta** (predefinita): un blocco per bici (interventi consecutivi dello stesso lavoro uniti), con nome e orario; orari di vendita/officina come piccole etichette; "Libero 3 h 30" su una riga. Toccando il blocco: cliente e telefono, interventi con orari, urgenza, note, *Sposta*, *Fissa/Sblocca*, *Apri lavoro*.
  - **Dettagliata**: com'era prima, un blocco per intervento.
  - **Linea del tempo**: blocchi alti quanto la durata, sulle ore della giornata, con le fasce come sfondo e un righello orario.
- La scelta resta nella sessione e si può passare nell'indirizzo (`/calendario?vista=tempo&settimane=1`).
- ⚠️ Dopo la demo conviene tenerne una o due e togliere le altre.

## D47 – Due settimane una sotto l'altra
- Per trascinare un lavoro alla settimana dopo, il calendario mostra **due settimane** (scelta *Settimane: 1 / 2*); le frecce avanzano di una.
- Trascinare un blocco sposta **tutti** i suoi interventi in un'unica operazione (tutto o niente, un solo ricalcolo): uno dopo l'altro dall'orario scelto o nel primo spazio libero.

## D48 – Ricalcolo automatico facoltativo (richiesta del titolare)
- Nuova voce in *Impostazioni → Funzionalità*: **Ricalcolo automatico del calendario**, **spenta** di default.
- **Spenta**: ciò che è in calendario resta dov'è. Dopo una modifica si pianifica solo ciò che manca (lavori nuovi, interventi aggiunti) nei primi spazi liberi; interventi fatti, rifiutati o tolti liberano il loro spazio; un intervento accorciato viene ricollocato. Niente ricalcolo all'ora né all'apertura del calendario. Il pulsante **Ripianifica** riorganizza tutto da capo.
- **Accesa**: comportamento precedente (ricalcolo completo dopo ogni modifica e ogni ora).
- In entrambi i casi gli interventi fissati a mano non si spostano. Il calendario mostra un avviso quando il ricalcolo automatico è spento.
- ⚠️ Con il ricalcolo spento, un lavoro urgente o con data promessa vicina non scavalca gli altri finché non si preme *Ripianifica* (l'avviso "finisce dopo la data promessa" resta).

## D49 – Appuntamenti di consulenza (richiesta del titolare; dove stanno: vedi D50)
- Le ore di **vendita** di ogni persona (impostate in *Impostazioni → Orari*, già per persona) diventano prenotabili per **consulenze**: acquisto, e-bike, posizionamento in sella, consegna bici nuova. Le ore di **officina** restano ai lavori: le due pianificazioni non si toccano.
- **Tipi di appuntamento** (*Impostazioni → Tipi di appuntamento*): nome, descrizione per il cliente, durata, prenotabile sì/no.
- **Dal cliente** (*menu → Appuntamenti*, invito nella pagina *Le mie bici*): sceglie il tipo, poi un giorno e un orario fra quelli **liberi** (passo di 30 minuti, almeno 2 ore di preavviso, fino a 30 giorni). La richiesta **tiene già l'orario** (nessun doppione) ed è *da confermare*. Facoltativi: bici e note. Massimo 3 appuntamenti futuri per cliente; può annullarli finché non sono passati.
- **Chi lo segue**: l'app sceglie fra le persone in vendita a quell'ora la meno impegnata quel giorno; il negozio può cambiarla.
- **Dal negozio**: pagina *Appuntamenti* (da confermare / prossimi 14 giorni), *Nuovo appuntamento* (anche con un cliente nuovo o senza app: nasce già confermato), e nel calendario in viola (bordo tratteggiato = da confermare) in tutte e tre le visualizzazioni. Toccandolo: Conferma, Sposta (solo in orari liberi di vendita), Svolto, Annulla, note interne. In bacheca: richieste da confermare e appuntamenti di oggi.
- **Avvisi**: al personale per una nuova richiesta o un annullamento del cliente; al cliente per conferma, spostamento, annullamento o appuntamento fissato dal negozio (email di riserva). Clienti senza app: niente avvisi, si chiamano.
- **Privacy**: gli appuntamenti sono nell'export dei dati; la cancellazione annulla quelli futuri e toglie note e bici. Informativa versione 4.
- **Alternative scartate**: il cliente propone solo una preferenza e il negozio sceglie l'ora (più telefonate); il cliente sceglie anche la persona (le ore di vendita cambiano di settimana in settimana).
- ⚠️ Da valutare con l'uso: preavviso minimo, limite di 3, tipi di appuntamento iniziali.

## D50 – Niente più fasce fisse di vendita: orario di lavoro e turni di vendita a mano (richiesta del titolare)
- L'orario settimanale di ogni persona è **solo orario di lavoro** (*Impostazioni → Orari*, senza la scelta Vendita/Officina). Le vecchie fasce di vendita sono diventate ore di lavoro (migrazione V15), così nessuno perde ore.
- In queste ore l'app mette **lavori di officina** e **appuntamenti di consulenza**:
  - gli appuntamenti hanno la **precedenza**: un lavoro non fissato che si trova sopra un appuntamento viene ricollocato (anche con il ricalcolo automatico spento); un lavoro trascinato a mano non può finire sopra un appuntamento;
  - un appuntamento non può sovrapporsi a un intervento **fissato a mano**.
- **Turni di vendita**: il titolare (o il collaboratore) li aggiunge dal calendario con il "+" di ogni cella (persona, giorno, dalle–alle, nota). Devono stare nell'orario di lavoro e non sovrapporsi; in quel tempo niente officina né appuntamenti. Si tolgono toccandoli. Ne compare uno di esempio nei dati demo (sabato pomeriggio).
- "Libero" nel calendario = ore di lavoro meno turni di vendita, lavori e appuntamenti.
- ⚠️ Il tipo di fascia "Vendita" resta nel modello solo per compatibilità; l'interfaccia non lo usa più.

## D51 – Festività del Canton Grigioni al posto di quelle italiane (richiesta del titolare)
- **Contesto**: il negozio è nei Grigioni; la D18 prevedeva le festività nazionali italiane.
- **Decisione**: chiusi in automatico i giorni festivi cantonali: Capodanno, Venerdì Santo, Lunedì di Pasqua, Ascensione, Lunedì di Pentecoste, Festa nazionale (1° agosto), Natale e Santo Stefano. Le date mobili si calcolano dalla Pasqua.
- **Non inclusi**: Epifania, 25 aprile, 1° maggio, 2 giugno, Ferragosto, Ognissanti, Immacolata (italiane). Nei Grigioni le feste religiose come Epifania, San Giuseppe, Corpus Domini, Assunta, Ognissanti e Immacolata valgono solo in alcuni comuni (cattolici): se il vostro comune le osserva, aggiungetele da *Assenze e chiusure*. Lo stesso per il 2 gennaio, se chiudete.
- **Fuso orario**: default `Europe/Zurich` (stesse ore e stesso cambio dell'ora di `Europe/Rome`, ma corretto per la sede).
- **Fonte**: elenco dei giorni festivi del Cantone (gr.ch) e legge sui giorni di riposo pubblici (CSC 520.100); da ricontrollare col comune.

## D52 – Pagina Appuntamenti e pianificazione degli interventi più semplici (richiesta del titolare)
- **Appuntamenti**:
  - ogni richiesta mostra cliente, telefono, tipo, persona, bici e note, con i pulsanti **Conferma**, **Cambia orario** e **Rifiuta** direttamente sulla scheda;
  - l'agenda dei prossimi 14 giorni è divisa per giorno (Oggi, Domani…) e c'è un filtro per persona;
  - gli appuntamenti già passati e confermati finiscono in **Da chiudere**, con il pulsante Svolto;
  - le richieste rimaste senza risposta restano in elenco per 14 giorni, invece di sparire;
  - rifiutare o annullare chiede conferma. Per i clienti senza app ricorda di telefonare.
- **Regole nuove**:
  - non si conferma una richiesta il cui orario è già passato: va spostata o rifiutata;
  - non si segna svolto un appuntamento non ancora iniziato.
- **Cambia orario**: invece di data e ora da compilare, si tocca un giorno tra quelli con posto nei prossimi 14 giorni, poi l'ora.
- **Interventi**:
  - nella scheda del lavoro il riquadro **Quando si fa** sta subito sotto l'intestazione e dice chi, che giorno, a che ora e se l'ha deciso il sistema o è fissato a mano;
  - **Scegli giorno e ora** apre "Quando lo facciamo?". Si scelgono chi, il giorno (con le ore libere di ciascun giorno) e l'ora da cui il lavoro sta tutto di seguito. Se non c'è un orario così, si mette il lavoro negli spazi liberi del giorno;
  - il lavoro resta fissato lì;
  - **Lascia decidere al sistema** toglie il blocco;
  - **Vedi nel calendario** apre la settimana giusta.
- **Calendario**: il dettaglio di un blocco usa la stessa finestra, al posto dei campi persona/data/ora. "Sblocca" diventa "Lascia decidere al sistema".
- **Testi**: i messaggi di errore parlano di "ore di lavoro" invece di "officina".

## D53 – Lavori annunciati fissati prima che arrivi la bici (richiesta del titolare)
- **Perché**: dare al cliente una data per portare la bici, invece di "quando ti è comodo".
- **Come**:
  - nella scheda di un lavoro *Annunciato* c'è **Fissa giorno e ora**, con la stessa finestra "Quando lo facciamo?";
  - si creano gli interventi in calendario, fissati;
  - il cliente riceve una notifica (o un'email, se non ha le notifiche) con giorno e ora entro cui portare la bici (l'inizio del primo intervento) e la data in cui sarà pronta. L'app lo mostra nella sua bici e nell'elenco *Le mie bici*;
  - se il cliente non usa l'app, l'avviso ricorda di telefonargli.
- **Regole**:
  - i lavori annunciati non fissati restano fuori dal calendario: entrano da soli al check-in, come prima;
  - quelli fissati occupano il loro posto: il calcolo automatico e gli appuntamenti ci girano attorno, e il ricalcolo completo non li tocca;
  - all'arrivo della bici (check-in) restano dove sono;
  - *Togli dal calendario* li rimuove (solo per gli annunciati). "Lascia decidere al sistema" non c'è, perché li toglierebbe;
  - se il giorno fissato passa senza che la bici arrivi, la scheda lo segnala e chiede un'altra data.
- **Nel calendario** si riconoscono subito: bordo tratteggiato, sfondo a righe, etichetta "bici non arrivata", voce in legenda. Nel dettaglio compare quando il cliente porta la bici.
- **Nell'elenco Lavori**, scheda Annunciati, la colonna *Arrivo bici* mostra il giorno e l'ora fissati.
- **Dati**: colonna `lavoro.arrivo_previsto` (V16), calcolata dal ricalcolo come per la data prevista.
