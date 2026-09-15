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
