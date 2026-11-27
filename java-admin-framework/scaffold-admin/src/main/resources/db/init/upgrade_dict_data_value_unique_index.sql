-- 存量库升级：为 sys_dict_data 补「同租户同字典类型下未删除字典值唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 dict_value_active 与同名唯一索引。
-- 执行约束：先备份、建议停写窗口内执行；加列与建索引均带存在性守卫（information_schema + PREPARE/EXECUTE 动态 SQL，非存储过程），可安全重复执行；去重 DELETE 天然幂等。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_dict_data'
--     AND column_name = 'dict_value_active';
USE `scaffold`;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict_data' AND column_name = 'dict_value_active');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `sys_dict_data`
    ADD COLUMN `dict_value_active` VARCHAR(100)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `dict_value` ELSE NULL END) STORED',
    'SELECT ''dict_value_active 列已存在，跳过加列'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 去重：同一租户同一字典类型下同一未删除字典值只保留一条，优先高版本、其次最新 id；
-- 逻辑删除行不会进入唯一键（生成列为 NULL），无需清理。
-- 注意：dict_value 是前端枚举渲染的取值键，被去重丢弃的行须人工确认标签归并。
DELETE r FROM `sys_dict_data` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, dict_type_code, dict_value_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `sys_dict_data`
        WHERE dict_value_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict_data' AND index_name = 'uk_dict_tenant_type_value_active');
SET @ddl := IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_dict_tenant_type_value_active`
    ON `sys_dict_data` (`tenant_id`, `dict_type_code`, `dict_value_active`)',
    'SELECT ''uk_dict_tenant_type_value_active 索引已存在，跳过建索引'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
