# 上线就绪评估报告 — java-admin-framework（V1.0.7）

> 评估依据：以 `QA-TEST-REPORT-V1.0.7.md`（含缺陷清单 F0–F6 与验收结论）为"预定上线标准与验收条件"锚点，叠加四维源码级走查（安全就绪度 / 性能与稳定性 / 功能完整性与代码质量 / 兼容性与可运维性）独立取证。
> 仓库：`outputs/java-admin-framework`，分支 `feat/V1.0.7`，17 个 Maven 模块。
> 门禁现状：双门禁（929+ 单元 + 24 集成，0 失败；`scripts/check-conventions.py` EXIT=0）通过；本地领先 origin = 0（已对齐）。
> 置信度：§3 所列 P0/P1 高风险项均经 `file:line` 独立复验；P2 中低风险项由源码走查得出，已在条目内标注来源。

---

## 1. 五维度总览评分

| 维度 | 就绪度 | 风险等级 | 关键缺口 |
|---|---|---|---|
| 功能完整性 | 部分达标 | 中 | 部分模块（ai/im/cms/approval/plugin/codegen）实为占位/启发式，单测薄弱、独立 IT 缺失 |
| 稳定性 | 部分达标 | 中-高 | 通知重试同步阻塞、多处 fail-open 默认、缺熔断 |
| 性能 | 部分达标 | 中-高 | 深翻页默认 OFFSET 退化、role/org 全量加载、缓存无防雪崩、缺熔断 |
| 安全性 | **未达标（关键短板）** | **高** | 数据权限 fail-open 默认全量可见、告警 webhook 令牌空即匿名、脱敏 record 跳过 |
| 兼容性/可运维性 | 部分达标 | 中 | 构建/运行 JDK 不一致、缺部署制品、日志非结构化、无 API 版本段、文档漂移 |

**总评：Conditional No-Go（有条件不发布）** — P0 项闭环前不建议生产发布。

---

## 2. 功能评估清单（达标 / 部分达标 / 未达标）

### 2.1 安全性（Security）
**达标（绿）**
- S1 SQL 注入防护：全链路 jOOQ 类型安全 DSL，无字符串拼接 SQL。
- S2 认证授权主体：JWT 资源服务器 + `@PreAuthorize` 普遍启用；`TokenVersionVerifier` / `OnlineSessionFilter` / `OAuth2TokenRevocationFilter` 三后置过滤器齐备。
- S3 敏感凭据无硬编码：密钥经环境变量注入（`ALERT_WEBHOOK_TOKEN`、DB 连接串等）。
- S4 统一响应/异常体系：`GlobalExceptionHandler` 全局兜底，无堆栈外泄。
- S5 外发通道默认关闭：AI 云模型 `CloudLlmProvider` 默认 `@ConditionalOnProperty(enabled=false)`，走 `HeuristicLlmProvider` 占位。
- S6 HTTP 客户端超时已配置，无无限等待。

**未达标（红，高）**
- S7 数据权限默认 fail-open 全量可见（§3-P0-1）。
- S8 告警 webhook 令牌空即匿名开放（§3-P0-2）。
- S9 字段级脱敏对 `record` 视图跳过（§3-P1-1）。

**部分达标（黄，中）**
- S10 限流 fail-open 回落本地（§3-P1-4）。

### 2.2 性能（Performance）
**达标（绿）**
- P1 统一分页原语 `JooqWriters.page` 存在；keyset 分页 `JooqKeysetPager` 已实现（可选端点）。
- P2 连接池 HikariCP 已配（默认 maxPoolSize=50）。
- P3 导出 `MAX_ROWS` 受控物化（前序 R2/R3 已闭环）。

**未达标/部分达标（黄-红，中-高）**
- P4 默认列表深翻页走 OFFSET 退化；`role`/`org` 默认 `fetchList` 全量加载无分页（§3-P0-3 / P1-2）。
- P5 通知/导出同步阻塞、缺独立线程池隔离（§3-P0-3 / P1-5）。
- P6 缓存无防雪崩（无 TTL 抖动 / 无 `CacheErrorHandler` / 无本地二级缓存）（§3-P1-3）。
- P7 缺 Resilience4j 熔断/舱壁（§3-P1-6）。

### 2.3 稳定性（Stability）
**达标（绿）**
- ST1 优雅停机已配置。
- ST2 Flyway 版本化迁移治理，禁止改历史脚本。
- ST3 统一重试/退避框架 `DeliveryRetryRules` 存在。

