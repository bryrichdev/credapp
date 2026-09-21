-- Adds the superuser tier above admin. Superusers manage admins; admins manage everyone below.
ALTER TABLE users
    DROP CONSTRAINT role_allowed;

ALTER TABLE users
    ADD CONSTRAINT role_allowed CHECK (role IN ('superuser', 'admin', 'coordinator', 'readonly'));
