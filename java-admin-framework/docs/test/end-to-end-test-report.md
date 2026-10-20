# 端到端功能测试报告（End-to-End Test Report）

| 项 | 内容 |
| --- | --- |
| 项目 | `java-admin-framework`（Spring Boot 3.3.4 / Java 21，仅后端） |
| 测试日期 | 2026-09-28（沙箱离线执行） |
| 测试形态 | 离线端到端切片（Offline E2E Slice） |
| 测试载体 | `src/test/java/com/acme/scaffold/e2e/CoreApiE2eTest.java`（30 例）+ `RealtimeE2eTest.java`（6 例）+ `KafkaMonitorE2eTest.java`（5 例）+ `ObservabilityE2eTest.java`（6 例）+ `NPlusOneE2eTest.java`（5 例）+ 单元：`KafkaMonitorServiceTest`（4 例）+ `SpanRecorderTest`（10 例）+ `ObservabilityServiceTest`（8 例）+ `ObservabilityPropertiesTest`（3 例）+ `NPlusOneSuspectRegistryTest`（10 例）+ `NPlusOneListenerTest`（4 例）+ `NPlusOneServiceTest`（6 例） |
| 配套文档 | [test-case-specification.md](./test-case-specification.md) · [sequence-diagrams.html](./sequence-diagrams.html) |
| 总体结论 | ✅ **通过** — 800 例单测全绿（含新增 52 例 E2E 与 46 例单元），0 失败 / 0 错误 / 0 跳过 |

---

## 1. 测试概述

依据"覆盖所有核心业务场景与用户操作路径"的要求，本次在沙箱离线环境下构建了一套**离线端到端测试切片**，对系统 8 大核心用户路径（认证授权、系统参数、用户管理、工作流任务、实时推送管理、Kafka 消费监控、可观测性管理、N+1 查询探测）进行全流程验证。测试以 standalone `MockMvc` 装配**真实**的 `Controller`、`GlobalExceptionHandler` 与 `I18nService`，服务层以 Mockito 桩接，从而在不依赖 MySQL 的前提下，真实校验：

1. 请求路由与 HTTP 动词映射；
2. Bean Validation 参数校验与 400 响应；
3. 业务异常 → 错误码（`Result.code`）→ HTTP 状态 的全局映射；
4. i18n 错误消息键解析；
5. `Result<T>` 统一响应包络的形态与空值约定。

---

## 2. 测试范围

| 模块 | 路径前缀 | 用例数 | 说明 |
| --- | --- | --- | --- |
| 认证授权 | `/api/auth` | 9 | 登录 / 刷新 / 改密 / 忘记密码 / 登出（AUTH-01~09） |
| 系统参数 | `/api/system/configs` | 7 | 列表 / 按 key 查询 / 新建 / 更新 / 删除（CFG-01~07） |
| 用户管理 | `/api/system/users` | 9 | 分页 / 查询 / 新建 / 更新 / 重置密码 / 删除（USR-01~09） |
| 工作流任务 | `/api/workflow/tasks` | 5 | 待办 / 认领 / 审批 / 转办（WF-01~05） |
| 实时推送管理 | `/api/realtime` | 6 | 会话列表 / 在线数 / 广播 / 定向推送（RT-01~06） |
| Kafka 消费监控 | `/api/kafka` | 5 | 状态 / 死信列表 / 重试 / 丢弃（KM-01~05） |
| 可观测性管理 | `/api/observability` | 6 | span 回溯 / 按 traceId 检索 / 摘要 / 指标快照 / 清空（OB-01~06） |
| N+1 查询探测 | `/api/system/monitor/n-plus-one` | 5 | 疑似事件列表 / 运行态 / 清空（NP-01~05） |
| **合计** | — | **52** | 正常流程 + 异常/边界 |

未纳入本切片、但已在仓库中存在并执行通过的相邻测试：
- `ConnectivityControllerTest`（15 例，前后端联通契约，含 504 超时链路）；
- 既有单元测试与规则类测试（如 `ClaimRulesTest`、`WorkflowDesignValidatorTest` 等）；
- `*IT` 集成测试（真实 Spring 上下文 + 本机 MySQL + `TestRestTemplate`），沙箱无法运行，需在 CI/本机执行（见 §6）。

