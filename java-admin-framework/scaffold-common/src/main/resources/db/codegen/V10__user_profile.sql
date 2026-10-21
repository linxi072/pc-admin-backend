-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V10__user_profile.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(255) NULL;

ALTER TABLE sys_user ADD COLUMN notify_site_message TINYINT NOT NULL DEFAULT 1;

ALTER TABLE sys_user ADD COLUMN notify_email TINYINT NOT NULL DEFAULT 1;

ALTER TABLE sys_user ADD COLUMN notify_mobile TINYINT NOT NULL DEFAULT 0;

ALTER TABLE sys_user ADD COLUMN show_login_log TINYINT NOT NULL DEFAULT 1;

ALTER TABLE sys_user ADD COLUMN mask_mobile TINYINT NOT NULL DEFAULT 1;

ALTER TABLE sys_user ADD COLUMN discoverable TINYINT NOT NULL DEFAULT 1;

CREATE INDEX idx_user_revoked ON sys_refresh_token (user_id, revoked_at);

