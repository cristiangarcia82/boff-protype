-- Fase 1: dati di contatto dei clienti e catalogo degli interventi

ALTER TABLE utente
    ADD COLUMN telefono             VARCHAR(30),
    ADD COLUMN privacy_accettata_il TIMESTAMPTZ;

CREATE TABLE tipo_intervento (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome                VARCHAR(80) NOT NULL,
    durata_standard_min INTEGER     NOT NULL CHECK (durata_standard_min BETWEEN 5 AND 1440),
    attivo              BOOLEAN     NOT NULL DEFAULT TRUE,
    ordine              INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX tipo_intervento_nome_uk ON tipo_intervento (lower(nome));

-- Catalogo iniziale con tempi indicativi: il titolare può modificarli dall'app
INSERT INTO tipo_intervento (nome, durata_standard_min, ordine) VALUES
    ('Tagliando base (controllo, regolazioni, lubrificazione)', 60, 10),
    ('Tagliando completo (smontaggio, pulizia, regolazioni)', 180, 20),
    ('Regolazione cambio', 20, 30),
    ('Regolazione freni', 20, 40),
    ('Spurgo freni idraulici', 45, 50),
    ('Sostituzione pastiglie / pattini freno', 20, 60),
    ('Sostituzione catena', 20, 70),
    ('Sostituzione trasmissione (catena, pacco pignoni, corone)', 60, 80),
    ('Riparazione foratura', 15, 90),
    ('Sostituzione copertone', 20, 100),
    ('Montaggio tubeless', 30, 110),
    ('Centratura ruota', 30, 120),
    ('Sostituzione cavi e guaine', 45, 130),
    ('Revisione forcella (base)', 60, 140),
    ('Diagnosi e aggiornamento e-bike', 45, 150),
    ('Lavaggio', 30, 160);
