ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(1000) DEFAULT '';
UPDATE users SET profile_image_url = '' WHERE profile_image_url IS NULL;
ALTER TABLE IF EXISTS session_requests ALTER COLUMN IF EXISTS mentor_note RENAME TO meeting_link;
ALTER TABLE IF EXISTS session_requests ADD COLUMN IF NOT EXISTS meeting_link VARCHAR(1000);
INSERT INTO mentor_profiles (
    user_id,
    admin_note,
    bio,
    current_company,
    current_title,
    expertise,
    hourly_rate_cents,
    offers_free_session,
    sessions_completed,
    trial_session_end_time,
    trial_session_start_time,
    trial_session_weekday,
    verification_status
)
SELECT
    u.id,
    '',
    '',
    '',
    '',
    '',
    0,
    FALSE,
    0,
    NULL,
    NULL,
    NULL,
    'PENDING'
FROM users u
WHERE LOWER(u.role) = 'mentor'
  AND NOT EXISTS (
      SELECT 1
      FROM mentor_profiles mp
      WHERE mp.user_id = u.id
  );
