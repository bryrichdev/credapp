-- Who changed what, and when, for every record a group keeps about its providers and groups.
--
-- Triggers write the history, so every path that changes a record is covered: the forms,
-- the spreadsheet import, and anything added later. The app stamps each database connection
-- with the signed-in user (credapp.actor_id / credapp.actor_email); a change made with no one
-- signed in is recorded without a name.

CREATE TABLE change_log (
    id            BIGSERIAL PRIMARY KEY,
    user_group_id BIGINT      NOT NULL REFERENCES user_groups (id),
    table_name    TEXT        NOT NULL,
    operation     CHAR(1)     NOT NULL CHECK (operation IN ('I', 'U', 'D')),
    row_id        BIGINT,
    -- The provider or group whose page shows it. No foreign key: history outlives the record.
    provider_id   BIGINT,
    group_id      BIGINT,
    -- The row after an insert or update, before a delete, with secrets masked.
    row_data      JSONB       NOT NULL,
    -- For an update, each changed column: {"column": {"from": ..., "to": ...}}.
    changes       JSONB,
    actor_id      BIGINT,
    actor_email   TEXT,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX change_log_provider ON change_log (user_group_id, provider_id, id DESC) WHERE provider_id IS NOT NULL;
CREATE INDEX change_log_group ON change_log (user_group_id, group_id, id DESC) WHERE group_id IS NOT NULL;
CREATE INDEX change_log_owner ON change_log (user_group_id, row_id, id DESC) WHERE table_name = 'owners';

CREATE FUNCTION log_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    -- Recorded as changed, never with their values.
    masked   TEXT[] := ARRAY ['ssn', 'caqh_secret_ref', 'content'];
    -- Bookkeeping, not worth a line of history.
    skipped  TEXT[] := ARRAY ['created_at', 'updated_at', 'user_group_id'];
    old_row  JSONB;
    new_row  JSONB;
    snapshot JSONB;
    diff     JSONB;
    col      TEXT;
BEGIN
    -- Wiping or deleting a whole user group turns this off for its own transaction.
    IF current_setting('credapp.audit_off', true) = 'on' THEN
        RETURN NULL;
    END IF;
    IF TG_OP <> 'INSERT' THEN
        old_row := to_jsonb(OLD);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        new_row := to_jsonb(NEW);
    END IF;

    IF TG_OP = 'UPDATE' THEN
        diff := '{}';
        FOR col IN SELECT jsonb_object_keys(new_row)
            LOOP
                CONTINUE WHEN col = ANY (skipped);
                IF new_row -> col IS DISTINCT FROM old_row -> col THEN
                    diff := diff || jsonb_build_object(col,
                            CASE WHEN col = ANY (masked) THEN '{"masked": true}'::jsonb
                                 ELSE jsonb_build_object('from', old_row -> col, 'to', new_row -> col) END);
                END IF;
            END LOOP;
        IF diff = '{}' THEN
            RETURN NULL; -- saved without a real change
        END IF;
    END IF;

    snapshot := coalesce(new_row, old_row) - skipped;
    FOREACH col IN ARRAY masked
        LOOP
            IF snapshot ? col AND jsonb_typeof(snapshot -> col) <> 'null' THEN
                snapshot := jsonb_set(snapshot, ARRAY [col], '"masked"');
            END IF;
        END LOOP;

    INSERT INTO change_log (user_group_id, table_name, operation, row_id, provider_id, group_id,
                            row_data, changes, actor_id, actor_email)
    VALUES ((coalesce(new_row, old_row) ->> 'user_group_id')::BIGINT,
            TG_TABLE_NAME,
            left(TG_OP, 1),
            (snapshot ->> 'id')::BIGINT,
            CASE WHEN TG_TABLE_NAME = 'providers' THEN snapshot ->> 'id' ELSE snapshot ->> 'provider_id' END::BIGINT,
            CASE WHEN TG_TABLE_NAME = 'groups' THEN snapshot ->> 'id' ELSE snapshot ->> 'group_id' END::BIGINT,
            snapshot,
            diff,
            nullif(current_setting('credapp.actor_id', true), '')::BIGINT,
            nullif(current_setting('credapp.actor_email', true), ''));
    RETURN NULL;
END;
$$;

DO
$$
    DECLARE
        audited TEXT;
    BEGIN
        FOREACH audited IN ARRAY ARRAY [
            'providers', 'licenses', 'certifications', 'hospital_privileges', 'malpractice_policies',
            'malpractice_claims', 'criminal_charges', 'provider_references', 'provider_taxonomies',
            'provider_training', 'provider_work_history', 'provider_payers', 'provider_locations',
            'group_providers', 'documents', 'payer_contacts',
            'groups', 'group_locations', 'group_owners', 'group_owner_relationships', 'group_payers',
            'group_taxonomies', 'owners']
            LOOP
                EXECUTE format('CREATE TRIGGER %I AFTER INSERT OR UPDATE OR DELETE ON %I '
                                   || 'FOR EACH ROW EXECUTE FUNCTION log_change()',
                               audited || '_history', audited);
            END LOOP;
    END
$$;
