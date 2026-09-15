CREATE TABLE education
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id      BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    program_type     TEXT        NOT NULL
        CONSTRAINT program_type_allowed CHECK (program_type IN
                                               ('undergraduate', 'medical_school', 'internship', 'residency',
                                                'fellowship', 'other')),
    institution      TEXT        NOT NULL,
    degree           TEXT,
    specialty        TEXT,
    program_director TEXT,
    start_date       DATE,
    end_date         DATE,
    graduation_date  DATE,
    is_completed     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT education_dates_ordered
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_education_provider_id ON education (provider_id);


CREATE TABLE employment
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id     BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    employer_name   TEXT        NOT NULL,
    position_title  TEXT,
    city            TEXT,
    state           TEXT CHECK (state IS NULL OR state ~ '^[A-Z]{2}$'),
    start_date      DATE        NOT NULL,
    end_date        DATE,
    is_current      BOOLEAN     NOT NULL DEFAULT FALSE,
    gap_explanation TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT employment_dates_ordered
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT employment_current_has_no_end
        CHECK (NOT is_current OR end_date IS NULL)
);

CREATE INDEX idx_employment_provider_id ON employment (provider_id);


CREATE TABLE hospital_affiliations
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id        BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    facility_name      TEXT        NOT NULL,
    department         TEXT,
    staff_status       TEXT,
    privilege_category TEXT,
    appointment_date   DATE,
    reappointment_date DATE,
    is_current         BOOLEAN     NOT NULL DEFAULT TRUE,
    standing           TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_affiliations_provider_id ON hospital_affiliations (provider_id);


CREATE TABLE peer_references
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id  BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    full_name    TEXT        NOT NULL,
    specialty    TEXT,
    relationship TEXT,
    email        TEXT,
    phone_number TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_peer_references_provider_id ON peer_references (provider_id);


CREATE TABLE attestations
(
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id                BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    attested_at                TIMESTAMPTZ,

    license_action             BOOLEAN,
    license_action_detail      TEXT,
    dea_action                 BOOLEAN,
    dea_action_detail          TEXT,
    felony_conviction          BOOLEAN,
    felony_conviction_detail   TEXT,
    privileges_denied          BOOLEAN,
    privileges_denied_detail   TEXT,
    medicare_sanction          BOOLEAN,
    medicare_sanction_detail   TEXT,
    substance_use              BOOLEAN,
    substance_use_detail       TEXT,
    malpractice_history        BOOLEAN,
    malpractice_history_detail TEXT,
    able_to_perform            BOOLEAN,
    able_to_perform_detail     TEXT,

    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attestations_provider_id ON attestations (provider_id);