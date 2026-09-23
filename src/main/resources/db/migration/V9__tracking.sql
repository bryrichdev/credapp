-- Dates that come due, so CredApp can say what's expiring, overdue or waiting too long.

-- Hospitals reappoint medical staff on a cycle, usually every two years.
ALTER TABLE hospital_privileges ADD COLUMN reappointment_date DATE;

-- CAQH asks providers to re-attest their profile on a fixed cycle (120 days by default).
ALTER TABLE providers ADD COLUMN caqh_attested_date DATE;

-- When the payer expects to recredential or revalidate an enrollment, and a date someone
-- has set to chase it up.
ALTER TABLE provider_payers
    ADD COLUMN recredential_date DATE,
    ADD COLUMN follow_up_date DATE;
ALTER TABLE group_payers
    ADD COLUMN recredential_date DATE,
    ADD COLUMN follow_up_date DATE;

-- How far ahead each user group wants to hear about what's coming due. A group without
-- a row uses the defaults below.
CREATE TABLE tracking_settings
(
    user_group_id BIGINT PRIMARY KEY REFERENCES user_groups (id) ON DELETE CASCADE,
    -- Anything due within this many days is listed as coming up.
    warning_days  INT         NOT NULL DEFAULT 90,
    -- Within this many days it's flagged as due soon.
    urgent_days   INT         NOT NULL DEFAULT 30,
    -- An application submitted or in progress this long with no decision is stalled.
    stalled_days  INT         NOT NULL DEFAULT 60,
    -- How often CAQH attestation comes due.
    caqh_days     INT         NOT NULL DEFAULT 120,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT tracking_settings_warning CHECK (warning_days BETWEEN 1 AND 365),
    CONSTRAINT tracking_settings_urgent CHECK (urgent_days BETWEEN 1 AND warning_days),
    CONSTRAINT tracking_settings_stalled CHECK (stalled_days BETWEEN 7 AND 365),
    CONSTRAINT tracking_settings_caqh CHECK (caqh_days BETWEEN 30 AND 365)
);

-- The tracking queries look for dates falling inside a window.
CREATE INDEX licenses_expiration_idx ON licenses (user_group_id, expiration_date);
CREATE INDEX certifications_expiration_idx ON certifications (user_group_id, expiration_date);
CREATE INDEX malpractice_policies_expiration_idx ON malpractice_policies (user_group_id, expiration_date);
