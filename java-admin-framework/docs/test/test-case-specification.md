# 端到端功能测试用例规格（Test Case Specification）

> 项目：`java-admin-framework`（Spring Boot 3.3.4 / Java 21，仅后端）
> 测试类型：离线端到端切片测试（Offline E2E Slice）
> 测试载体：`src/test/java/com/acme/scaffold/e2e/CoreApiE2eTest.java`
> 配套文档：[end-to-end-test-report.md](./end-to-end-test-report.md) · [sequence-diagrams.html](./sequence-diagrams.html)

---

## 1. 测试策略与范围

| 维度 | 说明 |
| --- | --- |
| 被测对象 | 4 大核心用户路径：认证授权、系统参数、用户管理、工作流任务 |
| 测试形态 | Standalone `MockMvc` 装配**真实** `Controller` + **真实** `GlobalExceptionHandler` + **真实** `I18nService`；服务层以 Mockito 桩接，**无需 MySQL** |
| 覆盖目标 | 正常流程 + 异常/边界（参数校验失败、业务异常、HTTP 状态与 `Result` 包络映射） |
| 用例总量 | 30 例，分 4 个 `@Nested` 类，全部离线可跑、全绿 |
| 不覆盖 | 需本机 MySQL 的真实事务/多表联动，由 `*IT` 集成测试在 CI/本机执行（见报告 §4） |

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

## 6. 全局异常与错误码映射（统一断言基线）

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

## 7. 用例与测试代码映射

| 测试类（@Nested） | 方法数 | 覆盖用例 |
| --- | --- | --- |
| `CoreApiE2eTest.AuthE2e` | 9 | AUTH-01 ~ AUTH-09 |
| `CoreApiE2eTest.ConfigE2e` | 7 | CFG-01 ~ CFG-07 |
| `CoreApiE2eTest.UserE2e` | 9 | USR-01 ~ USR-09 |
| `CoreApiE2eTest.WorkflowE2e` | 5 | WF-01 ~ WF-05 |

> 每个 `@Test` 方法断言三要素：**HTTP 状态**、**`Result.code` 业务错误码**、**`Result.data` 形态/空值**，并覆盖 i18n 消息键解析。
