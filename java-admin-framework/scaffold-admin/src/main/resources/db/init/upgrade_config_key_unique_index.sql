-- 存量库升级：为 sys_config 补「未删除配置键全局唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 config_key_active 与同名唯一索引。
-- 执行约束：先备份、停写窗口内执行；本脚本不做列/索引存在性守卫，重复执行会报重复列。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_config'
--     AND column_name = 'config_key_active';
USE `scaffold`;

ALTER TABLE `sys_config`
    ADD COLUMN `config_key_active` VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `config_key` ELSE NULL END) STORED;

-- 去重：同一未删除配置键只保留一条，优先高版本、其次最新 id；
-- 逻辑删除行不会进入唯一键（生成列为 NULL），无需清理。
DELETE r FROM `sys_config` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY config_key_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `sys_config`
        WHERE config_key_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

CREATE UNIQUE INDEX `uk_config_key_active`
    ON `sys_config` (`config_key_active`);
