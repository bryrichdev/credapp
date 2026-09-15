CREATE TABLE users
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    full_name     TEXT,
    role          TEXT        NOT NULL DEFAULT 'coordinator'
        CONSTRAINT role_allowed CHECK (role IN ('admin', 'coordinator', 'readonly')),
    is_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE documents
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id     BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    document_type   TEXT        NOT NULL
        CONSTRAINT document_type_allowed CHECK (document_type IN
                                                ('cv', 'license', 'dea_certificate', 'board_certificate',
                                                 'insurance_face_sheet',
                                                 'diploma', 'immunization', 'photo_id', 'w9', 'attestation', 'other')),
    file_name       TEXT        NOT NULL,
    content_type    TEXT,
    size_bytes      BIGINT,
    s3_bucket       TEXT        NOT NULL,
    s3_key          TEXT        NOT NULL,
    expiration_date DATE,
    uploaded_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_documents_s3_key UNIQUE (s3_bucket, s3_key)
);

CREATE INDEX idx_documents_provider_id ON documents (provider_id);
CREATE INDEX idx_documents_expiration_date ON documents (expiration_date);


CREATE TABLE form_templates
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL,
    payer_name TEXT,
    version    TEXT        NOT NULL,
    form_kind  TEXT        NOT NULL
        CONSTRAINT form_kind_allowed CHECK (form_kind IN ('pdf', 'portal')),
    s3_bucket  TEXT,
    s3_key     TEXT,
    portal_url TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_form_templates_name_version UNIQUE (name, version)
);


CREATE TABLE field_mappings
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id  BIGINT      NOT NULL REFERENCES form_templates (id) ON DELETE CASCADE,
    target_field TEXT        NOT NULL,
    source_path  TEXT        NOT NULL,
    transform    TEXT,
    options      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    is_required  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_field_mappings_template_field UNIQUE (template_id, target_field)
);

CREATE INDEX idx_field_mappings_template_id ON field_mappings (template_id);


CREATE TABLE submissions
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id      BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    template_id      BIGINT      NOT NULL REFERENCES form_templates (id),
    requested_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    status           TEXT        NOT NULL DEFAULT 'pending'
        CONSTRAINT submission_status_allowed CHECK (status IN
                                                    ('pending', 'running', 'awaiting_review', 'submitted', 'failed',
                                                     'cancelled')),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    output_s3_bucket TEXT,
    output_s3_key    TEXT,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_submissions_provider_id ON submissions (provider_id);
CREATE INDEX idx_submissions_status ON submissions (status);


CREATE TABLE audit_log
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name TEXT        NOT NULL,
    record_id  BIGINT      NOT NULL,
    action     TEXT        NOT NULL
        CONSTRAINT audit_action_allowed CHECK (action IN ('insert', 'update', 'delete')),
    changed_by BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    old_values JSONB,
    new_values JSONB,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_table_record ON audit_log (table_name, record_id);
CREATE INDEX idx_audit_log_changed_at ON audit_log (changed_at);