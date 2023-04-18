CREATE TABLE vedlegg
(
    id         SERIAL PRIMARY KEY,
    melding_id INTEGER REFERENCES melding (id) ON DELETE CASCADE,
    uuid       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    number     INTEGER NOT NULL,
    pdf        bytea       NOT NULL
);

CREATE INDEX IX_VEDLEGG_MELDING_ID ON vedlegg (melding_id);
