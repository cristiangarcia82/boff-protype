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
