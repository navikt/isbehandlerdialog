ALTER TABLE MELDING ADD COLUMN msg_id TEXT DEFAULT NULL;
CREATE INDEX IX_MELDING_MSG_ID ON MELDING (msg_id) WHERE msg_id IS NOT NULL;
