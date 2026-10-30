CREATE TABLE IF NOT EXISTS "sys_oauth2_token" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "jti" VARCHAR(64) NOT NULL,
    "client_id" VARCHAR(128) NOT NULL,
    "user_id" BIGINT NULL,
    "username" VARCHAR(128) NULL,
    "scope" VARCHAR(512) NULL,
    "token_type" VARCHAR(20) NOT NULL,
    "issued_at" DATETIME(3) NOT NULL,
    "expires_at" DATETIME(3) NOT NULL,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "version" BIGINT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_jti" ON "sys_oauth2_token" ("tenant_id", "jti");
CREATE INDEX "idx_oauth2_client" ON "sys_oauth2_token" ("tenant_id", "client_id");
CREATE INDEX "idx_oauth2_user" ON "sys_oauth2_token" ("tenant_id", "user_id");
CREATE INDEX "idx_oauth2_status_exp" ON "sys_oauth2_token" ("tenant_id", "status", "expires_at");
