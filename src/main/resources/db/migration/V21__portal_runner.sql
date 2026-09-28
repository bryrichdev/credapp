-- Runners: the CredCloud program on a coordinator's computer that fills payer portals in her
-- own Chrome. A runner is paired to one account with a one-time code, then signs each request
-- with a device token. Only SHA-256 hashes of the code and token are kept, so a copy of the
-- database can't pair or act as a runner. Revoking one stops it at its next request; deleting
-- the account removes its runners and their jobs.
CREATE TABLE runners (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id      BIGINT      NOT NULL REFERENCES user_groups (id),
    user_id            BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name               TEXT        NOT NULL,
    pairing_code_hash  BYTEA UNIQUE,
    pairing_expires_at TIMESTAMPTZ,
    token_hash         BYTEA UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    paired_at          TIMESTAMPTZ,
    last_seen_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    UNIQUE (user_group_id, id)
);
CREATE INDEX runners_user ON runners (user_group_id, user_id);

-- A payer portal's form: where it starts and, per version, which box gets which answer. Each
-- save adds a version, so every fill records exactly the mapping it used.
CREATE TABLE portal_templates (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id BIGINT      NOT NULL REFERENCES user_groups (id),
    payer_id      BIGINT      NOT NULL,
    name          TEXT        NOT NULL,
    start_url     TEXT        NOT NULL,
    revision      INTEGER     NOT NULL DEFAULT 0,
    created_by    TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_group_id, id),
    FOREIGN KEY (user_group_id, payer_id) REFERENCES payers (user_group_id, id) ON DELETE CASCADE
);
CREATE INDEX portal_templates_payer ON portal_templates (user_group_id, payer_id);

CREATE TABLE portal_template_versions (
    user_group_id BIGINT      NOT NULL REFERENCES user_groups (id),
    template_id   BIGINT      NOT NULL,
    revision      INTEGER     NOT NULL,
    fields        JSONB       NOT NULL,
    created_by    TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (template_id, revision),
    FOREIGN KEY (user_group_id, template_id) REFERENCES portal_templates (user_group_id, id) ON DELETE CASCADE
);

-- Work for a runner. 'learn' opens a portal so the coordinator can show which box gets which
-- answer. 'fill' types one provider's answers into a portal and stops; she submits it herself.
-- Answers are encrypted and cleared once the job ends. result lists which fields were filled
-- or not found, by label, never with values.
CREATE TABLE runner_jobs (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id     BIGINT      NOT NULL REFERENCES user_groups (id),
    user_id           BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind              TEXT        NOT NULL CHECK (kind IN ('learn', 'fill')),
    template_id       BIGINT      NOT NULL,
    template_revision INTEGER     NOT NULL,
    provider_id       BIGINT,
    answers           BYTEA,
    status            TEXT        NOT NULL DEFAULT 'waiting'
        CHECK (status IN ('waiting', 'claimed', 'done', 'cancelled')),
    runner_id         BIGINT REFERENCES runners (id) ON DELETE SET NULL,
    result            JSONB,
    created_by        TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    CHECK ((kind = 'fill') = (provider_id IS NOT NULL)),
    CHECK (status IN ('waiting', 'claimed') OR answers IS NULL),
    FOREIGN KEY (user_group_id, template_id) REFERENCES portal_templates (user_group_id, id) ON DELETE CASCADE,
    FOREIGN KEY (user_group_id, provider_id) REFERENCES providers (user_group_id, id) ON DELETE CASCADE
);
CREATE INDEX runner_jobs_waiting ON runner_jobs (user_group_id, user_id, id) WHERE status = 'waiting';
CREATE INDEX runner_jobs_provider ON runner_jobs (user_group_id, provider_id, id DESC);
