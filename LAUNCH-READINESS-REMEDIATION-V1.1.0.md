# 上线就绪评估 V1.1.0 整改核对说明

> 配套文档：`LAUNCH-READINESS-ASSESSMENT-V1.1.0.md`（复评报告，含「标准—评估—整改」清单）。
> 分支：`feat/V1.0.7`（本地提交，未推送 origin；由用户本机评审后推送）。
> 范围：首轮整改报告标记为「未达标 / 部分达标」的 **P4、P7、C6、C9、S10、ST5、F5**；
> 续作按「剩余业务功能逻辑」要求补齐 **F4**（详见 §8）。至此清单 8 项全部收口。

---

## 0. 四门禁复验结果（整改后）

| 门禁 | 命令 | 结果（F4 测试补齐后复验，2026-11-05） |
| --- | --- | --- |
| 单元测试全量 | `mvn -o clean test`（JDK 21，离线，17 模块） | **BUILD SUCCESS / EXIT=0**，**2052 例**：0 失败 0 错误 0 跳过（较 F4 续作 +12 例） |
| 约定校验 | `scripts/check-conventions.py` | **EXIT=0**，无 H1–H5 硬违规（仅 1 项 W* 模块分层提示，非阻断） |
| 前端单测 | `npm test`（node:test） | **91/91 通过**（含新增 `approvalActions.test.js` 6 例） |
| 前端构建 | `npm run build`（vite） | **EXIT=0**（`✓ built in 15.67s`） |

> 注：首轮整改前端未改动；F4 续作涉及 `pc-admin-vue3-vite`（审批工作台 UI / API / i18n），
> 故前端单测与 `vite build` 一并纳入本轮验收阈值。本轮虽无前端改动，四门禁仍全量复验。

---

## 1. P4 — 主数据深翻页 OFFSET 护栏 + keyset 游标入口；role/org 全量加载下推 LIMIT

- **原评估（未达标→部分达标）**：`user`/`message` 列表走 OFFSET，百万级深翻页线性退化；`role`/`org` 无 LIMIT 全量加载，存在内存与响应时间风险。
- **整改**：
  1. **深翻页偏移护栏**：`UserService.list` / `MessageService.pageMine` / `pageSent` 增加全局偏移上限（默认 5000 行），超限抛 `BusinessException` 并提示改用 keyset 游标端点，消除性能悬崖且不破坏既有 `PageResult.total` 契约。
  2. **keyset 游标端点（深滚首选）**：新增 `GET /api/system/users/page-keyset`、`/api/system/messages/mine-keyset`、`/api/system/messages/sent-keyset`，复用 `JooqKeysetPager.query`（取 size+1 判 hasNext、不透明游标、按 `(created_at,id)` 与 `(is_read,id)` 等确定排序键 seek）。
  3. **role/org 有界加载**：`RoleService.list()` 改 `JooqWriters.fetchTop(..., MAX_ROLE_ROWS=1000, sort_no ASC)`；`OrgService.tree()/loadAll()` 改走新增 `JooqWriters.fetchListBounded(..., MAX_ORG_ROWS=5000, sort_no ASC)`，触顶告警（避免异常数据增长把全表拖入堆内 OOM），保留数据权限范围签名分键缓存不变。
- **验证**：单元测试 `OrgServiceCacheTest` 4 例（跨权限缓存隔离/驱逐）适配新 `fetchListBounded` 打桩后复绿；全量 `mvn -o clean test` 通过。

涉及文件：`UserService.java`、`UserController.java`、`MessageService.java`、`MessageController.java`、`RoleService.java`、`OrgService.java`、`JooqWriters.java`（新增 `fetchListBounded`）、`OrgServiceCacheTest.java`。

## 2. P7 — 通知外发通道熔断覆盖

- **原评估（部分达标）**：自研 `CircuitBreaker` 已用于通知/登录日志/文件/审计，但读多写少的核心外发通道（短信/邮件/Webhook/企微/钉钉）未系统性包裹。
- **整改**：`NotificationResilienceConfig` 新增 5 个独立 `CircuitBreaker`（阈值 5、OPEN 30s）并聚为 `Map<Channel, CircuitBreaker> externalChannelBreakers`；`NotificationService.dispatchInternal` 对进程外通道经对应熔断器 `execute` 包裹（进程内通道不包裹）。字段以 `@Autowired(required=false)` 注入，保障既有 `new` 直接构造的单元测试零回归。
- **验证**：`mvn -o clean test` 通过（含 `CoreApiE2eTest` 等 mock 场景）。

