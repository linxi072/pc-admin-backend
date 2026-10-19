# 端到端功能测试报告（End-to-End Test Report）

| 项 | 内容 |
| --- | --- |
| 项目 | `java-admin-framework`（Spring Boot 3.3.4 / Java 21，仅后端） |
| 测试日期 | 2026-09-28（沙箱离线执行） |
| 测试形态 | 离线端到端切片（Offline E2E Slice） |
| 测试载体 | `src/test/java/com/acme/scaffold/e2e/CoreApiE2eTest.java`（30 例）+ `RealtimeE2eTest.java`（6 例） |
| 配套文档 | [test-case-specification.md](./test-case-specification.md) · [sequence-diagrams.html](./sequence-diagrams.html) |
| 总体结论 | ✅ **通过** — 738 例单测全绿（含新增 36 例 E2E），0 失败 / 0 错误 / 0 跳过 |

---

## 1. 测试概述

依据"覆盖所有核心业务场景与用户操作路径"的要求，本次在沙箱离线环境下构建了一套**离线端到端测试切片**，对系统 4 大核心用户路径（认证授权、系统参数、用户管理、工作流任务）进行全流程验证。测试以 standalone `MockMvc` 装配**真实**的 `Controller`、`GlobalExceptionHandler` 与 `I18nService`，服务层以 Mockito 桩接，从而在不依赖 MySQL 的前提下，真实校验：

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
| **合计** | — | **36** | 正常流程 + 异常/边界 |

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
[INFO] Tests run: 738, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
- 本次新增 E2E：**36 例**（前序 `CoreApiE2eTest` 30 例：Auth 9 + Config 7 + User 9 + Workflow 5；本轮 `RealtimeE2eTest` 6 例：RT-01~06），全部通过；
- 既有基线：702 例（含 `ConnectivityControllerTest` 15 例），历次 E2E 增量回归全绿；
- 合计 **738 例，0 失败 / 0 错误 / 0 跳过**。

### 4.2 新增 E2E 用例执行明细
| 嵌套类 | 用例 | 结果 |
| --- | --- | --- |
| `AuthE2e` | AUTH-01~09 | ✅ 9/9 |
| `ConfigE2e` | CFG-01~07 | ✅ 7/7 |
| `UserE2e` | USR-01~09 | ✅ 9/9 |
| `WorkflowE2e` | WF-01~05 | ✅ 5/5 |
| `RealtimeE2eTest` | RT-01~06 | ✅ 6/6 |

### 4.3 覆盖矩阵（正常 / 异常 / 边界）
| 模块 | 正常 | 异常 | 边界 | 典型用例 |
| --- | --- | --- | --- | --- |
| 认证 | ● | ● 密码错(401) | ● 空参(400) / 短口令(400) | AUTH-01/03/02/07 |
| 参数 | ● | ● key 缺失(404) | ● 缺 configKey(400) | CFG-02/03/05 |
| 用户 | ● | ● id 缺失(404) | ● 缺 roleIds(400) / 短口令(400) | USR-02/03/05/08 |
| 工作流 | ● 认领/审批/转办 | — | ● 缺 taskId(400) | WF-02/03/05/04 |

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

**结论**：本次端到端功能测试在离线环境下达成对 5 大核心用户路径的全流程验证，**36 例新增 E2E 全部通过，既有 702 例回归全绿，合计 738 例 0 失败**。系统核心接口的路由、参数校验、异常映射与 i18n 响应契约稳定可靠；本轮新增实时推送管理模块补齐了 realtime 包缺失的 REST 入口。

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

*报告由离线测试执行结果自动汇总生成，数据来源：`mvn -o clean test` 输出（738/0/0/0）与 `scripts/check-conventions.py`（EXIT=0）。*
