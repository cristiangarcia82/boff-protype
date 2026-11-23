-- Foglio lavori: per ogni intervento fatto, quando e da chi.
ALTER TABLE riga_lavoro
    ADD COLUMN completata_il TIMESTAMPTZ,
    ADD COLUMN completata_da BIGINT REFERENCES utente (id) ON DELETE SET NULL;

-- Interventi già fatti prima di questa versione: fine dell'ultimo blocco in calendario e chi lo aveva,
-- altrimenti il momento in cui la bici è stata segnata pronta (o chiusa).
UPDATE riga_lavoro r SET
    completata_il = COALESCE(
        (SELECT (a.data + a.ora_fine) AT TIME ZONE 'Europe/Zurich' FROM assegnazione a
          WHERE a.riga_lavoro_id = r.id ORDER BY a.data DESC, a.ora_fine DESC LIMIT 1),
        (SELECT COALESCE(l.pronto_il, l.chiuso_il) FROM lavoro l WHERE l.id = r.lavoro_id)),
    completata_da = (SELECT a.utente_id FROM assegnazione a
          WHERE a.riga_lavoro_id = r.id ORDER BY a.data DESC, a.ora_fine DESC LIMIT 1)
WHERE r.completata = TRUE;
