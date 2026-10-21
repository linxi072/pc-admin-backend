# 可观测性运营侧部署（G1 / G2）

本目录提供「可采集 → 可定位 → 可告警」的运营侧产物。**采用原生进程部署（无 Docker）**，与项目整体约定一致。

## 端口规划（避免冲突）

| 组件 | 端口 | 说明 |
|---|---|---|
| 应用管理端点 | **9090** | `application.yml` `management.server.port=9090`，暴露 `/actuator/prometheus` |
| Prometheus | **9091** | 启动加 `--web.listen-address=:9091`，避免与应用 9090 冲突 |
| Grafana | **3000** | 默认 |
| Alertmanager | **9093** | 默认 |

## 目录结构

```
monitoring/
├── prometheus/
│   ├── prometheus.yml      # 抓取配置：scrape 应用 9090/actuator/prometheus
│   └── alert-rules.yml     # 5 条告警规则（错误率/P99/老年代/连接池/JobRunr）
├── alertmanager/
│   └── alertmanager.yml    # 路由 + 接收端（webhook/email 占位，需替换）
└── grafana/
    ├── provisioning/
    │   ├── datasources/prometheus.yml   # 数据源指向 Prometheus 9091
    │   └── dashboards/dashboards.yml     # 自动加载下方 5 个看板
    └── dashboards/
        ├── jvm.json         # JVM（堆/老年代/CPU/线程/GC/类）
        ├── http.json         # HTTP（QPS/错误率/P99/状态码）
        ├── hikaricp.json     # HikariCP（使用率/活跃空闲/获取耗时/创建耗时）
        ├── workflow.json     # 工作流/认证（审批耗时/登录成败）
        └── jobrunr.json      # 调度（失败/成功/队列）
```

## 快速启动（原生）

```bash
# 1) 应用：已暴露 /actuator/prometheus（management.server.port=9090）

# 2) Prometheus
prometheus --config.file=prometheus/prometheus.yml --web.listen-address=:9091

# 3) Alertmanager
alertmanager --config.file=alertmanager/alertmanager.yml

# 4) Grafana（指向本目录做 provisioning）
grafana-server \
  --cfg:default.paths.provisioning=/abs/path/monitoring/grafana/provisioning \
  --cfg:default.paths.plugins=/abs/path/monitoring/grafana
# 或把 grafana/dashboards 软链到 /etc/grafana/provisioning/dashboards
```

启动后访问 Grafana `http://localhost:3000`，左侧「Dashboards → Java Admin Framework」即可看到 5 个看板；
Prometheus `http://localhost:9091` 的 Alerts 页可看到告警状态，触发后由 Alertmanager 推送到配置的 webhook/email。

## 验收对照（《方案》§15 阶段 4 / 验收清单）

| 验收项 | 实现 |
|---|---|
| grafana dashboard：JVM/HTTP/HikariCP/工作流/调度 | `grafana/dashboards/*.json` 五块看板 |
| alertmanager rules：错误率>1% / P99>800ms / 老年代>80% / 连接池>85% / JobRunr 失败>0 | `prometheus/alert-rules.yml` 五条 |
| 原生环境触发一条告警可被 Alertmanager 接收 | 调整阈值或人为制造高错误率即可验证路由与接收端 |

## 启用 JobRunr 指标

`jobrunr.json` 依赖 `jobrunr_jobs_*` 指标。需在 `application.yml` 增加：

```yaml
org:
  jobrunr:
    metrics:
      enabled: true
```

未开启时该看板为 No Data（不影响其余面板）。

## 与 G1 W3C traceparent 的关系

`monitor/observability` 已实现 W3C `traceparent` 传播（见 `TraceContext` / `TraceIdFilter`），
MDC `traceId` 与审计日志、Result 共用同一链路 ID。待具备 OTel Collector 时，以 Maven profile
`-Potel` 引入 OTel SDK 即可将 span 导出至 Jaeger/Tempo，无需改造业务代码。
