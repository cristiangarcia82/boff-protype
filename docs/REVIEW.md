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
