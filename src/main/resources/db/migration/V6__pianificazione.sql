-- Fase 4: assegnazione degli interventi alle persone, nelle ore di officina
CREATE TABLE assegnazione (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    riga_lavoro_id BIGINT  NOT NULL REFERENCES riga_lavoro (id) ON DELETE CASCADE,
    utente_id      BIGINT  NOT NULL REFERENCES utente (id),
    data           DATE    NOT NULL,
    ora_inizio     TIME    NOT NULL,
    ora_fine       TIME    NOT NULL,
    -- spostata a mano: la ripianificazione automatica non la tocca
    bloccata       BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT assegnazione_intervallo CHECK (ora_fine > ora_inizio)
);
CREATE INDEX assegnazione_data_idx ON assegnazione (data);
CREATE INDEX assegnazione_riga_idx ON assegnazione (riga_lavoro_id);
