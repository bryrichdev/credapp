ALTER TABLE providers
    ADD COLUMN prev_names         TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN us_citizen         BOOLEAN,
    ADD COLUMN ecfmg              TEXT,
    ADD COLUMN degree             TEXT,
    ADD COLUMN email_address      TEXT,
    ADD COLUMN street_1           TEXT,
    ADD COLUMN street_2           TEXT,
    ADD COLUMN city               TEXT,
    ADD COLUMN state              TEXT,
    ADD COLUMN zip_code           TEXT,
    ADD COLUMN school_name        TEXT,
    ADD COLUMN graduation_date    DATE,
    ADD COLUMN caqh_id            TEXT,
    ADD COLUMN caqh_username      TEXT,
    ADD COLUMN caqh_secret_ref    TEXT,
    ADD COLUMN languages          TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN flu_shot_date      DATE,
    ADD COLUMN tb_test_date       DATE,
    ADD COLUMN modalities         TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN areas_of_expertise TEXT[] NOT NULL DEFAULT '{}';

CREATE TABLE taxonomies
(
    code       TEXT PRIMARY KEY,
    specialty  TEXT        NOT NULL,
    grouping   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE provider_taxonomies
(
    provider_id BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    code        TEXT        NOT NULL REFERENCES taxonomies (code) ON DELETE RESTRICT,
    is_primary  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider_id, code)
);

CREATE UNIQUE INDEX provider_taxonomies_one_primary
    ON provider_taxonomies (provider_id)
    WHERE is_primary;

CREATE TABLE group_taxonomies
(
    group_id   BIGINT      NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    code       TEXT        NOT NULL REFERENCES taxonomies (code) ON DELETE RESTRICT,
    is_primary BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, code)
);

CREATE UNIQUE INDEX group_taxonomies_one_primary
    ON group_taxonomies (group_id)
    WHERE is_primary;

ALTER TABLE groups
    DROP COLUMN specialty;

ALTER TABLE group_locations
    ALTER COLUMN languages TYPE TEXT[] USING CASE
                                                 WHEN btrim(COALESCE(languages, '')) = '' THEN '{}'::TEXT[]
                                                 ELSE regexp_split_to_array(btrim(languages), '\s*,\s*')
        END;

ALTER TABLE group_locations
    ALTER COLUMN languages SET DEFAULT '{}';

UPDATE group_locations
SET languages = '{}'
WHERE languages IS NULL;

ALTER TABLE group_locations
    ALTER COLUMN languages SET NOT NULL;

-- Lets provider_locations point at (group_id, location_id) as a unit.
ALTER TABLE group_locations
    ADD CONSTRAINT uq_group_locations_group_id_id UNIQUE (group_id, id);

ALTER TABLE owners
    ADD COLUMN street_1 TEXT,
    ADD COLUMN street_2 TEXT,
    ADD COLUMN city     TEXT,
    ADD COLUMN state    TEXT,
    ADD COLUMN zip_code TEXT;

UPDATE owners
SET street_1 = home_address
WHERE home_address IS NOT NULL;

ALTER TABLE owners
    DROP COLUMN home_address;

CREATE TABLE provider_locations
(
    group_id    BIGINT      NOT NULL,
    location_id BIGINT      NOT NULL,
    provider_id BIGINT      NOT NULL,
    pcp_scp     TEXT        NOT NULL CHECK ( pcp_scp IN ('pcp', 'scp') ),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (location_id, provider_id),
    FOREIGN KEY (group_id, location_id)
        REFERENCES group_locations (group_id, id) ON DELETE CASCADE,
    FOREIGN KEY (group_id, provider_id)
        REFERENCES group_providers (group_id, provider_id) ON DELETE CASCADE
);

