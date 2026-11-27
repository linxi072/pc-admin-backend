-- 存量库升级：为 sys_idempotency_record 补 (tenant_id, api_scope, idempotency_key) 唯一索引。
-- 新库无需执行：db/init/ddl.sql 已内联同名唯一索引。
-- 执行约束：先备份、停写窗口内执行；本脚本不做索引存在性守卫，重复执行会报索引已存在。
-- 前置检查（应为 0 行）：
--   SELECT index_name FROM information_schema.STATISTICS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_idempotency_record'
--     AND index_name = 'uk_idem_tenant_scope_key';
USE `scaffold`;

-- 去重：同一 (tenant_id, api_scope, idempotency_key) 只保留一条。
-- 优先级 SUCCESS > FAILED > PROCESSING，同级保留 id 最大（最近登记）的一条，
-- 避免并发双写留下的 PROCESSING 覆盖已完成的 SUCCESS 快照。
DELETE r FROM `sys_idempotency_record` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, api_scope, idempotency_key
                   ORDER BY CASE status
                                WHEN 'SUCCESS' THEN 0
                                WHEN 'FAILED' THEN 1
                                ELSE 2
                            END, id DESC
               ) AS rn
        FROM `sys_idempotency_record`
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

CREATE UNIQUE INDEX `uk_idem_tenant_scope_key`
    ON `sys_idempotency_record` (`tenant_id`, `api_scope`, `idempotency_key`);
