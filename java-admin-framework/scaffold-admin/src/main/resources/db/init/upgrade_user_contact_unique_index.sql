-- 存量库升级：为 sys_user 补「租户内未删除且已绑定的手机号/邮箱唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 mobile_active / email_active 与同名唯一索引。
-- 执行约束：先备份、停写窗口内执行；本脚本不做列/索引存在性守卫，重复执行会报重复列。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_user'
--     AND column_name IN ('mobile_active', 'email_active');
USE `scaffold`;

ALTER TABLE `sys_user`
    ADD COLUMN `mobile_active` VARCHAR(32)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `mobile` ELSE NULL END) STORED,
    ADD COLUMN `email_active` VARCHAR(128)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `email` ELSE NULL END) STORED;

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

CREATE UNIQUE INDEX `uk_user_tenant_mobile_active`
    ON `sys_user` (`tenant_id`, `mobile_active`);
CREATE UNIQUE INDEX `uk_user_tenant_email_active`
    ON `sys_user` (`tenant_id`, `email_active`);