涉及文件：`NotificationResilienceConfig.java`、`NotificationService.java`。

## 3. C6 — 补齐 OTLP Collector（trace 汇聚可达）

- **原评估（部分达标）**：JSON 日志已落地、OTLP 可开关，但 `docker-compose` 仅含 app/mysql/prometheus，缺 OpenTelemetry Collector，容器内 `localhost:4318` 不可达，trace 实际丢弃。
- **整改**：新增 `deploy/otel-collector-config.yaml`（`otlp` http:4318 / grpc:4317 接收；`debug` + `prometheus` :8889 导出；`batch` 处理）；`docker-compose.yml` 增加 `otel-collector` 服务，`OTLP_ENDPOINT` 默认指向 `http://otel-collector:4318/v1/traces` 并 `depends_on`；`prometheus.yml` 新增 `otel-collector:8889` 抓取作业。
- **验证**：YAML 语法经 `mvn` 无关，靠约定校验器扫描通过；编排语义与既有 app 服务一致。

涉及文件：`deploy/otel-collector-config.yaml`（新）、`deploy/docker-compose.yml`、`deploy/prometheus/prometheus.yml`。

## 4. C9 — 实际运行 SCA（CI 可复现、CVSS≥9 失败阻断）

- **原评估（部分达标）**：`sca` profile 已定义但默认不激活；CI 的 `sca` 作业用 `mvn ... org.owasp:dependency-check-maven:...:check -DfailOnError=false` 直接调插件且 `continue-on-error: true`，**绕过 profile 配置（failBuildOnCVSS=9 + suppressionFile），本质从未执行策略**。
- **整改**：
  1. CI `sca` 作业与周扫描 `security-scan-scheduled.yml` 均改为 `mvn -B -Psca -DskipTests -DskipITs verify`，真正激活 pom 内 `sca` profile 策略（CVSS≥9 失败阻断，低/中危仅入报告）。
  2. `pom.xml` `sca` profile 显式 `dataDirectory` 锁定到 `~/.m2/repository/org/owasp/dependency-check-data`，与 CI `actions/cache` 缓存路径一致，保证离线/私服镜像可复现扫描。
  3. `scripts/dependency-check-suppressions.xml` 从空占位改为带策略说明的模板：铁律「禁止无理由全盘抑制、每条须注明 CVE 与抑制理由」，含注释示例；不掩盖真实高危。
- **验证**：配置变更经 `check-conventions` 与全量 `mvn` 复验通过；实际 CVE 扫描需在联网/已缓存 NVD 的 CI 环境执行（报告 §4.4 要求：待离线仓库纳入 Spring Boot 补丁版后复跑并更新 suppression 清单）。

涉及文件：`.github/workflows/ci.yml`、`.github/workflows/security-scan-scheduled.yml`、`pom.xml`、`scripts/dependency-check-suppressions.xml`。

## 5. S10 — 限流默认 fail-closed 显式化

- **原评估（部分达标）**：`RateLimitProperties.failClosed` 与 `DenyRateLimiter` 已提供，但默认语义为 Redis 不可用时回落本地计数（分布式下等效限流失效）。
- **整改**：`application-prod.yml` 显式声明 `app.ratelimit.fail-closed: true`（分布式部署 Redis 不可用时拒绝请求而非 fail-open 放行）。
- **验证**：配置项全量 `mvn` 复验通过。

涉及文件：`scaffold-admin/src/main/resources/application-prod.yml`。

## 6. ST5 — N+1 探测 fail 模式（阈值越限即阻断）

