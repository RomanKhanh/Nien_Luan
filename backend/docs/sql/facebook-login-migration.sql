-- Optional when ddl-auto=validate; ddl-auto=update adds the entity column automatically.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS facebook_subject varchar(255);
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_facebook_subject ON users (facebook_subject);
COMMIT;
