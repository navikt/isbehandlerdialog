CREATE TABLE pdf
(
    id         SERIAL PRIMARY KEY,
    melding_id INTEGER REFERENCES melding (id) ON DELETE CASCADE,
    uuid       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    pdf        bytea       NOT NULL
);

CREATE INDEX IX_PDF_MELDING_ID ON PDF (melding_id);