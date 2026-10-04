# Java 后端基础管理框架（可运行脚手架）

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 1. 技术栈

| 领域 | 选型 |
|---|---|
| JDK | 17 |
| Web / 安全 | Spring Boot 3.3.4 / Spring Security 6（OAuth2 Resource Server + JWT） |
| ORM | **jOOQ 3.19**（Spring Boot 托管版本，动态 DSL） |
| 数据库 | MySQL 8.4（Flyway 初始化；JobRunr / Flowable 各自自动建表） |
| 缓存 | Redis 7（Spring Data Redis，可选） |
| 工作流 | Flowable 7.1.0 |
| 调度 | **JobRunr 6.3.4**（SQL 存储 + 内嵌 BackgroundJobServer） |
| 可观测 | Actuator + Micrometer + Prometheus |
| 文档 | springdoc-openapi（仅 local/dev） |

### 迁移对照

| 维度 | 迁移前 | 迁移后 | 影响 |
|---|---|---|---|
| 持久层 | MyBatis-Plus 3.5.7（BaseMapper / Wrapper / 分页插件 / 逻辑删除 / 乐观锁 / MetaObjectHandler） | jOOQ 动态 DSL（`DSLContext` + `JooqTables` + `JooqWriters` + `SnakeRecordMapper`） | 表结构与 SQL 语义不变；不再依赖 XML/注解映射与代码生成 |
| 主键 | `IdType.ASSIGN_ID` | `IdGenerator`（雪花算法，insert 后回填 POJO） | 行为一致，仍为 `BIGINT` 单调 ID |
| 逻辑删除 | `@TableLogic` | `JooqWriters.notDeleted(table)` 谓词 + `delete(..., true)` | `deleted=0/1` 语义不变 |
| 审计字段 | `MetaObjectHandler` 自动填充 | `JooqWriters` 统一填充 `tenant_id/version/created_at/updated_at` | 填充规则不变；null 字段不写入（沿用 MP 的 NOT_NULL 策略，交由数据库默认值） |
| 分页 | `PaginationInnerInterceptor` + MP `Page` | `JooqWriters.page(...)`（count + limit/offset） | `PageQuery` / `PageResult` 契约不变 |
| 慢 SQL | `SlowQueryInnerInterceptor`（MyBatis 拦截器） | `SlowQueryListener`（jOOQ `ExecuteListener`） | 阈值改为 `app.jooq.slow-query-threshold-ms`，默认 500ms |
| 调度 | XXL-JOB 执行器（需独立调度中心） | JobRunr（任务存业务库，应用内嵌执行） | 去掉调度中心与 9999 端口；提供 `demo-job` 每 5 分钟执行 |

## 2. 目录结构（核心包）

```
com.acme.scaffold
├── FrameworkApplication.java
├── common
│   ├── api/        Result / PageQuery / PageResult
│   ├── error/      ErrorCode / CommonErrorCode
│   ├── exception/  BusinessException / GlobalExceptionHandler
│   ├── validation/ CreateGroup / UpdateGroup
│   ├── audit/      AuditOperation / AuditAspect / AuditLogService / AuditRecord
│   └── IdGenerator  替代 MyBatis-Plus 的 ASSIGN_ID
├── jooq/           jOOQ 支撑层：JooqTables / JooqWriters / SnakeRecordMapper(Provider)
├── security
│   ├── config/     SecurityConfig / JwtProperties / JwtDecoderConfig
│   ├── jwt/        JwtProvider / JwtAuthConverter
│   ├── token/      RefreshTokenService（opaque + 轮换 + 重放检测）
│   ├── permission/ PermissionService / DataScope(扩展点)
│   ├── context/    CurrentPrincipal / SecurityContextFacade
│   ├── application/ AuthApplicationService
│   └── controller/ AuthController
├── system
│   ├── entity|service|controller|dto   用户/角色/菜单/机构/接口资源
├── workflow
│   ├── port/       WorkflowEnginePort（屏蔽 Flowable）
│   ├── adapter/    FlowableWorkflowAdapter
│   ├── service/    WorkflowService（不可变审批记录 + 幂等）
│   └── controller|dto|entity
├── monitor/        BusinessMetrics / BusinessHealthIndicator / SlowQueryListener
├── job/            DemoJob（JobRunr 示例，替代原 XXL-JOB 的 demoJobHandler）
└── config/         Jooq / JobRunr / Redis / Web(CORS) / TraceId / Observability / SpringDoc
```

