-- Payer enrollment: which payers each provider and each group is (or is becoming) in network
-- with, and where each application stands. A group's enrollment can name the payer's
-- account rep assigned to that group, chosen from the payer's contacts.

-- A contact can now carry a person's name, so a rep reads as someone rather than a role.
ALTER TABLE payer_contacts ADD COLUMN name TEXT;

-- Lets an enrollment's rep be pinned to a contact of the same payer in the same workspace.
ALTER TABLE payer_contacts ADD UNIQUE (user_group_id, payer_id, id);

CREATE TABLE provider_payers
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id     BIGINT      NOT NULL REFERENCES user_groups (id),
    provider_id       BIGINT      NOT NULL,
    payer_id          BIGINT      NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'not_started',
    payer_assigned_id TEXT,
    submitted_date    DATE,
    effective_date    DATE,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT provider_payers_status_check CHECK (status IN
        ('not_started', 'in_progress', 'submitted', 'active', 'denied', 'terminated')),
    CONSTRAINT provider_payers_active_has_effective_date CHECK (status <> 'active' OR effective_date IS NOT NULL),
    CONSTRAINT provider_payers_once UNIQUE (user_group_id, provider_id, payer_id),
    FOREIGN KEY (user_group_id, provider_id) REFERENCES providers (user_group_id, id) ON DELETE CASCADE,
    FOREIGN KEY (user_group_id, payer_id) REFERENCES payers (user_group_id, id) ON DELETE CASCADE
);

CREATE INDEX provider_payers_payer_idx ON provider_payers (payer_id);

CREATE TABLE group_payers
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id     BIGINT      NOT NULL REFERENCES user_groups (id),
    group_id          BIGINT      NOT NULL,
    payer_id          BIGINT      NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'not_started',
    payer_assigned_id TEXT,
    submitted_date    DATE,
    effective_date    DATE,
    notes             TEXT,
    -- The payer's designated rep for this group, when it has one. Deleting the contact
    -- clears the rep but keeps the enrollment.
    account_rep_id    BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT group_payers_status_check CHECK (status IN
        ('not_started', 'in_progress', 'submitted', 'active', 'denied', 'terminated')),
    CONSTRAINT group_payers_active_has_effective_date CHECK (status <> 'active' OR effective_date IS NOT NULL),
    CONSTRAINT group_payers_once UNIQUE (user_group_id, group_id, payer_id),
    FOREIGN KEY (user_group_id, group_id) REFERENCES groups (user_group_id, id) ON DELETE CASCADE,
    FOREIGN KEY (user_group_id, payer_id) REFERENCES payers (user_group_id, id) ON DELETE CASCADE,
    FOREIGN KEY (user_group_id, payer_id, account_rep_id)
        REFERENCES payer_contacts (user_group_id, payer_id, id) ON DELETE SET NULL (account_rep_id)
);

CREATE INDEX group_payers_payer_idx ON group_payers (payer_id);
CREATE INDEX group_payers_account_rep_idx ON group_payers (account_rep_id);
