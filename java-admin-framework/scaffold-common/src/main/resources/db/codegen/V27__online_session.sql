CREATE TABLE IF NOT EXISTS "sys_online_session" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "user_id" BIGINT NOT NULL,
    "device_id" VARCHAR(128) NULL,
    "client_id" VARCHAR(64) NOT NULL DEFAULT 'web',
    "username" VARCHAR(64) NOT NULL,
    "display_name" VARCHAR(100) NULL,
    "login_ip" VARCHAR(64) NULL,
    "user_agent" VARCHAR(500) NULL,
    "login_at" DATETIME(3) NOT NULL,
    "last_active_at" DATETIME(3) NOT NULL,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    "offline_reason" VARCHAR(64) NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_user_device" ON "sys_online_session" ("tenant_id", "user_id", "device_id");
CREATE INDEX "idx_status_active" ON "sys_online_session" ("tenant_id", "status", "last_active_at");
CREATE INDEX "idx_session_username" ON "sys_online_session" ("tenant_id", "username");
