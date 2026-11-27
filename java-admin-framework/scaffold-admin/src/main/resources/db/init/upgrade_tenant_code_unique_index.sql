-- 存量库升级：为 sys_tenant 补「未删除租户编码全局唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 tenant_code_active 与同名唯一索引。
-- 执行约束：先备份、停写窗口内执行；本脚本不做列/索引存在性守卫，重复执行会报重复列。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_tenant'
--     AND column_name = 'tenant_code_active';
USE `scaffold`;

ALTER TABLE `sys_tenant`
    ADD COLUMN `tenant_code_active` VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `tenant_code` ELSE NULL END) STORED;

-- 去重：同一未删除租户编码只保留一条，优先高版本、其次最新 id；
-- 逻辑删除行不会进入唯一键（生成列为 NULL），无需清理。
DELETE r FROM `sys_tenant` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_code_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `sys_tenant`
        WHERE tenant_code_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

CREATE UNIQUE INDEX `uk_tenant_code_active`
    ON `sys_tenant` (`tenant_code_active`);
