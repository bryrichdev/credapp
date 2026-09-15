CREATE TABLE certifications
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id        BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    specialty          TEXT        NOT NULL,
    certifying_board   TEXT        NOT NULL,
    initial_cert_date  DATE,
    expiration_date    DATE,
    recert_date        DATE,
    is_moc_participant BOOLEAN     NOT NULL DEFAULT FALSE,
    status             TEXT        NOT NULL
        CONSTRAINT cert_status_allowed CHECK (status IN
                                              ('active', 'expired', 'lapsed', 'revoked', 'pending', 'lifetime')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_certifications_provider_board_specialty
        UNIQUE (provider_id, certifying_board, specialty)
);

CREATE INDEX idx_certifications_provider_id ON certifications (provider_id);
CREATE INDEX idx_certifications_expiration_date ON certifications (expiration_date);


CREATE TABLE dea_registrations
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id           BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    dea_number            TEXT        NOT NULL CHECK (dea_number ~ '^[A-Z]{2}[0-9]{7}$'),
    state                 TEXT        NOT NULL CHECK (state ~ '^[A-Z]{2}$'),
    schedules             TEXT[]      NOT NULL DEFAULT '{}',
    registered_address_id BIGINT      REFERENCES addresses (id) ON DELETE SET NULL,
    issue_date            DATE,
    expiration_date       DATE        NOT NULL,
    status                TEXT        NOT NULL
        CONSTRAINT dea_status_allowed CHECK (status IN
                                             ('active', 'expired', 'surrendered', 'revoked', 'suspended', 'pending')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_dea_provider_number UNIQUE (provider_id, dea_number)
);

CREATE INDEX idx_dea_provider_id ON dea_registrations (provider_id);
CREATE INDEX idx_dea_expiration_date ON dea_registrations (expiration_date);


CREATE TABLE malpractice_policies
(
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id          BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    carrier_name         TEXT        NOT NULL,
    policy_number        TEXT        NOT NULL,
    coverage_type        TEXT        NOT NULL
        CONSTRAINT coverage_type_allowed CHECK (coverage_type IN ('claims-made', 'occurrence')),
    per_occurrence_limit NUMERIC(12, 2),
    aggregate_limit      NUMERIC(12, 2),
    effective_date       DATE        NOT NULL,
    expiration_date      DATE        NOT NULL,
    has_tail_coverage    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT malpractice_dates_ordered CHECK (expiration_date >= effective_date),
    CONSTRAINT uq_malpractice_provider_policy UNIQUE (provider_id, carrier_name, policy_number)
);

CREATE INDEX idx_malpractice_provider_id ON malpractice_policies (provider_id);
CREATE INDEX idx_malpractice_expiration_date ON malpractice_policies (expiration_date);


CREATE TABLE malpractice_claims
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id   BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    policy_id     BIGINT      REFERENCES malpractice_policies (id) ON DELETE SET NULL,
    incident_date DATE,
    filed_date    DATE,
    allegation    TEXT,
    disposition   TEXT,
    amount_paid   NUMERIC(12, 2),
    status        TEXT        NOT NULL
        CONSTRAINT claim_status_allowed CHECK (status IN
                                               ('open', 'settled', 'dismissed', 'judgment', 'withdrawn')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_malpractice_claims_provider_id ON malpractice_claims (provider_id);