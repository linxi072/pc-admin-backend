#!/usr/bin/env bash
# =====================================================================================
# 千万级 L4（Spring Boot HTTP 全链路）压测隔离库准备
# -------------------------------------------------------------------------------------
# 与 seed-ten-million.sh 的区别：
#   - java_admin_perf 只有 7 张表，无法拉起完整 Spring 上下文（db/init/Flowable/菜单/
#     角色/admin 账号等 110 张表缺失），因此新建独立的 java_admin_perf_l4；
#   - L4 库 = java_admin 的完整结构与种子数据（含 admin 账号、权限、菜单）
#     + java_admin_perf 的 1280 万行 sys_operation_log（真实千万级主表）；
#   - 只替换主表：sys_user 等表保留 java_admin 原始行，确保登录/鉴权链路可用。
# 绝不修改 java_admin 与 java_admin_perf。
# 用法：bash scripts/perf/seed-l4-db.sh
# =====================================================================================
set -euo pipefail

MYSQL=${MYSQL:-/usr/local/mysql/bin/mysql}
DUMP=${DUMP:-/usr/local/mysql/bin/mysqldump}
CONN=(-h127.0.0.1 -P3306 -uroot)
export MYSQL_PWD=${MYSQL_PWD:-root}
SRC=java_admin
BIG=java_admin_perf
DST=java_admin_perf_l4

echo "[1/5] 重建隔离库 ${DST} ..."
"$MYSQL" "${CONN[@]}" -e \
  "DROP DATABASE IF EXISTS ${DST}; CREATE DATABASE ${DST} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "[2/5] 从 ${SRC} 完整复制结构与种子数据（110 表，约 197MB）..."
"$DUMP" "${CONN[@]}" --single-transaction --quick --no-tablespaces --skip-comments "$SRC" \
  | "$MYSQL" "${CONN[@]}" "$DST"

echo "[3/5] 主表去二级索引、清空，准备分批装载..."
"$MYSQL" "${CONN[@]}" "$DST" <<'SQL'
ALTER TABLE sys_operation_log
  DROP INDEX idx_module_time,
  DROP INDEX idx_operator_time,
  DROP INDEX idx_trace_id;
TRUNCATE TABLE sys_operation_log;
SQL

echo "[4/5] 按 128 批 × 10 万行从 ${BIG} 跨库装载主表（含 4 条边界行）并重建索引..."
"$MYSQL" "${CONN[@]}" "$DST" <<'SQL'
DROP PROCEDURE IF EXISTS load_l4_main;
DELIMITER $$
CREATE PROCEDURE load_l4_main()
BEGIN
  DECLARE b INT DEFAULT 0;
  WHILE b < 128 DO
    INSERT INTO sys_operation_log
      SELECT * FROM java_admin_perf.sys_operation_log
       WHERE id >  900000000000000000 + b * 100000
         AND id <= 900000000000000000 + (b + 1) * 100000;
    SET b = b + 1;
  END WHILE;
  INSERT INTO sys_operation_log
    SELECT * FROM java_admin_perf.sys_operation_log
     WHERE id >= 990000000000000000;
END$$
DELIMITER ;
CALL load_l4_main();
DROP PROCEDURE load_l4_main;
ALTER TABLE sys_operation_log
  ADD INDEX idx_module_time (tenant_id, module_code, occurred_at),
  ADD INDEX idx_operator_time (tenant_id, operator_id, occurred_at),
  ADD INDEX idx_trace_id (trace_id);
ANALYZE TABLE sys_operation_log;
SQL

echo "[5/5] 精确 COUNT 校验..."
"$MYSQL" "${CONN[@]}" -N -e "
SELECT CONCAT('L4_TABLES=', COUNT(*)) FROM information_schema.tables WHERE table_schema='${DST}';
SELECT CONCAT('L4_OP_LOG_EXACT=', COUNT(*)) FROM ${DST}.sys_operation_log;
SELECT CONCAT('L4_USER_ROWS=', COUNT(*)) FROM ${DST}.sys_user;
SELECT CONCAT('BIG_OP_LOG_UNCHANGED=', COUNT(*)) FROM ${BIG}.sys_operation_log;
SELECT CONCAT('SRC_OP_LOG_UNCHANGED=', COUNT(*)) FROM ${SRC}.sys_operation_log;
"
echo "L4_SEED_DONE"
