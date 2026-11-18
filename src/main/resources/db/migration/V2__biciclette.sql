-- Biciclette dei clienti
CREATE TABLE bicicletta (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proprietario_id BIGINT       NOT NULL REFERENCES utente (id),
    marca          VARCHAR(60)  NOT NULL,
    modello        VARCHAR(80)  NOT NULL,
    tipologia      VARCHAR(20)  NOT NULL CHECK (tipologia IN
                       ('CITTA', 'STRADA', 'GRAVEL', 'MTB', 'TREKKING', 'EBIKE', 'BAMBINO', 'ALTRO')),
    elettrica      BOOLEAN      NOT NULL DEFAULT FALSE,
    taglia         VARCHAR(20),
    colore         VARCHAR(40),
    anno           INTEGER CHECK (anno BETWEEN 1950 AND 2100),
    numero_telaio  VARCHAR(40),
    note           VARCHAR(500),
    -- una bici rimossa dal cliente resta in archivio (servirà per lo storico dei lavori)
    archiviata     BOOLEAN      NOT NULL DEFAULT FALSE,
    creata_il      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT bicicletta_marca_non_vuota CHECK (btrim(marca) <> ''),
    CONSTRAINT bicicletta_modello_non_vuoto CHECK (btrim(modello) <> '')
);

CREATE INDEX bicicletta_proprietario_idx ON bicicletta (proprietario_id) WHERE NOT archiviata;
