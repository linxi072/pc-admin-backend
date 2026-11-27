-- 存量库升级：为 sys_user 补「租户内未删除且已绑定的手机号/邮箱唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 mobile_active / email_active 与同名唯一索引。
-- 执行约束：先备份、建议停写窗口内执行；加列与建索引均带存在性守卫（information_schema + PREPARE/EXECUTE 动态 SQL，非存储过程），可安全重复执行；去重 DELETE 天然幂等。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_user'
--     AND column_name IN ('mobile_active', 'email_active');
USE `scaffold`;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'mobile_active');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `sys_user`
    ADD COLUMN `mobile_active` VARCHAR(32)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `mobile` ELSE NULL END) STORED',
    'SELECT ''mobile_active 列已存在，跳过加列'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'email_active');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `sys_user`
    ADD COLUMN `email_active` VARCHAR(128)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `email` ELSE NULL END) STORED',
    'SELECT ''email_active 列已存在，跳过加列'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 去重：同一 (tenant_id, 已绑定手机号) / (tenant_id, 已绑定邮箱) 只保留一条，
-- 优先高版本、其次最新 id；NULL（未绑定）与逻辑删除行不进入唯一键，无需清理。
DELETE r FROM `sys_user` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, mobile_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `sys_user`
        WHERE mobile_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

DELETE r FROM `sys_user` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, email_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `sys_user`
        WHERE email_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_user_tenant_mobile_active');
SET @ddl := IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_user_tenant_mobile_active`
    ON `sys_user` (`tenant_id`, `mobile_active`)',
    'SELECT ''uk_user_tenant_mobile_active 索引已存在，跳过建索引'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_user_tenant_email_active');
SET @ddl := IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_user_tenant_email_active`
    ON `sys_user` (`tenant_id`, `email_active`)',
    'SELECT ''uk_user_tenant_email_active 索引已存在，跳过建索引'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