**未达标（红，高）**
- ST4 通知重试为"名义非阻塞"：调用线程仍经 `.get()` 同步阻塞；`maxAttempts` 无上界；无总超时/熔断（§3-P0-3 + §5 复核）。

**部分达标（黄，中）**
- ST5 N+1 仅检测不阻断（`NPlusOneProbe` / `NPlusOneListener` 仅采样上报，无 CI 门禁拦截）。

### 2.4 功能完整性（Functional Completeness）
**达标（绿）**
- F1 RBAC/多租户/数据权限/字段脱敏/通知/文件/工作流/低代码/开放平台/OAuth2 等核心能力已落地。
- F2 契约自洽：无空壳 Controller（走查确认）。
- F3 导出堆内物化 + MAX_ROWS（R2/R3）。

**部分达标（黄，中）**
- F4 模块 `ai`/`im`/`cms`/`approval`/`plugin`/`codegen` 部分为启发式/占位实现，单测少且无独立 IT。
- F5 `CloudLlmProvider` 云模型接入仍为占位异常（设计默认关闭，可接受，但视为"功能未就绪"）。

### 2.5 兼容性 / 可运维性（Compatibility / Operability）
**达标（绿）**
- C1 MySQL 8.x + utf8mb4 表结构完整，Flyway 迁移可重放。
- C2 多数据源/读写分离原语存在。

**部分达标（黄，中）**
- C3 构建 JDK21 / 运行 JDK17 不一致、缺 `maven.compiler.release`（§3-P1-7）。
- C4 MySQL 8.0.4+ 专属 collation `utf8mb4_0900_ai_ci` 锁定（§3-P2-1）。
- C5 无 Dockerfile / K8s / 启动脚本（仅 `deploy/prometheus`）（§3-P1-8）。
- C6 日志纯文本、无 JSON 结构化、无集中采集、OTLP 关闭（§3-P1-9）。
- C7 无 `/api/v1` 版本段（§3-P2-2）。
- C8 文档模块数漂移（ROOT pom 17 个 module，README/ROADMAP 记 12/13）（§3-P2-3）。
- C9 Spring Boot 3.3.4 非最新补丁 + 未跑 SCA 依赖扫描（§3-P2-4）。

---

## 3. 不达标项问题说明（含证据与差距程度）

### P0（高 / 阻断级）

**P0-1 数据权限默认 fail-open 全量可见**
- **问题**：`DefaultDataScopeProvider.resolve` 在 userId 为 null（:38）、用户无角色（:43）、无数据范围规则（:49）、配了机构类规则却解析不出机构（:127）、`scope_type` 非法/空（:136/:142）五种情形一律返回 `DataScopeResult.all()`（可见全部租户/机构数据）。注释标明"故意 fail-open，可见性优先"。
- **差距程度**：高。对多租户或数据敏感的生产场景，任何未显式配置数据范围的用户都将看到全量数据，违背最小权限原则；属"配置缺失即最大暴露面"反模式。
- **证据**：`scaffold-security/src/main/java/com/acme/scaffold/security/permission/DefaultDataScopeProvider.java:38,43,49,127,136,142`。
- **与验收条件差距**：QA 报告未将"数据权限默认策略"列为验收项，属标准缺口；即便按通用安全基线亦应 fail-closed 或租户/本人最小可见。

**P0-2 告警 Webhook 令牌空即匿名开放**
- **问题**：`AlertWebhookController` 仅在 `expected` 非空且非空白时才校验 `X-Alert-Token`（:45-46）；而 `application.yml:342` `token: ${ALERT_WEBHOOK_TOKEN:}` 默认空串、`env.template:60` 亦默认空。令牌未配置时校验被整体跳过，端点匿名可达，外部可伪造告警注入。
- **差距程度**：中-高（取决于是否暴露公网）。默认配置下即存在未授权告警注入风险。
- **证据**：`scaffold-monitor/src/main/java/com/acme/scaffold/monitor/alert/AlertWebhookController.java:45-46`；`scaffold-admin/src/main/resources/application.yml:342`；`scripts/init/env.template:60`。
- **与验收条件差距**：QA 报告未覆盖该匿名端点，属验收盲区。

