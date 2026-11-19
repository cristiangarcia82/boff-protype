-- Codici di accesso di 6 cifre inviati via email (uno valido per utente alla volta)
CREATE TABLE codice_accesso (
    id          BIGSERIAL PRIMARY KEY,
    utente_id   BIGINT       NOT NULL REFERENCES utente (id) ON DELETE CASCADE,
    hash        VARCHAR(64)  NOT NULL,
    scade_il    TIMESTAMPTZ  NOT NULL,
    tentativi   INTEGER      NOT NULL DEFAULT 0,
    creato_il   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX codice_accesso_utente ON codice_accesso (utente_id, creato_il);

-- Passkey (Face ID / Touch ID): schema standard di Spring Security per PostgreSQL
CREATE TABLE user_entities (
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(200),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX user_entities_name ON user_entities (name);

CREATE TABLE user_credentials (
    credential_id                VARCHAR(1000) NOT NULL,
    user_entity_user_id          VARCHAR(1000) NOT NULL REFERENCES user_entities (id) ON DELETE CASCADE,
    public_key                   BYTEA         NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL,
    PRIMARY KEY (credential_id)
);
CREATE INDEX user_credentials_utente ON user_credentials (user_entity_user_id);
