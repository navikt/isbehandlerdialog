CREATE TABLE MELDING
(
    id                              SERIAL PRIMARY KEY,
    uuid                            CHAR(36) NOT NULL UNIQUE,
    created_at                      TIMESTAMPTZ NOT NULL,
    innkommende                     BOOLEAN NOT NULL,
    type                            VARCHAR(20) NOT NULL,
    conversation_ref                VARCHAR(50) NOT NULL,
    parent_ref                      VARCHAR(50),
    tidspunkt                       TIMESTAMPTZ NOT NULL,
    arbeidstaker_personident        VARCHAR(11) NOT NULL,
    behandler_personident           VARCHAR(11),
    behandler_ref                   CHAR(36),
    tekst                           TEXT,
    antall_vedlegg                  INT NOT NULL
);

CREATE INDEX IX_MELDING_ARBEIDSTAKER_PERSONIDENT ON MELDING (arbeidstaker_personident);

CREATE TABLE MELDING_FELLESFORMAT
(
    id                              SERIAL PRIMARY KEY,
    melding_id                      INTEGER REFERENCES MELDING (id) ON DELETE CASCADE,
    fellesformat                    TEXT
);

CREATE INDEX IX_MELDING_FELLESFORMAT_MELDING_ID ON MELDING_FELLESFORMAT (melding_id);
