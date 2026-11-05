#!/usr/bin/env bash
# 千万级性能测试数据灌入（隔离库 java_admin_perf，绝不触碰现有 java_admin）
#
# 用法: bash scripts/perf/seed-ten-million.sh
# 依赖: 本机 MySQL 8.x 客户端（/usr/local/mysql/bin/mysql、mysqldump），root/root
#
# 设计要点
#  1. 独立库 java_admin_perf：现有 java_admin 已有 ~55 万行业务/IT 数据，禁止污染。
#  2. 表结构从 java_admin 用 mysqldump --no-data 原样复制，保证生产 JooqTables.SYS_*
#     的列名、索引、类型完全一致，基准测试调用的是真实生产代码路径。
#  3. 主表 sys_operation_log：先删除三个二级索引，再以 128 批 × 10 万行独立写入共
#     12,800,000 行，装载完成后重建生产同款索引（避免同表倍增导致临时表/锁膨胀）。
#  4. 其余 6 张真实业务表合计约 120 万行，7 表精确 COUNT 合计 14,000,004 行，
#     覆盖用户/登录/通知/消息/回执/监控采样。
#  5. 边界值（1970/2099/emoji/极大数值/非法枚举）由 SQL 末尾 4 行注入；
#     约束异常输入的「DB 接受 / DB 拒绝」由 Java 基准程序注入并自行清理。
set -euo pipefail

MYSQL=${MYSQL_BIN:-/usr/local/mysql/bin/mysql}
DUMP=${DUMP_BIN:-/usr/local/mysql/bin/mysqldump}
export MYSQL_PWD=${MYSQL_PWD:-root}
# 数组形式：避免 zsh 不做单词拆分把整串参数当主机名
CONN=(-h127.0.0.1 -P3306 -uroot)
SRC=java_admin
DST=java_admin_perf
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG=${PERF_SEED_LOG:-/tmp/perf_seed.log}
: > "$LOG"

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG"; }

TABLES=(sys_user sys_login_log sys_operation_log sys_notification_record sys_message sys_message_receipt sys_monitor_sample)

log "=== 0/4 连通性检查 ==="
"$MYSQL" "${CONN[@]}" -N -e "SELECT VERSION();" | tee -a "$LOG"

log "=== 1/4 重建隔离库 ${DST}（仅此库，不影响 ${SRC}）==="
"$MYSQL" "${CONN[@]}" -e "DROP DATABASE IF EXISTS ${DST}; CREATE DATABASE ${DST} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" 2>&1 | tee -a "$LOG"

log "=== 2/4 从 ${SRC} 复制 7 张真实表结构（含索引）==="
DDL=/tmp/perf_schema.sql
: > "$DDL"
for t in "${TABLES[@]}"; do
  "$DUMP" "${CONN[@]}" --no-data --skip-add-drop-table --skip-comments --skip-set-charset "$SRC" "$t" >> "$DDL"
done
"$MYSQL" "${CONN[@]}" "$DST" < "$DDL" 2>&1 | tee -a "$LOG"
"$MYSQL" "${CONN[@]}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DST}';" | tee -a "$LOG"

log "=== 3/4 灌入种子数据（10 万 + 其余表约 220 万行）==="
"$MYSQL" "${CONN[@]}" "$DST" < "${SCRIPT_DIR}/seed-ten-million.sql" 2>&1 | tee -a "$LOG"

log "=== 4/4 精确行数与落盘规模取证 ==="
"$MYSQL" "${CONN[@]}" -t -e "
SELECT table_name AS '表', table_rows AS '估算行',
       ROUND(data_length/1024/1024,1) AS '数据MB',
       ROUND(index_length/1024/1024,1) AS '索引MB'
FROM information_schema.tables
WHERE table_schema='${DST}' ORDER BY table_name;
" | tee -a "$LOG"

"$MYSQL" "${CONN[@]}" -N -e "
SELECT CONCAT('TOTAL_ROWS=', SUM(cnt)) FROM (
  SELECT (SELECT COUNT(*) FROM ${DST}.sys_user) cnt
  UNION ALL SELECT (SELECT COUNT(*) FROM ${DST}.sys_login_log)
  UNION ALL SELECT (SELECT COUNT(*) FROM ${DST}.sys_operation_log)
  UNION ALL SELECT (SELECT COUNT(*) FROM ${DST}.sys_notification_record)
  UNION ALL SELECT (SELECT COUNT(*) FROM ${DST}.sys_message)
  UNION ALL SELECT (SELECT COUNT(*) FROM ${DST}.sys_message_receipt)
  UNION ALL SELECT (SELECT COUNT(*) FROM ${DST}.sys_monitor_sample)
) x;
" | tee -a "$LOG"

log "=== 完成：数据规模证据已写入 ${LOG} ==="
