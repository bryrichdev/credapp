-- Account workspaces are distinct from medical practice groups.
CREATE TABLE user_groups (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0),
    name TEXT NOT NULL,
    join_code TEXT NOT NULL UNIQUE
);

ALTER TABLE users
    ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id),
    ADD COLUMN membership_status TEXT NOT NULL DEFAULT 'APPROVED'
        CHECK (membership_status IN ('APPROVED', 'PENDING', 'REJECTED')),
    ADD CONSTRAINT users_pending_disabled CHECK (membership_status = 'APPROVED' OR NOT is_enabled);
CREATE INDEX users_user_group_id_idx ON users(user_group_id);

-- Every record requires an explicit group. This migration targets a fresh database;
-- there is no seeded group or automatic assignment for pre-existing ungrouped records.
-- Hibernate supplies the authenticated group for new records.
ALTER TABLE providers ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX providers_user_group_idx ON providers(user_group_id);
ALTER TABLE licenses ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX licenses_user_group_idx ON licenses(user_group_id);
ALTER TABLE groups ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX groups_user_group_idx ON groups(user_group_id);
ALTER TABLE group_locations ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX group_locations_user_group_idx ON group_locations(user_group_id);
ALTER TABLE group_providers ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX group_providers_user_group_idx ON group_providers(user_group_id);
ALTER TABLE owners ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX owners_user_group_idx ON owners(user_group_id);
ALTER TABLE group_owners ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX group_owners_user_group_idx ON group_owners(user_group_id);
ALTER TABLE group_owner_relationships ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX group_owner_relationships_user_group_idx ON group_owner_relationships(user_group_id);
ALTER TABLE payers ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX payers_user_group_idx ON payers(user_group_id);
ALTER TABLE payer_contacts ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX payer_contacts_user_group_idx ON payer_contacts(user_group_id);
ALTER TABLE ssn_access_log ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX ssn_access_log_user_group_idx ON ssn_access_log(user_group_id);
ALTER TABLE provider_taxonomies ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX provider_taxonomies_user_group_idx ON provider_taxonomies(user_group_id);
ALTER TABLE group_taxonomies ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX group_taxonomies_user_group_idx ON group_taxonomies(user_group_id);
ALTER TABLE provider_locations ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX provider_locations_user_group_idx ON provider_locations(user_group_id);
ALTER TABLE malpractice_policies ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX malpractice_policies_user_group_idx ON malpractice_policies(user_group_id);
ALTER TABLE malpractice_claims ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX malpractice_claims_user_group_idx ON malpractice_claims(user_group_id);
ALTER TABLE provider_references ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX provider_references_user_group_idx ON provider_references(user_group_id);
ALTER TABLE hospital_privileges ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX hospital_privileges_user_group_idx ON hospital_privileges(user_group_id);
ALTER TABLE certifications ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX certifications_user_group_idx ON certifications(user_group_id);
ALTER TABLE criminal_charges ADD COLUMN user_group_id BIGINT NOT NULL REFERENCES user_groups(id);
CREATE INDEX criminal_charges_user_group_idx ON criminal_charges(user_group_id);

-- Allow different workspaces to record the same real-world provider, payer, or policy.
ALTER TABLE providers DROP CONSTRAINT providers_npi_key, ADD UNIQUE (user_group_id, npi);
ALTER TABLE groups DROP CONSTRAINT groups_npi_key, ADD UNIQUE (user_group_id, npi);
ALTER TABLE payers DROP CONSTRAINT payers_name_key, ADD UNIQUE (user_group_id, name);
ALTER TABLE malpractice_policies DROP CONSTRAINT malpractice_policies_carrier_name_policy_number_key, ADD UNIQUE (user_group_id, carrier_name, policy_number);
ALTER TABLE malpractice_claims DROP CONSTRAINT malpractice_claims_carrier_name_claim_number_key, ADD UNIQUE (user_group_id, carrier_name, claim_number);

-- Composite foreign keys also prevent cross-workspace relationships at the database boundary.
ALTER TABLE providers ADD UNIQUE (user_group_id, id);
ALTER TABLE groups ADD UNIQUE (user_group_id, id);
ALTER TABLE group_locations ADD UNIQUE (user_group_id, id);
ALTER TABLE owners ADD UNIQUE (user_group_id, id);
ALTER TABLE payers ADD UNIQUE (user_group_id, id);
ALTER TABLE malpractice_policies ADD UNIQUE (user_group_id, id);
ALTER TABLE group_owners ADD UNIQUE (user_group_id, group_id, owner_id);
ALTER TABLE group_locations ADD UNIQUE (user_group_id, group_id, id);
ALTER TABLE group_providers ADD UNIQUE (user_group_id, group_id, provider_id);
ALTER TABLE licenses ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_locations ADD FOREIGN KEY (user_group_id, group_id) REFERENCES groups(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_providers ADD FOREIGN KEY (user_group_id, group_id) REFERENCES groups(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_providers ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_owners ADD FOREIGN KEY (user_group_id, group_id) REFERENCES groups(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_owners ADD FOREIGN KEY (user_group_id, owner_id) REFERENCES owners(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_owner_relationships ADD FOREIGN KEY (user_group_id, group_id, owner_id) REFERENCES group_owners(user_group_id, group_id, owner_id) ON DELETE CASCADE;
ALTER TABLE group_owner_relationships ADD FOREIGN KEY (user_group_id, group_id, related_owner_id) REFERENCES group_owners(user_group_id, group_id, owner_id) ON DELETE CASCADE;
ALTER TABLE payer_contacts ADD FOREIGN KEY (user_group_id, payer_id) REFERENCES payers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE payer_contacts ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE payer_contacts ADD FOREIGN KEY (user_group_id, group_id) REFERENCES groups(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE provider_taxonomies ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE group_taxonomies ADD FOREIGN KEY (user_group_id, group_id) REFERENCES groups(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE provider_locations ADD FOREIGN KEY (user_group_id, group_id, location_id) REFERENCES group_locations(user_group_id, group_id, id) ON DELETE CASCADE;
ALTER TABLE provider_locations ADD FOREIGN KEY (user_group_id, group_id, provider_id) REFERENCES group_providers(user_group_id, group_id, provider_id) ON DELETE CASCADE;
ALTER TABLE malpractice_policies ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE RESTRICT;
ALTER TABLE malpractice_policies ADD FOREIGN KEY (user_group_id, group_id) REFERENCES groups(user_group_id, id) ON DELETE RESTRICT;
ALTER TABLE malpractice_claims ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE RESTRICT;
ALTER TABLE malpractice_claims ADD FOREIGN KEY (user_group_id, policy_id) REFERENCES malpractice_policies(user_group_id, id) ON DELETE RESTRICT;
ALTER TABLE provider_references ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE hospital_privileges ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE RESTRICT;
ALTER TABLE hospital_privileges ADD FOREIGN KEY (user_group_id, admitting_physician) REFERENCES providers(user_group_id, id) ON DELETE SET NULL (admitting_physician);
ALTER TABLE certifications ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE CASCADE;
ALTER TABLE criminal_charges ADD FOREIGN KEY (user_group_id, provider_id) REFERENCES providers(user_group_id, id) ON DELETE RESTRICT;
