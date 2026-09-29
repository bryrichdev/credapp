-- CredCloud Helper, the program that fills portals in a coordinator's own Chrome, connects to
-- her account with a one-time code she gets on a signed-in CredCloud page. Only a hash of the
-- code is kept. It works once, and only for 30 minutes. A code belongs to an account, not a
-- workspace: it goes when the account does, and the helper joins the account's workspace.
CREATE TABLE runner_pairing_codes (
    code_hash  BYTEA PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX runner_pairing_codes_user ON runner_pairing_codes (user_id);
