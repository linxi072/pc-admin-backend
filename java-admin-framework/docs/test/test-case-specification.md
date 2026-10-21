# 端到端功能测试用例规格（Test Case Specification）

> 项目：`java-admin-framework`（Spring Boot 3.3.4 / Java 21，仅后端）
> 测试类型：离线端到端切片测试（Offline E2E Slice）
> 测试载体：`src/test/java/com/acme/scaffold/e2e/CoreApiE2eTest.java`、`RealtimeE2eTest.java`、`KafkaMonitorE2eTest.java`、`ObservabilityE2eTest.java`、`NPlusOneE2eTest.java`、`IdempotencyRecordE2eTest.java`、`AlertInboxE2eTest.java`，单元 `src/test/java/com/acme/scaffold/kafka/service/KafkaMonitorServiceTest.java`、`src/test/java/com/acme/scaffold/observability/recorder/SpanRecorderTest.java`、`src/test/java/com/acme/scaffold/observability/service/ObservabilityServiceTest.java`、`src/test/java/com/acme/scaffold/observability/export/ObservabilityPropertiesTest.java`、`src/test/java/com/acme/scaffold/monitor/detection/NPlusOneSuspectRegistryTest.java`、`src/test/java/com/acme/scaffold/monitor/detection/NPlusOneListenerTest.java`、`src/test/java/com/acme/scaffold/monitor/service/NPlusOneServiceTest.java`、`src/test/java/com/acme/scaffold/common/idempotency/rules/IdempotencyRecordRulesTest.java`、`src/test/java/com/acme/scaffold/common/idempotency/service/IdempotencyRecordServiceTest.java`、`src/test/java/com/acme/scaffold/monitor/alert/rules/AlertInboxRulesTest.java`、`src/test/java/com/acme/scaffold/monitor/alert/service/AlertInboxServiceTest.java`；前端 `pc-admin-vue3-vite/scripts/fe-observability.mjs`（25 例，node:test）与 `fe-connectivity.mjs`（5 例，node:test）
> 配套文档：[end-to-end-test-report.md](./end-to-end-test-report.md) · [sequence-diagrams.html](./sequence-diagrams.html)

---

## 1. 测试策略与范围

| 维度 | 说明 |
| --- | --- |
| 被测对象 | 8 大核心用户路径：认证授权、系统参数、用户管理、工作流任务、实时推送管理、Kafka 消费监控、可观测性管理、N+1 查询探测 |
| 测试形态 | Standalone `MockMvc` 装配**真实** `Controller` + **真实** `GlobalExceptionHandler` + **真实** `I18nService`；服务层以 Mockito 桩接，**无需 MySQL** |
| 覆盖目标 | 正常流程 + 异常/边界（参数校验失败、业务异常、HTTP 状态与 `Result` 包络映射） |
| 用例总量 | 52 例 E2E（分 8 个测试类：4 个 `@Nested` + `RealtimeE2eTest` + `KafkaMonitorE2eTest` + `ObservabilityE2eTest` + `NPlusOneE2eTest`）+ 46 例单元（`KafkaMonitorServiceTest` 4 + `SpanRecorderTest` 10 + `ObservabilityServiceTest` 8 + `ObservabilityPropertiesTest` 3 + `OtlpTracingFilterTest` 本轮新增 1 例 + `NPlusOneSuspectRegistryTest` 10 + `NPlusOneListenerTest` 4 + `NPlusOneServiceTest` 6），全部离线可跑、全绿；另 24 例幂等记录（E2E 6 + 单元 18）与 24 例告警收件箱（E2E 7 + 单元 17）见 §10/§11；前端 `node:test` 另计 30 例（`fe-observability.mjs` 25 + `fe-connectivity.mjs` 5，见 §12） |
| 不覆盖 | 需本机 MySQL 的真实事务/多表联动，由 `*IT` 集成测试在 CI/本机执行（见报告 §4）；浏览器 DOM 级渲染（Element Plus 组件）由前端构建与人工验收覆盖，Node 侧以 API 契约 + 纯函数等价验证 |

### 优先级约定
- **P0（核心）**：主成功路径与会导致越权/数据损坏的关键异常。
- **P1（重要）**：次要成功路径与高频异常分支。
- **P2（补充）**：边界值与低频异常。

### 通用前置条件
- 服务层（Service）已 Mockito 桩接，返回确定性 `View` 对象或按用例抛 `BusinessException`。
- `I18nService` 加载 `classpath:i18n/messages*` / `i18n/validation*`（`fallbackToSystemLocale=false`，UTF-8）。
- 统一响应体为 `Result<T>`：成功 `code="0"` / `message="OK"`；业务失败 `data=null`，`code` 取错误码，`message` 经 i18n 解析。

