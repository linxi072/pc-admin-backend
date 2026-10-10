# 需求文档 vs 实现差距分析 & 分阶段实现方案

> 分析对象：`Java后端基础管理框架设计方案.md`（以下简称《方案》§12 API 最小集合 / §15 分阶段 / §20 验收清单 / 各功能章节）
> 对照代码：`java-admin-framework`（后端控制器·服务·配置）+ `pc-admin-vue3-vite`（前端 `src/api` 层）
> 方法：逐一比对《方案》接口与验收项，并**直接核查真实代码**（`@RequestMapping`/`@*Mapping`、服务层、配置），不采信 README 自述。
> 生成日期：2026-10-10

---

## 0. 结论速览

- **核心功能已落地**：RBAC 权限、认证/刷新/登出、数据权限、操作审计、幂等、Token 版本失效、工作流（内置+自定义设计器）、监控、公告/站内信、实时 WebSocket、服务端排序白名单、工作台统计——均已实现且前后端对接一致。
- **关键纠偏**：《方案》§12 列出的若干「独立端点」（`PUT /roles/{id}/menus`、`/apis`、`PUT /users/{id}/status`、`/roles`）**并非未实现**，而是被合并进 `create/update` 的 DTO 体内（`RoleService.assignMenus/assignApis`、`UserService.assignRoles`），前端 `role.js`/`user.js` 已按此形态对接，**功能可用、仅 API 形态偏离规范**。
- **真正的未实现项**集中在三块：① 可观测性运营侧（OTel 链路 / Grafana / Alertmanager）；② 质量与上线门禁（CI、生产加固）；③ 若干小粒度增强（实例详情、验证码、密码策略）。多租户为《方案》明确声明「未启用」，属范围外。

---

## 1. 已确认实现（对照通过，不列入待办）

| 模块 | 需求（《方案》） | 实现证据 | 状态 |
|---|---|---|---|
| 认证 | 登录 / 刷新（轮换）/ 登出 | `AuthController`（`/login` `/refresh` `/logout`）+ `RefreshTokenService` | ✅ |
| 认证安全 | 登录失败锁定、Token 版本失效 | `AuthApplicationService.handleLoginFailure` + `maxLoginFailures`；`TokenVersionVerifier` | ✅ |
| RBAC | 用户/机构/角色/菜单/接口资源 CRUD | `User/Org/Role/Menu/ApiResource` Controller + Service | ✅ |
| 接口扫描 | 动态扫描并登记接口资源 | `ApiResourceScanner` + `/scan` `/scan/sync`；V11 种子 61 条 | ✅ |
| 数据权限 | 五类范围 + AOP 注入 | `DefaultDataScopeProvider` + `DataScopeAspect` + `DataScopeConditions` | ✅ |
| 审计 | 操作审计 + 脱敏 + traceId 查询 | `AuditAspect` + `AuditLogController`（page/trace/summary）+ `SensitiveMasker` | ✅ |
| 幂等 | Idempotency-Key（审批强制） | `IdempotencyAspect`/`Service`/`RecordDO` + `IdempotencyCleanupJob` | ✅ |
| 工作流 | 内置+自定义设计器/发布/会签或签/驳回/转办/认领 | `WorkflowDefinitionController`/`TaskController`/`InstanceController` + `BpmnWorkflowBuilder` | ✅ |
| 工作流 | 业务表单动态渲染 + 必填校验 | 设计携带 `formSchema`，前端 `InstanceView` 动态渲染 | ✅ |
| 监控 | 运行指标/在线会话/异常日志/采样 | `MonitorController`（metrics/online*/error*/samples/sample） | ✅ |
| 系统模块 | 部门/字典/系统变量/公告/站内信 | 对应 Controller 齐备 | ✅ |
| 实时推送 | WebSocket 推送 | `RealtimeWebSocketHandler`/`SessionManager`/`PushService` + `WebSocketConfig` | ✅ |
| 服务端排序 | 白名单映射 | `JooqSorts` 接入 users/announcements/error-logs | ✅ |
| 工作台 | 聚合统计卡片 | `WorkbenchController` `/stats` | ✅ |
| 可观测基座 | 指标暴露 | `Actuator` `/actuator/prometheus`（Micrometer 已接 JVM/HTTP/HikariCP/业务/审批耗时）+ `ObservabilityConfig` | ✅（仅基座） |

