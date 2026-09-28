-- Where each document's bytes live. 'database' keeps them in content, as before. 's3' keeps
-- them in the workspace's own bucket under documents/<id>, and content is empty. Either way
-- the bytes are encrypted by the app first.
ALTER TABLE documents ALTER COLUMN content DROP NOT NULL;
ALTER TABLE documents ADD COLUMN stored_in TEXT NOT NULL DEFAULT 'database'
    CHECK (stored_in IN ('database', 's3'));
ALTER TABLE documents ADD CONSTRAINT documents_content_where_stored
    CHECK ((stored_in = 'database') = (content IS NOT NULL));