**P0-3 通知重试为"名义非阻塞" + 无熔断 + maxAttempts 无上界**
- **问题**：`NotificationService.awaitBackoff`（:266-273）经 `notificationRetryScheduler.schedule(() -> {}, ...).get()` 计时——调用线程仍通过 `.get()` **同步阻塞**等待退避完成，专用线程池仅执行空任务，调用方视角仍是同步阻塞；`DeliveryRetryRules.clampAttempts`（:43）仅 `Math.max(1, maxAttempts)`，**只有下界、无上界**；全链路无总超时、无熔断。
- **差距程度**：高。QA 报告（F2）称已"非阻塞修复"，但实证为名义修复，调用线程阻塞语义未变；下游通道长时间不可用时，重试会占用业务/请求线程直至耗尽；`maxAttempts` 若误配极大值将死循环重试。
- **证据**：`scaffold-system/src/main/java/com/acme/scaffold/system/notification/service/NotificationService.java:236-282`；`.../notification/rules/DeliveryRetryRules.java:42-43`；`scaffold-framework/.../config/observability/ObservabilityConfig.java:43-52`。
- **与验收条件差距**：与 QA 报告 F2 结论直接冲突，详见 §5。

### P1（中）

**P1-1 字段级脱敏对 record 视图跳过**
- **问题**：`ColumnPermissionApplier`（:24-25/:119）对 `record` 等不可变类型无法就地反射改写，直接跳过该字段并记 WARN；需对 record 视图改用"构造前脱敏"/Jackson 序列化期脱敏，目前未自动接入。
- **差距程度**：中-高（取决于 VO 是否大量采用 record）。脱敏静默失效。
- **证据**：`scaffold-security/src/main/java/com/acme/scaffold/security/permission/ColumnPermissionApplier.java:24-25,119`。

**P1-2 深翻页默认 OFFSET 退化 + role/org 全量 fetchList**
- **问题**：`JooqWriters.page` 默认 `.limit(size).offset(offset)`（:234/:237），百万级深翻页 OFFSET 线性退化；`RoleService`/`OrgService` 默认 `fetchList` 全量加载无分页（靠"规模小/ORG_TREE_CACHE 兜底"假设）。
- **差距程度**：中-高（数据量增长后触发）。
- **证据**：`scaffold-common/src/main/java/com/acme/scaffold/jooq/dsl/JooqWriters.java:227-237`；`RoleService.java:106-108`；`OrgService.java:70,231`。

**P1-3 缓存无防雪崩**
- **问题**：`CacheConfig` 仅 `entryTtl(固定)` + key 前缀，无 TTL 抖动、无 `CacheErrorHandler`（Redis 异常直接抛）、无本地二级缓存；Redis 不可用时缓存读失败回落 DB（stampede 风险）。
- **差距程度**：中。
- **证据**：`scaffold-framework/src/main/java/com/acme/scaffold/config/cache/CacheConfig.java:57-88`。

**P1-4 限流 fail-open 回落本地**
- **问题**：`RateLimitConfig`（:48/:53）Redis 不可用时返回 `InMemoryRateLimiter` fallback——分布式部署下各实例独立计数，等效于限流失效。
- **差距程度**：中。
- **证据**：`scaffold-security/src/main/java/com/acme/scaffold/security/ratelimit/RateLimitConfig.java:45-56`。

**P1-5 导出/通知同步阻塞、缺独立线程池隔离**
- **问题**：导出与通知投递在请求/业务线程内同步执行，未与 IO/重活隔离到独立有界线程池；与 P0-3 同源。
- **差距程度**：中。

**P1-6 缺 Resilience4j 熔断/舱壁**
- **问题**：全仓无 `resilience4j`/`CircuitBreaker` 依赖（Grep 检索无命中）；下游（DB/Redis/外部通道）故障无熔断保护。
- **差距程度**：中。
- **证据**：全仓 Grep `resilience4j|CircuitBreaker|@CircuitBreaker` 无结果。

**P1-7 构建 JDK21 / 运行 JDK17 不一致、缺 release 标志**
- **问题**：`pom.xml:65-67` `java.version=17` 且 `source/target=17`，但 CI 用 JDK 21 构建，且无 `maven.compiler.release=17`——非 release 模式不锁定 bootstrap classpath，存在偶然引用 JDK21 专属 API 的隐患。
- **差距程度**：中-低。
- **证据**：`pom.xml:65-67`。

