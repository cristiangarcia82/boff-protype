-- Le fasce settimanali "Vendita" non ci sono più (richiesta del titolare): l'orario settimanale è
-- solo l'orario di lavoro, condiviso da lavori di officina e appuntamenti. Le vecchie fasce di vendita
-- diventano ore di lavoro (nessuno perde ore). La vendita al banco si inserisce a mano, giorno per giorno.
UPDATE fascia_oraria SET tipo = 'MANUTENZIONE' WHERE tipo = 'VENDITA';

CREATE TABLE turno_vendita (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    utente_id   BIGINT       NOT NULL REFERENCES utente (id),
    data        DATE         NOT NULL,
    ora_inizio  TIME         NOT NULL,
    ora_fine    TIME         NOT NULL CHECK (ora_fine > ora_inizio),
    note        VARCHAR(200)
);
CREATE INDEX turno_vendita_giorno ON turno_vendita (data, utente_id);
