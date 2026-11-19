-- Data della cancellazione (anonimizzazione) dei dati personali di un cliente
ALTER TABLE utente ADD COLUMN eliminato_il TIMESTAMPTZ;
-- i clienti anonimizzati in precedenza non tengono un'email fittizia
UPDATE utente SET eliminato_il = now(), email = NULL WHERE email LIKE '%@utente-eliminato.invalid';