CREATE TABLE malpractice_policies
(
    id                                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id                       BIGINT REFERENCES providers (id) ON DELETE RESTRICT,
    group_id                          BIGINT REFERENCES groups (id) ON DELETE RESTRICT,
    policy_number                     TEXT        NOT NULL,
    effective_date                    DATE        NOT NULL,
    expiration_date                   DATE,
    original_effective_date           DATE,
    carrier_name                      TEXT        NOT NULL,
    type_of_coverage                  TEXT        NOT NULL,
    amount_of_coverage_per_occurrence NUMERIC(12, 2),
    amount_of_coverage_per_aggregate  NUMERIC(12, 2),
    shared_individual                 TEXT        NOT NULL CHECK ( shared_individual IN ('shared', 'individual') ),
    created_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(provider_id, group_id) = 1),
    CHECK (expiration_date IS NULL OR expiration_date > effective_date),
    CHECK (original_effective_date IS NULL OR original_effective_date <= effective_date),
    UNIQUE (carrier_name, policy_number)
);

CREATE TABLE malpractice_claims
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id  BIGINT      NOT NULL REFERENCES providers (id) ON DELETE RESTRICT,
    policy_id    BIGINT REFERENCES malpractice_policies (id) ON DELETE RESTRICT,
    claim_number TEXT        NOT NULL,
    carrier_name TEXT        NOT NULL,
    outcome      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (carrier_name, claim_number)
);

CREATE TABLE provider_references
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id   BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    name          TEXT        NOT NULL,
    title         TEXT,
    relationship  TEXT        NOT NULL,
    email_address TEXT,
    street_1      TEXT,
    street_2      TEXT,
    city          TEXT,
    state         TEXT,
    zip_code      TEXT,
    phone_number  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hospital_privileges
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id         BIGINT      NOT NULL REFERENCES providers (id) ON DELETE RESTRICT,
    name                TEXT        NOT NULL,
    status              TEXT        NOT NULL CHECK ( status IN ('active', 'temporary', 'courtesy', 'pending') ),
    admitting_physician BIGINT      REFERENCES providers (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (admitting_physician IS NULL OR admitting_physician <> provider_id)
);

CREATE TABLE certifications
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id     BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    board           TEXT        NOT NULL,
    effective_date  DATE        NOT NULL,
    expiration_date DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expiration_date IS NULL OR expiration_date > effective_date)
);

CREATE TABLE criminal_charges
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id        BIGINT      NOT NULL REFERENCES providers (id) ON DELETE RESTRICT,
    classification     TEXT        NOT NULL CHECK ( classification IN ('felony', 'misdemeanor') ),
    status             TEXT        NOT NULL CHECK ( status IN ('pending', 'convicted', 'dismissed', 'acquitted', 'expunged') ),
    incident_date      DATE,
    date_of_filing     DATE,
    case_number        TEXT,
    court              TEXT,
    statutory_citation TEXT,
    sentencing_terms   TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (date_of_filing IS NULL OR incident_date IS NULL OR date_of_filing >= incident_date)
);

CREATE INDEX ON malpractice_policies (provider_id);
CREATE INDEX ON malpractice_policies (group_id);
CREATE INDEX ON malpractice_claims (provider_id);
CREATE INDEX ON malpractice_claims (policy_id);
CREATE INDEX ON provider_references (provider_id);
CREATE INDEX ON hospital_privileges (provider_id);
CREATE INDEX ON hospital_privileges (admitting_physician);
CREATE INDEX ON certifications (provider_id);
CREATE INDEX ON criminal_charges (provider_id);
CREATE INDEX ON provider_locations (provider_id);
CREATE INDEX ON provider_locations (group_id, provider_id);
CREATE INDEX ON provider_taxonomies (code);
CREATE INDEX ON group_taxonomies (code);

CREATE INDEX ON certifications (expiration_date);
CREATE INDEX ON malpractice_policies (expiration_date);

CREATE INDEX ON providers USING GIN (languages);
CREATE INDEX ON group_locations USING GIN (languages);
