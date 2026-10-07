-- V10 个人中心与账号设置：为 sys_user 补充头像与偏好字段。
--
-- 说明：
--   手机号 / 邮箱绑定复用 V1 已有的 mobile、email 列，此处仅新增「偏好」类字段，
--   避免为同一语义重复建列。绑定状态由列是否为空推导，无需额外标记位。
--
-- 幂等性：所有 DDL 均以 information_schema 判定做守卫，重复执行不报错
-- （与 V6 保持同一风格，MySQL 无事务 DDL，迁移中途失败后可安全重跑）。

-- 1) 头像：存相对路径（如 /uploads/avatar/1_1710000000.png），不存二进制
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `avatar_url` VARCHAR(255) NULL COMMENT ''头像地址(相对路径)'' AFTER `email`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_url'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 通知偏好：三个开关独立列，便于「只开站内信、关邮件」这类组合
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_site_message` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''站内信通知开关'' AFTER `avatar_url`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_site_message'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_email` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''邮件通知开关'' AFTER `notify_site_message`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_email'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''短信通知开关'' AFTER `notify_email`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_mobile'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 隐私偏好：登录日志展示、脱敏手机号、允许被搜索
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `show_login_log` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否展示我的登录记录'' AFTER `notify_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'show_login_log'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `mask_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''对外展示时脱敏手机号'' AFTER `show_login_log`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'mask_mobile'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `discoverable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''允许被其他用户搜索到'' AFTER `mask_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'discoverable'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 登录设备索引：支撑「我的登录设备」按用户聚合查询
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_refresh_token`
             ADD KEY `idx_user_revoked` (`user_id`, `revoked_at`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_refresh_token' AND INDEX_NAME = 'idx_user_revoked'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;