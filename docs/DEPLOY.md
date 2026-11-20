# Messa online

Guida per pubblicare Boff Bike su un piccolo server, con HTTPS e backup automatici.
Tempo stimato: un'ora, la prima volta.

## Cosa serve

| Cosa | Esempio | Costo indicativo |
|---|---|---|
| Un server Linux con Docker (2 GB di RAM bastano) | VPS Hetzner CX22, Aruba Cloud, OVH… in Europa | 5–10 €/mese |
| Un dominio o sottodominio | `officina.boffbike.it` | quello che hai già, oppure ~10 €/anno |
| Un servizio SMTP per le email (consigliato) | quello del tuo provider di posta, Brevo, Mailjet… | spesso gratuito per pochi messaggi |

L'HTTPS è **obbligatorio**: senza di esso l'app non si installa sul telefono e le notifiche non funzionano.
Caddy (incluso) ottiene e rinnova da solo il certificato gratuito Let's Encrypt.

## Passi

1. **DNS**: crea un record `A` del dominio verso l'IP del server.
2. **Sul server** (con Docker e il plugin compose installati):
   ```bash
   git clone https://github.com/cristiangarcia82/boff-protype.git boffbike
   cd boffbike/deploy
   cp .env.example .env
   nano .env            # compila dominio, password e, se possibile, l'SMTP
   docker compose -f compose.prod.yaml up -d --build
   ```
3. Dopo 2–3 minuti apri `https://<tuo-dominio>` e accedi con `BOFF_TITOLARE_EMAIL` / `BOFF_TITOLARE_PASSWORD`.
   **Cambia subito la password** dal profilo.
4. Da *Impostazioni*:
   - **Personale** → crea l'account del collaboratore;
   - **Orari** → imposta le ore di lavoro di entrambi (i turni di vendita si aggiungono poi dal calendario);
   - **Catalogo interventi** → adatta i tempi standard ai vostri.
5. Da *Assenze e chiusure* aggiungi il patrono e le ferie del negozio.
6. **Completa l'informativa privacy** (`src/main/java/it/boff/bike/ui/views/PrivacyView.java`): ragione sociale, indirizzo, P. IVA, contatti, fornitori di hosting ed email. Meglio farla rileggere a un consulente.
7. Installa l'app su Mac e telefono (pagina `/installa`) e attiva le notifiche dalla bacheca.

## Aggiornare a una nuova versione

```bash
cd boffbike && git pull
cd deploy && docker compose -f compose.prod.yaml up -d --build
```
Le modifiche al database vengono applicate in automatico all'avvio (Flyway).

## Backup

- Ogni giorno viene salvata una copia del database in `deploy/backup/` (ultimi 30 giorni).
- **Copiala anche fuori dal server** (es. con `rsync` o `rclone` verso un altro computer o un cloud):
  se il server si rompe, la copia sul server si perde con lui.
- Ripristino di una copia:
  ```bash
  docker compose -f compose.prod.yaml exec -T db \
    pg_restore -U boffbike -d boffbike --clean --if-exists < backup/boffbike-AAAAMMGG.dump
  ```

## Variabili d'ambiente

| Variabile | Obbligatoria | Descrizione |
|---|---|---|
| `DOMINIO` | sì | dominio pubblico (senza https://) |
| `DB_PASSWORD` | sì | password del database |
| `BOFF_REMEMBER_ME_KEY` | sì | segreto per "resta collegato" (`openssl rand -hex 32`) |
| `BOFF_TITOLARE_EMAIL`, `BOFF_TITOLARE_PASSWORD` | al primo avvio | account del titolare |
| `BOFF_NOME_NEGOZIO`, `BOFF_EMAIL_MITTENTE` | no | nome e mittente nelle email |
| `BOFF_FESTIVI_CHIUSI` | no | `false` se aprite nei festivi (default `true`) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | consigliate | server SMTP |
| `BOFF_FUSO_ORARIO` | no | default `Europe/Rome` |

## Controlli

- Stato dell'app: `https://<dominio>/actuator/health` deve rispondere `{"status":"UP"}`.
- Log: `docker compose -f compose.prod.yaml logs -f app`.
