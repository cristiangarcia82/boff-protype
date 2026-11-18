# Boff Bike

Web app (PWA, senza app store) per un negozio di biciclette con officina:
clienti e loro bici, lavori annunciati dai clienti, calendario dell'officina pianificato
automaticamente su due persone, separando le ore di vendita da quelle di manutenzione.

- **Clienti** (telefono): registrano le bici, annunciano i lavori, seguono lo stato e la data prevista, ricevono una notifica quando la bici è pronta.
- **Negozio** (Mac + telefono): bacheca, lavori, calendario settimanale con interventi trascinabili, clienti, assenze e chiusure, orari, catalogo interventi.

Documentazione: [piano](docs/PIANO.md) · [decisioni](docs/DECISIONI.md) · [review tecnica](docs/REVIEW.md) · [messa online](docs/DEPLOY.md)

**Stack:** Java 21 · Spring Boot 4.1 · Vaadin 25 (Flow, PWA, Web Push) · Spring Security · PostgreSQL · Flyway

## Avvio in sviluppo

Requisiti: JDK 21, Maven, Docker.

```bash
BOFF_DEMO=true mvn spring-boot:run
```

Spring Boot avvia PostgreSQL da `compose.yaml`. Apri <http://localhost:8080>.
Con `BOFF_DEMO=true` vengono creati utenti, orari, bici e lavori di prova (password `demo1234`):

| Email | Ruolo |
|---|---|
| `titolare@demo.it` | Titolare (vede anche *Impostazioni*) |
| `collaboratore@demo.it` | Collaboratore |
| `cliente@demo.it`, `paolo.ferri@demo.it`, `sara.conti@demo.it` | Clienti |

Senza SMTP le email (es. recupero password) sono scritte nel log, link compreso.
Le notifiche push funzionano solo con la build di produzione (service worker attivo) su HTTPS o `localhost`.

## Test

```bash
mvn verify   # test unitari + integrazione su PostgreSQL reale (Testcontainers, serve Docker)
```

## Produzione

Vedi [docs/DEPLOY.md](docs/DEPLOY.md): `deploy/compose.prod.yaml` avvia app, database, HTTPS (Caddy) e backup.

## Struttura

```
src/main/java/it/boff/bike
├── utente/            account, registrazione, profilo
├── bicicletta/        bici dei clienti
├── catalogo/          interventi e tempi standard
├── lavoro/            lavori di officina e loro stati
├── calendario/        orari settimanali, assenze, chiusure, festività
├── pianificazione/    algoritmo e calendario dell'officina
├── notifiche/         web push, email, recupero password
├── security/          Spring Security, ruoli, utente corrente
├── config/            proprietà, orologio, dati iniziali/demo
└── ui/                layout, viste e componenti Vaadin
src/main/resources/db/migration   migrazioni Flyway (V1…V7)
```
