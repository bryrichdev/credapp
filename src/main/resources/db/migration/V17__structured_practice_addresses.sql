-- Keep existing full addresses; never guess their components.
ALTER TABLE group_locations
    ADD COLUMN street1 TEXT,
    ADD COLUMN street2 TEXT,
    ADD COLUMN city TEXT,
    ADD COLUMN state TEXT,
    ADD COLUMN zip_code TEXT;