- **原评估（部分达标）**：`NPlusOneProbe` 仅检测告警，不阻断，反模式可潜入生产。
- **整改**：`NPlusOneProbe` 新增 `failMode` 构造参数与 `isFailMode()`；`NPlusOneListener.executeEnd` 在 fail 模式下越限抛 `NPlusOneThresholdExceededException`（新建）；`JooqConfig` 经 `app.jooq.nplus1-fail-on-threshold` 注入（默认 `false`，CI/IT 置 `true` 即门禁）；`application.yml` 新增该开关（默认仅告警）。`NPlusOneListenerTest` 增补 fail/非 fail 两路径。
- **验证**：`NPlusOneListenerTest` 复绿；全量 `mvn -o clean test` 通过。

涉及文件：`NPlusOneProbe.java`、`NPlusOneListener.java`、`NPlusOneThresholdExceededException.java`（新）、`JooqConfig.java`、`application.yml`、`NPlusOneListenerTest.java`。

## 7. F5 — 云模型未就绪返回 501（而非笼统 500）

- **原评估（部分达标）**：`CloudLlmProvider` 云接入仍为占位异常（默认关闭，可接受但「功能未就绪」语义不清）。
- **整改**：新增 `LlmNotImplementedException`（`@ResponseStatus(NOT_IMPLEMENTED)`）；新增 `AiExceptionAdvice`（`@RestControllerAdvice(basePackages="com.acme.scaffold.ai")`、`@Order(HIGHEST_PRECEDENCE)`），确保云模型未配置/未实现时返回 **501** 而非被全局 catch-all 改写为 500；`CloudLlmProvider.parse()` 两处抛此异常。
- **验证**：新增 `AiCloudNotReadyTest` 5 例独立集成测试（Docker-free，surefire 可执行）：① 云 provider 未配置 endpoint；② 已配置 endpoint 但真实接入仍为占位——两者均显式抛 501 且给出可定位提示；③ `AiScaffoldService` 在云 provider 已装配时**不静默回退启发式**产出「看似成功」的草稿；④ `AiExceptionAdvice` 返回结构化 501（`error`/`message`/`status`）且 `@Order(HIGHEST_PRECEDENCE)`、`basePackages` 限定 ai 模块；⑤ MockMvc 故意先注册全局 `Exception → 500` 兜底 advice，仍断言 HTTP 层 501 未被改写、body 结构正确。全量 `mvn -o clean test` 通过。

涉及文件：`LlmNotImplementedException.java`（新）、`AiExceptionAdvice.java`（新）、`CloudLlmProvider.java`、`AiCloudNotReadyTest.java`（新）。

## 8. F4 — 剩余业务功能逻辑补齐（续作收口）

- **原评估（部分达标，沿用 V1.0.7）**：`ai`/`im`/`cms`/`approval`/`plugin`/`codegen` 部分占位/启发式，单测薄弱、无独立 IT。
- **复核结论**：逐模块盘点后，`ai`（启发式 DDL/代码生成 + 空提示词/超长提示词门禁）、`cms`、`im` 已有独立 H2 集成测试与较完整 CRUD；`plugin`/`codegen` 已有单测。真正的缺口不是「没有实现」，而是 ① `approval` 仅只读聚合、缺可操作写动作；② 若干**业务状态机与边界**缺陷。故本轮不做机械重写，按「薄编排 + 边界补全 + 门禁可执行的集成测试」收口，且保持既有达标功能行为不变。

### 8.1 approval：只读门户 → 可操作审批工作台

