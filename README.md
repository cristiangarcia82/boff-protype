# Boff Bike

Web app mobile (PWA) per un negozio di biciclette: clienti, biciclette, lavori di officina
e pianificazione della manutenzione. Il piano completo è in [docs/PIANO.md](docs/PIANO.md).

**Stack:** Java 21 · Spring Boot 4.1 · Vaadin 25 (Flow) · Spring Security · PostgreSQL · Flyway

## Avvio in sviluppo

Requisiti: JDK 21, Maven, Docker.

```bash
BOFF_DEMO=true mvn spring-boot:run
```

Spring Boot avvia automaticamente PostgreSQL da `compose.yaml`. Apri <http://localhost:8080>.
Con `BOFF_DEMO=true` vengono creati utenti di prova (password `demo1234`):

| Email | Ruolo |
|---|---|
| `titolare@demo.it` | Staff + titolare (vede anche *Impostazioni*) |
| `collaboratore@demo.it` | Staff |
| `cliente@demo.it` | Cliente |

## Test

```bash
mvn verify   # include i test di integrazione con PostgreSQL in Docker (Testcontainers)
```

## Produzione

```bash
mvn -Pproduction package        # oppure: docker build -t boff-bike .
```

Variabili d'ambiente:

| Variabile | Descrizione |
|---|---|
| `DATABASE_URL` | es. `jdbc:postgresql://db:5432/boffbike` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | credenziali database |
| `BOFF_TITOLARE_EMAIL`, `BOFF_TITOLARE_PASSWORD` | primo account titolare, creato al primo avvio se non esiste |
| `BOFF_TITOLARE_NOME`, `BOFF_TITOLARE_COGNOME` | facoltativi |
| `PORT` | porta HTTP (default 8080) |

L'app va servita in **HTTPS** (necessario per installarla come PWA e per le notifiche push).

## Struttura

```
src/main/java/it/boff/bike
├── AppShell.java          configurazione PWA e tema
├── config/                proprietà e dati iniziali (titolare, utenti demo)
├── security/              Spring Security, ruoli, utente corrente
├── utente/                entità Utente e repository
└── ui/                    layout e viste Vaadin
src/main/resources/db/migration   migrazioni Flyway
```
