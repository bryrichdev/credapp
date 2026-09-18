CREATE TABLE providers
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     TEXT        NOT NULL,
    last_name      TEXT        NOT NULL,
    dob            DATE,
    place_of_birth TEXT,
    npi            TEXT UNIQUE CHECK (npi ~ '^[0-9]{10}$'),
    ssn            TEXT CHECK (ssn ~ '^[0-9]{9}$'),
    sex            TEXT
        CONSTRAINT sex_allowed CHECK (sex IN ('M', 'F', 'X', 'U')),
    phone_number   TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE licenses
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id     BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    state           TEXT        NOT NULL CHECK ( state ~ '^[A-Z]{2}$' ),
    license_number  TEXT        NOT NULL,
    license_type    TEXT        NOT NULL,
    issue_date      DATE,
    expiration_date DATE        NOT NULL,
    status          TEXT        NOT NULL
        CONSTRAINT status_allowed CHECK (status IN
                                         ('active', 'expired', 'suspended', 'revoked', 'surrendered', 'probation',
                                          'inactive', 'pending')),
    restrictions    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_licenses_provider_state_number UNIQUE (provider_id, state, license_number)
);

CREATE INDEX idx_licenses_expiration_date ON licenses (expiration_date);
CREATE INDEX idx_licenses_provider_id ON licenses (provider_id);

CREATE TABLE users
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    full_name     TEXT,
    role          TEXT        NOT NULL DEFAULT 'coordinator'
        CONSTRAINT role_allowed CHECK (role IN ('admin', 'coordinator', 'readonly')),
    is_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email));

CREATE TABLE spring_session (
                                primary_id            CHAR(36) NOT NULL,
                                session_id            CHAR(36) NOT NULL,
                                creation_time         BIGINT   NOT NULL,
                                last_access_time      BIGINT   NOT NULL,
                                max_inactive_interval INT      NOT NULL,
                                expiry_time           BIGINT   NOT NULL,
                                principal_name        VARCHAR(100),
                                CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
                                           session_primary_id CHAR(36)     NOT NULL,
                                           attribute_name     VARCHAR(200) NOT NULL,
                                           attribute_bytes    BYTEA        NOT NULL,
                                           CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
                                           CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
                                               REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
