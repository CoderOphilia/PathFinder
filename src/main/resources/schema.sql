ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(1000) DEFAULT '';
UPDATE users SET profile_image_url = '' WHERE profile_image_url IS NULL;
ALTER TABLE IF EXISTS session_requests ALTER COLUMN IF EXISTS mentor_note RENAME TO meeting_link;
ALTER TABLE IF EXISTS session_requests ADD COLUMN IF NOT EXISTS meeting_link VARCHAR(1000);
