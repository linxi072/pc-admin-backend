-- 千万级性能测试数据集（隔离库 java_admin_perf）
-- 由 seed-ten-million.sh 调用。表结构由 mysqldump 从 java_admin 原样复制。
--
-- 装载策略（大表安全）：
--   * 装载前 DROP 三个二级索引 → 128 批 × 10 万行独立事务写入 → 装载后重建索引。
--     避免 INSERT ... SELECT FROM 同表导致的临时表/锁表膨胀，也避免单事务锁爆。
--   * 主表 sys_operation_log = 12,800,000 行；其余 6 张真实业务表合计约 120 万行。
--   * 总规模约 1400 万行（>= 1000 万），且最终索引形态与生产完全一致。
--   * 约束/异常输入的「DB 接受 / DB 拒绝」由 Java 基准程序注入并记录，不在此文件。

SET SESSION cte_max_recursion_depth = 1000000;

-- 装载期移除二级索引，装载完成后重建（与生产 DDL 一致）
ALTER TABLE sys_operation_log DROP INDEX idx_module_time;
ALTER TABLE sys_operation_log DROP INDEX idx_operator_time;
ALTER TABLE sys_operation_log DROP INDEX idx_trace_id;

-- ============================================================
-- 1) sys_user：10 万（唯一键 tenant_id+username / tenant_id+mobile 均需唯一）
-- ============================================================
INSERT INTO sys_user
  (id, tenant_id, username, password_hash, display_name, mobile, email, status, created_by, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
SELECT 800000000000000000 + n,
       (n MOD 8) + 1,
       CONCAT('perf_u_', n),
       '$2a$10$PerfTestOnlyHashValueNotForLogin0000000000000000000000000000000',
       CONCAT('压测用户', n),
       CONCAT('139', LPAD(n, 8, '0')),
       CONCAT('perf_u_', n, '@example.com'),
       CASE WHEN n MOD 20 = 0 THEN 'DISABLED' ELSE 'ACTIVE' END,
       987654321,
       DATE_ADD('2025-01-01 00:00:00', INTERVAL n SECOND),
       DATE_ADD('2025-01-01 00:00:00', INTERVAL n SECOND)
FROM seq;

-- ============================================================
-- 2) sys_operation_log：128 批 × 10 万 = 12,800,000 行
--    id = 900000000000000000 + batch*100000 + n，全局唯一且单调递增。
-- ============================================================
DROP PROCEDURE IF EXISTS perf_fill_oplog;
DELIMITER $$
CREATE PROCEDURE perf_fill_oplog()
BEGIN
  DECLARE b INT DEFAULT 0;
  WHILE b < 128 DO
    INSERT INTO sys_operation_log
      (id, tenant_id, module_code, operation_type, operation_name, operator_id, operator_name,
       request_method, request_path, result_code, duration_ms, ip_address, trace_id, success, occurred_at)
    WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
    SELECT 900000000000000000 + b * 100000 + n,
           (n MOD 8) + 1,
           ELT(1 + (n MOD 5), 'USER', 'ROLE', 'MENU', 'WORKFLOW', 'NOTICE'),
           ELT(1 + (n MOD 5), 'CREATE', 'UPDATE', 'DELETE', 'QUERY', 'EXPORT'),
           CONCAT('perf-op-', b * 100000 + n),
           800000000000000000 + (n MOD 100000) + 1,
           CONCAT('operator', n MOD 1000),
           ELT(1 + (n MOD 4), 'GET', 'POST', 'PUT', 'DELETE'),
           CONCAT('/api/perf/', b * 100000 + n),
           CASE WHEN n MOD 50 = 0 THEN '500' WHEN n MOD 37 = 0 THEN '403' ELSE '200' END,
           (b * 100000 + n) MOD 2000,
           CONCAT('10.', FLOOR((b * 100000 + n) / 65536) MOD 256, '.',
                  FLOOR((b * 100000 + n) / 256) MOD 256, '.', (b * 100000 + n) MOD 256),
           'SEED',
           CASE WHEN n MOD 50 = 0 THEN 0 ELSE 1 END,
           DATE_ADD('2025-01-01 00:00:00', INTERVAL (b * 100000 + n) SECOND)
    FROM seq;
    SET b = b + 1;
    IF b MOD 16 = 0 THEN
      SELECT CONCAT('FILL_PROGRESS batch=', b, '/128 rows=',
                    (SELECT COUNT(*) FROM sys_operation_log)) AS progress;
    END IF;
  END WHILE;
END$$
DELIMITER ;
CALL perf_fill_oplog();
DROP PROCEDURE perf_fill_oplog;

-- 重建生产同款索引（记录耗时由 shell 完成）
ALTER TABLE sys_operation_log
  ADD INDEX idx_module_time (tenant_id, module_code, occurred_at),
  ADD INDEX idx_operator_time (tenant_id, operator_id, occurred_at),
  ADD INDEX idx_trace_id (trace_id);

-- ============================================================
-- 3) sys_login_log：40 万
-- ============================================================
INSERT INTO sys_login_log
  (id, tenant_id, user_id, username, login_type, result, ip_address, user_agent, trace_id, occurred_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 400000)
SELECT 810000000000000000 + n,
       (n MOD 8) + 1,
       800000000000000000 + (n MOD 100000) + 1,
       CONCAT('perf_u_', (n MOD 100000) + 1),
       CASE WHEN n MOD 3 = 0 THEN 'SMS' ELSE 'PASSWORD' END,
       CASE WHEN n MOD 50 = 0 THEN 'FAILURE' ELSE 'SUCCESS' END,
       CONCAT('10.0.', FLOOR(n / 256) MOD 256, '.', n MOD 256),
       'Mozilla/5.0 PerfAgent/1.0',
       'SEED',
       DATE_ADD('2025-01-01 00:00:00', INTERVAL (n MOD 86400) SECOND)