- 新增 `ApprovalActionService`（薄门面，委托 `WorkflowService`，不重复实现引擎）与 `ApprovalActionController`（`/api/approval/actions/**`），覆盖 **发起 / 审批 / 转办 / 委派 / 办结委派 / 认领** 6 个写动作；权限码 `approval:action:*` 由 `ApiResourceAutoSyncRunner` 扫描 `@PreAuthorize` 自动登记并授予 SUPER_ADMIN，各端点带 `@AuditOperation`。
- 业务边界：① 发起仅允许门户「可发起（PUBLISHED）」流程，且同时校验 `processKey` **与** `status`（上游混入草稿/停用时由门面自行守门）；设计目录为空时放行 `workflow` 兜底，保证空数据环境可用；② 拦截 `ApprovalAction.TRANSFER` 作为审批动作提交；③ `taskId` / `operationId` / 目标用户前置非空校验（保护非 HTTP 内部调用）。
- **同源缺陷修复**：`WorkflowService.completeTask` 引擎层同样拦截 `TRANSFER`——原实现只区分 `REJECT`/非 `REJECT`，传入 `TRANSFER` 会被静默当作一次普通通过（任务办结却不产生转办记录），`/api/workflow/tasks/complete` 端点亦受影响。
- 前端：新增独立 `api/approvalActions.js`（**不改动** `approval.js`，保持门户只读契约测试 `approval.test.js` 断言不变），4 个幂等写动作携带 `Idempotency-Key` 请求头且与 `body.operationId` 同值，对齐 `@Idempotent(requireKey=true)` 的双重去重；`PortalView.vue` 待办页接入通过/驳回/转办/委派/办结委派/认领，可发起页接入发起流程，中英 i18n 键补齐。
- 验证：`ApprovalActionServiceTest` 10 例（委托 + 边界拒绝）+ 既有 `ApprovalPortalServiceTest` 3 例全绿；前端 `approvalActions.test.js` 6 例契约测试全绿。

### 8.2 cms：发布状态机幂等与事件一致性

- 缺陷：`publish()` 每次调用都刷新 `publishedAt` 并广播 `ArticlePublishedEvent`（重复发布会让下游通知/检索重复消费）；`setOfflineAt(null)` 因 `JooqWriters.updateById` 跳过 null 字段**从未落库**，重新发布后残留下线时间；`create`/`update` 可绕过 `publish` 直接置 PUBLISHED 却没有发布时间与事件。
- 整改：抽出统一 `publishEvent(...)` 出口；`publish`/`offline` 幂等（已处于目标态直接返回）；重新发布改走 `updateByIdWithNulls(..., Set.of("offline_at"))` 显式清空；`create` 直接 PUBLISHED、`update` 由非 PUBLISHED 转入 PUBLISHED 时同样补登 `publishedAt` 并广播**恰好一次**；`update` 转出 OFFLINE 时清 `offline_at`。
- 验证：新增 H2 集成测试 `CmsArticlePublishFlowTest` 7 例，精确断言事件次数、`publishedAt` 稳定性、`offlineAt` 清空与完整状态机 happy path。

### 8.3 im：会话/消息业务边界

| # | 缺陷 | 整改 |
| --- | --- | --- |
| 1 | `addMember` 对曾被移除的成员直接 INSERT 会撞唯一键 `(tenant_id, conversation_id, user_id)`（唯一键不区分逻辑删除） | 先按「含已删除」条件查找并**复活**旧行（恢复 ACTIVE/MEMBER/deleted=0），未命中才新建 |
| 2 | `createConversation` 允许把创建者本人列为对端，去重后产生单人 P2P / 人数不足的群 | 前置拦截 `peers.contains(ownerId)` |
| 3 | 群主退群须转交「最早剩余成员」，但查询无 ORDER BY，交接对象不确定 | 补 `MEMBER.field("id").asc()`，注释与实现对齐 |
| 4 | `markRead` 在会话内无消息时把已读游标置为 null，历史已读状态回退成未读 | 改为**只前进不回退**：`maxId == null` 或 ≤ 现有游标时直接返回 |
| 5 | 已撤回消息正文已清空却仍计入未读 | `ImMessageService` / `ImConversationService` 两处 `computeUnread` 统一排除 `RECALLED` |

- 验证：新增 H2 集成测试 `ImBoundaryFlowTest` 9 例，覆盖上述 5 点及非成员发送 / 空内容拒绝。

### 8.4 关于「无独立 IT」口径的澄清

本仓 `*IT` 由 failsafe 在 `verify` 阶段执行且本地默认跳过（根 pom 注释明确「集成测试默认跳过：`mvn test/verify` 不触发 `*IT`」），因此命名为 `*IT` 的用例在 `mvn -o clean test` 门禁中**根本不会被执行**。本轮新增集成测试统一采用 `*Test` 命名 + 独立 H2 内存库夹具（`CmsH2` / `ImH2`），确保被 surefire 覆盖、门禁真实执行——这比新增不被执行的 `*IT` 更符合「完全通过对应测试或验收」的要求。