---

## 3. 测试方法

### 3.1 离线切片架构
```
Client (MockMvc 请求)
   │
   ▼
[真实] Controller ──► [真实] GlobalExceptionHandler ──► [真实] I18nService
   │
   ▼ (服务层以 Mockito 桩接)
[Mock] Service ── 返回确定性 View / 按用例抛 BusinessException
```
- **MockMvc**：`MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler(i18n())).build()`
- **i18n()**：加载 `classpath:i18n/messages*`、`i18n/validation*`，UTF-8，`fallbackToSystemLocale=false`，与运行时一致。
- **服务桩**：`mock(...)` 精确桩接返回值或异常，保证用例可重复、确定性。

### 3.2 断言三要素
每个用例均断言：
1. **HTTP 状态码**（200 / 400 / 401 / 404 等）；
2. **`Result.code` 业务错误码**（如 `COMMON_001` / `COMMON_404` / `AUTH_001`）；
3. **`Result.data` 形态 / 空值**（成功有值、失败为 `null`）。

### 3.3 异常映射基线
所有模块共享 `GlobalExceptionHandler`：

| 异常 | HTTP | 错误码 |
| --- | --- | --- |
| `MethodArgumentNotValidException` / `HttpMessageNotReadableException` | 400 | `COMMON_001` |
| `BusinessException` | `ec.httpStatus()` | `ec.code()` |
| `AuthenticationException` | 401 | `COMMON_401` |
| `AccessDeniedException` | 403 | `COMMON_403` |
| `DataIntegrityViolationException` | 409 | `COMMON_409` |
| `NoResourceFoundException` | 404 | `COMMON_404` |
| `HttpRequestMethodNotSupportedException` | 405 | `COMMON_405` |
| `TimeoutException` | 504 | `COMMON_504` |
| `Exception` | 500 | `COMMON_500` |

---

## 4. 测试结果

