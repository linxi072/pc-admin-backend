# java-admin-framework 后续模块开发规划

> 日期：2026-10-08 ｜ 仓库：`outputs/java-admin-framework`（main） + 前端 `pc-admin-vue3-vite`  
> 依据：`Java后端基础管理框架设计方案.md` §12 API 最小集合、§20 最小可交付验收清单；`review-java-admin-framework-2026-10-08.md`（缺陷已全部闭环）  
> 前置状态：站内信实时化（WebSocket）已落地；全部 P1/P2/P3 缺陷已修复并本地提交

---

## 一、待开发模块清单（按优先级）

| 序号 | 模块                  | 优先级 | 缺口依据                                                                                                         | 依赖的既有资产                                                                                   | 是否需新表  | 状态             |
| -- | ------------------- | --- | ------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- | ------ | -------------- |
| M1 | 审计日志查询与脱敏           | P1  | 设计 §20「审计日志可按 traceId 查询且无敏感字段」；当前 `sys_operation_log` **只写不查**，无任何查询 API                                    | `sys_operation_log`(V2)、`AuditLogService`、`JooqWriters.page`、`PageQuery/PageResult`       | 否      | ✅ 已实现（单测 19 例） |
| M2 | 幂等性 Idempotency-Key | P1  | 设计 §12「写接口建议支持 Idempotency-Key，**审批类接口必须强制使用**」；当前完全缺失                                                       | Spring AOP、`DataIntegrityViolationException` 映射、`JobRunrConfig`、统一 `Result`               | 是（V16） | ✅ 已实现（单测 11 例） |
| M3 | Token 版本与权限缓存失效     | P2  | 设计 §20「权限变更后旧权限缓存与 Token 版本**正确失效**」；`sys_user.token_version`(V1) 已建列但**全仓无引用**，JWT 有 `tokenVersion` 声明但从不校验 | `sys_user.token_version`、`JwtProvider`/`JwtAuthConverter`、`CurrentPrincipal.tokenVersion` | 否      | ✅ 已实现（单测 9 例）  |
| M4 | 工作流任务认领（claim）      | P2  | 设计 §12 API 清单 `POST /api/workflow/tasks/{id}/claim`；当前端口与任务服务**完全无 claim 能力**                                | `WorkflowEnginePort`、`FlowableWorkflowAdapter`、`WorkflowService`、`ClaimRules` 纯函数         | 否      | ✅ 已实现（单测 5 例）  |

**验证状态（沙箱）**：`mvn -o test-compile` 通过；`mvn -o test` 离线单测 **80 例全通过**（较修复前 41 例 +39）；M4 认领新增纯函数单测 5 例，离线 `mvn -o test -Dtest=ClaimRulesTest` 绿灯（总单测 85 例）。  
**待本机验证（需 MySQL/Redis）**：审计分页与 traceId 链路查询、幂等并发去重与重放、改角色后旧 token 返回 401、claim 在真实 Flowable 引擎下的认领/抢认领行为。

**暂不纳入本轮**（列为后续候选，见第五节）

---

## 二、M1 审计日志查询与脱敏（P1）

### 2.1 功能清单

1. **分页多条件查询**：按模块、操作类型、操作人、结果（成功/失败）、时间区间、traceId、关键词（操作名/请求路径）过滤。
2. **traceId 链路查询**：一次请求的全链路操作（设计 §20 明确要求）。
3. **统计摘要**：总条数 / 成功 / 失败 / 慢操作（>1000ms）/ 平均耗时 / 最大耗时。
4. **敏感字段脱敏**：请求/响应摘要中的密码类键、手机号、邮箱、身份证统一打码；IP 默认掩码，持 `system:audit:sensitive` 权限者可见原文。

### 2.2 接口定义

| 方法  | 路径                                       | 权限码                 | 入参                                                                                                                                            | 出参                                 |
| --- | ---------------------------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| GET | `/api/system/audit-logs/page`            | `system:audit:read` | page(1) size(20) module operationType operatorId operatorName success traceId keyword startTime endTime minDurationMs sortField sortDirection | `Result<PageResult<AuditLogView>>` |
| GET | `/api/system/audit-logs/trace/{traceId}` | `system:audit:read` | path 变量 traceId                                                                                                                               | `Result<List<AuditLogView>>`       |
| GET | `/api/system/audit-logs/summary`         | `system:audit:read` | startTime endTime module                                                                                                                      | `Result<AuditLogSummary>`          |

