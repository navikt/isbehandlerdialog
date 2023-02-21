CREATE TABLE DIALOGMELDING_IN
(
    id                              SERIAL PRIMARY KEY,
    uuid                            CHAR(36) NOT NULL UNIQUE,
    created_at                      TIMESTAMPTZ NOT NULL,
    msg_id                          VARCHAR(63) NOT NULL,
    msg_type                        VARCHAR(20) NOT NULL,
    mottak_id                       VARCHAR(63) NOT NULL,
    conversation_ref                VARCHAR(50),
    parent_ref                      VARCHAR(50),
    mottatt_tidspunkt               TIMESTAMPTZ NOT NULL,
    arbeidstaker_personident        VARCHAR(11) NOT NULL,
    behandler_personident           VARCHAR(11),
    behandler_hpr_id                VARCHAR(9),
    legekontor_org_nr               VARCHAR(9),
    legekontor_her_id               VARCHAR(9),
    legekontor_navn                 VARCHAR(255),
    tekst_notat_innhold             TEXT,
    antall_vedlegg                  INT NOT NULL
);

CREATE INDEX IX_DIALOGMELDING_IN_ARBEIDSTAKER_PERSONIDENT ON DIALOGMELDING_IN (arbeidstaker_personident);
CREATE INDEX IX_DIALOGMELDING_IN_BEHANDLER_PERSONIDENT ON DIALOGMELDING_IN (behandler_personident);

CREATE TABLE DIALOGMELDING_IN_FELLESFORMAT
(
    id                              SERIAL PRIMARY KEY,
    dialogmelding_in_id             INTEGER REFERENCES DIALOGMELDING_IN (id) ON DELETE CASCADE,
    fellesformat                    TEXT
);

CREATE INDEX IX_DIALOGMELDING_IN_FELLESFORMAT_DIALOGMELDING_IN_ID ON DIALOGMELDING_IN_FELLESFORMAT (dialogmelding_in_id);