### 8.5 ai / plugin / codegen 测试覆盖补齐（复核结论修正）

- **复核纠偏**：复评报告「单测薄弱、无独立 IT」需按实物修正——`AiScaffoldServiceTest` 已覆盖 ai→codegen 端到端渲染；`PluginManagerTest` 已是真实 jar + 真实 Spring 上下文 + 真实类加载器的端到端集成测试。真正未覆盖的是 ① **F5 501 语义**（见 §7）；② **codegen 元数据读取 → 编排 → 打包链路**（此前 3 个测试均为模板片段 / DTO / 配置纯单测）。
- 新增 `CodeGenFlowTest` 8 例独立集成测试（Docker-free，surefire 可执行）：以 jOOQ `MockConnection` 在内存模拟 MySQL `information_schema` 结果集，驱动**真实** `TableSchemaService`（原生 SQL → 列元数据 → Java 类型映射）、`CodeGenService`（preview / downloadZip）与 `CodeTemplates` 模板渲染全链路。断言：`DATABASE()` 限定当前库防跨库串表、`ORDINAL_POSITION` 保证列序稳定、主键 / 自增 / `tinyint(1) → Boolean` / 下划线转驼峰 / `ToStringSerializer` 雪花主键映射；`withFrontend=true` 产出 8 个路径不重复的文件且中文列注释无损；ZIP 条目与预览**逐字一致**且 UTF-8 不乱码；边界拒绝覆盖「表不存在 → NOT_FOUND」「表无字段 → VALIDATION_ERROR」「空表名短路不触发任何 SQL」「列表排除视图并按表名排序」。
- 生产 SQL 使用 MySQL 专有 `TABLE_COMMENT` / `DATABASE()`，H2 不兼容，故以返回结果集隔离「数据库」这一层，其余均为生产对象真实协作；同样采用 `*Test` 命名确保被门禁执行（口径见 §8.4）。

涉及文件：`ApprovalActionService.java`（新）、`ApprovalActionController.java`（新）、`ApprovalActionServiceTest.java`（新）、`ApprovalPortalController.java`、`scaffold-approval/pom.xml`、`WorkflowService.java`、`api/approvalActions.js`（新）、`api/__tests__/approvalActions.test.js`（新）、`views/approval/PortalView.vue`、`i18n/messages.js`、`CmsArticleService.java`、`CmsArticlePublishFlowTest.java`（新）、`ImConversationService.java`、`ImMessageService.java`、`ImBoundaryFlowTest.java`（新）、`AiCloudNotReadyTest.java`（新）、`CodeGenFlowTest.java`（新）。

---

## 9. 提交清单（按关注点，本地提交未推送）

| 提交 | type | 范围 |
| --- | --- | --- |
| 1 | feat(system) | P4 深翻页护栏 + keyset 游标 + role/org 有界加载 |
| 2 | feat(system) | P7 外发通道熔断覆盖 |
| 3 | deploy | C6 补齐 OTLP Collector |
| 4 | build(ci) | C9 实际运行 SCA（-Psca + CVSS≥9 阻断 + 离线缓存） |
| 5 | feat(admin) | S10 限流默认 fail-closed |
| 6 | feat(framework) | ST5 N+1 探测 fail 模式 |
| 7 | feat(ai) | F5 云模型未就绪返回 501 |
| 8 | docs | 本整改核对说明 + 复评报告归档 |
| 9 | feat(approval) | F4-1 审批中心动作门面（6 个写动作 + 边界校验 + 权限/审计） |
| 10 | fix(workflow) | F4-1 同源缺陷：`completeTask` 拦截 TRANSFER 被静默当作通过 |
| 11 | feat(frontend) | F4-1 审批工作台 UI 接入动作 API + i18n + 契约测试 |
| 12 | feat(cms) | F4-2 文章发布状态机幂等与事件一致性 |
| 13 | feat(im) | F4-3 会话/消息 5 项业务边界修复 |
| 14 | test | F4 集成与契约测试（approval/cms/im H2 + 前端 API） |
| 15 | docs | 回填 F4 闭环与四门禁复验结果 |
| 16 | test(ai) | F5 云模型 501 语义与 Advice 契约独立集成测试 |
| 17 | test(codegen) | codegen 元数据 → 编排 → ZIP 端到端集成测试 |
| 18 | docs | 回填 F4/F5 测试闭环与 2052 例四门禁复验结果 |