**P1-8 无部署制品 / 容器化**
- **问题**：`deploy/` 仅 `prometheus/prometheus.yml`，无 Dockerfile / K8s / systemd / 启动脚本；README 设计为原生运行，但缺可复现部署与回滚方案。
- **差距程度**：中。
- **证据**：`deploy/` 目录仅含 prometheus。

**P1-9 日志非结构化、无集中采集、OTLP 关闭**
- **问题**：`logback-spring.xml:19` 纯文本 `%msg%n`，无 JSON、无 traceId 结构化字段、无采集端；`application.yml` OTLP 默认关闭，根日志 `com.acme.scaffold=DEBUG`。
- **差距程度**：中。
- **证据**：`scaffold-admin/src/main/resources/logback-spring.xml:19`；`application.yml:320,416`。

### P2（低-中）

**P2-1 MySQL 8.0.4+ 专属 collation 锁定**
- **问题**：`schema_ddl.sql` 与全部 Flyway 脚本统一 `utf8mb4_0900_ai_ci`（MySQL 8.0.4+ 专属），无法降级至 8.0.3- 或 MariaDB。
- **差距程度**：低（环境约束明确即可接受）。
- **证据**：`db/reference/schema_ddl.sql`、`db/migration/*` 全量命中。

**P2-2 无 API 版本段**
- **问题**：Controller 未统一 `/api/v1` 前缀，演进期 Breaking Change 难以隔离。
- **差距程度**：低-中。
- **证据**：全仓 Grep `/api/v1` 仅 `application.yml` 命中，控制器无版本段。

**P2-3 文档模块数漂移**
- **问题**：ROOT pom 声明 17 个 `<module>`，README/ROADMAP 记为 12/13，文档与代码不一致。
- **差距程度**：低（但误导新成员/审计）。

**P2-4 Spring Boot 3.3.4 非最新补丁 + 缺 SCA**
- **问题**：主框架版本非最新补丁；无依赖 SCA（如 OWASP Dependency-Check / Trivy）扫描门禁。
- **差距程度**：低-中。

---

## 4. 对应的优化措施（可落地修改建议）

### P0 级（发布前必闭环）

**P0-1 数据权限默认收缩（fail-closed / 最小可见）**
- 新增配置 `app.datascope.default-policy`（枚举 `ALL`/`TENANT`/`SELF`/`NONE`），默认 `SELF` 或 `TENANT`（而非 `ALL`）。
- `DefaultDataScopeProvider` 五种兜底分支改为返回 `DataScopeResult.ofPolicy(defaultPolicy)`；对"配了机构类规则却解析不出机构"场景，运维告警保留，但默认降级为 `SELF` 而非 `ALL`（切换点见 :124 注释）。
- 补充单测覆盖"无角色/无规则/脏 scope_type → 默认最小可见"。

**P0-2 告警 Webhook 强制令牌**
- `AlertWebhookController`：当 `expected` 为空/空白时**拒绝启用端点**（返回 503 或启动时 fail-fast），不再"跳过校验"。
- `env.template` 标注该令牌为**必填**；部署检查脚本校验非空。

**P0-3 通知重试真异步化 + 熔断 + 上界**
- 将 `dispatchWithRetry` 改为返回 `CompletableFuture<Integer>`，退避用 `CompletableFuture.delayedExecutor` 链式 `.thenApply`，调用方不阻塞（或 Reactor `retryWhen` + `TimeoutException` 总超时）。
- `DeliveryRetryRules.clampAttempts` 增加上界 `Math.min(MAX_CAP, Math.max(1, maxAttempts))`（如 `MAX_CAP=10`）。
- 引入 Resilience4j `TimeLimiter` + `CircuitBreaker` 包裹下游 `port.dispatch`，熔断半开探测。
- 调整 `ObservabilityConfig.notificationRetryScheduler` 为独立有界线程池（与业务线程隔离），并补充"重试耗尽 → 死信/告警"路径。

### P1 级（发布前或首版内尽快闭环）

