CREATE TABLE IF NOT EXISTS "sys_tenant" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "tenant_code" VARCHAR(64) NOT NULL,
    "tenant_name" VARCHAR(128) NOT NULL,
    "contact_name" VARCHAR(64) NULL,
    "contact_phone" VARCHAR(32) NULL,
    "contact_email" VARCHAR(128) NULL,
    "status" VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    "expire_at" DATETIME(3) NULL,
    "remark" VARCHAR(255) NULL,
    "version" BIGINT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_by" VARCHAR(64) NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" VARCHAR(64) NULL,
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_sys_tenant_code" ON "sys_tenant" ("tenant_code");
CREATE INDEX "idx_sys_tenant_status" ON "sys_tenant" ("status");
