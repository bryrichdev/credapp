-- Every time a stored SSN is decrypted for someone to look at, a row lands here.
--
-- Deliberately no foreign keys: an audit trail has to outlive the records it describes,
-- so the subject and the viewer are snapshotted by id and by the label they had at the
-- time. Deleting an owner or a user leaves their access history intact.
CREATE TABLE ssn_access_log
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_type TEXT        NOT NULL CHECK (subject_type IN ('owner', 'provider')),
    subject_id   BIGINT      NOT NULL,
    subject_name TEXT        NOT NULL,
    user_id      BIGINT      NOT NULL,
    user_email   TEXT        NOT NULL,
    ip_address   TEXT,
    accessed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ssn_access_log_subject
    ON ssn_access_log (subject_type, subject_id, accessed_at DESC);

CREATE INDEX idx_ssn_access_log_user
    ON ssn_access_log (user_id, accessed_at DESC);