`AuditLogView`：id / moduleCode / operationType / operationName / operatorId / operatorName / requestMethod / requestPath / requestSummary(脱敏) / resultSummary(脱敏) / resultCode / durationMs / ipAddress(默认掩码) / traceId / success / occurredAt

`AuditLogSummary`：total / successCount / failureCount / slowCount / avgDurationMs / maxDurationMs

### 2.3 依赖关系

- 依赖：`common.api.PageQuery/PageResult/Result`、`jooq.JooqWriters/JooqTables.JooqSorts`、`security.context.SecurityContextFacade`、`common.error.CommonErrorCode`
- 被依赖：无（只读模块）
- 新增类：`common/audit/SensitiveMasker`、`common/audit/AuditLogQuery`、`common/audit/AuditLogView`、`common/audit/AuditLogSummary`、`system/controller/AuditLogController`；扩展 `common/audit/AuditLogService` 与其实现

### 2.4 错误处理

| 场景                  | 处理                                                                     |
| ------------------- | ---------------------------------------------------------------------- |
| startTime > endTime | `BusinessException(VALIDATION_ERROR, "开始时间不能晚于结束时间")` → 400 COMMON_001 |
| traceId 为空/空白       | `BusinessException(VALIDATION_ERROR, "traceId 不能为空")` → 400            |
| traceId 不存在         | 返回空列表（非 404，便于链路排查静默处理）                                                |
| 排序字段不在白名单           | 由 `JooqSorts.resolve` 忽略，回落默认排序（时间倒序）                                  |

### 2.5 验证方式

- 单测 `SensitiveMaskerTest`：手机号/邮箱/身份证/密码键/IP/空值/短串边界
- 单测 `AuditLogQueryTest`：时间区间校验、分页钳制、关键词 trim
- 本机（需 MySQL）：`GET /api/system/audit-logs/page?traceId=xxx` 命中链路；失败操作 `success=0` 可筛出

---

## 三、M2 幂等性 Idempotency-Key（P1）

### 3.1 功能清单

1. **注解驱动**：`@Idempotent(scope=..., ttlSeconds=..., requireKey=...)` 标注写接口。
2. **请求指纹**：`SHA-256(method + path + body)`，同 key 不同 body → 422，避免误重放。
3. **三态记录**：`PROCESSING` / `SUCCESS` / `FAILED`。命中 SUCCESS 直接重放缓存响应；命中 PROCESSING → 409；命中 FAILED 允许重试。
4. **独立事务**：幂等记录写入用 `REQUIRES_NEW`，避免随业务事务回滚丢失。
5. **过期清理**：JobRunr 周期任务删除过期记录。

### 3.2 接口定义（横切能力，无新增 REST）

- 请求头：`Idempotency-Key: <16~128 字符>`
- 强制点（requireKey=true，缺头直接 400）：
  - `POST /api/workflow/tasks/complete`（审批/驳回）
  - `POST /api/workflow/tasks/transfer`（转办）
  - `POST /api/system/messages/send`（群发）

### 3.3 表结构（V16\_\_idempotency_record.sql）

```
sys_idempotency_record(
  id, tenant_id,
  idempotency_key VARCHAR(128), api_scope VARCHAR(128),
  user_id, request_fingerprint VARCHAR(64),
  status VARCHAR(16),              -- PROCESSING / SUCCESS / FAILED
  response_snapshot JSON, error_code, error_message,
  expires_at DATETIME(3), created_at, updated_at,
  UNIQUE KEY uk_key_scope (tenant_id, idempotency_key, api_scope),
  KEY idx_expires (expires_at)
)
```

### 3.4 依赖关系

- 依赖：`spring-boot-starter-aop`（已有）、`PlatformTransactionManager`、`ObjectMapper`、`JobRunrConfig`、统一 `Result`
- 被依赖：工作流审批/转办、站内信群发
- 新增包：`common/idempotency/`（`Idempotency` 注解、`IdempotencyAspect`、`IdempotencyService`、`IdempotencyRecord`、`IdempotencyStatus`、`IdempotencyRules`、`IdempotencyErrorCode`）；`job/IdempotencyCleanupJob`

### 3.5 错误处理

