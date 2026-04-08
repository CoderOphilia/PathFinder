ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(1000) DEFAULT '';
UPDATE users SET profile_image_url = '' WHERE profile_image_url IS NULL;
