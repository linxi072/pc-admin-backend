# 上线就绪评估报告 — java-admin-framework（V1.1.0，整改后复评）

> 评估依据：以 `LAUNCH-READINESS-ASSESSMENT-V1.0.7.md`（含 P0×3 / P1×9 / P2×4 共 16 项）为"预定上线标准与验收条件"锚点，叠加本次对整改后代码的 `file:line` 独立复验。
> 仓库：`outputs/java-admin-framework`，分支 `feat/V1.0.7`，17 个 Maven 模块；整改提交区间 `371a27b`…`553502c`（见 `docs/LAUNCH-READINESS-REMEDIATION-V1.0.7.md`）。
> 门禁现状：双门禁（全量 `mvn -o clean test` 18 模块 0 失败；`scripts/check-conventions.py` EXIT=0）通过。
> 置信度：本报告的"部分达标"结论均经源码 `file:line` 复验；未在整改范围内的遗留项沿用 V1.0.7 的走查结论并标注。

---

## 1. 五维度总览评分（整改后）

| 维度 | 就绪度 | 风险等级 | 关键缺口（整改后残留） |
|---|---|---|---|
| 功能完整性 | 部分达标 | 中 | `ai/im/cms/approval/plugin/codegen` 仍为占位/启发式，单测少、无独立 IT（**未被本次整改覆盖**） |
| 稳定性 | **达标** | 低 | 通知异步化+熔断+上界已闭环；残留 N+1 仅检测不阻断（非阻断级） |
| 性能 | **达标（含已知部分达标项）** | 中 | keyset 入口与防雪崩已落地；但 `user/message` 列表仍走 OFFSET、`role/org` 仍全量加载 |
| 安全性 | **达标** | 低 | P0-1/2/3 与 P1-1/4 已闭环 |
| 兼容性/可运维性 | **达标（含已知部分达标项）** | 中 | 部署制品/JSON 日志/API 版本段/文档已闭环；OTLP 缺 Collector、SCA 未实际运行 |

**总评：Conditional Go（准发布）** —— P0 三项已全部闭环，剩余为 P1 部分达标与已知占位模块，建议首版内跟踪闭环并持续迭代。

---

## 2. 功能评估清单（达标 / 部分达标 / 未达标）

### 2.1 安全性（Security）
**达标（绿）**
- S1 SQL 注入防护（jOOQ 类型安全 DSL）。
- S2 认证授权主体（JWT + `@PreAuthorize` + 三后置过滤器）。
- S3 敏感凭据无硬编码（环境变量注入）。
- S4 统一响应/异常体系。
- S5 外发通道默认关闭。
- S6 HTTP 客户端超时已配置。
- **S7 数据权限默认 fail-closed（SELF）** — 已闭环（P0-1）。
- **S8 告警 Webhook 空令牌拒绝 503** — 已闭环（P0-2）。
- **S9 字段级脱敏覆盖 record（构造器重建副本）** — 已闭环（P1-1）。

**部分达标（黄，中）**
- **S10 限流 fail-open 回落** — 已新增 `failClosed` 开关与 `DenyRateLimiter`（P1-4 已落地），但默认策略仍为 `fail-open` 回落本地计数；分布式场景需在部署侧显式开启 `fail-closed` 方为真正达标。

### 2.2 性能（Performance）
**达标（绿）**
- P1 统一分页原语 + keyset 入口（已新增 `JooqWriters.page` size/offset 钳制 + `JooqKeysetPager`）。
- P2 HikariCP 连接池。
- P3 导出 `MAX_ROWS` 受控物化。
- **P6 缓存防雪崩** — TTL 随机抖动 + `LoggingCacheErrorHandler` 读写降级，已闭环（P1-3）。