FROM seq;

-- ============================================================
-- 4) sys_notification_record：20 万
-- ============================================================
INSERT INTO sys_notification_record
  (id, tenant_id, channel, msg_type, title_summary, content_summary, receiver_summary,
   receiver_count, delivered, status, trace_id, sent_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 200000)
SELECT 820000000000000000 + n,
       (n MOD 8) + 1,
       ELT(1 + (n MOD 5), 'IN_APP', 'EMAIL', 'SMS', 'WEBHOOK', 'WEBSOCKET'),
       CASE WHEN n MOD 2 = 0 THEN 'NOTICE' ELSE 'ALERT' END,
       CONCAT('通知摘要', n),
       CONCAT('内容摘要', n),
       '138****8000',
       1 + (n MOD 100),
       CASE WHEN n MOD 2 = 0 THEN 1 ELSE 0 END,
       CASE WHEN n MOD 25 = 0 THEN 'FAILED' ELSE 'SUCCESS' END,
       'SEED',
       DATE_ADD('2025-01-01 00:00:00', INTERVAL (n MOD 86400) SECOND)
FROM seq;

-- ============================================================
-- 5) sys_message：10 万；sys_message_receipt：30 万
-- ============================================================
INSERT INTO sys_message
  (id, tenant_id, title, content, msg_type, sender_id, receiver_ids, total_count, read_count,
   sent_at, created_by)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
SELECT 830000000000000000 + n,
       (n MOD 8) + 1,
       CONCAT('压测消息', n),
       CONCAT('这是一条千万级数据集中的站内信内容 #', n, '，用于还原真实业务读取与导出场景。'),
       CASE WHEN n MOD 2 = 0 THEN 'NOTICE' ELSE 'ALERT' END,
       800000000000000000 + (n MOD 100000) + 1,
       CONCAT(800000000000000000 + (n MOD 100000) + 1),
       3,
       n MOD 4,
       DATE_ADD('2025-01-01 00:00:00', INTERVAL (n MOD 86400) SECOND),
       987654321
FROM seq;

-- 唯一键 (tenant_id, message_id, user_id)：同一 message 的 3 个回执 user_id 互异
INSERT INTO sys_message_receipt
  (id, tenant_id, message_id, user_id, is_read, read_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 300000)
SELECT 840000000000000000 + n,
       ((n DIV 3) MOD 8) + 1,
       830000000000000000 + (n DIV 3) + 1,
       800000000000000000 + (((n DIV 3) * 3 + (n MOD 3)) MOD 100000) + 1,
       CASE WHEN n MOD 2 = 0 THEN 1 ELSE 0 END,
       CASE WHEN n MOD 2 = 0 THEN DATE_ADD('2025-01-01 00:00:00', INTERVAL (n MOD 86400) SECOND) ELSE NULL END
FROM seq;

-- ============================================================
-- 6) sys_monitor_sample：10 万
-- ============================================================
INSERT INTO sys_monitor_sample
  (id, tenant_id, cpu_usage, memory_usage, system_memory_usage, disk_usage,
   used_heap_bytes, max_heap_bytes, used_memory_bytes, total_memory_bytes,
   online_users, active_sessions, thread_count, sampled_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
SELECT 850000000000000000 + n,
       (n MOD 8) + 1,
       n MOD 100,
       (n MOD 90) + 5,
       (n MOD 80) + 10,
       (n MOD 70) + 20,
       (n MOD 2000) * 1000000,
       4294967296,
       (n MOD 8000) * 1000000,
       17179869184,
       n MOD 500,
       n MOD 300,
       50 + (n MOD 200),
       DATE_ADD('2025-01-01 00:00:00', INTERVAL n SECOND)
FROM seq;

-- ============================================================
-- 7) 边界值种子（合法但语义边界，供功能用例核验过滤/排序/聚合）
--    id 使用独立段，不参与批量装载的连续区间。
-- ============================================================
INSERT INTO sys_operation_log
  (id, tenant_id, module_code, operation_type, operation_name, operator_id, operator_name,
   request_method, request_path, result_code, duration_ms, ip_address, trace_id, success, occurred_at)
VALUES
  (990000000000000001, 1, 'USER', 'QUERY', '边界-空字符串', NULL, '', 'GET', '', '200', 0, '0.0.0.0', 'BOUNDARY', 1, '1970-01-01 00:00:00.000'),
  (990000000000000002, 8, 'NOTICE', 'EXPORT', '边界-未来时间', 800000000000000001, '边界操作员', 'POST', '/api/perf/boundary', '500', 9223372036854775807, '255.255.255.255', 'BOUNDARY', 0, '2099-12-31 23:59:59.999'),
  (990000000000000003, 1, 'USER', 'QUERY', '边界-Unicode与emoji测试🚀', 800000000000000002, '张三丰', 'GET', '/api/perf/unicode', '403', 1, '::1', 'BOUNDARY', 1, '2025-06-15 12:00:00.000'),
  (990000000000000004, 999, 'ZZ_INVALID', 'ZZ_INVALID', '边界-非法枚举语义', NULL, NULL, 'PATCH', '/api/perf/invalid', '-1', 18446744073709551615, '999.999.999.999', 'BOUNDARY', 2, '2025-06-15 12:00:00.001');

SELECT CONCAT('SEED_TABLE_ROWS op_log=', (SELECT COUNT(*) FROM sys_operation_log)) AS seed_summary;