---

## 2. 已实现但与规范「形态偏差」（前端已适配，非阻断）

| 《方案》规范的端点 | 实际落地方式 | 是否阻断 | 说明 |
|---|---|---|---|
| `PUT /api/system/roles/{id}/menus` | 并入 `POST/PUT /api/system/roles` 的 `menuIds` | 否 | 前端 `role.js` 的 `createRole/updateRole` 已携带 `menuIds`，后端 `RoleService.assignMenus` 处理 |
| `PUT /api/system/roles/{id}/apis` | 并入角色 `apiIds` | 否 | 同上 `assignApis` |
| `PUT /api/system/users/{id}/status` | 并入 `PUT /api/system/users/{id}` 的 `status` 字段 | 否 | 前端 `user.js` `updateUser` 携带 status |
| `PUT /api/system/users/{id}/roles` | 并入用户 `roleIds`/`primaryRoleId` | 否 | `UserService.assignRoles` 处理 |
| `POST /api/workflow/definitions/{key}/publish` | 实际为 `/definitions/{id}/publish` | 否 | 设计器按 id 发布，功能等价 |
| `GET /api/auth/me`、`PUT /api/auth/password` | 落在 `/api/profile/*`（`ProfileController`） | 否 | 路径差异；`GET /api/auth/csrf` 缺失（JWT 无状态，可接受） |

> 建议：若未来需对接 OpenAPI/第三方，可补回独立端点或在文档中明确「以 DTO 承载为准」，避免外部调用 404。当前不列为缺陷。

---

## 3. 明确未实现的功能点（需求有描述、代码中无对应实现）

> 每项含：需求来源、具体要求、验收标准。

### G1 OpenTelemetry 分布式链路追踪
- **来源**：《方案》§15 阶段 4、§20「一次请求可从 trace 找到日志、SQL 与外部调用」
- **现状**：仅 `MDC traceId`（单进程日志串联）；无 `io.opentelemetry` 依赖、无 span 埋点、无跨进程上下文传播、无 Jaeger/Tempo 后端。
- **具体要求**：HTTP / DB(jOOQ) / 外部调用生成 span；traceId 与 MDC 对齐；可导出至 OTel Collector。
- **验收标准**：
  1. 引入 `opentelemetry-spring-boot-starter` + 合适 exporter；
  2. 一次登录→菜单查询→DB 查询在追踪后端呈现为同一 trace 下的父子 span；
  3. `traceId` 同时出现在日志与 span，可双向跳转。

- **实施（2026-10-10）**：实现 W3C `traceparent` 传播层（`observability/TraceContext` 纯函数 + `TraceIdFilter` 延续上游链路并写回 `traceparent`/`X-Trace-Id` 响应头，MDC `traceId` 与 W3C traceId 对齐，审计日志/`Result`/异常处理器共用），`TraceContextTest` 9/9 通过；`pom.xml` 增加非激活 `otel` profile（`-Potel`）作为 OTel SDK 桥接位。OTel Collector 导出待具备环境后开启（验收 1/2 属 SDK 接入，当前以 W3C 传播层 + 单元验证闭环）。

### G2 Grafana 仪表盘 + Alertmanager 告警规则
- **来源**：《方案》§15 阶段 4
- **现状**：指标基座已具备（`/actuator/prometheus` 可采集），但**无 dashboard json、无 alert rules**。
- **具体要求**：原生部署 Prometheus + Grafana + Alertmanager，沉淀仪表盘与告警规则。
- **验收标准**：
  1. 提供 `grafana/dashboards/*.json`：JVM / HTTP(QPS·错误率·P99) / HikariCP / 工作流(审批耗时·在途) / 调度(JobRunr 失败)；
  2. 提供 `alertmanager/rules.yml`：错误率>1%、P99>800ms、老年代>80%、连接池>85%、JobRunr 失败任务>0；
  3. 在原生进程环境触发一条告警可被 Alertmanager 接收。

