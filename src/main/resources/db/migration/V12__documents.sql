-- Uploaded files: license copies, DEA certificates, CVs, W-9s and the like. Each belongs to a
-- provider or to a practice group. The file itself is encrypted by the app (AES-256-GCM, the
-- same key as SSNs) before it's stored, so a copy of the database alone can't be read.
CREATE TABLE documents
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id   BIGINT      NOT NULL REFERENCES user_groups (id),
    provider_id     BIGINT REFERENCES providers (id) ON DELETE CASCADE,
    group_id        BIGINT REFERENCES groups (id) ON DELETE CASCADE,
    doc_type        TEXT        NOT NULL,
    title           TEXT,
    file_name       TEXT        NOT NULL,
    content_type    TEXT        NOT NULL,
    size_bytes      BIGINT      NOT NULL,
    content         BYTEA       NOT NULL,
    expiration_date DATE,
    uploaded_by     TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT documents_one_owner CHECK ((provider_id IS NULL) <> (group_id IS NULL))
);

CREATE INDEX documents_provider_idx ON documents (provider_id) WHERE provider_id IS NOT NULL;
CREATE INDEX documents_group_idx ON documents (group_id) WHERE group_id IS NOT NULL;
CREATE INDEX documents_user_group_idx ON documents (user_group_id);

-- Who opened which document, like the SSN and CAQH password logs. A W-9 or a DEA certificate
-- carries the same sort of detail. Kept after the document is deleted.
CREATE TABLE document_access_log
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id BIGINT      NOT NULL REFERENCES user_groups (id),
    document_id   BIGINT      NOT NULL,
    file_name     TEXT        NOT NULL,
    user_id       BIGINT      NOT NULL,
    user_email    TEXT        NOT NULL,
    ip_address    TEXT,
    accessed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX document_access_log_document_idx ON document_access_log (user_group_id, document_id, accessed_at DESC);
