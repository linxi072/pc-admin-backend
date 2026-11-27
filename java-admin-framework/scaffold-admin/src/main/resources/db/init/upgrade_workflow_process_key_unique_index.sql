-- 存量库升级：为 wf_workflow_design 补「同租户内未删除流程编码唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 process_key_active 与唯一索引 uk_tenant_key。
-- 执行约束：先备份、停写窗口内执行；本脚本不做列/索引存在性守卫，重复执行会报重复列。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'wf_workflow_design'
--     AND column_name = 'process_key_active';
USE `scaffold`;

ALTER TABLE `wf_workflow_design`
    ADD COLUMN `process_key_active` VARCHAR(128)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `process_key` ELSE NULL END) STORED;

-- 去重：同一租户内同一未删除流程编码只保留一条，优先高版本、其次最新 id；
-- 逻辑删除行不会进入唯一键（生成列为 NULL），无需清理（墓碑行 process_key 已带 _del_ 后缀）。
-- 注意：WorkflowDesignService#create 为幂等语义（同 key 返回既有设计），
-- 去重丢弃的重复行对应的前端草稿须人工确认后归并。
DELETE r FROM `wf_workflow_design` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, process_key_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `wf_workflow_design`
        WHERE process_key_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

CREATE UNIQUE INDEX `uk_tenant_key`
    ON `wf_workflow_design` (`tenant_id`, `process_key_active`);
