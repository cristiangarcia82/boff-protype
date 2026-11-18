-- Fase 5: notifiche push, impostazioni di sistema, recupero password

-- Impostazioni generate dall'app (es. chiavi VAPID per le notifiche push)
CREATE TABLE impostazione (
    chiave VARCHAR(100) PRIMARY KEY,
    valore TEXT         NOT NULL
);

-- Un dispositivo/browser su cui l'utente ha attivato le notifiche
CREATE TABLE iscrizione_push (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    utente_id BIGINT        NOT NULL REFERENCES utente (id) ON DELETE CASCADE,
    endpoint  VARCHAR(1000) NOT NULL UNIQUE,
    p256dh    VARCHAR(200)  NOT NULL,
    auth      VARCHAR(100)  NOT NULL,
    creata_il TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX iscrizione_push_utente_idx ON iscrizione_push (utente_id);

-- Link monouso per impostare/reimpostare la password (si salva solo l'hash del token)
CREATE TABLE token_password (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    utente_id  BIGINT      NOT NULL REFERENCES utente (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    creato_il  TIMESTAMPTZ NOT NULL,
    scade_il   TIMESTAMPTZ NOT NULL,
    usato_il   TIMESTAMPTZ
);
CREATE INDEX token_password_utente_idx ON token_password (utente_id);
