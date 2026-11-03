-- V74 内容管理 CMS（ROADMAP R5）：栏目树 + 文章 CRUD + 富文本 + 发布态。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（H2 元数据库名小写、生产 MySQL 大小写敏感，未引号会坏）；
--   2) 索引用独立 CREATE INDEX 语句（内联 KEY "x"(...) 会被 H2 误解析为 domain）；
--   3) tenant_id 承载租户隔离，enforce 关闭时恒为 0；
--   4) status 用 VARCHAR(20) 承载 DRAFT/PUBLISHED/OFFLINE，由应用层校验；
--   5) 菜单种子：挂载顶层「内容管理」目录（id=9301），下挂栏目管理/文章管理（9302/9303），
--      permission_code 作为菜单可见性判定码；更细的 cms:*:publish 等由 ApiResourceAutoSyncRunner
--      扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，无需手工登记；
--   6) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

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

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9301, 0, 0, 'cms', '内容管理', 'CATALOG', NULL, NULL, NULL, 'Document', 1, 0, 80, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9302, 0, 9301, 'cms:category', '栏目管理', 'MENU', '/cms/category', 'cms/CategoryView', 'cms:category:manage', 'FolderOpened', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9303, 0, 9301, 'cms:article', '文章管理', 'MENU', '/cms/article', 'cms/ArticleView', 'cms:article:manage', 'FileText', 1, 0, 20, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9302;
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9303;