**部分达标（黄，中-高）**
- **P4 深翻页 + role/org 全量加载** — keyset 能力已具备且被 `LoginLogService`/`FileService`/`AuditLogServiceImpl` 采用；但**核心主数据列表未迁移**：`UserService.list` 仍 `JooqWriters.page`（OFFSET，:356）、`MessageService` 仍 OFFSET（:223/:346）；`RoleService.list()` 仍 `fetchList(ROLE)` 全量（:106-107）、`OrgService` 仍 `fetchList(ORG)` 全量（:70/:231）。原"role/org 全量无分页"差距**未消除**。
- **P7 熔断能力** — 自研 `CircuitBreaker` 已落地并被通知/登录日志/文件/审计采用（P1-6 已闭环于部分路径），但 DB/Redis/外部通道未系统性包裹，与"全面熔断/舱壁"标准仍有差距。

### 2.3 稳定性（Stability）
**达标（绿）**
- ST1 优雅停机；ST2 Flyway 治理；ST3 统一重试退避。
- **ST4 通知真异步化 + 上界 + 熔断** — 已闭环（P0-3）：`dispatchAsync` 经 `notificationExecutor` 异步、`orTimeout` 上界、`notificationDispatchBreaker` 熔断、`clampAttempts` 钳制 1..10。

**部分达标（黄，中）**
- **ST5 N+1 仅检测不阻断** — `NPlusOneProbe`/`NPlusOneListener` 仅采样上报，无 CI 门禁拦截（**未被本次整改覆盖**，沿用 V1.0.7 结论）。

### 2.4 功能完整性（Functional Completeness）
**达标（绿）**
- F1 核心能力落地；F2 契约自洽；F3 导出物化受控。

**部分达标（黄，中）**
- **F4 占位/启发式模块** — `ai/im/cms/approval/plugin/codegen` 部分仍为占位实现，单测少且无独立集成测试（**未被本次整改覆盖**）。
- **F5 云模型接入占位** — `CloudLlmProvider` 默认关闭、仍为占位异常（设计可接受，但视为"功能未就绪"）。

### 2.5 兼容性 / 可运维性（Compatibility / Operability）
**达标（绿）**
- C1 MySQL 8.x + utf8mb4 迁移可重放；C2 多数据源原语。
- **C3 JDK 一致** — `maven.compiler.release=17` 已锁定（P1-7 闭环）。
- **C4 collation 固化** — `utf8mb4_0900_ai_ci` + 部署文档硬约束（P2-1 闭环）。
- **C5 部署制品** — Dockerfile / docker-compose / systemd / 启停脚本（P1-8 闭环）。
- **C7 API 版本段** — `context-path=/api/v1`（P2-2 闭环）。
- **C8 文档模块数** — 统一 17（P2-3 闭环）。

**部分达标（黄，中-低）**
- **C6 日志结构化 + 采集** — JSON 日志（`JsonLogEncoder`）已落地、OTLP 经环境变量可开启；但 `docker-compose` 中 `OBSERVABILITY_OTLP_ENABLED=true` 指向 `localhost:4318`，而参考编排**未包含 OpenTelemetry Collector**（仅 app/mysql/prometheus），生产链路实际无采集端。
- **C9 Spring Boot 补丁 + SCA** — 已新增 OWASP `sca` profile（`failBuildOnCVSS=9`、suppression 清单），但离线环境未含新版 SB 补丁、且 SCA 默认不激活、从未实际运行（P2-4 部分为"门禁就绪但未执行"）。

---

## 3. 不达标 / 部分达标项问题说明（证据 + 差距程度）

### 3.1 P4（部分达标，中-高）—— 主数据列表深翻页与全量加载残留
- **问题**：整改仅补齐了 keyset 入口与 `page` 的 size/offset 钳制，但**核心主数据列表端点未迁移**——
  - `UserService.list`：`JooqWriters.page(dsl, USER, ...)`（OFFSET 分页，:356）。
  - `MessageService`：`.limit(q.limit()).offset(q.offset())`（:223）与 `JooqWriters.page`（:346）均为 OFFSET。
  - `RoleService.list()`：`JooqWriters.fetchList(dsl, ROLE, ...)` **无 LIMIT 全量加载**（:106-107）。
  - `OrgService`：`fetchList(dsl, ORG, ...)` **无 LIMIT 全量加载**（:70、:231）。
