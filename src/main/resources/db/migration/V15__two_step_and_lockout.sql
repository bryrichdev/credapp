-- Account lockout: after repeated wrong passwords (or two-step codes) an account is locked
-- for a while. The app writes these columns directly; the User entity only reads them.
ALTER TABLE users
    ADD COLUMN failed_sign_ins     INT NOT NULL DEFAULT 0,
    ADD COLUMN last_failed_sign_in TIMESTAMPTZ,
    ADD COLUMN locked_until        TIMESTAMPTZ;

-- Two-step sign-in with an authenticator app (TOTP). The secret is encrypted under the SSN
-- key. enabled_at stays null while it's being set up, until the first code checks out.
CREATE TABLE user_two_step (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret     BYTEA       NOT NULL,
    enabled_at TIMESTAMPTZ,
    -- The newest 30-second step a code was accepted for; a code is never accepted twice.
    last_step  BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One-time codes for signing in without the phone. Only hashes are stored.
CREATE TABLE recovery_codes (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash TEXT   NOT NULL,
    used_at   TIMESTAMPTZ
);

CREATE INDEX recovery_codes_user ON recovery_codes (user_id);