### 4.1 全量单测汇总
```
[INFO] Tests run: 800, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
- 本次新增 E2E：**52 例**（前序 `CoreApiE2eTest` 30 例：Auth 9 + Config 7 + User 9 + Workflow 5；`RealtimeE2eTest` 6 例：RT-01~06；`KafkaMonitorE2eTest` 5 例：KM-01~05；`ObservabilityE2eTest` 6 例：OB-01~06；本轮 `NPlusOneE2eTest` 5 例：NP-01~05），全部通过；
- 本次新增单元：**46 例**（`KafkaMonitorServiceTest` 4 例：消费容器缺失降级与输入校验；`SpanRecorderTest` 10 例：有界缓冲/倒序检索/traceId 聚合/并发安全；`ObservabilityServiceTest` 8 例：limit/status/traceId 校验与指标快照；`ObservabilityPropertiesTest` 3 例：属性类与 `application.yml` recorder 段绑定一致性；`OtlpTracingFilterTest` 由 2 例扩至 3 例：双 sink 开关正交；本轮 `NPlusOneSuspectRegistryTest` 10 例：有界留存/淘汰/累计口径/截断/并发守恒；`NPlusOneListenerTest` 4 例：越限登记/未越限不登记/每轮一次/null 安全；`NPlusOneServiceTest` 6 例：limit 校验/视图映射/状态聚合/清空委托）；
- 既有基线：702 例（含 `ConnectivityControllerTest` 15 例），历次 E2E 增量回归全绿；
- 合计 **800 例，0 失败 / 0 错误 / 0 跳过**。

### 4.2 新增 E2E 用例执行明细
| 嵌套类 | 用例 | 结果 |
| --- | --- | --- |
| `AuthE2e` | AUTH-01~09 | ✅ 9/9 |
| `ConfigE2e` | CFG-01~07 | ✅ 7/7 |
| `UserE2e` | USR-01~09 | ✅ 9/9 |
| `WorkflowE2e` | WF-01~05 | ✅ 5/5 |
| `RealtimeE2eTest` | RT-01~06 | ✅ 6/6 |
| `KafkaMonitorE2eTest` | KM-01~05 | ✅ 5/5 |
| `ObservabilityE2eTest` | OB-01~06 | ✅ 6/6 |
| `NPlusOneE2eTest` | NP-01~05 | ✅ 5/5 |

### 4.3 覆盖矩阵（正常 / 异常 / 边界）
| 模块 | 正常 | 异常 | 边界 | 典型用例 |
| --- | --- | --- | --- | --- |
| 认证 | ● | ● 密码错(401) | ● 空参(400) / 短口令(400) | AUTH-01/03/02/07 |
| 参数 | ● | ● key 缺失(404) | ● 缺 configKey(400) | CFG-02/03/05 |
| 用户 | ● | ● id 缺失(404) | ● 缺 roleIds(400) / 短口令(400) | USR-02/03/05/08 |
| 工作流 | ● 认领/审批/转办 | — | ● 缺 taskId(400) | WF-02/03/05/04 |
| Kafka 监控 | ● 状态/死信列表/重试/丢弃 | ● 空 ID(400) | ● 未启用返回 false | KM-01~05 |
| 可观测性 | ● span 列表/按 traceId/摘要/指标 | ● limit 越界(400) | ● status 非法(400) / 空 traceId(400) | OB-01~06 |
| N+1 探测 | ● 疑似列表/运行态/清空 | ● limit 越界(400) | ● limit 缺省默认 50 | NP-01~05 |

---

## 5. 关键交互流程（时序图）

针对高频、易错的核心链路绘制时序图（自包含 SVG，离线可看）：

| 图 | 流程 | 关键看点 |
| --- | --- | --- |
| SD-01 | 登录鉴权 | 验证码→用户查询→口令→权限装配→令牌签发→在线登记→审计落库；失败分支统一落日志并抛 `SecurityErrorCode` |
| SD-02 | 数据权限切面 | 修复后单点 `@annotation` + `AnnotationUtils.findAnnotation` 手动解析，避免 OR 组合绑定切点参数绑定异常；`HIGHEST_PRECEDENCE` 先于缓存拦截器写入 `DataScopeContext` |
| SD-03 | 参数读写 | 读命中内存缓存直接返回，未命中回源 `sys_config` 并回填；写路径同步失效/回填缓存 |
| SD-04 | 任务认领/审批 | 认领天然幂等；审批/转办强制 `Idempotency-Key`；审批前校验 assignee 归属与任务存在性 |
| SD-05 | 实时推送管理 | Client→Controller→AdminService；读取会话态或经 PushService 广播/定向推送触达 WebSocket；离线用户由前端 REST 拉取兜底 |
| SD-06 | Kafka 消费监控与死信补偿 | Client→Controller→MonitorService→Consumer；读取运行指标/死信列表，或经 retry/discard 清除失败计数与版本表占位以补偿毒消息 |
| SD-07 | 可观测性管理与 span 回溯 | Client→Controller→ObservabilityService；请求级 span 由 OtlpTracingFilter 采集并分发给本地留存记录器（有界环形缓冲）与 OTLP 导出器；管理侧按 traceId 回溯、查摘要与指标快照 |
| SD-08 | N+1 查询探测与疑似事件留存 | Client→Controller→NPlusOneService；jOOQ 执行链路上 NPlusOneListener 借 probe 线程级计数，越阈值即告警并向有界留存登记；管理侧倒序查询疑似事件、查看运行态与清空 |

> 详见 [sequence-diagrams.html](./sequence-diagrams.html)。

---

## 6. 缺陷与风险

| # | 类别 | 描述 | 状态 / 处置 |
| --- | --- | --- | --- |
| R1 | 环境限制 | 沙箱无 MySQL 8.1.0、无出网，无法执行 `*IT` 真·E2E（含多表事务、Flowable 引擎真实推进） | 离线切片等价覆盖契约层；`*IT` 由用户在 CI/本机执行 |
| R2 | 已修复 | 数据权限切面此前因 OR 组合绑定切点导致"用户/部门/待办/流程"等多模块集体报"参数绑定失败"（提交 `c155a02`） | ✅ 已修复并纳入 SD-02 验证 |
| R3 | 安全运维 | 历史遗留：`LoginView` 硬编码口令轮换、多实例 `app.id.worker-id` 配置待运维处置 | 记录于项目待办，非测试阻塞 |
| R4 | 覆盖率 | 离线切片未覆盖真实数据权限 SQL 注入、Flowable 任务推进、导出/导入二进制往返 | 由 `*IT` + 专项测试补充 |

---

## 7. 结论与后续建议

**结论**：本次端到端功能测试在离线环境下达成对 8 大核心用户路径的全流程验证，**52 例新增 E2E 全部通过，另有 46 例单元覆盖降级路径、输入校验、并发安全与配置绑定，既有 702 例回归全绿，合计 800 例 0 失败**。系统核心接口的路由、参数校验、异常映射与 i18n 响应契约稳定可靠；本轮新增 N+1 查询探测管理模块，将此前「只在日志中出现一次」的 N+1 告警升级为可查询、可复盘、可清理的运行态数据，并修正了命名数据源 DSL 缺少 N+1 观测的装配缺口（与主 DSLContext 双路一致）。

**后续建议**：
1. 在 CI/本机 MySQL 环境执行 `*IT` 集成测试，补全真实事务与 Flowable 引擎链路验证；
2. 将 `CoreApiE2eTest` / `RealtimeE2eTest` 纳入 CI `build-and-unit-test` 阶段（已满足 `convention-check` 硬约束）；
3. 针对导出/导入二进制往返、数据权限 SQL 注入、504 超时链路补充专项用例；
4. 推进 R3 安全运维项处置。

---

## 8. 集成说明（实时推送管理如何接入现有系统）

`realtime` 包此前已有 `RealtimePushService` / `RealtimeSessionManager` / `RealtimeWebSocketHandler` 与握手鉴权，唯独缺少 REST 入口。本轮新增 `RealtimeController` + `RealtimeAdminService` + 4 个 DTO，补齐该入口，集成方式如下：

1. **自动装配，无需改配置**：`RealtimeController`（`@RestController`）随组件扫描注册，`@RequestMapping("/api/realtime")` 下暴露四个端点；`RealtimeAdminService` 由构造注入 `RealtimeSessionManager` 与 `RealtimePushService`，无新增 Bean 定义、无 `application*.yml` 变更。
2. **权限接入现有体系**：端点沿用项目统一的 `@PreAuthorize("hasAuthority('system:realtime:read|push')")` 注解；`ApiResourceScanner` 启动时自动登记这些端点为 API 资源（`system:realtime:read` / `system:realtime:push`），`SUPER_ADMIN` 等已含全部权限的角色天然可用，无需手工改角色种子。
3. **API 文档**：`springdoc-openapi` 已启用，新增端点自动出现在 `/v3/api-docs` 与 `/swagger-ui.html`（`@Tag` / `@Operation` 已标注）。
4. **无数据库依赖**：在线会话为内存态（WebSocket 生命周期维护），本模块不引入任何表结构迁移（DDL）或外部依赖。
5. **前端对接建议**：管理后台「在线会话 / 实时推送」页调用 `GET /api/realtime/sessions` 与 `POST /api/realtime/push/{broadcast,user}` 即可；`type` 复用既有 `UNREAD_MESSAGE` / `TODO_REMINDER` / `NOTIFICATION` / `PONG` 常量。菜单种子可按需补 `system:realtime` 一类（非阻塞）。

---

## 9. 集成说明（Kafka 消费监控与死信补偿如何接入现有系统）

`kafka` 包此前已有 `OrderedKafkaConsumer`（有序消费容器，含版本去重、毒消息跳过与位移提交失败计数），但**没有任何 REST 管理入口**，运维无法观测其运行态、也无法对毒消息做补偿。本轮新增 `KafkaMonitorController` + `KafkaMonitorService` + 2 个视图 DTO + 领域对象 `PoisonMessage`，并以最小侵入方式在 `OrderedKafkaConsumer` 内建内存死信存储，补齐该入口，集成方式如下：

1. **自动装配，无需改配置**：`KafkaMonitorController`（`@RestController`）随组件扫描注册，`@RequestMapping("/api/kafka")` 下暴露四个端点；`KafkaMonitorService` 由构造注入 `ObjectProvider<OrderedKafkaConsumer>`，无新增 Bean 定义、无 `application*.yml` 变更。
2. **消费容器门禁兼容**：消费容器由 `acme.kafka.ordered-consumer.enabled` 控制，未启用时 `OrderedKafkaConsumer` Bean 不注册；`KafkaMonitorService` 通过 `ObjectProvider#getIfAvailable()` 惰性获取，缺失时状态返回 `enabled=false`、补偿操作返回 `false`，对未装配环境零硬依赖。
3. **权限接入现有体系**：端点沿用项目统一的 `@PreAuthorize("hasAuthority('system:kafka:read|manage')")` 注解；`ApiResourceScanner` 启动时自动登记这些端点为 API 资源（`system:kafka:read` / `system:kafka:manage`），`SUPER_ADMIN` 等已含全部权限的角色天然可用，无需手工改角色种子。
4. **无数据库依赖**：运行指标来自 `OrderedKafkaConsumer` 内存计数器（`LongAdder`/`ConcurrentHashMap`），死信存储为内存态、随容器重启清零——与既有版本表「重启即全量重投」语义一致，本模块不引入任何表结构迁移（DDL）或外部依赖。
5. **毒消息补偿语义**：`retry` 清除该消息的位点失败计数与版本表占位，使上游重投后能被重新处理（需上游修正后重发，否则仍被版本表挡下）；`discard` 仅从死信存储移除、视为已人工确认。两者均为内存态操作，幂等（重复调用对不存在的 id 返回 `false`）。
6. **前端对接建议**：运维「消息队列监控」页调用 `GET /api/kafka/consumer/status` 展示实时指标、`GET /api/kafka/consumer/poison` 分页列出死信，对单条毒消息提供「重试 / 丢弃」按钮分别调用 `POST /api/kafka/consumer/poison/{id}/retry|discard`。菜单种子可按需补 `system:kafka` 一类（非阻塞）。