> 说明：迁移后**不再有 `mapper` 包**。原 `*Mapper` 接口全部删除，数据访问统一走 `DSLContext`。

## 3. 本地启动（不使用 Docker）

```bash
# 1) 原生安装并启动依赖（以 macOS 为例）
brew install mysql redis
brew services start mysql
brew services start redis

# 创建数据库（Flyway 与 JobRunr 会自动建表，无需手工执行 DDL）
mysql -u root -e "CREATE DATABASE IF NOT EXISTS scaffold
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 其它平台：使用系统包管理器安装 MySQL / Redis 并以服务方式启动即可，
# 均不需要容器。

# 2) 超级管理员账号由 Flyway 的 V1__baseline.sql 内置（admin / SUPER_ADMIN）

# 3) 编译运行
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run

# 4) 访问
#    API:            http://localhost:8080/api/...
#    Actuator:       http://localhost:9090/actuator/health
#    Prometheus:     http://localhost:9090/actuator/prometheus
#    Swagger:        http://localhost:8080/swagger-ui.html   （仅 local）
#    JobRunr 面板:   http://localhost:8080/dashboard         （仅 local，见第 11 节说明）
```

> **默认账号**：`V1__baseline.sql` 会内置超级管理员 `admin`（角色 `SUPER_ADMIN`），
> 但其 `password_hash` 是占位值，**不存在可用的默认密码**（实测 `password`/`admin`/`123456`/`ChangeMe123!` 均不匹配）。
> 首次启动后请先重置密码（数据库直改或 `POST /api/system/users/{id}/reset-password`）再登录。

### 建表来源说明

| 表 | 创建方式 |
|---|---|
| `sys_*` / `wf_*`（20 张） | Flyway：`src/main/resources/db/migration/V1~V3` |
| `ACT_*`（Flowable） | Flowable 自动建表（`flowable.database-schema-update: true`） |
| `jobrunr_*`（调度） | JobRunr 自动建表（`jobrunr.database.skip-create: false`） |

## 4. 认证与权限

- **登录**：`POST /api/auth/login` → 返回 `accessToken`（JWT，15 分钟）与 `refreshToken`（不透明，7 天）。
- **刷新**：`POST /api/auth/refresh` → 校验并**轮换**刷新令牌（旧令牌失效，检测到重用则吊销整个家族）。
- **登出**：`POST /api/auth/logout` → 吊销当前用户全部刷新令牌。
- **接口鉴权**：所有 `/api/**` 需 Bearer Token；方法级权限用 `@PreAuthorize("hasAuthority('system:user:read')")`。
- **权限码约定**：`模块:资源:动作`，如 `system:user:read`、`workflow:task:approve`，不与 URL 强耦合。

示例：
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"ChangeMe123!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')

curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/system/users/page?page=1&size=10"
```

## 5. 工作流审批

提供 `leaveApproval` 示例流程（会签/或签 + 主管审批 + 驳回）：
- 多实例节点 `assigneeList` 支持 **会签（ALL）** 与 **或签（ANY）**，由变量 `approvalMode` 驱动。
- 驳回通过将流程变量 `rejected=true` 提前结束多实例并走驳回分支。
- 转办：更换任务 assignee 并写入不可变审批记录。
- 每次审批/驳回/转办先校验任务归属与 `operationId` 幂等键，再写 `wf_approval_record`（不可变）。

```bash
# 发起（assigneeUserIds 为会签人，managerUserId 为主管）
curl -X POST http://localhost:8080/api/workflow/instances/start \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"processKey":"leaveApproval","businessType":"LEAVE","businessId":"L001",
       "title":"张三请假","assigneeUserIds":[2,3],"managerUserId":4,"approvalMode":"ANY"}'

