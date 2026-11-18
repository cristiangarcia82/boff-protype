-- Fase 2: lavori di officina e loro righe (interventi)
CREATE TABLE lavoro (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bicicletta_id  BIGINT       NOT NULL REFERENCES bicicletta (id),
    stato          VARCHAR(20)  NOT NULL CHECK (stato IN
                       ('ANNUNCIATO', 'IN_NEGOZIO', 'PIANIFICATO', 'IN_LAVORAZIONE', 'PRONTO', 'RITIRATO', 'ANNULLATO')),
    urgente        BOOLEAN      NOT NULL DEFAULT FALSE,
    note_cliente   VARCHAR(1000),
    note_interne   VARCHAR(2000),
    data_promessa  DATE,          -- data concordata con il cliente (facoltativa)
    fine_prevista  DATE,          -- calcolata dalla pianificazione
    annunciato_il  TIMESTAMPTZ  NOT NULL,
    checkin_il     TIMESTAMPTZ,
    pronto_il      TIMESTAMPTZ,
    chiuso_il      TIMESTAMPTZ,   -- ritiro o annullamento
    versione       BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX lavoro_stato_idx ON lavoro (stato);
CREATE INDEX lavoro_bicicletta_idx ON lavoro (bicicletta_id);
-- Una bici può avere un solo lavoro aperto alla volta
CREATE UNIQUE INDEX lavoro_aperto_per_bici_uk ON lavoro (bicicletta_id)
    WHERE stato NOT IN ('RITIRATO', 'ANNULLATO');

CREATE TABLE riga_lavoro (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lavoro_id          BIGINT       NOT NULL REFERENCES lavoro (id) ON DELETE CASCADE,
    tipo_intervento_id BIGINT       REFERENCES tipo_intervento (id),
    descrizione        VARCHAR(200) NOT NULL,
    durata_min         INTEGER      NOT NULL CHECK (durata_min BETWEEN 5 AND 1440),
    completata         BOOLEAN      NOT NULL DEFAULT FALSE,
    ordine             INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX riga_lavoro_lavoro_idx ON riga_lavoro (lavoro_id);
