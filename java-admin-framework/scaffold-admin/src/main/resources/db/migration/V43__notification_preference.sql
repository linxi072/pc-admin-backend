-- V43 通知偏好中心（M13）：sys_notification_preference，让 M6 多渠道通知首次具备
-- 「谁想收什么」的接收侧口径，并修复 sys_user 三个 notify_* 渠道总开关为「死开关」的缺陷
-- （此前唯一读取方是个人中心自身，发送链路从不读取，用户关闭站内信通知仍照收）。
--
-- 设计要点：
--   1) 懒行模型：无行 = 默认开通，由 sys_user 渠道级总开关（notify_site_message /
--      notify_email / notify_mobile）兜底；恢复默认即物理删行，故无 deleted 列。
--   2) 唯一键 uk_user_channel_type：同一用户同一 (渠道, 消息类型) 至多一行，
--      写入走「查现存 → 更新或插入」，唯一索引兜底并发（与幂等记录表同范式）。
--   3) 渠道白名单 IN_APP / EMAIL / SMS / WEBSOCKET：WEBHOOK 的地址属外部系统，
--      无个人偏好语义，拒绝建行（规则见 NotificationPreferenceRules）。
--   4) 自服务页面（个人中心 → 通知订阅）：无菜单、无权限码播种，
--      接口 /api/notification-preferences/mine 仅登录可访问，userId 一律服务端取自安全上下文。

CREATE TABLE IF NOT EXISTS sys_notification_preference (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL DEFAULT 'NOTICE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel_type (user_id, channel, msg_type),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
