CREATE TABLE addresses
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id  BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    address_type TEXT        NOT NULL
        CONSTRAINT address_type_allowed CHECK (address_type IN ('home', 'mailing', 'practice')),
    street_1     TEXT        NOT NULL,
    street_2     TEXT,
    city         TEXT        NOT NULL,
    state        TEXT        NOT NULL CHECK (state ~ '^[A-Z]{2}$'),
    postal_code  TEXT        NOT NULL CHECK (postal_code ~ '^[0-9]{5}(-[0-9]{4})?$'),
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_provider_id ON addresses (provider_id);

CREATE UNIQUE INDEX uq_addresses_one_primary
    ON addresses (provider_id, address_type)
    WHERE is_primary;


CREATE TABLE prior_names
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    first_name  TEXT        NOT NULL,
    last_name   TEXT        NOT NULL,
    used_from   DATE,
    used_until  DATE,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT prior_names_dates_ordered
        CHECK (used_until IS NULL OR used_from IS NULL OR used_until >= used_from)
);

CREATE INDEX idx_prior_names_provider_id ON prior_names (provider_id);