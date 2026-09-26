-- Templates belong to a payer and workspace. The source PDF is immutable; upload a new
-- version to replace it. Mapping revisions are captured in every application draft.
CREATE TABLE application_templates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id BIGINT NOT NULL REFERENCES user_groups(id),
    payer_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    content BYTEA NOT NULL,
    mappings BYTEA NOT NULL,
    configured BOOLEAN NOT NULL DEFAULT false,
    revision INTEGER NOT NULL DEFAULT 1,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_group_id, id),
    FOREIGN KEY (user_group_id, payer_id) REFERENCES payers(user_group_id, id) ON DELETE CASCADE
);
CREATE INDEX application_templates_payer ON application_templates(user_group_id, payer_id);

-- Draft values and final field values are encrypted, including manual corrections.
-- A generated run is immutable; repeating generation returns its existing document.
CREATE TABLE application_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id BIGINT NOT NULL REFERENCES user_groups(id),
    provider_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    template_revision INTEGER NOT NULL,
    practice_group_id BIGINT,
    location_id BIGINT,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'generated')),
    field_values BYTEA NOT NULL,
    document_id BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    created_by TEXT NOT NULL,
    generated_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    generated_at TIMESTAMPTZ,
    FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE,
    FOREIGN KEY (user_group_id, template_id) REFERENCES application_templates(user_group_id, id) ON DELETE CASCADE
);
CREATE INDEX application_runs_provider ON application_runs(user_group_id, provider_id, id DESC);

CREATE TABLE application_access_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_group_id BIGINT NOT NULL REFERENCES user_groups(id),
    run_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_email TEXT NOT NULL,
    action TEXT NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX application_access_log_run ON application_access_log(user_group_id, run_id, id DESC);