- **差距程度**：中-高。`user`/`message` 在百万级深翻页时 OFFSET 仍线性退化；`role`/`org` 在数据增长后一次性全量加载，存在内存与响应时间风险。
- **证据**：`scaffold-system/.../service/UserService.java:356`、`MessageService.java:223,346`、`RoleService.java:106-107`、`OrgService.java:70,231`。

### 3.2 P7（部分达标，中）—— 熔断覆盖面不全
- **问题**：自研 `CircuitBreaker` 已被 `NotificationService`/`LoginLogService`/`FileService`/`AuditLogServiceImpl` 采用，但**读多写少的核心服务调用 DB/Redis 与外部通道（短信/邮件/IM/低代码/工作流引擎）未系统性包裹**，缺舱壁（bulkhead）与超时防护。
- **差距程度**：中。局部具备能力，但"全面熔断/舱壁"标准未达成，单点下游故障仍可能级联。
- **证据**：`grep -l "CircuitBreaker" **/src/main/**/*.java` 命中 6 个文件，均集中在通知/日志/文件/审计路径。

### 3.3 C6（部分达标，中-低）—— OTLP 无 Collector
- **问题**：`docker-compose.yml` 设 `OBSERVABILITY_OTLP_ENABLED="true"`、`OTLP_ENDPOINT=${OTLP_ENDPOINT:-http://localhost:4318/v1/traces}`，但参考编排未部署 OpenTelemetry Collector；容器内 `localhost:4318` 不可达，span 实际导出失败/丢弃，集中观测链路未打通。
- **差距程度**：中-低（结构化日志已具备，仅缺 trace 汇聚端）。
- **证据**：`deploy/docker-compose.yml:16-18,50-56`（仅 app/mysql/prometheus 三服务）。

### 3.4 C9（部分达标，低-中）—— SCA 未实际运行
- **问题**：OWASP `sca` profile 已定义，但默认不激活、离线仓库无新版 SB 补丁，CI 未配置该阶段，故**从未实际执行依赖漏洞扫描**；"发布前阻断高危 CVE"标准未达成。
- **差距程度**：低-中。
- **证据**：`pom.xml` `sca` profile（默认不激活）；`scripts/dependency-check-suppressions.xml`（占位抑制）。

### 3.5 S10（部分达标，中）—— 限流默认仍 fail-open
- **问题**：`RateLimitProperties.failClosed` 开关与 `DenyRateLimiter` 已提供，但**默认语义仍为 Redis 不可用时回落本地计数**（分布式下等效限流失效），需在部署侧显式开启 `fail-closed` 方为真正达标。
- **差距程度**：中。
- **证据**：`scaffold-security/.../ratelimit/RateLimitConfig.java`、`RateLimitProperties.java`。

### 3.6 F4 / F5 / ST5（部分达标，沿用 V1.0.7，未被本次整改覆盖）
- **F4**：`ai/im/cms/approval/plugin/codegen` 部分占位/启发式，单测薄弱、无独立 IT。差距中。
- **F5**：`CloudLlmProvider` 云模型接入仍为占位异常（默认关闭，可接受但"功能未就绪"）。差距低。
- **ST5**：`NPlusOneProbe`/`NPlusOneListener` 仅采样上报，无 CI 门禁拦截。差距中。

---

## 4. 对应的优化措施（可落地修改建议）

### 4.1 P4 — 主数据列表 keyset 化 + role/org 分页（建议首版内闭环）
- `UserService.list` / `MessageService` 列表端点改为 `JooqKeysetPager.query`（游标），排序键服务端固定并经 `CursorCodec` 不透明编码；取 `size+1` 判定 `hasNext`，避免深翻页 OFFSET 退化。
- `RoleService.list()` / `OrgService` 全量 `fetchList` 增加 `LIMIT`（如 `JooqWriters.fetchList(..., limit)` 或显式 `.limit(maxRows)`）；若需树形展示，分页返回顶层 + 懒加载子节点，避免一次性全量物化。
- 对"机构树"等必须全量的场景，保留 `ORG_TREE_CACHE` 但增加 TTL 抖动与失效回源保护（与 P6 防雪崩一致）。

