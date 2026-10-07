-- V8 新增功能模块：站内信（发件主表 + 收件明细）。
-- 表名 / 列名与 com.acme.scaffold.jooq.JooqTables 中的 SYS_MESSAGE / SYS_MESSAGE_RECEIPT 注册严格一致。
--
-- sys_message 为发件主表(一条消息一次发送动作)，sys_message_receipt 为收件明细。
-- 「批量发送」= 按角色/部门筛选接收人后在 receipt 表批量插入(同一 message_id 多行)。
-- 已读状态与回执时间记录在 receipt 行：is_read / read_at。
CREATE TABLE IF NOT EXISTS sys_message (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    -- NOTICE(通知) / ALERT(告警) / SYSTEM(系统)
    msg_type VARCHAR(20) NOT NULL DEFAULT 'NOTICE',
    sender_id BIGINT UNSIGNED NULL,
    -- 接收人筛选快照：便于审计「当时按什么范围发的」
    filter_role_ids VARCHAR(500) NULL,
    filter_org_ids VARCHAR(500) NULL,
    -- 单条发送时的显式接收人
    receiver_ids VARCHAR(500) NULL,
    total_count INT UNSIGNED NOT NULL DEFAULT 0,
    read_count INT UNSIGNED NOT NULL DEFAULT 0,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_sent (tenant_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_message_receipt (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    message_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    -- 0=未读 1=已读
    is_read TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- 已读回执时间
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user (tenant_id, message_id, user_id),
    KEY idx_user_read (tenant_id, user_id, is_read),
    KEY idx_msg (tenant_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
