-- Fase 8: foto del problema allegate ai lavori (salvate nel database: rientrano nei backup)
CREATE TABLE foto_lavoro (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lavoro_id   BIGINT       NOT NULL REFERENCES lavoro (id) ON DELETE CASCADE,
    nome_file   VARCHAR(200) NOT NULL,
    tipo        VARCHAR(50)  NOT NULL,
    dati        BYTEA        NOT NULL,
    dimensione  INTEGER      NOT NULL CHECK (dimensione > 0 AND dimensione <= 8388608),
    dal_cliente BOOLEAN      NOT NULL,
    caricata_il TIMESTAMPTZ  NOT NULL
);
CREATE INDEX foto_lavoro_lavoro_idx ON foto_lavoro (lavoro_id);
