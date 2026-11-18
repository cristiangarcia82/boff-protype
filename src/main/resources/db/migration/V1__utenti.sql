-- Fase 0: utenti e ruoli
CREATE TABLE utente (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nome          VARCHAR(100) NOT NULL,
    cognome       VARCHAR(100) NOT NULL,
    ruolo         VARCHAR(20)  NOT NULL CHECK (ruolo IN ('STAFF', 'CLIENTE')),
    titolare      BOOLEAN      NOT NULL DEFAULT FALSE,
    attivo        BOOLEAN      NOT NULL DEFAULT TRUE,
    creato_il     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT utente_email_minuscola CHECK (email = lower(email)),
    CONSTRAINT utente_titolare_staff CHECK (NOT titolare OR ruolo = 'STAFF')
);

CREATE UNIQUE INDEX utente_email_uk ON utente (email);
