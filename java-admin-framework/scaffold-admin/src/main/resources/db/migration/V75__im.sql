-- V75 站内消息 IM（ROADMAP R6）：会话 / 会话成员 / 消息 + 已读回执 + 实时推送解耦。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（H2 元数据库名小写、生产 MySQL 大小写敏感，未引号会坏）；
--   2) 索引用独立 CREATE INDEX 语句（内联 KEY `x`(...) 会被 H2 误解析为 domain）；
--   3) tenant_id 承载租户隔离，enforce 关闭时恒为 0；
--   4) 会话类型 conversation_type（P2P/GROUP）、消息类型 msg_type（TEXT/IMAGE/...）、状态由应用层校验；
--   5) 已读回执由 im_conversation_member.last_read_message_id 驱动（雪崩 id 单调递增，可比较）；
--   6) 菜单种子：挂载顶层「站内消息」目录（id=9401），下挂会话管理/消息中心（9402/9403），
--      permission_code 作为菜单可见性判定码；更细的 im:*:send 等由 ApiResourceAutoSyncRunner
--      扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，无需手工登记；
--   7) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

CREATE TABLE `im_conversation` (
    `id`                 BIGINT        NOT NULL,
    `tenant_id`          BIGINT        NOT NULL DEFAULT 0,
    `conversation_type`  VARCHAR(20)   NOT NULL,
    `title`              VARCHAR(255)  NULL,
    `owner_id`           BIGINT        NULL,
    `status`             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`            INT           NOT NULL DEFAULT 0,
    `deleted`            INT           NOT NULL DEFAULT 0,
    `created_by`         VARCHAR(64)   NULL,
    `created_at`         TIMESTAMP     NULL,
    `updated_by`         VARCHAR(64)   NULL,
    `updated_at`         TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_im_conversation_tenant_type` ON `im_conversation` (`tenant_id`, `conversation_type`);
CREATE INDEX `idx_im_conversation_status`      ON `im_conversation` (`tenant_id`, `status`);

CREATE TABLE `im_conversation_member` (
    `id`                   BIGINT        NOT NULL,
    `tenant_id`            BIGINT        NOT NULL DEFAULT 0,
    `conversation_id`      BIGINT        NOT NULL,
    `user_id`              BIGINT        NOT NULL,
    `member_role`          VARCHAR(20)   NOT NULL DEFAULT 'MEMBER',
    `last_read_message_id` BIGINT        NULL,
    `status`               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`              INT           NOT NULL DEFAULT 0,
    `deleted`              INT           NOT NULL DEFAULT 0,
    `created_at`           TIMESTAMP     NULL,
    `updated_at`           TIMESTAMP     NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_im_member_conv_user` UNIQUE (`tenant_id`, `conversation_id`, `user_id`)
);

CREATE INDEX `idx_im_member_tenant_conv` ON `im_conversation_member` (`tenant_id`, `conversation_id`);
CREATE INDEX `idx_im_member_tenant_user` ON `im_conversation_member` (`tenant_id`, `user_id`);

CREATE TABLE `im_message` (
    `id`              BIGINT        NOT NULL,
    `tenant_id`       BIGINT        NOT NULL DEFAULT 0,
    `conversation_id` BIGINT        NOT NULL,
    `sender_id`       BIGINT        NOT NULL,
    `content`         TEXT          NULL,
    `msg_type`        VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'SENT',
    `reply_to_id`     BIGINT        NULL,
    `version`         INT           NOT NULL DEFAULT 0,
    `deleted`         INT           NOT NULL DEFAULT 0,
    `created_by`      VARCHAR(64)   NULL,
    `created_at`      TIMESTAMP     NULL,
    `updated_by`      VARCHAR(64)   NULL,
    `updated_at`      TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_im_message_tenant_conv` ON `im_message` (`tenant_id`, `conversation_id`);
CREATE INDEX `idx_im_message_created`     ON `im_message` (`tenant_id`, `conversation_id`, `created_at`);

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9401, 0, 0, 'im', '站内消息', 'CATALOG', NULL, NULL, NULL, 'ChatDotRound', 1, 0, 90, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9402, 0, 9401, 'im:conversation', '会话管理', 'MENU', '/im/conversation', 'im/ConversationView', 'im:conversation:manage', 'ChatSquare', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9403, 0, 9401, 'im:message', '消息中心', 'MENU', '/im/message', 'im/MessageView', 'im:message:view', 'Message', 1, 0, 20, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9402;
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9403;
