-- Files in S3 whose documents rows are gone: deleted on their own, with their provider or
-- group, or with a wiped or deleted workspace. The trigger catches every one of those paths,
-- cascades included, in the same transaction as the delete, so a rolled-back delete queues
-- nothing. DocumentFileCleanup deletes the files, then these rows.
--
-- workspace_id, not user_group_id, on purpose: the queue must outlive a deleted workspace, and
-- wipes and deletes clear every table with a user_group_id column.
CREATE TABLE document_file_deletions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id BIGINT      NOT NULL,
    document_id  BIGINT      NOT NULL,
    queued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts     INTEGER     NOT NULL DEFAULT 0,
    last_error   TEXT
);

CREATE FUNCTION queue_document_file_deletion() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    INSERT INTO document_file_deletions (workspace_id, document_id) VALUES (OLD.user_group_id, OLD.id);
    RETURN NULL;
END
$$;

CREATE TRIGGER documents_queue_file_deletion
    AFTER DELETE ON documents
    FOR EACH ROW
    WHEN (OLD.stored_in = 's3')
EXECUTE FUNCTION queue_document_file_deletion();
