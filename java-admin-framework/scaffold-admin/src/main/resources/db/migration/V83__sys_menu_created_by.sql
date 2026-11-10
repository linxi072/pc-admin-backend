-- V83 sys_menu 审计创建人补齐（若依规范对齐 P0-2）
--
-- 背景：V32 基线中 sys_user / sys_org / sys_role 等主数据表均带 created_by / updated_by
--   审计列，唯独 sys_menu 缺失 created_by，导致「谁新建了这条菜单」无法追溯。
--   本版本仅补 created_by（不补 updated_by），保持与报告 §3 P0-2 的改动范围一致。
--
-- 版本链约束：db/migration/README.md 明令「版本文件字节不可变、结构性变更一律前向追加」，
--   故 V32 基表定义保持原样，新库由 V32 建表 → 本文件补列，结果与直接改基表等价。
--
-- 幂等：沿用 V32 L518–L535 的「列缺失才补」模式（information_schema 守卫 + 动态 DDL）。
--   MySQL 无事务性 DDL，迁移中途失败会留下已生效的结构；重复执行命中 'DO 0' 直接无操作，
--   不会撞 1060 Duplicate column。
-- 兼容：列可空，存量行一律为 NULL，不回填、不造数据；不影响任何既有查询与写入路径。
-- 影响：1 张表 1 列；无接口变更、无前端变更。

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_menu`
             ADD COLUMN `created_by` BIGINT UNSIGNED NULL COMMENT ''创建人用户ID'' AFTER `status`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'created_by'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