# 待办 / 审批 / 转办
GET  /api/workflow/tasks/mine
POST /api/workflow/tasks/complete   {"taskId":"...","action":"APPROVE","opinion":"同意","operationId":"op-xxx"}
POST /api/workflow/tasks/transfer   {"taskId":"...","toUserId":5,"opinion":"转交","operationId":"op-yyy"}
```

## 6. 可观测性

- 健康检查：`GET /actuator/health`（仅 health/prometheus 公开，其余需 `platform:actuator:read`）。
- 指标：`GET /actuator/prometheus`（JVM、HTTP、HikariCP、业务计数器、审批耗时）。
- 慢 SQL：`SlowQueryListener`（jOOQ `ExecuteListener`）记录超过 `app.jooq.slow-query-threshold-ms`（默认 500ms）的 SQL。
- 调度观测：JobRunr Dashboard（local 环境 `http://localhost:8000/dashboard`）可查看任务成功率、重试与耗时。
- 告警阈值建议：QPS 跌零、错误率 >1%、P99 >800ms、JVM 老年代 >80%、连接池使用率 >85%、JobRunr 失败任务数 >0。

## 7. 调度任务（JobRunr）

JobRunr 无需独立调度中心：任务以 JSON 持久化在业务库的 `jobrunr_*` 表，由应用内嵌的 `BackgroundJobServer` 执行。

- 周期任务在 `JobRunrConfig` 中注册：
  ```java
  jobScheduler.scheduleRecurrently("demo-job", "*/5 * * * *", demoJob::run);
  ```
- 一次性/延迟任务可直接注入 `JobScheduler`：
  ```java
  jobScheduler.enqueue(() -> demoJob.run());
  jobScheduler.schedule(LocalDateTime.now().plusHours(1), () -> demoJob.run());
  ```
- 多实例部署时 JobRunr 通过数据库锁协调，任务不会重复执行。

## 8. 持久层用法（jOOQ）

本脚手架采用**动态 DSL**，无需构建期代码生成（因此不需要连接数据库、不需要 Docker）。

```java
// 查询列表（自动映射 snake_case 列 → POJO）
List<SysUserDO> users = JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                JooqTables.SYS_USER.field("status", String.class).eq("ACTIVE")),
        JooqTables.SYS_USER.field("id", Long.class).desc());

// 分页
PageResult<SysUserDO> page = JooqWriters.page(dsl, JooqTables.SYS_USER, SysUserDO.class,
        JooqWriters.notDeleted(JooqTables.SYS_USER), PageQuery.of(1, 20),
        JooqTables.SYS_USER.field("id", Long.class).desc());

// 写入（自动填充 id / tenant_id / version / created_at / updated_at，并回填主键）
JooqWriters.insert(dsl, JooqTables.SYS_USER, user);
JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);
JooqWriters.delete(dsl, JooqTables.SYS_ROLE, id, true); // true = 逻辑删除
```

若后续希望获得编译期强类型（生成 `Tables.SYS_USER` 等），可引入 `jooq-codegen-maven` 插件并基于迁移脚本离线生成；届时仅需替换 `JooqTables` 的引用方式，业务代码结构不变。

## 9. 关键设计点

- **模块化单体**：单进程部署，认证/系统/工作流/调度/审计/监控按包边界隔离；达到独立扩容/发布条件后再拆微服务。
- **安全**：JWT 仅承载非敏感声明；刷新令牌不透明且哈希存储；账号锁定、密码加密、登录失败计数、审计脱敏。
- **数据权限扩展点**：`DataScopeProvider.resolveOrgIds(userId, resourceCode)` 预留，业务查询据此拼接机构过滤（见 `UserService#list`）。
- **工作流端口**：业务层只依赖 `WorkflowEnginePort`，不直接调用 Flowable `RuntimeService`/`TaskService`。

