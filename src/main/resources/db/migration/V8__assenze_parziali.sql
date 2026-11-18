-- Fase 7: assenze di una parte della giornata (es. pomeriggio libero, visita medica)
ALTER TABLE assenza
    ADD COLUMN ora_inizio TIME,
    ADD COLUMN ora_fine   TIME,
    ADD CONSTRAINT assenza_parziale CHECK (
        (ora_inizio IS NULL AND ora_fine IS NULL)
        OR (ora_inizio < ora_fine AND dal = al));
