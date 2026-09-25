-- Forgotten passwords. A coordinator's or read-only account's request waits for an admin to
-- approve it before the reset link is emailed; admins and superusers get the link straight away.
CREATE TABLE password_reset_requests
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id   BIGINT      NOT NULL REFERENCES user_groups (id),
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- PENDING: waiting for an admin. SENT: link emailed. DECLINED, USED, or CANCELLED when a
    -- newer request or a finished reset replaced it.
    status          TEXT        NOT NULL,
    -- SHA-256 of the link's token. The token itself is only ever in the email.
    token_hash      TEXT UNIQUE,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    requested_ip    TEXT,
    decided_at      TIMESTAMPTZ,
    decided_by      TEXT,
    link_expires_at TIMESTAMPTZ,
    used_at         TIMESTAMPTZ,
    CONSTRAINT password_reset_requests_status_check
        CHECK (status IN ('PENDING', 'SENT', 'DECLINED', 'USED', 'CANCELLED'))
);

CREATE INDEX password_reset_requests_group_status_idx ON password_reset_requests (user_group_id, status);
CREATE INDEX password_reset_requests_user_idx ON password_reset_requests (user_id);