---

## 10. 集成说明（可观测性管理如何接入现有系统）

`observability` 包此前已有完整的 span 采集与导出能力（`OtlpTracingFilter` 请求级采集、`OtlpSpanExporter` 零依赖 OTLP 推送、`MetricsRegistry` Micrometer 门面），但存在两个缺口：**span 导出后本地零留存**（Collector 未部署时无法回溯追踪），**指标无管理面板视角的按名检索**。本轮新增 `ObservabilityController` + `ObservabilityService` + 3 个视图 DTO + `SpanRecorder` 本地留存组件，集成方式如下：

1. **自动装配 + 一处显式配置**：`SpanRecorder`（`@Component`，实现既有 `SpanExporter` 接口）与 `ObservabilityController` 随组件扫描注册，无需新增 `@Configuration`；`OtlpTracingFilter` 的构造由单 exporter 改为 `List<SpanExporter>`（Spring 自动聚合本地留存 + OTLP 两个 sink），因此**新增配置段** `observability.recorder.enabled`（默认 true）与 `observability.recorder.capacity`（默认 200），已在 `application.yml` 显式声明（含注释）。
2. **与 OTLP 导出开关正交**：`recorder.enabled` 与 `otlp.enabled` 互相独立——本地留存开启时无需 Collector 即可经 `GET /api/observability/traces` 回溯；两个开关均关闭时过滤器直接透传（保持既有「零开销」语义）。`SpanRecorder` 复用原作者在 `SpanExporter` 接口注释中预留的多 sink 设计（「解耦过滤器与具体导出实现」），本地留存即一种 sink。
3. **权限接入现有体系**：端点沿用项目统一的 `@PreAuthorize("hasAuthority('system:observability:read|manage')")`；`ApiResourceScanner` 自动登记为 API 资源，`SUPER_ADMIN` 等角色天然可用，无需手工改角色种子。
4. **无数据库依赖**：span 留存为应用内有界环形缓冲（默认 200 条，超出按入队序淘汰，重启清零），指标快照直接读 Micrometer `MeterRegistry` 内存态；本模块不引入任何表结构迁移（DDL）或外部依赖。
5. **补偿/清理语义**：`DELETE /api/observability/traces` 仅清空本地留存并返回清除条数，不影响 OTLP 导出链路；span 与日志经同一 MDC `traceId` 关联，支持「日志 → span → Collector」双向跳转。
6. **前端对接建议**：运维「链路追踪」页调用 `GET /api/observability/traces?limit=50&status=ERROR` 展示最近请求、点击某条后以 `traceId` 调 `GET /api/observability/traces/{traceId}` 查看同追踪全链路；`GET /api/observability/summary` 用于展示双链路开关与留存水位告警。菜单种子可按需补 `system:observability` 一类（非阻塞）。

