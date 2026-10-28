-- ============================================================
-- 模块参考 SQL：文件中心
-- 说明：文件管理（V33）+ 文件去重（V50）
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V33__file_management.sql
-- ============================================================

-- V33 文件管理（运维管理 → 文件管理）
-- 依赖：V32（含「运维管理」一级目录 id=1070，menu_code='ops'）。
-- 接口资源（system:file:upload/read/download/delete）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 文件表：记录原始名、服务端存储名、大小、类型、来源功能模块、上传人、下载次数。
--    deleted/version 支持逻辑删除（删除时仅清理物理文件，保留记录供审计）。
CREATE TABLE IF NOT EXISTS sys_file (
    id            BIGINT UNSIGNED NOT NULL,
    tenant_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    original_name VARCHAR(255)    NOT NULL,
    stored_name   VARCHAR(255)    NOT NULL COMMENT '服务端生成的存储文件名（UUID.ext），不含目录',
    storage_path  VARCHAR(500)    NOT NULL COMMENT '上传时的绝对存储路径（记录用，读取以 baseDir+stored_name 为准）',
    file_size     BIGINT          NOT NULL,
    content_type  VARCHAR(128)    NULL,
    source_module VARCHAR(64)     NOT NULL COMMENT '来源功能模块，如 workflow/report/file-management',
    biz_id        VARCHAR(64)     NULL COMMENT '业务关联 ID（可选，如流程实例/报表 ID）',
    uploader_id   BIGINT UNSIGNED NULL,
    uploader_name VARCHAR(100)    NULL COMMENT '上传人姓名（冗余存储，用户删除后仍可读）',
    download_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version       INT UNSIGNED    NOT NULL DEFAULT 0,
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_source (tenant_id, source_module),
    KEY idx_tenant_uploader (tenant_id, uploader_id),
    KEY idx_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：文件管理（挂在「运维管理」1070 下，sort_no=110 置于末尾）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1080, 0, 1070, 'file-management', '文件管理', 'MENU', '/system/file', 'system/FileManagement', 'system:file:read', 'Document', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1080;


-- ============================================================
-- Source migration: V50__file_dedup.sql
-- ============================================================

-- =====================================================================================
-- V50 文件管理增强（File Management Enhancement）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
--
-- 背景与模型取舍：
--   sys_file 每上传一个文件就落一条记录 + 一份物理文件，常见管理场景存在三类浪费：
--     a) 重复上传：同一份内容（如公共头像、模板文档）被不同业务/用户反复上传，
--        磁盘与记录双倍膨胀；
--     b) 误删物理文件：两条记录 stored_name 相同时（历史批量导入或复制），删一条
--        会把另一条指向的物理文件一并删掉，下载即 404；
--     c) 缺少统计：管理者看不到「哪个来源模块传了多少文件、占用多少空间」。
--
--   故本迁移为 sys_file 增加 content_hash 列，支撑三项增强：
--     1) 秒传（dedup）：上传时计算内容 SHA-256，命中未删记录则复用其
--        stored_name/storage_path，不落盘、不产生新物理文件；
--     2) 引用计数删除保护：删除前按 stored_name 统计未删记录数，>1 时仅逻辑删
--        记录、保留物理文件；
--     3) 统计：按 source_module 分组聚合（无需新列，本迁移只加索引支撑）。
--
-- 幂等设计：
--   - 加列与加索引均用 information_schema 守卫做幂等 ALTER（重复执行不报
--     1060/1061  duplicate 错误），可直接在目标库重复执行；
--   - 存量行 content_hash 为 NULL，不参与秒传去重（NULL 不匹配等值查询），
--     语义安全；后续重新上传同内容时自然回填哈希并纳入去重；
--   - CHAR(64) 固定长度存 SHA-256 十六进制，定长列比较快且与 normalizeHash
--     校验严格对齐。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 幂等扩展：sys_file 增加 content_hash 列（内容 SHA-256 十六进制，秒传去重锚点）
--    位置置于 content_type 之后，与 JooqTables.SYS_FILE 列顺序无关（DSL 按名映射）。
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD COLUMN `content_hash` CHAR(64) NULL COMMENT ''内容 SHA-256 十六进制（秒传去重锚点）'' AFTER `content_type`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'content_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：content_hash 加普通索引（秒传命中等值查询；NULL 值不进索引，零膨胀）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD KEY `idx_content_hash` (`content_hash`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_content_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

