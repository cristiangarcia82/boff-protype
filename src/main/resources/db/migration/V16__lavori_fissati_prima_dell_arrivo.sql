-- Un lavoro annunciato può essere messo in calendario prima che arrivi la bici:
-- quando il cliente deve portarla (inizio del primo intervento fissato).
ALTER TABLE lavoro ADD COLUMN arrivo_previsto TIMESTAMP;
