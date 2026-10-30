CREATE TABLE IF NOT EXISTS "sys_login_restriction" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "user_id" BIGINT NOT NULL,
    "username" VARCHAR(64) NOT NULL,
    "display_name" VARCHAR(100) NULL,
    "reason" VARCHAR(255) NOT NULL,
    "duration_minutes" INT NOT NULL DEFAULT 0,
    "restricted_by" BIGINT NULL,
    "restricted_by_name" VARCHAR(100) NULL,
    "restricted_at" DATETIME(3) NOT NULL,
    "expire_at" DATETIME(3) NULL,
    "released_at" DATETIME(3) NULL,
    "released_by" BIGINT NULL,
    "release_type" VARCHAR(20) NULL,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_user_status" ON "sys_login_restriction" ("tenant_id", "user_id", "status");
CREATE INDEX "idx_status_expire" ON "sys_login_restriction" ("tenant_id", "status", "expire_at");
CREATE INDEX "idx_username" ON "sys_login_restriction" ("tenant_id", "username");
