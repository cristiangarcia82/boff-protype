-- Fase 10: clienti gestiti dal negozio che non usano l'app (senza email)
ALTER TABLE utente ALTER COLUMN email DROP NOT NULL;
ALTER TABLE utente ADD CONSTRAINT utente_email_per_staff CHECK (email IS NOT NULL OR ruolo = 'CLIENTE');
-- l'indice unico su email resta: più clienti senza email (NULL) sono ammessi
