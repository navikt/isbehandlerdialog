ALTER TABLE MELDING ADD COLUMN document JSONB NOT NULL DEFAULT '[]'::jsonb;