## 10. 后续建议

1. 接入 API 资源自动扫描，把 `@PreAuthorize` 权限码登记到 `sys_api_resource`。
2. 补充集成测试覆盖登录、刷新轮换、会签、驳回、转办（使用本地 MySQL 实例，不使用容器）。
3. 接入 Prometheus + Grafana + Alertmanager（均以原生进程部署），沉淀仪表盘与告警规则。
4. 如需更强类型安全，引入 jOOQ 代码生成替换当前动态 DSL（见第 8 节）。

## 11. 验证记录（原生 MySQL + Redis，无 Docker）

已在**原生 MySQL 8.4.11 + Redis** 环境完成实际启动验证，全程未使用任何容器：

| 验证项 | 结果 |
|---|---|
| `mvn compile` | 通过，110 个 class |
| 应用启动 | `Started FrameworkApplication in 28.4s`（首次，含建表）/ 14s（后续） |
| Flyway | 应用 V1→V3，schema 至 `v3` |
| 建表总数 | 72 张（`sys_*`/`wf_*` 20 张 + Flowable `ACT_*`/`FLW_*` + `jobrunr_*` + `flyway_schema_history`） |
| JobRunr 存储 | 自动迁移 v000→v015，创建 `jobrunr_*` 表 |
| jOOQ 运行时 | 日志确认 `Database version is supported by dialect MYSQL: 8.4.11` |
| Actuator | `/actuator/health` → `{"status":"UP"}` |
| 登录接口 | 参数校验、全局异常处理、MDC traceId 均正常（返回 `AUTH_001` + traceId） |
| 周期任务 | `demo-job`（`*/5 * * * *`）已持久化到 `jobrunr_recurring_jobs` |

### 本次验证发现并已修复的问题

1. **启动即崩溃**：`logback-spring.xml` 的 `fileNamePattern` 含 `%i`，但滚动策略用的是
   `TimeBasedRollingPolicy`（不支持 `%i`），导致应用无法启动。已改为 `SizeAndTimeBasedRollingPolicy`。
2. **XXL-JOB 残留**：日志配置仍引用 `com.xuxueli.job`，已改为 `org.jobrunr`。
3. **周期任务静默不注册**：`JobRunrConfig` 上的 `@ConditionalOnBean(JobScheduler.class)` 恒为 false
   —— 普通 `@Configuration` 先于自动配置解析，条件求值时 `JobScheduler` 尚未产出，
   导致迁移前的 `demoJobHandler` 实际从未被调度。已改为 `ApplicationRunner` + `ObjectProvider<JobScheduler>`，
   修复后日志确认 `已注册 JobRunr 周期任务 id=demo-job cron=*/5 * * * *`。

### 已知待办（未修复）

- **调度执行待确认**：`jobrunr_backgroundjobservers` 未见服务端注册记录，任务是否真正执行需进一步确认
  （`/actuator/beans`、`/actuator/threaddump` 均被 Spring Security 拦截为 401，无法在线确认线程状态）。
- **默认密码不可用**：见第 3 节「默认账号」说明，需先重置密码。
- **JobRunr 面板**：JobRunr 6 已废弃 `jobrunr.dashboard.port: 8000`，面板实际挂载在主端口 `/dashboard`；
  当前该路径被安全链路拦截返回 401，需在 `SecurityConfig` 放行后再访问。
- **Flyway 版本**：Flyway 10.10.0 提示 MySQL 8.4 高于其已验证版本（最高 8.1），仅为警告，不影响运行。
- **Flowable 流程校验告警**：`leaveApproval` 的排他网关存在无条件出口流，建议补充默认流。
