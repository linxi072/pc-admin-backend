-- 测试用 CMS 建表（与 V74__cms.sql 的表结构严格一致，仅去掉菜单种子）。
-- 标识符统一引号小写，配合 H2 MySQL 模式 + DATABASE_TO_LOWER 模拟生产列名语义。

CREATE TABLE "cms_category" (
    "id"           BIGINT        NOT NULL,
    "tenant_id"    BIGINT        NOT NULL DEFAULT 0,
    "parent_id"    BIGINT        NULL,
    "category_code" VARCHAR(64)  NOT NULL,
    "category_name" VARCHAR(128) NOT NULL,
    "description"  VARCHAR(512)  NULL,
    "sort_no"      INT           NOT NULL DEFAULT 0,
    "status"       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    "version"      INT           NOT NULL DEFAULT 0,
    "deleted"      INT           NOT NULL DEFAULT 0,
    "created_by"   VARCHAR(64)   NULL,
    "created_at"   TIMESTAMP     NULL,
    "updated_by"   VARCHAR(64)   NULL,
    "updated_at"   TIMESTAMP     NULL,
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_cms_category_tenant_parent" ON "cms_category" ("tenant_id", "parent_id");
CREATE INDEX "idx_cms_category_code"          ON "cms_category" ("tenant_id", "category_code");
CREATE INDEX "idx_cms_category_status"        ON "cms_category" ("tenant_id", "status");

CREATE TABLE "cms_article" (
    "id"           BIGINT        NOT NULL,
    "tenant_id"    BIGINT        NOT NULL DEFAULT 0,
    "category_id"  BIGINT        NOT NULL,
    "title"        VARCHAR(255)  NOT NULL,
    "summary"      VARCHAR(512)  NULL,
    "content"      TEXT          NULL,
    "author"       VARCHAR(64)   NULL,
    "cover_url"    VARCHAR(512)  NULL,
    "status"       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    "view_count"   BIGINT        NOT NULL DEFAULT 0,
    "published_at" TIMESTAMP     NULL,
    "offline_at"   TIMESTAMP     NULL,
    "version"      INT           NOT NULL DEFAULT 0,
    "deleted"      INT           NOT NULL DEFAULT 0,
    "created_by"   VARCHAR(64)   NULL,
    "created_at"   TIMESTAMP     NULL,
    "updated_by"   VARCHAR(64)   NULL,
    "updated_at"   TIMESTAMP     NULL,
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_cms_article_tenant_category" ON "cms_article" ("tenant_id", "category_id");
CREATE INDEX "idx_cms_article_status"          ON "cms_article" ("tenant_id", "status");
CREATE INDEX "idx_cms_article_title"           ON "cms_article" ("tenant_id", "title");