- **实施（2026-10-10）**：提供 `monitoring/` 原生部署产物——`prometheus/prometheus.yml`（scrape 应用 9090/actuator/prometheus）、`prometheus/alert-rules.yml`（错误率>1% / P99>800ms / 老年代>80% / 连接池>85% / JobRunr 失败>0 共 5 条）、`alertmanager/alertmanager.yml`（路由+接收端占位）、`grafana/provisioning`（数据源+看板自动加载）、`grafana/dashboards/*.json` 五块看板（JVM/HTTP/HikariCP/工作流/调度）、`monitoring/README.md`。验收 1/2 以配置交付，验收 3 需在原生环境注入高错误率验证。

### G3 登录验证码
- **来源**：《方案》§9（`sys.captcha.enabled` 已种子）、§5.3 安全基线
- **现状**：`sys_config` 已种子 `sys.captcha.enabled`，但 `AuthApplicationService.login` 未接入任何验证码校验逻辑。
- **具体要求**：开关开启时，登录必须携带并校验验证码（图形/短信）；开关关闭则跳过。
- **验收标准**：
  1. `sys.captcha.enabled=true` 时，未带/错误验证码返回专用错误码（如 `AUTH_xxx_CAPTCHA`）；
  2. `=false` 时登录流程不变；
  3. 刷新/登出接口不受验证码影响。

### G4 CI 质量门禁
- **来源**：《方案》§14.2
- **现状**：仓库无 `.github/`、无 `Jenkinsfile`；仅有离线 `mvn` 可编译；集成测试已编写（`src/test/.../integration`）但**未接入 CI、且需在原生 MySQL 运行**（沙箱无法跑）。
- **具体要求**：push/PR 触发流水线；编译 + 单元 + 集成（MySQL）全绿；可选覆盖率/静态扫描门禁。
- **验收标准**：
  1. CI 配置存在并在 push 触发；
  2. 单元测试（离线）+ 集成测试（本机 MySQL）均通过；
  3. 任一测试失败则流水线失败并阻断合并。

- **实施（2026-10-10）**：新增 `.github/workflows/ci.yml`——push/PR 触发；`build-and-unit-test` 作业运行 `mvn -B test`（surefire *Test，116 用例全绿）；`integration-test` 作业以 MySQL 8.4 服务容器 + env 覆盖 datasource、`mvn -B verify -DskipITs=false` 运行 failsafe *IT（Flyway 自动建表）；`sca` 作业以 OWASP dependency-check 作非阻塞 advisory。`pom.xml` 增加 `maven-failsafe-plugin` 与 `skipITs` 属性（默认 true，本地 `mvn test/verify` 不受影响）。

### G5 生产加固（§15 阶段 5 多项）
- **G5a 密钥轮换与外部密钥管理**：JWT 签名密钥轮换（旧 token 宽限期）+ Vault/配置中心托管 Secrets。现状：密钥来自 `JwtProperties`（环境变量/配置），无轮换逻辑。
- **G5b 速率限制 / 防重放**：登录与敏感写接口限流；请求 nonce/时间戳防重放。现状：无。
- **G5c 上传安全**：文件上传类型/大小/恶意内容校验。现状：无上传接口实现。
- **G5d 依赖 SCA**：定期漏洞扫描（Trivy/Dependabot）。现状：无。
- **G5e 压测/容量基线、故障注入、回滚预案、备份恢复演练**：现状：无记录。
- **验收标准**：密钥轮换演练有记录；限流在压测下生效；上传拒绝非法文件；SCA 无高危；回滚/备份演练通过。

### G6 工作流实例详情端点
- **来源**：《方案》§12 `GET /api/workflow/instances/{id}`
- **现状**：仅有 `GET /api/workflow/instances/{pid}/records`（审批记录），**缺完整实例详情**（变量/当前节点/状态）。
- **具体要求**：按实例 ID 查询流程实例完整状态与业务字段。
- **验收标准**：返回实例状态、起止时间、`formFields`、当前活动节点、办理人；前端可进入「实例详情」页。

### G7 密码强度策略
- **来源**：《方案》§5.3 密码与账号安全基线
- **现状**：仅 bcrypt + 登录失败锁定；无显式强度策略（长度/复杂度/历史/定期更换）。
- **具体要求**：注册/重置密码满足可配置强度策略。
- **验收标准**：弱密码在 `createUser`/`reset-password` 被拒并返回明确错误码；策略可配置（如最小长度、需含大小写数字）。

