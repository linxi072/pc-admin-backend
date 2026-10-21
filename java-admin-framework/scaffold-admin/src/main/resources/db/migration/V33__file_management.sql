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