---

## 2. 认证授权模块（AuthE2e，9 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| AUTH-01 | 账号密码登录成功 | P0 | 正常 | `authService.login(...)` 桩返回 `TokenView(access,refresh,3600)` | 1. `POST /api/auth/login` 带 `LoginCommand{username,password}`<br>2. 解析 `Result<TokenView>` | HTTP 200；`code="0"`；`data.accessToken="access"`、`data.refreshToken="refresh"`、`expiresIn=3600` |
| AUTH-02 | 登录参数为空 | P0 | 边界 | 无 | 1. `POST /api/auth/login` 发送空 `@NotBlank` 字段 | HTTP 400；`code="COMMON_001"`（`MethodArgumentNotValidException`→全局异常处理器） |
| AUTH-03 | 密码错误 | P0 | 异常 | `authService.login(...)` 抛 `BusinessException(AUTH_001)` | 1. `POST /api/auth/login` 带错误口令 | HTTP 401；`code="AUTH_001"`；`data=null` |
| AUTH-04 | 刷新令牌成功 | P1 | 正常 | `authService.refresh(...)` 桩返回新 `TokenView` | 1. `POST /api/auth/refresh` 带 `RefreshCommand{refreshToken}` | HTTP 200；`code="0"`；`data` 含新令牌 |
| AUTH-05 | 刷新令牌为空 | P0 | 边界 | 无 | 1. `POST /api/auth/refresh` 发送空 `refreshToken` | HTTP 400；`code="COMMON_001"` |
| AUTH-06 | 会话无关改密 | P1 | 正常 | `authService.changePassword(...)` 桩正常返回 | 1. `POST /api/auth/change-password` 带 `username/oldPassword/newPassword` | HTTP 200；`code="0"`；`data=null` |
| AUTH-07 | 改密口令过短（<8） | P0 | 边界 | 无 | 1. `POST /api/auth/change-password` 带 `newPassword` 长度<8 | HTTP 400；`code="COMMON_001"`（`@Size(8,64)` 违例） |
| AUTH-08 | 忘记密码自助重置 | P2 | 正常 | `authService.forgotPassword(...)` 桩正常返回 | 1. `POST /api/auth/forgot-password` 带 `username/newPassword/mobile` | HTTP 200；`code="0"` |
| AUTH-09 | 登出 | P1 | 正常 | `authService.logout()` 桩正常返回 | 1. `POST /api/auth/logout` | HTTP 200；`code="0"` |

