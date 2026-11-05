# 部署制品（P1-8）

本目录补齐此前缺失的可复现部署与回滚方案（原仅有 `prometheus/`）。

## 目录结构
- `Dockerfile`：多阶段构建，构建用 JDK21、运行用 JRE17，镜像内嵌存活探针（`/actuator/health/liveness`）。
- `docker-compose.yml`：本地/单机编排，含 `app` + `mysql:8.4`（强制 `utf8mb4_0900_ai_ci`，见 P2-1）+ `prometheus`。
- `systemd/java-admin-framework.service`：原生进程部署单元（`Type=simple` + 优雅停机 + 资源/安全加固）。
- `scripts/start.sh`：部署新包 → 重启 → 健康检查 → 失败自动回滚。
- `scripts/rollback.sh`：恢复最近备份包并重启。

## 使用
- 容器：`docker compose -f deploy/docker-compose.yml up -d`（敏感变量经 `.env` 注入）。
- 裸机/VM：`cp scaffold-admin/target/scaffold-admin-*.jar /opt/java-admin-framework/app.jar`，
  配置 `/opt/java-admin-framework/env`（JAVA_OPTS、DB_*、JWT_SECRET、ALERT_WEBHOOK_TOKEN 等），
  `systemctl enable --now java-admin-framework`，升级用 `sudo ./deploy/scripts/start.sh <新jar>`。

## 硬约束（与评估报告一致）
- **MySQL ≥ 8.0.4**：迁移脚本统一 `utf8mb4_0900_ai_ci`，无法降级至 8.0.3- 或 MariaDB（P2-1）。
  - 若必须兼容更低版本或 MariaDB，需另提供 `utf8mb4_general_ci` 备选迁移脚本并切换数据库方言，属后续迭代项。
- **告警 Webhook 令牌必填**：启用接收端时 `ALERT_WEBHOOK_TOKEN` 为空将导致端点拒绝服务（P0-2）。
- 构建/运行 JDK 不一致收敛：CI 与 Docker 构建统一 JDK21，`maven.compiler.release=17` 锁定运行基线（P1-7）。

## API 版本段（P2-2）
- 所有 HTTP 端点经 `server.servlet.context-path=${APP_API_CONTEXT_PATH:}` 统一加前缀；
  默认值空（与既有集成测试/网关兼容），生产/容器部署在 `deploy/docker-compose.yml` 置 `APP_API_CONTEXT_PATH=/api/v1`。
- 演进期如需 Breaking Change 隔离，可经网关重定向或双前缀过渡，不破坏存量调用方。

## 软件成分分析（P2-4）
- 默认构建不跑依赖漏洞扫描（保持离线纯净、不打 NVD 库）；在具备网络/NVD 镜像的环境用
  `mvn -Psca verify` 激活 OWASP Dependency-Check，CVSS≥9 阻断构建，抑制规则见 `scripts/dependency-check-suppressions.xml`。
- **Spring Boot 补丁**：当前 `3.3.4` 非最新补丁，建议在在线构建环境升级至当前 3.3.x 补丁版（仅改父 POM `version`，
  不引入高风险行为变更），并随 `-Psca` 一并纳入 CI 门禁。本次离线环境未强行升级以避免破坏门禁。

## 线程池隔离（P1-5 / P0-3）
- 通知异步投递、通知重试退避、导出字节生成分别运行于独立有界线程池
  （`notificationExecutor` / `notificationRetryScheduler` / `exportExecutor`，均 `CallerRunsPolicy` 兜底），
  与请求/业务线程隔离；导出控制器可切换 `ExportCenterService#downloadAsync` 离场到 `exportExecutor`。
