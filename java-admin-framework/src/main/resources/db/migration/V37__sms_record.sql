-- V37 短信发送记录（系统管理 → 短信记录），补全短信通知渠道（M6）的运维对账面。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:sms-record:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 表：sys_sms_record（脱敏存储：手机号掩码、正文摘要，PII 不明文落库）
CREATE TABLE IF NOT EXISTS sys_sms_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    batch_no VARCHAR(64) NOT NULL,
    mobiles_masked VARCHAR(1024) NOT NULL,
    mobile_count INT NOT NULL DEFAULT 0,
    content_summary VARCHAR(256) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sent_at),
    KEY idx_status_time (status, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：短信记录（挂在「系统管理」1002 下，sort_no=102 紧随导入中心）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1092, 0, 1002, 'system:sms-record', '短信记录', 'MENU', '/system/sms-record', 'system/SmsLog', 'system:sms-record:read', 'Message', 1, 0, 102, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1092;
