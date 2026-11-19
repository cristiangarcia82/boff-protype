-- Appuntamenti di consulenza (vendita): richiesti dai clienti dall'app o fissati dal negozio,
-- pianificati nelle ore di vendita di una persona.

CREATE TABLE tipo_appuntamento (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome        VARCHAR(80)  NOT NULL,
    descrizione VARCHAR(300),
    durata_min  INTEGER      NOT NULL CHECK (durata_min BETWEEN 10 AND 240),
    attivo      BOOLEAN      NOT NULL DEFAULT TRUE,
    ordine      INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX tipo_appuntamento_nome_uk ON tipo_appuntamento (lower(nome));

INSERT INTO tipo_appuntamento (nome, descrizione, durata_min, ordine) VALUES
    ('Consulenza per l''acquisto', 'Scegliamo insieme la bici giusta per te: uso, budget, taglia.', 45, 10),
    ('Consulenza e-bike', 'Modelli, motori, batterie e prova su strada.', 45, 20),
    ('Posizionamento in sella', 'Regolazione di sella, manubrio e tacchette per pedalare comodo.', 60, 30),
    ('Consegna bici nuova', 'Ritiro della bici acquistata, regolazioni e spiegazioni.', 30, 40);

CREATE TABLE appuntamento (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id        BIGINT       NOT NULL REFERENCES utente (id),
    tipo_id           BIGINT       NOT NULL REFERENCES tipo_appuntamento (id),
    utente_id         BIGINT       NOT NULL REFERENCES utente (id),   -- chi del negozio lo segue
    bicicletta_id     BIGINT       REFERENCES bicicletta (id),
    data              DATE         NOT NULL,
    ora_inizio        TIME         NOT NULL,
    ora_fine          TIME         NOT NULL CHECK (ora_fine > ora_inizio),
    stato             VARCHAR(20)  NOT NULL,
    note_cliente      VARCHAR(1000),
    note_interne      VARCHAR(1000),
    richiesto_dal_cliente BOOLEAN  NOT NULL,
    creato_il         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    aggiornato_il     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX appuntamento_giorno ON appuntamento (data, utente_id);
CREATE INDEX appuntamento_cliente ON appuntamento (cliente_id);