**关键交互时序**：见 [sequence-diagrams.html#sd1](./sequence-diagrams.html)（SD-01 登录鉴权链路）。

---

## 3. 系统参数模块（ConfigE2e，7 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| CFG-01 | 参数列表查询 | P1 | 正常 | `configService.list()` 桩返回 `List<ConfigVO>` | 1. `GET /api/system/configs` | HTTP 200；`code="0"`；`data` 为参数数组 |
| CFG-02 | 按 key 查询命中 | P0 | 正常 | `configService.getByKey("key1")` 桩返回 `ConfigVO` | 1. `GET /api/system/configs/key/key1` | HTTP 200；`code="0"`；`data.configKey="key1"` |
| CFG-03 | 按 key 查询缺失 | P0 | 异常 | `configService.getByKey(...)` 抛 `BusinessException(NOT_FOUND)` | 1. `GET /api/system/configs/key/missing` | HTTP 404；`code="COMMON_404"`；`data=null` |
| CFG-04 | 新建参数 | P1 | 正常 | `configService.create(...)` 桩返回 `id=1L` | 1. `POST /api/system/configs` 带 `CreateConfigRequest` | HTTP 200；`code="0"`；`data=1` |
| CFG-05 | 新建参数缺 configKey | P0 | 边界 | 无 | 1. `POST /api/system/configs` 发送空 `configKey` | HTTP 400；`code="COMMON_001"` |
| CFG-06 | 更新参数 | P1 | 正常 | `configService.update(...)` 桩正常返回 | 1. `PUT /api/system/configs/1` 带 `UpdateConfigRequest` | HTTP 200；`code="0"` |
| CFG-07 | 删除参数成功 | P1 | 正常 | `configService.delete(1L)` 桩正常返回 | 1. `DELETE /api/system/configs/1` | HTTP 200；`code="0"` |

**关键交互时序**：见 [sequence-diagrams.html#sd3](./sequence-diagrams.html)（SD-03 参数读写与缓存一致）。

---

## 4. 用户管理模块（UserE2e，9 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| USR-01 | 用户分页查询 | P1 | 正常 | `userService.page(...)` 桩返回 `PageResult<UserView>` | 1. `GET /api/system/users/page` | HTTP 200；`code="0"`；`data` 为分页对象 |
| USR-02 | 按 id 查询命中 | P0 | 正常 | `userService.get(1L)` 桩返回 `UserView` | 1. `GET /api/system/users/1` | HTTP 200；`code="0"`；`data.username` 非空 |
| USR-03 | 按 id 查询缺失 | P0 | 异常 | `userService.get(...)` 抛 `BusinessException(NOT_FOUND)` | 1. `GET /api/system/users/999` | HTTP 404；`code="COMMON_404"` |
| USR-04 | 新建用户 | P1 | 正常 | `userService.create(...)` 桩返回 `id=1L` | 1. `POST /api/system/users` 带 `CreateUserRequest` | HTTP 200；`code="0"`；`data=1` |
| USR-05 | 新建用户缺 roleIds/deptIds | P0 | 边界 | 无 | 1. `POST /api/system/users` 发送空 `@NotEmpty` 集合 | HTTP 400；`code="COMMON_001"` |
| USR-06 | 更新用户 | P1 | 正常 | `userService.update(...)` 桩正常返回 | 1. `PUT /api/system/users/1` 带 `UpdateUserRequest` | HTTP 200；`code="0"` |
| USR-07 | 重置密码 | P1 | 正常 | `userService.resetPassword(...)` 桩正常返回 | 1. `POST /api/system/users/1/reset-password` 带 `ResetPasswordRequest` | HTTP 200；`code="0"` |
| USR-08 | 重置密码过短（<8） | P0 | 边界 | 无 | 1. `POST /api/system/users/1/reset-password` 带长度<8 口令 | HTTP 400；`code="COMMON_001"` |
| USR-09 | 删除用户成功 | P1 | 正常 | `userService.delete(1L)` 桩正常返回 | 1. `DELETE /api/system/users/1` | HTTP 200；`code="0"` |

---

## 5. 工作流任务模块（WorkflowE2e，5 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| WF-01 | 我的待办列表 | P1 | 正常 | `workflowService.myTasks()` 桩返回 `List<TaskView>` | 1. `GET /api/workflow/tasks/mine` | HTTP 200；`code="0"`；`data` 为任务数组 |
| WF-02 | 认领任务 | P0 | 正常 | `workflowService.claim("t1")` 桩正常返回 | 1. `POST /api/workflow/tasks/t1/claim` | HTTP 200；`code="0"` |
| WF-03 | 审批通过（APPROVE） | P0 | 正常 | `workflowService.completeTask(...)` 桩正常返回 | 1. `POST /api/workflow/tasks/complete` 带 `CompleteTaskRequest{action=APPROVE}` | HTTP 200；`code="0"` |
| WF-04 | 审批缺 taskId | P0 | 边界 | 无 | 1. `POST /api/workflow/tasks/complete` 发送空 `@NotBlank taskId` | HTTP 400；`code="COMMON_001"` |
| WF-05 | 转办任务 | P1 | 正常 | `workflowService.transfer(...)` 桩正常返回 | 1. `POST /api/workflow/tasks/transfer` 带 `TransferTaskRequest` | HTTP 200；`code="0"` |

**关键交互时序**：见 [sequence-diagrams.html#sd4](./sequence-diagrams.html)（SD-04 任务认领/审批）。

---

## 6. 实时推送管理模块（RealtimeE2e，6 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| RT-01 | 在线会话列表 | P1 | 正常 | `adminService.listSessions()` 桩返回 2 条会话视图 | 1. `GET /api/realtime/sessions` | HTTP 200；`code="0"`；`data[0].userId=1`、`data[0].sessionCount=2` |
| RT-02 | 在线会话总数 | P1 | 正常 | `adminService.onlineCount()` 桩返回 3 | 1. `GET /api/realtime/online-count` | HTTP 200；`code="0"`；`data=3` |
| RT-03 | 广播推送成功 | P0 | 正常 | `adminService.broadcast(...)` 桩返回 `RealtimePushResultView(4)` | 1. `POST /api/realtime/push/broadcast` 带 `{type,payload}` | HTTP 200；`code="0"`；`data.delivered=4` |
| RT-04 | 广播缺 type | P0 | 边界 | 无 | 1. `POST /api/realtime/push/broadcast` 带空 `type` | HTTP 400；`code="COMMON_001"` |
| RT-05 | 定向推送成功 | P0 | 正常 | `adminService.pushToUsers(...)` 桩返回 `RealtimePushResultView(2)` | 1. `POST /api/realtime/push/user` 带 `userIds`/`type` | HTTP 200；`code="0"`；`data.delivered=2` |
| RT-06 | 定向推送 userIds 为空 | P0 | 边界 | 无 | 1. `POST /api/realtime/push/user` 带空 `userIds` | HTTP 400；`code="COMMON_001"` |

**关键交互时序**：见 [sequence-diagrams.html#sd5](./sequence-diagrams.html)（SD-05 实时推送管理）。

## 7. Kafka 消费监控模块（KafkaMonitorE2e，5 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| KM-01 | 消费容器状态 | P1 | 正常 | `monitorService.status()` 桩返回 `enabled=true` 及各项指标 | 1. `GET /api/kafka/consumer/status` | HTTP 200；`code="0"`；`data.enabled=true`、`data.handledCount=1024`、`data.poisonPending=2` |
| KM-02 | 死信列表 | P1 | 正常 | `monitorService.listPoison(...)` 桩返回含 1 条毒消息的分页 | 1. `GET /api/kafka/consumer/poison` | HTTP 200；`code="0"`；`data.total=1`、`data.records[0].id="pid-1"`、`data.records[0].topic="order-topic"` |
| KM-03 | 重试毒消息 | P0 | 正常 | `monitorService.retry("pid-1")` 桩返回 `true` | 1. `POST /api/kafka/consumer/poison/pid-1/retry` | HTTP 200；`code="0"`；`data=true` |
| KM-04 | 丢弃不存在的毒消息 | P2 | 边界 | `monitorService.discard("ghost")` 桩返回 `false` | 1. `POST /api/kafka/consumer/poison/ghost/discard` | HTTP 200；`code="0"`；`data=false` |
| KM-05 | 空 ID 重试 | P0 | 边界 | `monitorService.retry(any())` 桩抛 `BusinessException(VALIDATION_ERROR)` | 1. `POST /api/kafka/consumer/poison/x/retry` | HTTP 400；`code="COMMON_001"`（服务层 `requireNonBlankId` 经 `GlobalExceptionHandler` 映射） |

**关键交互时序**：见 [sequence-diagrams.html#sd6](./sequence-diagrams.html)（SD-06 Kafka 消费监控与死信补偿）。

## 8. 可观测性管理模块（ObservabilityE2e，6 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| OB-01 | 最近 span 列表 | P1 | 正常 | `observabilityService.recentSpans(...)` 桩返回 1 条 span 视图（含属性与耗时） | 1. `GET /api/observability/traces?limit=50` | HTTP 200；`code="0"`；`data[0].traceId="t-001"`、`data[0].name="GET /api/users"`、`data[0].status="OK"`、`data[0].durationMillis=42`、`data[0].attributes['http.status_code']="200"` |
| OB-02 | 按 traceId 检索 | P1 | 正常 | `spansByTraceId("t-001")` 桩返回同追踪 2 条 span（OK/ERROR） | 1. `GET /api/observability/traces/t-001` | HTTP 200；`code="0"`；`data.length()=2`、`data[1].statusMessage="boom"` |
| OB-03 | 运行态摘要 | P1 | 正常 | `summary()` 桩返回记录器启用、留存 12、OK 10/ERROR 2、导出器关闭 | 1. `GET /api/observability/summary` | HTTP 200；`code="0"`；`data.recorderEnabled=true`、`data.exporterEnabled=false`、`data.retained=12`、`data.okCount=10`、`data.errorCount=2` |
| OB-04 | 指标快照 | P1 | 正常 | `metricSnapshot(prefix)` 桩返回 1 条 counter 指标 | 1. `GET /api/observability/metrics?prefix=app.observability.` | HTTP 200；`code="0"`；`data[0].name="app.observability.test.spans"`、`data[0].type="COUNTER"`、`data[0].value=1.0` |
| OB-05 | limit 越界 | P0 | 边界 | `recentSpans(0, ...)` 桩抛 `BusinessException(VALIDATION_ERROR)` | 1. `GET /api/observability/traces?limit=0` | HTTP 400；`code="COMMON_001"`（服务层 limit 范围校验经 `GlobalExceptionHandler` 映射） |
| OB-06 | 清空本地留存 | P1 | 正常 | `clearSpans()` 桩返回 3 | 1. `DELETE /api/observability/traces` | HTTP 200；`code="0"`；`data=3`（清除条数） |

**关键交互时序**：见 [sequence-diagrams.html#sd7](./sequence-diagrams.html)（SD-07 可观测性管理与 span 回溯）。

## 9. N+1 查询探测模块（NPlusOneE2e，5 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| NP-01 | 疑似事件列表 | P1 | 正常 | `nPlusOneService.recentSuspects(...)` 桩返回 1 条疑似事件视图（指纹/样本/计数/线程） | 1. `GET /api/system/monitor/n-plus-one/suspects` | HTTP 200；`code="0"`；`data[0].fingerprint="select * from sys_user where id=?"`、`data[0].count=12`、`data[0].threadName="http-nio-1"` |
| NP-02 | 探测运行态 | P1 | 正常 | `status()` 桩返回阈值 10 / 容量 200 / 存量 3 / 累计 42 | 1. `GET /api/system/monitor/n-plus-one/status` | HTTP 200；`code="0"`；`data.threshold=10`、`data.capacity=200`、`data.registrySize=3`、`data.totalBreaches=42` |
| NP-03 | limit 越界 | P0 | 边界 | `recentSuspects(0)` 桩抛 `BusinessException(VALIDATION_ERROR)` | 1. `GET /api/system/monitor/n-plus-one/suspects?limit=0` | HTTP 400；`code="COMMON_001"`（服务层 limit 范围校验经 `GlobalExceptionHandler` 映射） |
| NP-04 | 清空疑似留存 | P1 | 正常 | `clear()` 桩返回 true | 1. `DELETE /api/system/monitor/n-plus-one/suspects` | HTTP 200；`code="0"`；`data=true` |
| NP-05 | limit 缺省 | P2 | 边界 | `recentSuspects(50)` 桩返回空列表 | 1. `GET /api/system/monitor/n-plus-one/suspects`（不带 limit） | HTTP 200；`code="0"`；`data` 为空数组；服务层按默认 50 条查询 |

> 配套单元（非 E2E 口径）：`NPlusOneSuspectRegistryTest` 10 例守护有界留存/淘汰/累计口径/样本截断/并发守恒；`NPlusOneListenerTest` 4 例守护越限登记/未越限不登记/每轮一次/null 安全；`NPlusOneServiceTest` 6 例守护 limit 校验/视图映射/状态聚合/清空委托。

**关键交互时序**：见 [sequence-diagrams.html#sd8](./sequence-diagrams.html)（SD-08 N+1 查询探测与疑似事件留存）。

## 10. 幂等记录管理模块（IdempotencyRecordE2e，6 例）

| 编号 | 标题 | 优先级 | 类型 | 前置条件 | 测试步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| ID-01 | 分页查询默认参数 | P1 | 正常 | `service.page(...)` 桩返回 1 条记录视图（key/scope/指纹/状态，expiresAt 早于查询时刻） | 1. `GET /api/system/idempotency-records/page` | HTTP 200；`code="0"`；`data.page=1`、`data.size=10`、`data.total=1`；`data.records[0].idempotencyKey="order-2026-1001"`、`status="SUCCESS"`、`expired=true`；`responseSnapshot` 字段不存在（契约级不暴露响应快照） |
| ID-02 | 分页筛选透传 | P1 | 正常 | `service.page(...)` 桩返回空分页 | 1. `GET /page?page=2&size=20&status=SUCCESS&apiScope=workflow:complete&userId=42&keyword=order&startTime=2026-10-19T00:00:00&endTime=2026-10-20T00:00:00&sortField=createdAt&sortDirection=DESC` | HTTP 200；`code="0"`；`data.records` 为空数组；`verify` 服务层收到原样透传的十个参数（含 `@DateTimeFormat` 解析后的两个 `LocalDateTime`） |
| ID-03 | 排序方向非法 | P0 | 边界 | `service.page(..., "SIDEWAYS")` 桩抛 `BusinessException(VALIDATION_ERROR)`（模拟服务层 `PageQuery.of` → `SortItem.single` 校验） | 1. `GET /api/system/idempotency-records/page?sortDirection=SIDEWAYS` | HTTP 400；`code="COMMON_001"`（经 `GlobalExceptionHandler` 映射） |
| ID-04 | 统计摘要 | P1 | 正常 | `service.summary(...)` 桩返回 total=100 / processing=3 / success=90 / failed=7 / expiredPending=12 / share=0.03 | 1. `GET /api/system/idempotency-records/summary` | HTTP 200；`code="0"`；`data.total=100`、`data.processing=3`、`data.failed=7`、`data.expiredPending=12`、`data.processingShare=0.03` |
| ID-05 | 手动清理过期记录 | P1 | 正常 | `service.purgeExpired()` 桩返回 7 | 1. `POST /api/system/idempotency-records/purge` | HTTP 200；`code="0"`；`data=7`；`verify` 清理委托发生一次 |
| ID-06 | 非法状态筛选值容错 | P0 | 边界 | `service.page(...)` 桩返回空分页 | 1. `GET /api/system/idempotency-records/page?status=UNKNOWN` | HTTP 200；`code="0"`；`data.records` 为空数组；`verify` 服务层收到原值 `"UNKNOWN"`（归一/忽略发生在服务层；守护「容错查询」posture，防未来误加严格校验导致 500 回归） |

> 配套单元（非 E2E 口径）：`IdempotencyRecordRulesTest` 12 例守护状态/接口域/用户 ID/关键字归一（非法值忽略的容错 posture）、时间窗收敛（默认 7 天 / 起晚于止钳制）、过期判定与截断；`IdempotencyRecordServiceTest` 6 例守护摘要组装口径（三态直出 / 占比 4 位小数 / 零记录占比 0.0 / 脏计数防御）。DB 交互路径（分页/计数/清理删除）按项目约定由 MySQL 集成测试覆盖（沙箱离线，以 E2E 桩接 + 规则单测达成等价契约验证）。

**关键交互时序**：见 [sequence-diagrams.html#sd9](./sequence-diagrams.html)（SD-09 幂等记录管理）。

## 11. 告警收件箱管理模块（AlertInboxE2e，7 例）

**被测对象**：`GET /api/monitoring/alerts/inbox`（severity/status/limit 筛选读取）、`GET /api/monitoring/alerts/inbox/stats`（汇总）、`DELETE /api/monitoring/alerts/inbox`（清空）；存量兼容端点 `GET /api/monitoring/alerts/webhook/inbox`。

**关键交互时序**：见 [sequence-diagrams.html#sd10](./sequence-diagrams.html)（SD-10 告警收件箱管理与快照读取）。

| 用例 | 场景 | 预期 |
|---|---|---|
| AI-01 | 列表默认参数 | 200 / code=0 / 数组；视图字段（id、receivedAtEpochMillis、status、name、severity、summary）正确 |
| AI-02 | 筛选参数透传 | 200；severity/status/limit 原样进入服务层（归一与钳制在服务层，与 ID-02 同分层） |
| AI-03 | 汇总 | 200；capacity/total/firingCount/resolvedCount/severityCounts/时间戳字段正确 |
| AI-04 | 清空 | 200；返回清理条数；委托 `AlertInboxService.clear()` |
| AI-05 | limit 缺省 | 200；控制器传 null，由服务层取默认值（分层守护，防未来误在控制器加默认值导致双口径） |
| AI-06 | 存量端点兼容 | 200 / code=0（本轮为 `/webhook/inbox` 补 `system:monitor:read` 权限后契约不回退） |
| AI-07 | 服务层异常 | 500 / COMMON_500（经真实 GlobalExceptionHandler 映射，不裸奔） |

**配套单元口径**：
- `AlertInboxRulesTest`（11 例）：status 白名单归一（大小写不敏感/白名单外 null）、severity 自由 label 归一（trim+小写+截断，空值 null）、limit 钳制（null 默认 100、<1 钳 1、超容量按容量自适应、容量误填 0 保底 1）、纯筛选保持顺序与容错、汇总状态分布/severity 计数降序（同数按键名序）/unknown 桶守恒/时间戳口径/null 安全、截断边界；
- `AlertInboxServiceTest`（6 例）：列表字段映射（receivedAt→epochMillis、summary 截断 200）、筛选委托规则类、limit 按容量自适应（3/999/0/2）、空箱返回空列表、汇总透传、清空委托；
- 存量回归：`AlertWebhookControllerTest`（4 例）与 `AlertReceiverServiceTest`（3 例）——本轮对两个类仅增量方法（`capacity()` / `clearInbox()`），无行为变更。

## 12. 可观测中心前端契约测试（fe-observability.mjs，25 例）

**被测对象**：`pc-admin-vue3-vite/src/api/` 下 5 个运维面 API 模块（observability / kafkaMonitor / nplusOne / idempotencyRecord / alertInbox）与 `src/views/monitor/components/format.js` 纯函数；测试以**可编排 axios 适配器**替代真实网络，端到端驱动生产 `src/api/http.js` 的请求构造与 `Result<T>` 拆包拦截器链（不重新实现契约）。

**测试基建**：`module.registerHooks`（Node ≥ 22.15）将 element-plus 替换为无操作虚拟模块（其完整依赖图在 Node 下实测被 kill）、解析 `@/` 别名与扩展名省略的相对导入、打桩 `localStorage`；自定义适配器对非 2xx **自行抛 `AxiosError`**（新版 axios 的 settle 逻辑在内置适配器内，否则 403 会被当作成功，页面失去降级依据——由 XUT-01 守护）。

| 用例 | 场景 | 预期 |
|---|---|---|
| OBS-01 | span 列表默认参数 | `GET /api/observability/traces`，limit 透传，拆包得数组 |
| OBS-02 | span 筛选参数 | status/traceId 原样透传 |
| OBS-03 | 链路详情 | traceId 经 `encodeURIComponent` 进入路径（防注入/特殊字符） |
| OBS-04 | 清空 span | `DELETE /api/observability/traces`，返回清理条数 |
| OBS-05 | 业务错误 | 2xx 但 `code!=0` → reject 且携带 code（拆包拦截器行为） |
| KFK-01 | Kafka 消费状态 | `GET /api/kafka/consumer/status` |
| KFK-02 | 死信分页 | 分页与 key 筛选参数透传，PageResult 拆包 |
| KFK-03 | 死信重试 | `POST /consumer/poison/{id}/retry`，id 编码 |
| KFK-04 | 死信丢弃 | `POST /consumer/poison/{id}/discard` |
| NP1-01 | N+1 疑似列表 | limit 透传 |
| NP1-02 | N+1 运行态 | `GET /status` |
| NP1-03 | N+1 清空 | `DELETE /suspects` |
| IDM-01 | 幂等记录分页 | 参数透传，空值保持 `undefined`（不传空串） |
| IDM-02 | 幂等记录时间范围 | 起止时间参数透传 |
| IDM-03 | 幂等记录摘要 | 同筛选口径打到 summary 端点 |
| IDM-04 | 幂等记录清理 | `POST /purge`，返回删除条数 |
| ALR-01 | 告警列表 | severity/status/limit 透传 |
| ALR-02 | 告警汇总 | `GET /inbox/stats`（守护不与列表路由混淆） |
| ALR-03 | 告警清空 | `DELETE /api/monitoring/alerts/inbox`，返回清理条数 |
| XUT-01 | 403 鉴权拒绝 | reject 携带 `response.status===403`（页面据此走 Tab 级降级空态） |
| FMT-01 | fmtEpoch | 0/负数/null 为无值（`--`），合法毫秒本地时区格式化 |
| FMT-02 | fmtDateTime | ISO 串 / 空格串 / Jackson 数组 / null 四种形态 |
| FMT-03 | fmtDuration | <1000ms 显示 ms，否则秒（2 位小数） |
| FMT-04 | fmtShare | 0~1 → 百分比；空值兜底 |
| FMT-05 | isForbidden | 仅 403 为真（其余状态不误判为降级） |

**关键交互时序**：见 [sequence-diagrams.html#sd11](./sequence-diagrams.html)（SD-11 可观测中心一页多权与 403 降级）。

## 13. 全局异常与错误码映射（统一断言基线）

所有模块共享 `GlobalExceptionHandler` 的错误码映射（测试中以 `Result.code` 断言）：

| 异常来源 | HTTP 状态 | 错误码 |
| --- | --- | --- |
| `MethodArgumentNotValidException` / `HttpMessageNotReadableException` | 400 | `COMMON_001` |
| `BusinessException`（业务拒绝） | 由 `ec.httpStatus()` 决定（典型 404/409/403） | `ec.code()`（如 `COMMON_404`/`AUTH_001`） |
| `AuthenticationException` | 401 | `COMMON_401` |
| `AccessDeniedException` | 403 | `COMMON_403` |
| `DataIntegrityViolationException` | 409 | `COMMON_409` |
| `NoResourceFoundException` | 404 | `COMMON_404` |
| `HttpRequestMethodNotSupportedException` | 405 | `COMMON_405` |
| `TimeoutException` | 504 | `COMMON_504` |
| `Exception`（兜底） | 500 | `COMMON_500` |

---

## 14. 用例与测试代码映射

| 测试类（@Nested） | 方法数 | 覆盖用例 |
| --- | --- | --- |
| `CoreApiE2eTest.AuthE2e` | 9 | AUTH-01 ~ AUTH-09 |
| `CoreApiE2eTest.ConfigE2e` | 7 | CFG-01 ~ CFG-07 |
| `CoreApiE2eTest.UserE2e` | 9 | USR-01 ~ USR-09 |
| `CoreApiE2eTest.WorkflowE2e` | 5 | WF-01 ~ WF-05 |
| `RealtimeE2eTest` | 6 | RT-01 ~ RT-06 |
| `KafkaMonitorE2eTest` | 5 | KM-01 ~ KM-05 |
| `ObservabilityE2eTest` | 6 | OB-01 ~ OB-06 |
| `NPlusOneE2eTest` | 5 | NP-01 ~ NP-05 |
| `IdempotencyRecordE2eTest` | 6 | ID-01 ~ ID-06 |
| `AlertInboxE2eTest` | 7 | AI-01 ~ AI-07 |
| `AlertInboxRulesTest` | 11 | 归一/钳制/筛选/汇总/截断 |
| `AlertInboxServiceTest` | 6 | 列表映射/筛选委托/limit 自适应/汇总/清空 |
| `IdempotencyRecordRulesTest`（单元） | 12 | 状态/接口域/用户 ID/关键字归一（容错忽略）/ 时间窗收敛 / 过期判定 / 截断 |
| `IdempotencyRecordServiceTest`（单元） | 6 | 摘要组装：三态直出 / 占比 4 位小数 / 零记录占比 0.0 / 全处理中 1.0 / 脏计数防御 / 无处理中透传 |
| `NPlusOneSuspectRegistryTest`（单元） | 10 | 有界留存/淘汰/累计口径/截断/null 安全/并发守恒 |
| `NPlusOneListenerTest`（单元） | 4 | 越限登记/未越限不登记/每轮一次/null SQL 安全 |
| `NPlusOneServiceTest`（单元） | 6 | limit 校验(0/越上限/负)/视图映射/状态聚合/清空委托 |
| `KafkaMonitorServiceTest`（单元） | 4 | 单元：消费容器缺失降级 / 空 ID 校验(VALIDATION_ERROR) / retry 委托 / 死信按 key 过滤映射 |
| `SpanRecorderTest`（单元） | 10 | 单元：倒序检索 / 容量淘汰 / traceId 聚合 / 状态统计 / 清空 / 禁用语义 / export 批量 / null 安全 / 并发有界守恒 |
| `ObservabilityServiceTest`（单元） | 8 | 单元：limit 越界校验 / status 非法校验 / traceId 空校验 / 过滤映射 / clear 委托 / 双链路摘要 / 指标前缀过滤与排序 |
| `ObservabilityPropertiesTest`（单元） | 3 | 单元：yml recorder 段绑定 / otlp 回归 / 默认值与 yml 一致 |
| `OtlpTracingFilterTest`（单元） | 3 | 单元：exporter+recorder 双消费 / 双关零开销透传 / 仅本地留存时仍记录（本轮新增 1 例） |
| `fe-observability.mjs`（前端，node:test） | 25 | OBS-01~05 / KFK-01~04 / NP1-01~03 / IDM-01~04 / ALR-01~03 / XUT-01 / FMT-01~05（API 形状契约 + 403 降级 + format.js 纯函数；驱动生产 http.js 拆包拦截器） |
| `fe-connectivity.mjs`（前端，node:test） | 5（离线）/ 3+2 跳过（真实后端模式） | 前后端联通契约（ping 包络 / 错误码映射 / 504 超时）；`API_BASE` 真实后端模式下 3 例走真实链路、2 例因后端未实现对应错误码探针端点条件跳过 |
| `ConnectivityControllerTest`（单元，standalone MockMvc + 真实 handler/i18n） | 19 | 联通探针契约：ping 包络字段/nonce 防缓存/全错误码映射（400/401/403/404/405/409/422/429/500/504）/i18n 中英文案/504 超时映射；`/slow` 超时探针 4 例：默认 5000ms（>探针阈值）/`[0,10000]` 收敛/非数字 400 `COMMON_001`/`delayMs=0` 快路径 |
| `realtime-ws-probe.py`（Python，RFC 6455 客户端） | 5 | WS-01 握手 101 + Accept 校验 / WS-02 ping→pong 真实帧往返 / WS-03 推送信封字段 / WS-04 缺 token 拒绝 / WS-05 篡改 token 拒绝（配 `realtime-ws-stub.py` 镜像后端 WS 契约） |

> 每个 `@Test` 方法断言三要素：**HTTP 状态**、**`Result.code` 业务错误码**、**`Result.data` 形态/空值**，并覆盖 i18n 消息键解析。