---

## 11. 集成说明（N+1 查询探测如何接入现有系统）

`monitor/detection` 包此前已有完整的 N+1 探测三件套（`NPlusOneProbe` 线程级同构 SQL 计数、`NPlusOneListener` 越阈值告警、`NPlusOneResetFilter` 请求级重置），且主 `DSLContext`（`JooqConfig`）已接线，但存在两个缺口：**检测数据只写不读**——越阈值仅打一条日志，运维无接口查询「哪些 SQL 疑似 N+1、出现频次如何」；**命名数据源 DSL 缺少观测**——`DataSourceConfig` 构建的 `Map<String, DSLContext>` 只挂了慢 SQL 监听。本轮新增 `NPlusOneController` + `NPlusOneService` + 2 个视图 DTO + `NPlusOneSuspectRegistry` 有界留存组件，集成方式如下：

1. **自动装配 + 显式配置**：`NPlusOneProbe` / `NPlusOneSuspectRegistry` 由 `JooqConfig` 注册为 Bean（阈值 `app.jooq.nplus1-threshold` 默认 10、留存容量 `app.jooq.nplus1-max-suspects` 默认 200，均已在 `application.yml` 显式声明含注释）；`NPlusOneController` 随组件扫描注册，无需新增 `@Configuration`。
2. **双路观测补齐**：`DataSourceConfig.buildDsl` 原仅注册 `SlowQueryListener`，本轮补挂 `NPlusOneListener`，与 `JooqConfig` 共用同一 probe 与留存组件——命名数据源显式访问路径（`DataSourceRouter`）与主路径行为一致。
3. **观测旁路零风险**：探测只做同构 SQL 计数（ThreadLocal，请求结束即清理），留存为有界环形缓冲（超出淘汰最旧，内存恒定）；`NPlusOneListener` 登记事件带 try-catch 防护，即使留存组件自身异常也仅记 debug 日志，绝不向 jOOQ 执行链路传播，不影响业务 SQL。
4. **权限接入现有体系**：端点沿用 `@PreAuthorize("hasAuthority('system:monitor:read')")`（与 `MonitorController` 同权）；`ApiResourceScanner` 自动登记为 API 资源，无需手工改角色种子。
5. **无数据库依赖**：疑似事件为进程内有界留存（默认 200 条，重启清零），累计计数 `totalBreaches` 单调递增、清空不清零（便于评估告警频率）；本模块不引入任何 DDL 或外部依赖。
6. **前端对接建议**：运维「SQL 质量」页调用 `GET /api/system/monitor/n-plus-one/suspects?limit=50` 展示最近疑似事件（指纹可直接定位代码中的查询），`GET /api/system/monitor/n-plus-one/status` 展示阈值/容量/水位，提供「清空」按钮调 `DELETE /api/system/monitor/n-plus-one/suspects`。菜单种子可按需补 `system:monitor` 一类（非阻塞，与监控页共用即可）。

*报告由离线测试执行结果自动汇总生成，数据来源：`mvn -o clean test` 输出（800/0/0/0）与 `scripts/check-conventions.py`（EXIT=0）。*
