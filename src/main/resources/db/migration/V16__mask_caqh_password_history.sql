-- Mask stored CAQH ciphertext in new history and scrub historical copies.
CREATE OR REPLACE FUNCTION log_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    -- Recorded as changed, never with their values.
    masked   TEXT[] := ARRAY ['ssn', 'caqh_secret_ref', 'caqh_password_ciphertext', 'content'];
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

UPDATE change_log
SET row_data = CASE WHEN row_data ? 'caqh_password_ciphertext'
    THEN jsonb_set(row_data, '{caqh_password_ciphertext}', '"masked"') ELSE row_data END,
    changes = CASE WHEN changes ? 'caqh_password_ciphertext'
    THEN jsonb_set(changes, '{caqh_password_ciphertext}', '{"masked":true}') ELSE changes END
WHERE table_name = 'providers'
  AND (row_data ? 'caqh_password_ciphertext' OR changes ? 'caqh_password_ciphertext');
