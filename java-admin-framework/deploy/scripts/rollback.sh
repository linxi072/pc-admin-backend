#!/usr/bin/env bash
# 回滚脚本：恢复最近一次备份的 app.jar 并重启（配合 start.sh 失败自动调用，亦可手动执行）
set -euo pipefail

DEPLOY_DIR="/opt/java-admin-framework"
JAR_NAME="app.jar"

cd "$DEPLOY_DIR"
BAK="$(ls -1 app.jar.bak-* 2>/dev/null | sort | tail -n1 || true)"
if [ -z "$BAK" ]; then
  echo "未找到可用备份，无法回滚" >&2
  exit 1
fi

cp "$BAK" "$JAR_NAME"
echo "已回滚至 $BAK"

if command -v systemctl >/dev/null 2>&1; then
  systemctl restart java-admin-framework.service
else
  pkill -f "java .*app.jar" || true
  nohup java ${JAVA_OPTS} -jar "$DEPLOY_DIR/$JAR_NAME" >/var/log/java-admin-framework/app.log 2>&1 &
fi
echo "回滚完成"
