-- Keep encrypted credentials separate from the legacy external-secret reference.
-- This column is deliberately not mapped on Provider: only the audited access service
-- reads/decrypts it, so ordinary provider loads and exports cannot expose the password.
ALTER TABLE providers ADD COLUMN caqh_password_ciphertext TEXT;

-- Snapshot the provider and viewer, as for SSN access. Retain history after deletion.
CREATE TABLE caqh_password_access_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id BIGINT NOT NULL REFERENCES user_groups(id),
    provider_id BIGINT NOT NULL,
    provider_name TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    user_email TEXT NOT NULL,
    ip_address TEXT,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX caqh_password_access_log_subject_idx
    ON caqh_password_access_log (user_group_id, provider_id, accessed_at DESC);
