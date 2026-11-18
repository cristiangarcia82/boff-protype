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
