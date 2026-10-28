#!/usr/bin/env bash
# java-admin-framework 后端性能压测启动器（需先启动应用：见 PERF-TEST-PLAN.md）
# 用法：bash run-perf.sh [BASE_URL] [TOTAL] [CONCURRENCY]
set -euo pipefail
BASE="${1:-http://127.0.0.1:8080}"
TOTAL="${2:-3000}"
CONCURRENCY="${3:-50}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> 连通性预检：GET ${BASE}/api/connectivity/ping"
if ! curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BASE}/api/connectivity/ping" | grep -q 200; then
  echo "应用未就绪（/api/connectivity/ping 未返回 200），请先启动后端再执行压测。"
  exit 1
fi

echo "==> 运行 Python 压测（total=${TOTAL}, concurrency=${CONCURRENCY}）"
python3 "${SCRIPT_DIR}/load_test.py" --base "${BASE}" --total "${TOTAL}" --concurrency "${CONCURRENCY}" --ramp