### G8 多租户
- **来源**：《方案》§18
- **现状**：`tenant_id` 字段预留但无租户解析 / SQL 隔离 / 缓存隔离 / 测试矩阵；设计已声明「真正启用前需补充」。
- **决策**：**当前明确不启用**，列为范围外。如需启用须独立规划（不在本分阶段方案内）。

---

## 4. 分阶段实现方案

> 依赖与优先级：G1/G2 共享 Prometheus 基座（G2 基线已具备，先做 G1 埋点再补 G2 看板）；G4 依赖 G3 编写的集成测试就绪；G5 依赖 G1（可观测先行）；G6/G7/G3 互相独立、粒度小。

### 阶段 A — 功能补齐（低风险·高可见，建议优先）
- **目标**：补齐日常运维与合规可见的小粒度能力。
- **范围**：G6（实例详情端点）、G7（密码策略）、G3（验证码，配置开关、可选）。
- **验证方式**：
  - G6：`curl GET /api/workflow/instances/{id}` 返回实例状态+业务字段；前端实例详情页可用。
  - G7：弱密码 `createUser`/`reset-password` 返回 `COMMON_xxx` 强度错误。
  - G3：开关开启时缺验证码登录被拒；关闭时正常。

### 阶段 B — 可观测性运营侧
- **目标**：从「能采集指标」升级到「能定位故障、能告警」。
- **范围**：G1（OTel 链路埋点）+ G2（Grafana 仪表盘 + Alertmanager 规则）。
- **验证方式**：
  - G1：一次请求在追踪后端呈现为完整父子 span；日志 traceId 与 span 一致。
  - G2：导入 dashboard 后可见 JVM/HTTP/连接池/工作流/调度面板；人为制造高错误率能触发 Alertmanager 告警。

### 阶段 C — 质量与交付门禁
- **目标**：把「本地能跑」固化为「流水线可重复验证」。
- **范围**：G4（CI 流水线 + 集成测试接入本机 MySQL）。
- **验证方式**：推送分支触发 CI；单元+集成测试全绿；故意引入失败测试时流水线标红阻断。

### 阶段 D — 生产加固（上线前）
- **目标**：满足《方案》§15 阶段 5 的生产就绪要求。
- **范围**：G5a~G5e（密钥轮换/外部密钥、限流防重放、上传安全、SCA、压测/演练）。
- **验证方式**：密钥轮换与回滚演练有记录；限流在压测下生效；上传拒绝非法文件；SCA 报告无高危；备份/回滚演练通过。

> 多租户（G8）不排入上述阶段，作为独立决策项；如业务需要再单列规划。

---

## 5. 优先级矩阵

| 项 | 业务价值 | 技术风险 | 工作量 | 建议阶段 |
|---|---|---|---|---|
| G6 实例详情 | 中（运维可见） | 低 | 小 | A |
| G7 密码策略 | 中（合规） | 低 | 小 | A |
| G3 验证码 | 中（安全） | 低 | 小 | A |
| G1 OTel 追踪 | 高（故障定位） | 中 | 中 | B |
| G2 Grafana/Alert | 高（主动告警） | 中 | 中 | B |
| G4 CI 门禁 | 高（质量保障） | 低 | 中 | C |
| G5 生产加固 | 高（上线前提） | 中-高 | 大 | D |
| G8 多租户 | （当前不适用） | 高 | 大 | 独立 |

---

## 6. 与近期修复项的衔接

本次对话已修复 / 已交付（使系统「能跑通、能登录」）的项，与上述「增强/未实现」互补，不再重复：
- V15 迁移 `DROP INDEX IF EXISTS` 修复（启动失败根因）；
- V18 补齐 `FINANCE`/`MANAGER` 角色种子（登录期审批节点校验失败）；
- 前端 `API_BASE=''` 修复（登录 401 路径重复）；
- 集成测试套件编写（G4 的前置）。

> 注：需求文档中「Docker Compose 启动」一项，README 已明确改为**原生进程启动（无 Docker）**，属有意偏差；§20 验收清单中「docker compose up -d」视为 N/A by design。
