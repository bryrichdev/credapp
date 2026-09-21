CREATE TABLE groups
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lbn        TEXT        NOT NULL,
    dba        TEXT,
    npi        TEXT UNIQUE CHECK (npi ~ '^[0-9]{10}$'),
    tax_id     TEXT        NOT NULL,
    specialty  TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_locations
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    location_name   TEXT        NOT NULL,
    address         TEXT        NOT NULL,
    fax_number      TEXT,
    phone_number    TEXT,
    handicap_access TEXT,
    languages       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_group_locations_group_id ON group_locations (group_id);

CREATE TABLE group_providers
(
    group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    effective_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, provider_id)
);

CREATE TABLE owners
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name   TEXT        NOT NULL,
    last_name    TEXT        NOT NULL,
    dob          DATE,
    ssn          TEXT,
    home_address TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN owners.ssn IS 'AES-256-GCM ciphertext, base64, IV-prefixed; see SsnConverter';

CREATE TABLE group_owners
(
    group_id      BIGINT       NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    owner_id      BIGINT       NOT NULL REFERENCES owners (id) ON DELETE CASCADE,
    percent_owned NUMERIC(5,2) CHECK (percent_owned > 0 AND percent_owned <= 100),
    effective_date DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, owner_id)
);

CREATE INDEX idx_group_owners_owner_id ON group_owners (owner_id);

CREATE TABLE group_owner_relationships
(
    group_id         BIGINT NOT NULL,
    owner_id         BIGINT NOT NULL,
    related_owner_id BIGINT NOT NULL,
    relationship     TEXT   NOT NULL
        CHECK (relationship in ('spouse', 'parent', 'child', 'sibling')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, owner_id, related_owner_id),
    CONSTRAINT gor_ordered CHECK (owner_id < related_owner_id),
    FOREIGN KEY (group_id, owner_id)
        REFERENCES group_owners (group_id, owner_id) ON DELETE CASCADE,
    FOREIGN KEY (group_id, related_owner_id)
        REFERENCES group_owners (group_id, owner_id) ON DELETE CASCADE
);

CREATE INDEX idx_gor_related ON group_owner_relationships (group_id, related_owner_id);

CREATE TABLE payers
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payer_contacts
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payer_id      BIGINT      NOT NULL REFERENCES payers (id) ON DELETE CASCADE,
    group_id      BIGINT REFERENCES groups (id) ON DELETE CASCADE,
    provider_id   BIGINT REFERENCES providers (id) ON DELETE CASCADE,
    role          TEXT        NOT NULL,
    phone_number  TEXT,
    fax_number    TEXT,
    email_address TEXT,
    address       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT payer_contacts_scope_exclusive
        CHECK (group_id IS NULL OR provider_id IS NULL)
);

ALTER TABLE providers DROP CONSTRAINT providers_ssn_check;

COMMENT ON COLUMN providers.ssn IS 'AES-256-GCM ciphertext, base64, IV-prefixed; see SsnConverter';

CREATE INDEX idx_payer_contacts_payer_id ON payer_contacts (payer_id);
CREATE INDEX idx_payer_contacts_group_id ON payer_contacts (group_id);
CREATE INDEX idx_payer_contacts_provider_id ON payer_contacts (provider_id);
CREATE INDEX idx_group_providers_provider_id ON group_providers (provider_id);