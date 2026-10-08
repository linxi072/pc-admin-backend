-- V15 用户-角色 / 用户-部门 恢复多对多（回退 V6 单值收敛）
--
-- 背景：V6 把 sys_user_role / sys_user_org 收敛为 sys_user.role_id / org_id 单列，
--       现按业务需要恢复 N:N：一个用户可拥有多个角色、归属多个部门（含主角色/主部门标记）。
-- 幂等：所有 DDL/DML 均以 information_schema 守卫，重复执行结果一致。
-- 表名 / 列名与 com.acme.scaffold.jooq.JooqTables 注册一致。

-- 1) 新建用户-角色关联表
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE TABLE `sys_user_role` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `user_id` BIGINT UNSIGNED NOT NULL,
            `role_id` BIGINT UNSIGNED NOT NULL,
            `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''是否主角色'',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_user_role` (`tenant_id`, `user_id`, `role_id`),
            KEY `idx_role` (`tenant_id`, `role_id`),
            KEY `idx_user` (`tenant_id`, `user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 新建用户-部门关联表
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE TABLE `sys_user_org` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `user_id` BIGINT UNSIGNED NOT NULL,
            `org_id` BIGINT UNSIGNED NOT NULL,
            `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''是否主部门'',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_user_org` (`tenant_id`, `user_id`, `org_id`),
            KEY `idx_org` (`tenant_id`, `org_id`),
            KEY `idx_user` (`tenant_id`, `user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 数据回填：把 V6 单列绑定写入关联表（标记 is_primary=1）。
--    仅在 sys_user 仍存在 role_id / org_id 列时执行；INSERT IGNORE 保证重复执行不报错。
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id, is_primary, created_at)
         SELECT 0, id, role_id, 1, NOW(3) FROM sys_user WHERE role_id IS NOT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary, created_at)
         SELECT 0, id, org_id, 1, NOW(3) FROM sys_user WHERE org_id IS NOT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 收敛列下线：删除 sys_user 的 role_id / org_id / primary_org_id（含索引）。
--    删除前关联数据已写入 sys_user_role / sys_user_org，主角色/主部门改由关联表 is_primary 推导。
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX IF EXISTS `idx_tenant_role`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX IF EXISTS `idx_tenant_org`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `role_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `primary_org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'primary_org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
