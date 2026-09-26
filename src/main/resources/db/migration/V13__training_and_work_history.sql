-- Postgraduate training and work history, as CAQH and most applications ask for them.

CREATE TABLE provider_training
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id     BIGINT      NOT NULL REFERENCES user_groups (id),
    provider_id       BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    -- INTERNSHIP, RESIDENCY, FELLOWSHIP or OTHER
    training_type     TEXT        NOT NULL,
    institution       TEXT        NOT NULL,
    specialty         TEXT,
    city              TEXT,
    state             TEXT,
    start_date        DATE        NOT NULL,
    end_date          DATE,
    completed         BOOLEAN     NOT NULL DEFAULT true,
    incomplete_reason TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX provider_training_provider_idx ON provider_training (provider_id);

-- Jobs, and time away from work with its explanation. Applications ask for every gap of six
-- months or more to be explained; a GAP entry is that explanation.
CREATE TABLE provider_work_history
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id      BIGINT      NOT NULL REFERENCES user_groups (id),
    provider_id        BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    -- JOB or GAP
    entry_type         TEXT        NOT NULL,
    employer           TEXT,
    position           TEXT,
    city               TEXT,
    state              TEXT,
    start_date         DATE        NOT NULL,
    end_date           DATE,
    reason_for_leaving TEXT,
    gap_explanation    TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX provider_work_history_provider_idx ON provider_work_history (provider_id);