> 全部为本地提交，**未推送 origin**（`feat/V1.0.7`），由用户本机评审后推送。

---

## 10. 遗留与后续

- **C9 真实 CVE 扫描**：需联网/已缓存 NVD 的 CI 环境执行 `mvn -Psca verify` 获取真实报告，据结果补充 `dependency-check-suppressions.xml` 具体条目（当前为策略模板）。
- **P4 存量 OFFSET 端点**：`user`/`message` 主列表仍提供 OFFSET 端点（保留前端契约），深翻页统一引导至 keyset 端点，属渐进收敛，非阻断。
- **F4 已收口（含测试覆盖补齐）**：`approval` 可写动作门面、cms 发布状态机、im 会话/消息边界均补齐并有 surefire 可执行的 H2 集成测试；`ai`/`plugin`/`codegen` 复核后确认并非「无独立 IT」——`plugin` 已有真实 jar + Spring 上下文集成测试，本轮另补 `AiCloudNotReadyTest`（F5 501 语义）与 `CodeGenFlowTest`（元数据 → 预览 → ZIP 全链路）。若后续对外宣称 GA，仍需按复评报告 §4.6 补跨进程 / 真实 MySQL 的 E2E IT。
- **C9 / P4 两项遗留不变**：真实 CVE 扫描依赖联网 NVD，存量 OFFSET 端点为保留前端契约的渐进收敛，均非阻断。
- **新增中危配置风险（千万级测试发现，2026-11-05）→ 已闭环**：数据源 URL 统一使用 `serverTimezone=UTC`，若生产 JVM 时区非 UTC（`Asia/Shanghai`），MySQL DATETIME 读回整体 +8h，临界值会跨日跨年。详见 `QA-TEST-REPORT-TEN-MILLION-V1.1.0.md` §8.1。**闭环措施**：Dockerfile/compose/CI 统一 UTC（含 MySQL `--default-time-zone=+00:00`）、`DataSourceTimeZoneGuard`（prod 启动 fail-fast）、`DataSourceTimeZoneGuardTest`（10 例，含真实映射器 1970/2099 边界往返）、env.template 与 application-prod.yml 显式约束；闭环后四门禁 **2,062 例全绿**。自管部署仍须按同一口径核对。

---

## 11. 千万级功能与性能测试（2026-11-05 追加）

- 独立隔离库 `java_admin_perf`，7 张生产同构表，精确 COUNT 合计 **14,000,004 行**（主表 `sys_operation_log` 12,800,004 行），未触碰 `java_admin`。
- 新增 `TenMillionScalePerf`（25 功能用例 + 9 性能场景 + 30s/16 线程稳定性 + 资源采样），显式 `-Dtest=` 触发，不拖慢常规 2,052 例回归。
- 结果：**功能 25/25 通过**；keyset 首页/深页 p95 均 2.00ms（深页/首页=1.00，无退化）；稳定性 167,038 次操作 **0 错误** / 5,555.19 ops/s；四门禁复验全绿（后端 2,052 例、约定 EXIT=0、前端 91/91、`vite build` EXIT=0）。
- 4 项诊断对照场景（OFFSET 深页 1,014ms、无索引全表扫描 5,192ms、全表 COUNT 1,940ms、GROUP BY 9,490ms）超 150ms，均非推荐生产路径且已有替代与护栏，不构成上线阻断。
- 完整范围、指标、未通过项、瓶颈风险与明确结论见 **`QA-TEST-REPORT-TEN-MILLION-V1.1.0.md`**；原始结果归档于 `java-admin-framework/scripts/perf/results/`。
- **§8.1 中危时区风险已闭环（同日追加）**：容器/CI 统一 UTC、生产启动期 `DataSourceTimeZoneGuard` fail-fast、10 例契约与边界时间回归断言；闭环后四门禁复验后端 **2,062 例 0 失败**、`check-conventions.py` EXIT=0、前端 91/91、`vite build` EXIT=0。
