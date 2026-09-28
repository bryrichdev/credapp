-- Browsers connect with one click from a signed-in page now, so pairing codes are gone.
ALTER TABLE runners DROP COLUMN pairing_code_hash, DROP COLUMN pairing_expires_at;
