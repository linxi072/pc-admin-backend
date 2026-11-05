#!/usr/bin/env bash
# 生产启动脚本：备份旧包 -> 部署新包 -> 启动 -> 健康检查 -> 失败自动回滚
# 用法：sudo ./deploy/scripts/start.sh /path/to/new-scaffold-admin.jar
set -euo pipefail

DEPLOY_DIR="/opt/java-admin-framework"
JAR_NAME="app.jar"
HEALTH_URL="${HEALTH_URL:-http://localhost:9090/actuator/health/liveness}"
TIMEOUT_SEC="${TIMEOUT_SEC:-60}"

NEW_JAR="${1:?用法: start.sh <新jar路径>}"
[ -f "$NEW_JAR" ] || { echo "新包不存在: $NEW_JAR" >&2; exit 1; }

cd "$DEPLOY_DIR"
ts="$(date +%Y%m%d-%H%M%S)"

# 1) 备份当前运行包
if [ -f "$JAR_NAME" ]; then
  cp "$JAR_NAME" "app.jar.bak-${ts}"
  echo "已备份旧包 -> app.jar.bak-${ts}"
fi

# 2) 放置新包
cp "$NEW_JAR" "$JAR_NAME"
echo "已部署新包 -> $JAR_NAME"

# 3) 重启服务
if command -v systemctl >/dev/null 2>&1; then
  systemctl restart java-admin-framework.service
else
  pkill -f "java .*app.jar" || true
  nohup java ${JAVA_OPTS} -jar "$DEPLOY_DIR/$JAR_NAME" >/var/log/java-admin-framework/app.log 2>&1 &
fi

# 4) 健康检查（轮询存活探针）
echo "等待健康检查 ($HEALTH_URL) ..."
for i in $(seq 1 "$TIMEOUT_SEC"); do
  if curl -fs "$HEALTH_URL" >/dev/null 2>&1; then
    echo "健康检查通过，启动成功"
    exit 0
  fi
  sleep 1
done

echo "健康检查超时，触发回滚" >&2
exec "$(dirname "$0")/rollback.sh"
