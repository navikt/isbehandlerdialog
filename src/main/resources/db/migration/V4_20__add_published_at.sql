ALTER TABLE MELDING ADD COLUMN utgaende_published_at TIMESTAMP WITH TIME ZONE;

UPDATE MELDING SET utgaende_published_at = created_at WHERE not innkommende