| 场景                                    | 处理                                                 |
| ------------------------------------- | -------------------------------------------------- |
| 缺 `Idempotency-Key` 且 requireKey=true | `BusinessException(VALIDATION_ERROR)` → 400        |
| key 含非法字符 / 长度越界                      | `BusinessException(VALIDATION_ERROR)` → 400        |
| 同 key 请求体指纹不一致                        | `BusinessException(IDEMPOTENCY_003)` → 422         |
| 同 key 正在处理中                           | `BusinessException(IDEMPOTENCY_001)` → 409         |
| 并发同 key 抢登记                           | 捕获 `DataIntegrityViolationException` → 409（唯一索引兜底） |

### 3.6 验证方式

- 单测 `IdempotencyRulesTest`：key 合法性、指纹确定性与差异、三态判定矩阵
- 本机：对 `/complete` 连发两次同 key 同 body → 第二次返回首次快照且不产生二次审批；同 key 改 body → 422；并发 20 线程同 key → 仅 1 成功

---

## 四、M3 Token 版本与权限缓存失效（P2）

### 4.1 功能清单

1. **版本递增**：权限变更点统一 bump `sys_user.token_version`。
2. **请求校验**：JWT `tokenVersion` 声明与库中当前版本比对，不一致即判失效。
3. **轻量缓存**：进程内 TTL 缓存（默认 30s）避免每请求打库。
4. **失效响应**：401 + `AUTH_005`（凭证已失效，请重新登录）。

### 4.2 bump 触发点

| 触发点                                   | 影响范围       |
| ------------------------------------- | ---------- |
| `UserService.assignRoles`（创建/更新用户角色）  | 该用户        |
| `UserService.delete` / 状态禁用           | 该用户        |
| `RoleDataScopeService.save`（角色数据权限变更） | 持有该角色的全部用户 |
| `UserService.resetPassword`           | 该用户        |

### 4.3 接口定义

- 无新增 REST；新增过滤器 `security/token/TokenVersionVerifier`（`OncePerRequestFilter`，位于 `BearerTokenAuthenticationFilter` 之后）
- 出参变化：失效时 `{"code":"AUTH_005", ...}` HTTP 401

### 4.4 依赖关系

- 依赖：`sys_user.token_version`、`JwtProvider`（已发 `tokenVersion` 声明）、`CurrentPrincipal`、`SecurityErrorCode`
- 被依赖：`UserService`、`RoleDataScopeService`（新增构造参数，项目统一构造器注入，安全）
- 新增：`security/token/TokenVersionService`、`security/token/TokenVersionCache`、`security/token/TokenVersionVerifier`、`security/token/TokenVersionRules`

### 4.5 错误处理

| 场景                | 处理                                        |
| ----------------- | ----------------------------------------- |
| 库中用户不存在           | 视为失效 → 401（用户已被删除）                        |
| 缓存/DB 读取异常        | **fail-open**：记录 warn 并放行，避免基础设施故障导致全站不可用 |
| 声明缺失 tokenVersion | 视为 1（兼容历史 token），按库中值比对                   |

### 4.6 验证方式

- 单测 `TokenVersionRulesTest`：版本比较矩阵、缺失声明兜底、bump 影响用户集合计算
- 本机：改用户角色 → 旧 token 再请求返回 401 AUTH_005；重新登录后正常

---

## 五、后续候选（本轮未做）

- ~~工作流任务认领 `POST /api/workflow/tasks/{id}/claim`~~ —— **已作为 M4 实现**（见主表），运行期行为待本机 Flowable 验证
- 数据导出中心（需引入 POI/EasyExcel，沙箱离线仓库不可用）
- Prometheus + Grafana + Alertmanager 接入（外部基础设施）
- jOOQ 代码生成替换动态 DSL（设计 §15）

---

## 六、统一约定（与现有实现保持一致）

- 分层：`controller`（`@Tag`/`@Operation`/`@PreAuthorize`/`@AuditOperation` 四件套）→ `service`（`@RequiredArgsConstructor` 构造器注入）→ jOOQ 动态 DSL
- 响应：统一 `Result<T>`；错误码实现 `ErrorCode` 接口，按 `模块前缀 + 三位编号`
- 排序：客户端字段名经 `JooqSorts.whitelist` 白名单映射，禁止直接拼列名
- 分页：`PageQuery` 构造时钳制，禁止超大页
- 注释：中文 Javadoc，说明「为什么」而非「做什么」；状态/枚举语义写清状态机
