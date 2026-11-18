-- Fase 3: orari settimanali del personale, assenze e chiusure del negozio

-- Orario settimanale "tipo" di ogni persona: fasce di vendita o di manutenzione
CREATE TABLE fascia_oraria (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    utente_id        BIGINT      NOT NULL REFERENCES utente (id),
    giorno_settimana INTEGER     NOT NULL CHECK (giorno_settimana BETWEEN 1 AND 7), -- 1 = lunedì
    ora_inizio       TIME        NOT NULL,
    ora_fine         TIME        NOT NULL,
    tipo             VARCHAR(20) NOT NULL CHECK (tipo IN ('VENDITA', 'MANUTENZIONE')),
    CONSTRAINT fascia_oraria_intervallo CHECK (ora_fine > ora_inizio)
);
CREATE INDEX fascia_oraria_utente_idx ON fascia_oraria (utente_id);

-- Ferie, giorni liberi, malattia… (giornate intere)
CREATE TABLE assenza (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    utente_id BIGINT      NOT NULL REFERENCES utente (id),
    dal       DATE        NOT NULL,
    al        DATE        NOT NULL,
    motivo    VARCHAR(20) NOT NULL CHECK (motivo IN ('FERIE', 'RIPOSO', 'MALATTIA', 'ALTRO')),
    note      VARCHAR(200),
    CONSTRAINT assenza_intervallo CHECK (al >= dal)
);
CREATE INDEX assenza_periodo_idx ON assenza (dal, al);

-- Giorni di chiusura del negozio oltre alle festività nazionali (patrono, inventario…)
CREATE TABLE chiusura (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data        DATE         NOT NULL UNIQUE,
    descrizione VARCHAR(100) NOT NULL
);