- **P1-1**：对 `record` 视图在序列化层（Jackson `BeanSerializerModifier` 或 DTO 投影）做脱敏，避免依赖反射改写不可变对象；补充 record VO 脱敏单测。
- **P1-2**：`user`/`message`/`tenant` 等默认列表端点改 `JooqKeysetPager`（keyset 游标）；`role`/`org` 全量查询加 `LIMIT` 或显式分页；大数据量表提供 keyset 选项。
- **P1-3**：`CacheConfig` 加 `CacheErrorHandler`（Redis 异常仅记日志、不抛）、TTL 加随机抖动、可选 Caffeine 本地二级缓存；热点 key 用逻辑过期而非物理删除。
- **P1-4**：限流 fallback 在分布式场景改为"Redis 不可用时拒绝服务（fail-closed）"或降级为保守固定阈值，避免完全绕过；配置开关明确语义。
- **P1-5**：导出/通知投递迁移到独立 `TaskExecutor`（有界队列 + 拒绝策略），HTTP 层用 `@Async` 或 `CompletableFuture` 返回，避免占用请求线程。
- **P1-6**：引入 `spring-cloud-starter-circuitbreaker-resilience4j`，对 DB/Redis/外部通道包裹熔断 + 舱壁（`bulkhead`）+ 超时。
- **P1-7**：`pom.xml` 增加 `<maven.compiler.release>17</maven.compiler.release>`，或将 CI 统一为 JDK 17 构建（与运行一致）；消除 source/target 与 release 不一致。
- **P1-8**：补充 `Dockerfile`（多阶段构建、`JAVA_OPTS` 注入）、`docker-compose.yml`/`k8s` 部署清单、`systemd` 单元与启动/回滚脚本；镜像内嵌健康检查。
- **P1-9**：`logback-spring.xml` 改用 `net.logstash.logback` JSON encoder（含 `traceId`/`spanId`/服务名），开启 OTLP exporter，生产环境把 `com.acme.scaffold` 调至 INFO。

### P2 级（后续迭代）

- **P2-1**：在部署文档固化"MySQL ≥ 8.0.4"硬约束；若需兼容更低版本，提供 `utf8mb4_general_ci` 备选迁移脚本。
- **P2-2**：在 API 网关/Controller 统一加 `/api/v1` 前缀（向后兼容可做重定向或双前缀过渡）。
- **P2-3**：校正 README/ROADMAP 模块数为 17，或将模块清单脚本化生成（`mvn help:evaluate -Dexpression=project.modules`）。
- **P2-4**：升级 Spring Boot 至当前补丁版本；CI 增加 OWASP Dependency-Check / Trivy 扫描门禁，阻断高危 CVE。

---

## 5. 复核说明：F2 与 QA 报告结论冲突（#1107）

`QA-TEST-REPORT-V1.0.7.md` 将 F2 标注为"已用 `notificationRetryScheduler` 非阻塞修复"。本次独立复验结论：

- `NotificationService.awaitBackoff`（:271-273）实现为 `notificationRetryScheduler.schedule(() -> {}, Instant.now().plusMillis(millis)).get()`。专用 `ThreadPoolTaskScheduler`（poolSize=2）只执行一个**空任务**，调用线程通过 `ScheduledFuture.get()` **同步阻塞**直至退避结束。换言之，退避计时线程与调用线程分离，但调用线程的阻塞语义**没有改变**——与朴素 `Thread.sleep(backoffMs)` 在调用方视角完全等价。
- 因此 QA 报告"非阻塞修复"为**过度乐观/名义修复**；真正的改进只是"由谁持有等待"，而非"调用方是否等待"。
- 叠加 `clampAttempts` 无上界（:43）与全链路无熔断/总超时，F2 在"下游长时间不可用"场景下仍会拖垮业务线程。

**建议**：QA 报告 F2 结论应更正为"未达标（名义修复）"，并纳入 §4-P0-3 的整改项统一闭环；验收条件应补充"异步化 + 上界 + 熔断"三项硬性指标。

---

## 6. 发布决策建议

- **总评：Conditional No-Go（有条件不发布）**。
- **发布前置（P0 必闭环）**：P0-1 数据权限默认收缩、P0-2 Webhook 强制令牌、P0-3 通知真异步+上界+熔断。
- **首版内闭环（P1）**：至少完成 P1-2（深翻页）、P1-3（缓存防雪崩）、P1-6（熔断）、P1-7（JDK 一致）、P1-8（部署制品）、P1-9（日志结构化）。
- **后续迭代（P2）**：P2-1~P2-4。
- 完成 P0 后建议重跑双门禁 + 补 P0/P1 对应单测与集成测试，并将本报告结论回填 QA 报告，形成"标准-评估-整改"闭环。
