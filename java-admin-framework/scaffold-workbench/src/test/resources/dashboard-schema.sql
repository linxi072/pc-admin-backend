-- 测试用看板模板表结构（仅 DDL，不含菜单种子；菜单种子走 V78 Flyway，依赖 sys_menu/sys_role_menu）。
-- 与 V78__dashboard_template.sql 的 CREATE TABLE / CREATE INDEX 严格一致，供 H2 内存库集成测试建表。

CREATE TABLE "dashboard_template" (
    "id"             BIGINT        NOT NULL,
    "tenant_id"      BIGINT        NOT NULL DEFAULT 0,
    "template_code"  VARCHAR(64)   NOT NULL,
    "template_name"  VARCHAR(128)  NOT NULL,
    "description"    VARCHAR(512)  NULL,
    "layout_json"    TEXT          NULL,
    "status"         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    "view_count"     BIGINT        NOT NULL DEFAULT 0,
    "version"        INT           NOT NULL DEFAULT 0,
    "deleted"        INT           NOT NULL DEFAULT 0,
    "created_by"     VARCHAR(64)   NULL,
    "created_at"     TIMESTAMP     NULL,
    "updated_by"     VARCHAR(64)   NULL,
    "updated_at"     TIMESTAMP     NULL,
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_dashboard_template_tenant_code" ON "dashboard_template" ("tenant_id", "template_code");
CREATE INDEX "idx_dashboard_template_status"       ON "dashboard_template" ("tenant_id", "status");
CREATE INDEX "idx_dashboard_template_name"         ON "dashboard_template" ("tenant_id", "template_name");
