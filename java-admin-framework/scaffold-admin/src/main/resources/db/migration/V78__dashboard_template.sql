-- V78 数据看板模板库（ROADMAP R8）：看板模板 CRUD + 布局配置（layout_json）+ 发布态流转。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（H2 元数据库名小写、生产 MySQL 大小写敏感，未引号会坏）；
--   2) 索引用独立 CREATE INDEX 语句（内联 KEY `x`(...) 会被 H2 误解析为 domain）；
--   3) tenant_id 承载租户隔离，enforce 关闭时恒为 0；
--   4) status 用 VARCHAR(20) 承载 DRAFT/PUBLISHED/OFFLINE，由应用层校验；
--   5) layout_json 用 TEXT 承载看板布局（栅格 / 卡片 / 图表配置），由前端以 JSON 字符串提交，
--      后端经 Jackson 校验合法性，SnakeRecordMapper 将 TEXT 直接映射为 String；
--   6) 菜单种子：挂载顶层「数据看板」目录（id=9701），下挂「看板模板管理」（9702），
--      permission_code 作为菜单可见性判定码，由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize
--      自动落库并授予 SUPER_ADMIN，无需手工登记；
--   7) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

CREATE TABLE `dashboard_template` (
    `id`             BIGINT        NOT NULL,
    `tenant_id`      BIGINT        NOT NULL DEFAULT 0,
    `template_code`  VARCHAR(64)   NOT NULL,
    `template_name`  VARCHAR(128)  NOT NULL,
    `description`    VARCHAR(512)  NULL,
    `layout_json`    TEXT          NULL,
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    `view_count`     BIGINT        NOT NULL DEFAULT 0,
    `version`        INT           NOT NULL DEFAULT 0,
    `deleted`        INT           NOT NULL DEFAULT 0,
    `created_by`     VARCHAR(64)   NULL,
    `created_at`     TIMESTAMP     NULL,
    `updated_by`     VARCHAR(64)   NULL,
    `updated_at`     TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_dashboard_template_tenant_code` ON `dashboard_template` (`tenant_id`, `template_code`);
CREATE INDEX `idx_dashboard_template_status`       ON `dashboard_template` (`tenant_id`, `status`);
CREATE INDEX `idx_dashboard_template_name`         ON `dashboard_template` (`tenant_id`, `template_name`);

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9701, 0, 0, 'dashboard', '数据看板', 'CATALOG', NULL, NULL, NULL, 'Dashboard', 1, 0, 70, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9702, 0, 9701, 'dashboard:template', '看板模板管理', 'MENU', '/dashboard/template', 'dashboard/DashboardTemplateView', 'dashboard:template:manage', 'Grid', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9702;
