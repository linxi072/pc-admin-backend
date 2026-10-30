-- V42 通知发送记录（系统管理 → 通知记录），补全 M12：全五渠道统一发送审计面。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:notification-record:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--
-- 口径说明（与 sys_sms_record 的分工）：
--   本表记「每次 NotificationService.dispatch 尝试」（含被拒与失败，覆盖 IN_APP/WEBHOOK/
--   WEBSOCKET/EMAIL/SMS 全渠道，是统一审计面，M11 模板发送回填 template_code）；
--   sys_sms_record 保持「仅校验后实际尝试的网关投递」（网关账单对账面）。
--   短信一次发送双写两表，属刻意设计而非重复。

-- 1) 表：sys_notification_record（摘要 + 掩码存储：标题/正文摘要化、接收方按渠道掩码，PII 不明文落库）
CREATE TABLE IF NOT EXISTS sys_notification_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    channel VARCHAR(16) NOT NULL,
    template_code VARCHAR(64) NULL,
    msg_type VARCHAR(32) NULL,
    title_summary VARCHAR(256) NOT NULL,
    content_summary VARCHAR(512) NOT NULL,
    receiver_summary VARCHAR(512) NOT NULL,
    receiver_count INT NOT NULL DEFAULT 0,
    delivered INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sent_at),
    KEY idx_channel_time (channel, sent_at),
    KEY idx_status_time (status, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：通知记录（挂在「系统管理」1002 下，sort_no=105 紧随通知模板）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1096, 0, 1002, 'system:notification-record', '通知记录', 'MENU', '/system/notification-records', 'system/NotificationRecord', 'system:notification-record:read', 'Tickets', 1, 0, 105, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1096;
