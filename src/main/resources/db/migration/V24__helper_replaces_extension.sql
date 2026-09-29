-- CredCloud Helper replaces CredCloud for Chrome and the browser CredCloud ran itself.
-- runners.platform is which build of the helper a computer runs (such as darwin-arm64), so it
-- can be kept up to date.
ALTER TABLE runners ADD COLUMN platform TEXT;

-- Every connection so far came from the extension: none of them can fetch or fill anything now.
UPDATE runners SET revoked_at = coalesce(revoked_at, now()), token_hash = NULL WHERE revoked_at IS NULL;

-- Jobs still open were waiting for the extension or open in CredCloud's own browser.
UPDATE runner_jobs SET status = 'cancelled', answers = NULL, finished_at = now()
WHERE status IN ('waiting', 'claimed');
