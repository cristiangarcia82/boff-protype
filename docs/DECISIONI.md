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
