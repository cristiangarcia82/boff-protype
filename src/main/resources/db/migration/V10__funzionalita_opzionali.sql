-- Fase 9: funzionalità opzionali, attivabili dal titolare (spente di default)

-- (1) Approvazione dei lavori extra da parte del cliente, (3) tempo effettivo
ALTER TABLE riga_lavoro
    ADD COLUMN approvazione      VARCHAR(20) CHECK (approvazione IN ('DA_APPROVARE', 'APPROVATA', 'RIFIUTATA')),
    ADD COLUMN nota_approvazione VARCHAR(300),
    ADD COLUMN risposta_il       TIMESTAMPTZ,
    ADD COLUMN minuti_effettivi  INTEGER CHECK (minuti_effettivi BETWEEN 1 AND 1440);

-- (2) Promemoria di manutenzione periodica
ALTER TABLE bicicletta ADD COLUMN promemoria_inviato_il TIMESTAMPTZ;
ALTER TABLE utente ADD COLUMN promemoria_attivi BOOLEAN NOT NULL DEFAULT TRUE;
