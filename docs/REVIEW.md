# Review tecnica (senior) – stato al termine della fase 0 + gestione bici

Revisione del codice prodotto finora, fatta prima di proseguire con le fasi successive.
Per ogni punto: gravità, problema, decisione presa.

Legenda gravità: 🔴 da correggere subito · 🟠 importante · 🟡 miglioria · ✅ va bene così

## Sicurezza

| # | Gravità | Problema | Esito |
|---|---|---|---|
| S1 | 🔴 | **"Ricordami" non funzionante.** Spring Security attiva il remember-me solo se la richiesta di login contiene il parametro `remember-me`, ma il `LoginForm` di Vaadin non lo invia: la configurazione era codice morto e gli utenti avrebbero dovuto rifare il login ogni 30 minuti di inattività. | **Corretto**: `alwaysRemember(true)` per 30 giorni. Per un'app installata sul telefono è il comportamento atteso; "Esci" invalida il cookie. |
| S2 | 🔴 | **Chiave del cookie remember-me scritta nel codice** (`"boff-bike-remember-me"`), quindi uguale per chiunque abbia il sorgente. | **Corretto**: letta da `BOFF_REMEMBER_ME_KEY`; se assente si genera una chiave casuale all'avvio (con avviso nel log). |
| S3 | 🟠 | **Nessuna protezione contro i tentativi ripetuti di password.** | **Corretto**: blocco di 15 minuti dopo 5 errori consecutivi sulla stessa email (`TentativiLogin`), con test. In memoria: va bene con una sola istanza dell'app. |
| S4 | 🟡 | `BCryptPasswordEncoder` fisso: cambiare algoritmo in futuro richiederebbe di invalidare le password. | **Corretto**: `DelegatingPasswordEncoder` (nuove password `{bcrypt}…`, le vecchie senza prefisso restano valide). |
| S5 | ✅ | Autorizzazione per vista con `@RolesAllowed`, controllo di proprietà delle bici nel **servizio** (non solo nell'interfaccia), CSRF e session fixation gestiti da Vaadin/Spring. | — |
| S6 | ✅ | Email normalizzate (minuscole) sia in applicazione sia con un vincolo `CHECK` nel database. | — |
| S7 | 🟡 | Utenti demo con password nota. | Già protetti da `boff.demo=false` di default; documentato nel README. |

## Correttezza e robustezza

| # | Gravità | Problema | Esito |
|---|---|---|---|
| C1 | 🟠 | **Fuso orario.** In produzione il server gira in UTC: "oggi" e gli orari di lavoro sarebbero sfalsati di 1–2 ore (critico per la pianificazione). | **Corretto**: bean `Clock` nel fuso del negozio (`boff.fuso-orario`, default `Europe/Rome`). Tutto il codice nuovo usa il `Clock`; i test possono fissare la data. |
| C2 | 🟠 | **Errori imprevisti** in un'azione dell'interfaccia mostravano la pagina d'errore tecnica di Vaadin. | **Corretto**: `UiConfig` registra l'errore nel log e mostra una notifica in italiano; gli errori "di regola" (`ErroreDominio`) mostrano il loro messaggio. |
| C3 | 🟠 | **Lingua dei componenti** decisa dal browser: un cliente con il telefono in inglese avrebbe visto date e calendari in inglese. `spring.mvc.locale` non ha effetto su Vaadin. | **Corretto**: lingua italiana forzata per ogni UI. |
| C4 | 🟡 | Modifiche concorrenti (es. cliente e negozio che modificano lo stesso lavoro) potrebbero sovrascriversi. | Per le nuove tabelle "calde" (lavori, assegnazioni) si usa il **blocco ottimistico** (`@Version`). Per le bici il rischio è trascurabile. |
| C5 | ✅ | Schema gestito solo da Flyway con `ddl-auto=validate`: Hibernate verifica all'avvio che entità e tabelle coincidano (ha già intercettato un errore di tipo su `anno`). | — |
| C6 | ✅ | `open-in-view=false`: nessuna query nascosta durante il rendering. | — |

## Manutenibilità

| # | Gravità | Problema | Esito |
|---|---|---|---|
| M1 | 🟡 | Ogni classe di test avviava un proprio contesto Spring e un proprio PostgreSQL. Con più classi i test sarebbero diventati lenti. | **Corretto**: annotazione `@IntegrationTest` unica → un solo contesto e un solo container. |
| M2 | ✅ | Package organizzati per funzionalità (`utente`, `bicicletta`, …) e non per strato tecnico: ogni area resta leggibile da sola. | — |
| M3 | 🟡 | Nessun test automatico dell'interfaccia. | **Decisione**: la logica sta nei servizi, coperti da test di integrazione su PostgreSQL reale; l'interfaccia viene verificata con script Playwright durante lo sviluppo. Test end-to-end automatici in CI messi in backlog. |

## Operatività

| # | Gravità | Problema | Esito |
|---|---|---|---|
| O1 | 🟠 | Nessun endpoint di stato per il monitoraggio/riavvio automatico del container. | **Corretto**: `/actuator/health` (solo stato UP/DOWN, nessun dettaglio esposto). |
| O2 | 🟡 | Il container Java non limitava la memoria rispetto al container. | **Corretto**: `-XX:MaxRAMPercentage=75`. |
| O3 | 🟡 | Mancano HTTPS, backup e configurazione di produzione. | Previsti nella fase 5. |

## Accessibilità

| # | Gravità | Problema | Esito |
|---|---|---|---|
| A1 | 🟡 | Le schede delle bici erano selezionabili da tastiera ma non attivabili con Invio. | **Corretto**. |

---

# Review finale (dopo le fasi 1–5)

Rilettura del codice a fine sviluppo. I problemi trovati durante la notte e già corretti (con test):

| Problema trovato | Correzione |
|---|---|
| Elenco lavori: righe non caricate fuori transazione (`LazyInitializationException`) perché `getRighe()` restituisce una vista non modificabile | caricamento esplicito `caricaRighe()` + test che usa i dati fuori transazione |
| Pianificazione: piccoli "buchi" tra interventi della stessa bici (arrotondamento al quarto d'ora applicato anche tra un intervento e l'altro) | arrotondamento solo su "adesso" + test |
| Pianificazione: dopo uno spostamento manuale, il resto del lavoro poteva finire **prima** della parte fissata | il resto va dopo l'ultima parte fissata + test |
| Editor orari: campi che uscivano dalla scheda | CSS a griglia con colonne comprimibili |
| Notifiche: su iPhone senza app installata il riquadro spariva e il cliente non scopriva come attivarle | invito a installare l'app |
| Rifiuto del permesso notifiche mostrato come "errore imprevisto" | messaggio specifico |

## Limiti noti e rischi residui

| # | Tema | Dettaglio | Proposta |
|---|---|---|---|
| R1 | **Notifiche push non provate fino al telefono** | In questo ambiente il browser automatico non raggiunge i servizi push di Google/Apple. Verificati: generazione chiavi, cifratura e firma verso un finto servizio push (test), registrazione del service worker e richiesta di iscrizione nel browser. | Prima prova reale appena l'app è online in HTTPS (10 minuti: iscriversi da telefono e segnare una bici "pronta"). |
| R2 | Immagine Docker non costruita qui | La build Docker non ha accesso alla rete in questo ambiente. Verificati: build di produzione del jar (avviata e provata), validità del `compose.prod.yaml`. | La prima `docker compose up --build` sul server è il vero collaudo. |
| R3 | Ricalcoli ripetuti | Un'azione può generare 2–3 ricalcoli del calendario. Con qualche decina di lavori sono millisecondi. | Ottimizzare solo se i lavori aperti diventano centinaia. |
| R4 | Un'istanza sola | Blocco dei tentativi di login e serializzazione dei ricalcoli sono in memoria. Corretto per un server singolo. | Da rivedere solo se si scalasse su più server. |
| R5 | Test dell'interfaccia | Le schermate sono state provate con script automatici durante lo sviluppo, ma non sono nei test di CI. | Aggiungere test end-to-end (Playwright) in CI. |
| R6 | GDPR | Informativa in bozza; nessuna funzione di esportazione/cancellazione dati self-service (si fa a mano, su richiesta). | Completare l'informativa; aggiungere "Elimina il mio account". |
| R7 | Backup fuori dal server | Il backup giornaliero resta sul server. | Copia periodica altrove (vedi DEPLOY.md). |

## Backlog funzionale (non richiesto, emerso durante lo sviluppo)
- Assenze di mezza giornata; orari stagionali (estate/inverno).
- Scelta dell'orario esatto quando si sposta un intervento.
- Foto della bici e del problema.
- Ricerca globale (cliente/bici) dalla barra in alto.
- Statistiche: tempi stimati vs reali, interventi più frequenti.

---

# Review fase 6 – Robustezza

| Voce | Esito |
|---|---|
| R3 (ricalcoli ripetuti) | **Risolto**: le richieste nella stessa transazione producono un solo ricalcolo (`Ripianificazione`), con test che lo verifica. |
| R5 (test interfaccia) | **Risolto**: 7 test delle schermate senza browser (Vaadin browserless, Apache 2.0) girano in CI: registrazione, annuncio dal telefono, check-in dal pannello, calendario, permessi. |
| R6 (GDPR) | **Risolto in parte**: "Scarica i miei dati" (JSON) ed "Elimina il mio account" nel profilo cliente. L'informativa resta da completare. |
| Trovato in review | La cancellazione lasciava le **note del cliente** nei lavori (possono contenere dati personali): ora vengono svuotate (con test). |
| Da tenere d'occhio | I test d'interfaccia usano anche i dati demo; se cambiano i dati demo vanno aggiornati. |

---

# Review fase 7 – Funzioni dal backlog

| Voce | Esito |
|---|---|
| Assenze di mezza giornata | Fatto: le ore di assenza sono tolte dall'officina (e la pianificazione si adegua), visibili nel calendario. Con test. |
| Orario esatto negli spostamenti | Fatto: campo "Alle" nel dialog "Sposta"; se a quell'ora non c'è spazio sufficiente l'app lo dice (con test). Il trascinamento continua a usare il primo spazio libero. |
| Ricerca rapida | Fatto: campo nella barra in alto (Alt+K), risultati su clienti e lavori, link diretti alla scheda (con test). |
| Trovato in review | Dopo una build di produzione (`-Pproduction`) l'avvio in sviluppo usava un CSS vecchio rimasto in `target/`: non è un difetto dell'app, ma è documentato nel README (`mvn clean`). |
| Limite accettato | In un giorno non si possono registrare due assenze parziali per la stessa persona (caso raro; basta un'unica fascia più ampia). |

---

# Review fase 8 – Foto e statistiche

| Voce | Esito |
|---|---|
| Foto del problema | Il cliente allega fino a 3 foto nell'annuncio (o dopo, finché il lavoro è aperto); il negozio ne aggiunge fino a 10 per lavoro e le può eliminare. Formato verificato leggendo l'intestazione del file (JPEG, PNG, WebP, HEIC), massimo 8 MB. Con test, provato nel browser. |
| Sicurezza foto | Le immagini sono servite solo a chi vede la schermata (legate alla sessione); un cliente non può allegare foto a lavori altrui; le foto vengono cancellate con l'account. |
| Statistiche | Lavori ricevuti, consegnati, annullati, tempo medio in negozio, puntualità sulla data promessa, nuovi clienti, interventi più richiesti (30 giorni / 3 mesi / anno). Con test. |
| Trovato in review | Testi del caricamento in inglese → tradotti; etichette delle barre ambigue ("2 · 40 min") → "2 volte · 40 min". |
| Rischio | **Foto HEIC** (formato iPhone): Safari le mostra, Chrome no. Di solito l'iPhone le converte in JPEG durante il caricamento; se così non fosse, sul Mac usare Safari. In backlog: conversione lato server. |
| Rischio | Le foto stanno nel database (incluse nei backup). Con molte foto il database cresce (stima: 3 foto × 2 MB × 20 lavori/settimana ≈ 6 GB/anno). In backlog: ridimensionamento automatico. |

---

# Review di sicurezza e del codice (dopo la fase 8)

Revisione completa: autenticazione, autorizzazioni, dati in ingresso, file caricati, notifiche,
recupero password, cookie, log, configurazione di produzione.

## Corretto (con test)

| # | Gravità | Problema | Correzione |
|---|---|---|---|
| SEC1 | 🔴 | **SSRF dalle notifiche push.** L'indirizzo dell'iscrizione arriva dal browser: un utente poteva registrare un indirizzo interno (es. `http://db:5432`) e far partire richieste dal server verso la rete interna a ogni notifica. | Accettati solo indirizzi `https` dei servizi push dei browser (Google, Mozilla, Apple, Microsoft), senza porta né credenziali; le iscrizioni non valide vengono scartate anche all'invio. |
| SEC2 | 🟠 | **Upload: limite di 8 MB solo nel browser.** Una richiesta costruita a mano poteva mandare file enormi, letti interamente in memoria prima del controllo (rischio di esaurire la memoria). | Il server rifiuta prima della lettura i file oltre 8 MB o di dimensione non dichiarata. Provato: 6 MB accettata, 32 MB rifiutata. |
| SEC3 | 🟠 | **Link di recupero password nei log** quando l'SMTP non è configurato: chi legge i log poteva reimpostare password altrui. | In produzione si registra solo "email non inviata"; il testo completo solo con i dati demo (sviluppo). |
| SEC4 | 🟠 | **Cookie non marcati Secure dietro il proxy HTTPS.** Con la gestione "framework" degli header Tomcat non sapeva di essere in HTTPS. | Gestione "native" degli header del proxy (accettati solo dalla rete interna) + `SameSite=Lax` e `HttpOnly` sul cookie di sessione. |
| SEC5 | 🟡 | Recupero password: usato un link, gli altri ancora validi restavano utilizzabili per 2 ore. | Usare un link invalida tutti gli altri dello stesso utente. |
| SEC6 | 🟡 | Recupero password: con un'email registrata la risposta era più lenta (invio sincrono), indizio per scoprire chi è cliente. | Invio dell'email in un altro thread: tempi di risposta uguali. |
| SEC7 | 🟡 | Messaggi d'errore: venivano mostrati i testi di `IllegalStateException`/`IllegalArgumentException`, che possono contenere dettagli interni (SQL, indirizzi). | All'utente solo i messaggi scritti per lui (`ErroreDominio`); il resto è un messaggio generico, il dettaglio va nel log. |
| SEC8 | 🟡 | Blocco tentativi di accesso: la mappa in memoria poteva crescere senza limite (tentativi su milioni di email). | Pulizia delle voci scadute oltre 10.000 voci. |
| SEC9 | 🟡 | Invito "imposta password" inviabile a qualsiasi utente, anche del personale. | Solo a clienti attivi. |
| SEC10 | 🟡 | Database di sviluppo raggiungibile dalla rete sulla porta 5432 con password nota. | Esposto solo su `127.0.0.1`. |
| CR1 | 🟡 | `/clienti/<id>` di un membro del personale lo mostrava come cliente. | La pagina mostra solo clienti. |
| CR2 | 🟡 | `/lavori/<id>` inesistente generava un errore. | Torna all'elenco dei lavori. |

## Verificato e già corretto

- **SQL injection**: solo query con parametri (JPQL), nessuna concatenazione di testo.
- **XSS**: Vaadin mostra i testi come testo, nessun HTML costruito da dati utente; foto servite con il tipo
  riconosciuto dai primi byte (niente SVG o HTML).
- **CSRF, session fixation, clickjacking, nosniff**: gestiti da Vaadin e Spring Security; HSTS da Caddy.
- **Autorizzazioni**: ogni schermata dichiara chi può aprirla (`@RolesAllowed`); le operazioni del cliente
  verificano nel servizio che bici, lavori e foto siano suoi (test dedicati). Le foto si scaricano solo
  tramite il componente mostrato a chi può vederle.
- **Password**: BCrypt, minimo 8 caratteri, blocco dopo 5 errori, token di recupero di 256 bit salvati come hash.
- **Durate e note interne**: mai mostrate al cliente (test dedicati).
- **Produzione**: database non esposto, app senza porte pubbliche (solo Caddy), container non root,
  `/actuator/health` senza dettagli, nessun segreto nel codice.

## Rischi accettati (da rivalutare se l'uso cresce)

| # | Rischio | Perché è accettabile oggi |
|---|---|---|
| RA1 | La registrazione dice "Esiste già un account con questa email" (si può scoprire se un'email è cliente). | Messaggio utile al cliente; il dato (essere cliente di un negozio di bici) è poco sensibile. |
| RA2 | Chiunque conosca l'email di un cliente può bloccargli l'accesso per 15 minuti sbagliando 5 password. | Disturbo temporaneo; l'alternativa (blocco per indirizzo IP) indebolisce la protezione dai tentativi. |
| RA3 | Nessun limite al numero di registrazioni (account finti). | Il titolare può disattivarli; aggiungere un CAPTCHA se si verificasse. |
| RA4 | I servizi non hanno controlli di ruolo propri: si affidano alle schermate. | Con Vaadin le operazioni partono solo dalle schermate (non c'è un'API esposta). **Se un giorno si aggiunge un'API REST, vanno aggiunti controlli sui servizi.** |
| RA5 | Nessuna Content-Security-Policy. | Vaadin richiede eccezioni che ne ridurrebbero il valore; il rischio XSS è basso (vedi sopra). |

---

# Review fase 9 – Funzionalità opzionali

| Voce | Esito |
|---|---|
| Spente di default | Verificato con test: con gli interruttori spenti le nuove funzioni sono rifiutate dal servizio (non solo nascoste nell'interfaccia). |
| Approvazione extra | Solo il proprietario della bici può rispondere, una sola volta, solo con lavoro aperto (test). Interventi in attesa o rifiutati esclusi da calendario, minuti, statistiche e "bici pronta". |
| Riservatezza tempi | La richiesta al cliente non mostra durate (test d'interfaccia con controllo automatico del testo). L'avviso di carico non mostra numeri (test). |
| Promemoria | Rispettano la scelta del cliente e non si ripetono per lo stesso ritiro (test con date retrodatate). |
| Tempo effettivo | Accettato solo per interventi fatti, 1–1440 minuti; cancellato se si toglie la spunta (test). |
| Trovato in review | Nella prima versione gli interruttori della pagina Funzionalità non avevano un'etichetta di stato chiara: aggiunta l'etichetta "Attiva / Spenta". |
| Rischio residuo | Il calcolo del carico avviene a ogni apertura della pagina bici da parte del cliente: trascurabile per un negozio, da memorizzare se gli accessi crescessero molto. |

# Review fase 10 – Clienti senza app

| Voce | Esito |
|---|---|
| Dati | Email facoltativa solo per i clienti (vincolo `CHECK` nel database oltre ai controlli nel servizio). Più clienti senza email convivono (in PostgreSQL i `NULL` non violano l'unicità). |
| Accesso | Un account senza email non può accedere (nessun nome utente): nessun rischio di account "aperti". La registrazione dal telefono e gli account del personale richiedono ancora l'email (test). |
| Invito | "Invia link" rifiutato finché manca l'email; aggiunta l'email, l'invito funziona; un'email già usata da un altro account è rifiutata (test). |
| Notifiche | "Bici pronta", promemoria e approvazioni per un cliente senza app non generano errori né tentativi di invio (test del percorso completo dal banco al ritiro). |
| Interfaccia | Test senza browser della creazione di cliente e bici dentro *Nuovo lavoro*; verificato anche nel browser. |
| Trovato in review | Un'email fatta di soli spazi veniva rifiutata come "non valida" invece di essere considerata assente: ora vuota = nessuna email. |
| Privacy (aggiunta) | Pulsanti per il personale *Scarica / Elimina i dati del cliente*, conferma obbligatoria dell'informativa al banco, informativa aggiornata (D44). Test: cancellazione rifiutata con lavoro aperto, dati anonimi, cliente non più ricercabile, seconda cancellazione rifiutata; creazione senza informativa rifiutata; percorso completo nell'interfaccia. |
| Rischio residuo | L'informativa contiene ancora segnaposto (dati del negozio e fornitori) da compilare prima della messa online. |

# Review fase 11 – Accesso con codice e passkey

| Voce | Esito |
|---|---|
| Codice via email | Una volta sola, 10 minuti, 5 tentativi, nuovo codice annulla il vecchio, limiti di invio, nessuna differenza di risposta per email sconosciute, account disattivati esclusi (test). Login completo via HTTP con verifica del cookie "resta collegato" (test). |
| Forza bruta | Oltre ai 5 tentativi per codice vale il blocco generale (5 errori sulla stessa email = 15 minuti), condiviso con la password. |
| Passkey | Provato nel browser con un autenticatore virtuale: attivazione, elenco nel profilo, accesso. Rimozione solo del proprietario, cancellazione con i dati personali (test). |
| Trovato in review (1) | I filtri WebAuthn leggevano l'utente collegato da un contenitore diverso da quello di Vaadin: la registrazione rispondeva "non autenticato". Allineati. |
| Trovato in review (2) | Vaadin bloccava l'indirizzo di registrazione della passkey (403): autorizzato per gli utenti collegati. |
| Trovato in review (3) | Dopo l'accesso con passkey il "resta collegato" non riconosceva l'utente (nome letto dal tipo sbagliato) e l'accesso falliva: corretto. Il filtro WebAuthn ora cambia anche l'id di sessione e usa "resta collegato" come il login normale. |
| Personale | Accesso di 12 ore (cookie e sessione), test della scadenza con orologio fisso. |
| Rischio residuo | Sul Mac condiviso con un unico utente macOS, Touch ID non distingue le persone (vedi D45). Le notifiche push e Face ID su un iPhone reale vanno provate dopo la messa online (servono HTTPS e il dominio vero). |

# Review fase 12 – Calendario, ricalcolo e appuntamenti

| Voce | Esito |
|---|---|
| Calendario | Tre visualizzazioni, due settimane, blocco per bici; spostamento di gruppo atomico (prima: errore "Assegnazione non trovata" perché il ricalcolo dopo il primo pezzo cambiava gli id degli altri). |
| Ricalcolo facoltativo | Test: con il ricalcolo spento un lavoro urgente arrivato dopo non sposta gli altri; un intervento aggiunto trova posto; uno fatto libera lo spazio. |
| Appuntamenti | Test di servizio: orari liberi solo nella vendita e senza sovrapposizioni, richiesta che tiene l'orario, doppia prenotazione rifiutata, conferma, spostamento dentro/fuori vendita, assenze, limite per cliente, annullamento solo del proprietario, privacy. Test d'interfaccia: prenotazione dal telefono e conferma dal negozio. |
| Test fragili trovati | Due test davano per scontato un'officina vuota o un intervento in un solo pezzo e fallivano con un ordine di esecuzione diverso: resi indipendenti (verificato in ordine alfabetico e inverso). |
| Trovato in review (interfaccia) | Pulsante dell'orario scelto illeggibile (testo bianco su fondo trasparente); nome del cliente nascosto nei blocchi brevi della linea del tempo: corretti. |
| Rischio residuo | Un appuntamento non si trascina nel calendario (si sposta dal dettaglio). Il logo è una ricostruzione vettoriale dall'immagine del profilo: sostituibile con il file originale (`icons/logo.svg` e `icons/logo.png`). |