### 4.2 P7 — 熔断覆盖面扩展
- 以 `CircuitBreaker` 包裹外部通道调用：`SmsChannel`/`EmailChannel`/`ImChannel` 派发、低代码/工作流引擎远程调用；为每个外部依赖配置独立失败阈值与半开探测。
- 对 DB/Redis 高扇出读路径引入舱壁（有界线程池隔离），避免单依赖耗尽全局线程。

### 4.3 C6 — 补齐 OTLP Collector
- 在 `docker-compose.yml` 增加 `otel-collector` 服务（如 `otel/opentelemetry-collector-contrib`），将 `OTLP_ENDPOINT` 指向 `http://otel-collector:4318/v1/traces`，并对接 Prometheus/后端；使"开启即有用"。
- 或将 trace 导出改为对接既有 Prometheus/Grafana Tempo，闭环集中观测。

### 4.4 C9 — 实际运行 SCA
- CI 增加 `mvn -Psca verify` 阶段（依赖联网或离线缓存 dependency-check 数据库），对 CVSS≥9 失败阻断；离线环境预置 NVD 缓存以可复现。
- 待离线仓库纳入 Spring Boot 补丁版后，升级并复跑 SCA，更新 suppression 清单。

### 4.5 S10 — 限流默认策略明确化
- 在 `application.yml` 显式声明 `app.ratelimit.fail-closed=true`（或文档标注生产必须开启）；部署检查脚本校验分布式部署下该项不为 false。

### 4.6 F4 / F5 / ST5 — 占位模块与 N+1（后续迭代）
- 为 `ai/im/cms/approval/plugin/codegen` 补最小可用实现 + 独立集成测试（至少 Happy Path IT），或将明确标注为"实验性/非 GA"并关闭对应端点暴露。
- `CloudLlmProvider` 提供真实最小接入或保留占位但明确返回 501，避免静默异常。
- `NPlusOneProbe` 接入 CI 门禁：单次请求 N+1 阈值超限即失败（或至少告警阻断合并请求）。

---

## 5. 发布决策建议

- **总评：Conditional Go（准发布）**。
- **已闭环（P0 全绿，可发布）**：P0-1 数据权限 fail-closed、P0-2 Webhook 强制令牌、P0-3 通知真异步+上界+熔断。
- **首版内建议闭环（P1 部分达标）**：P4（user/message/tenant 列表 keyset 化 + role/org 分页）、P7（熔断覆盖面）、C6（OTLP Collector）、C9（实际运行 SCA）、S10（限流默认 fail-closed 显式化）。
- **后续迭代**：F4/F5/ST5 占位模块与 N+1 门禁。
- **复验动作**：完成上述首版项后，重跑双门禁（`mvn -o clean test` + `scripts/check-conventions.py`），并回填本报告形成"标准-评估-整改"闭环。

---

## 6. 与 V1.0.7 的差异对照

| 原评估项 | V1.0.7 状态 | V1.1.0 状态 | 变化说明 |
|---|---|---|---|
| P0-1 / P0-2 / P0-3 | 未达标 | **达标** | 三 P0 全闭环 |
| P1-1 / P1-3 / P1-4(开关) / P1-5 / P1-7 / P1-8 / P2-1 / P2-2 / P2-3 | 部分/未达标 | **达标** | 已落地 |
| P1-2 | 未达标 | **部分达标** | 入口补齐+部分端点采用，核心主数据列表未迁移、role/org 仍全量 |
| P1-6 | 未达标 | **部分达标** | 自研熔断落地+多路径采用，覆盖面不全 |
| P1-9 | 未达标 | **部分达标** | JSON 日志达标，OTLP 缺 Collector |
| P2-4 | 未达标 | **部分达标** | SCA profile 就绪但未执行、无 SB 补丁 |
| F4 / F5 / ST5 | 部分达标 | 部分达标（不变） | 未被本次整改纳入 |
