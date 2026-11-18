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

## D3 – Clienti creati dal negozio ⚠️
- **Contesto**: un cliente può presentarsi in negozio senza essersi registrato.
- **Decisione**: lo staff crea il cliente con nome, email e telefono; l'account nasce con una password casuale che nessuno conosce. Il cliente la imposta con "Password dimenticata?" (link via email, fase 5).
- **Alternativa scartata**: password provvisoria comunicata a voce (insicura e scomoda).
- **Da valutare**: clienti senza email (anziani, ecc.). Oggi l'email è obbligatoria perché è il nome utente.

## D4 – Rimozione di una bici = archiviazione
- **Decisione**: la bici sparisce dall'elenco ma resta nel database.
- **Perché**: lo storico dei lavori deve restare consultabile.

## D5 – Fuso orario e "oggi"
- **Decisione**: tutto il calcolo di date e orari usa `Europe/Rome` tramite un `Clock` configurabile.

## D6 – Informativa privacy in bozza ⚠️
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

## D13 – I tempi non sono mostrati al cliente
- **Decisione**: il cliente vede interventi, stato e data prevista, non i minuti stimati.
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
