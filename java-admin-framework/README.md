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
│   ├── entity|service|controller|dto   用户/角色/菜单/机构(部门)/接口资源/字典/系统变量
│                                          公告(SysAnnouncement)/站内信(SysMessage + SysMessageReceipt)
├── workflow
│   ├── port/       WorkflowEnginePort（屏蔽 Flowable）
│   ├── adapter/    FlowableWorkflowAdapter
│   ├── service/    WorkflowService（不可变审批记录 + 幂等）
│   └── controller|dto|entity
├── monitor/        BusinessMetrics / BusinessHealthIndicator / SlowQueryListener
│                   RuntimeMetricsCollector(JDK 采集 CPU/内存/磁盘/JVM)
│                   MonitorService / MonitorController(运行指标+在线会话+异常日志)
├── job/            DemoJob（JobRunr 示例，替代原 XXL-JOB 的 demoJobHandler）
│                   AnnouncementExpiryJob(公告过期下线) / MonitorSampleJob(指标采样)
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
#    JobRunr 面板:   http://localhost:8080/dashboard         （仅 local，见第 15 节说明）
```

> **默认账号**：`V1__baseline.sql` 会内置超级管理员 `admin`（角色 `SUPER_ADMIN`），
> 其原始 `password_hash` 为占位死值（无对应明文）。已通过 `V4__fix_admin_password.sql`
> 将其重置为可用密码 **`admin123`**（bcrypt，`{bcrypt}` 前缀）。首次登录请使用 `admin / admin123`。

### 建表来源说明

| 表 | 创建方式 |
|---|---|
| `sys_*` / `wf_*`（20 张，V1~V4） | Flyway：`src/main/resources/db/migration/V1~V4` |
| `sys_dict_type` / `sys_dict_data` / `sys_config`（3 张） | Flyway：`V5__system_modules.sql`（字典分类/字典数据/系统变量，含种子数据） |
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

## 9. 系统管理模块：部门 / 字典 / 系统变量

本轮新增三个系统管理模块，均为「DTO 校验 → Service 业务规则 → Controller 权限 + 审计」三层结构，权限码遵循 `模块:资源:动作` 约定。

### 部门管理（复用机构表 `sys_org`）

部门即机构，**不新建冗余表**，直接补全既有 `Org` 模块的写操作：

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/system/orgs/tree` | `system:org:read` | 树形查询（含 `ancestors` 物化路径） |
| POST | `/api/system/orgs` | `system:org:create` | 新增 |
| PUT | `/api/system/orgs/{id}` | `system:org:update` | 编辑 + **层级移动** |
| DELETE | `/api/system/orgs/{id}` | `system:org:delete` | 删除（存在子部门拒绝） |

层级移动的核心是 `OrgService.recomputeSubtreeAncestors(id, newParentId)`：当 `parentId` 变更时，用 BFS 重算该节点**及其全部子孙**的 `ancestors` 物化路径再持久化；同时做防环校验——不能挂到自身或自身子孙之下，且父部门必须存在。

### 字典管理（`sys_dict_type` + `sys_dict_data`）

- `DictTypeController`（`/api/system/dict-types`）：分类 CRUD，权限码 `system:dict:read/create/update/delete`；`dict_code` 租户内唯一；**分类下存在字典数据时拒绝删除**（`CONFLICT`）。
- `DictDataController`（`/api/system/dict-data?dictType=`）：按 `dictType` 筛选的键值 CRUD，同一权限码；`(dict_type_code, dict_value)` 唯一。

### 系统变量（`sys_config`）

- `ConfigController`（`/api/system/configs`）：参数 CRUD，权限码 `system:config:read/create/update/delete`；`config_key` 租户内唯一。
- **动态读取**：`GET /api/system/configs/key/{key}`，由 `ConfigService.getByKey` 提供——先查 `ConcurrentHashMap` 缓存，命中直返；未命中查库并回填。`create/update/delete` 均同步 `cache.put/remove` 保证一致性，未命中抛 `NOT_FOUND`。
- 内置种子参数：`sys.title`、`sys.max.login.fail`、`sys.captcha.enabled`。

> 迁移脚本：`V5__system_modules.sql`（utf8mb4，`tenant_id` 默认 0，唯一键 `uk_tenant_dict_code` / `uk_tenant_config_key` 等）。

## 10. 公告 / 站内信 / 系统监控 + 用户单角色单部门收敛

本轮包含三个新增模块，以及一项**破坏性数据模型收敛**（用户不再支持多角色/多部门）。

### 10.1 系统公告（`V7__announcement.sql`）

`/api/system/announcements`，权限码 `system:announcement:read/create/update/delete`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 分页；支持 `keyword`（标题或内容模糊）、`status`、`onlyValid` |
| POST | `/`、`PUT /{id}` | 新增 / 编辑（**已发布禁止编辑**，须先下线） |
| POST | `/{id}/publish` | 发布，可带 `publishAt`（定时生效）与 `expireAt`（有效期） |
| POST | `/{id}/offline`、`/{id}/toggle-top` | 下线 / 切换置顶 |

- **状态机**：`DRAFT → PUBLISHED → OFFLINE`。
- **定时生效不依赖定时任务**：到达 `publishAt` 后由查询侧 `AnnouncementView#isEffective(now)` 实时判定可见，因此即使任务延迟也不会漏生效。`AnnouncementExpiryJob`（JobRunr `*/5`）只负责已发布公告**过期自动下线**。
- 排序：`is_top` 倒序 → `publish_at` 倒序 → `id` 倒序，置顶恒在前。
- 索引：`idx_tenant_status_pub`（状态+时间过滤）、`idx_tenant_top`（置顶排序）、`idx_expire`（过期扫描）。

### 10.2 站内信（`V8__message.sql`）

`/api/system/messages`，发送需 `system:message:send`，收件箱仅需登录。

- **双表设计**：`sys_message` 为发件主表（一条消息 = 一次发送动作），`sys_message_receipt` 为收件明细，`is_read` / `read_at` 记录已读状态与**回执时间**。
- `UNIQUE KEY uk_msg_user(tenant_id, message_id, user_id)` 保证批量投递**不重复**；`idx_user_read` 支撑「我的未读」。
- **发送**：单条用显式 `receiverIds`；批量用 `roleIds` / `orgIds` 取**并集（或关系）**，匹配 `sys_user.role_id` / `org_id` 单值列（承接 V6 收敛）。保留 `filter_role_ids` / `filter_org_ids` 筛选快照便于审计「当时按什么范围发的」。单次上限 **5000** 接收人。
- **已读跟踪**：`markRead` 幂等并回写 `read_count`；列表批量装载回执时间，避免 N+1。
- 接口：`POST /send`、`GET /mine`（`onlyUnread`，未读优先）、`GET /unread-count`、`POST /{id}/read`、`POST /read-all`、`GET /sent`。

### 10.3 系统监控（`V9__monitor_sample.sql`）

`/api/system/monitor`，权限统一 `system:monitor:read`。

- **零第三方依赖**：OSHI 不在本地 m2 仓库（禁离线引入），指标采集全部使用 **JDK Management API**。`RuntimeMetricsCollector` 中 CPU/物理内存需强转 `com.sun.management.OperatingSystemMXBean`，已加载类数取自 `ClassLoadingMXBean`（不在 `RuntimeMXBean` 上）；磁盘用 `File.getTotalSpace/getUsableSpace`；不可用指标返回 `null` 以便前端区分「无数据」。
- **在线会话口径**：`sys_refresh_token` 中未吊销且未过期，按 `session_id` 去重。
- **异常日志**：复用 `sys_operation_log` 中 `success=0` 的记录，按 `from` / `to` **时间范围**过滤，附 `errorSummary` 按模块 + 错误码聚合。
- **采样**：`MonitorSampleJob`（JobRunr `* * * * *`）周期写入 `sys_monitor_sample`，供时间范围趋势查询；`POST /sample` 可手动刷新并落库。
- 接口：`/metrics`、`/online-summary`、`/online-sessions`、`/error-logs`、`/error-summary`、`/samples`、`POST /sample`。

### 10.4 用户收敛为单角色 + 单部门（`V6__user_single_role_org.sql`）

**这是破坏性变更**：用户不再支持多角色、多部门。

- `sys_user` 新增单值列 `role_id` / `org_id`；`primary_org_id = org_id` 兼容旧读路径；`DROP TABLE sys_user_role` / `sys_user_org`（后者本就未被业务消费）。
- **回填策略（重要）**：历史多角色用户按「**数据范围最小（最严格）**」挑选角色——`CUSTOM > DEPT_AND_CHILD > DEPT/SELF > ALL`，避免收敛后权限被放大（越权）。
- 权限链同步改造：`PermissionService#roleIdsOf` 与 `DefaultDataScopeProvider#resolveOrgIds` 均改为直读 `sys_user.role_id`；删除 `SysUserRoleDO` 实体。
- DTO：`CreateUserRequest.roleId` 增加 `@NotNull`；`UserView` 由 `Set<String> roleCodes` 收敛为 `roleId` / `roleName` / `roleCode` / `orgId` / `orgName`；`UserQuery` 新增 `roleId`（`Long`）筛选，列表批量装载名称避免 N+1。

> 迁移顺序说明：V1 种子仍先写 `sys_user_role`，V6 再回填 `role_id` 后 drop，顺序安全，`admin` 保留 `SUPER_ADMIN`。

### 10.5 权限控制

- 读写分离：公告 `read` 与 `publish/update/delete` 分开，普通用户可读已发布公告但不能改；站内信发送需 `system:message:send`；监控统一 `system:monitor:read`。
- 全部写操作带 `@AuditOperation`，异常经 `GlobalExceptionHandler` 落 `sys_operation_log`，监控页即可查。
- 新增权限码沿用 `模块:资源:动作` 约定。注意：`sys_api_resource` **本身没有种子数据**（既有待办），新接口与现有接口一样，需先在「接口资源管理」页面登记并授权给角色后才能访问。

## 11. 关键设计点

- **模块化单体**：单进程部署，认证/系统/工作流/调度/审计/监控按包边界隔离；达到独立扩容/发布条件后再拆微服务。
- **安全**：JWT 仅承载非敏感声明；刷新令牌不透明且哈希存储；账号锁定、密码加密、登录失败计数、审计脱敏。
- **数据权限扩展点**：`DataScopeProvider.resolveOrgIds(userId, resourceCode)` 预留，业务查询据此拼接机构过滤（见 `UserService#list`）。
- **工作流端口**：业务层只依赖 `WorkflowEnginePort`，不直接调用 Flowable `RuntimeService`/`TaskService`。

## 12. 接口资源自动扫描登记

新增接口后，**不再需要**手工到「接口资源管理」页面逐条登记权限码。

### 背景

项目所有 Controller 方法都用 `@PreAuthorize("hasAuthority('x:y:z')")` 声明权限码，
而 `sys_api_resource` / `sys_role_api` 长期为空表 —— 意味着新接口上线后，
除非人工登记，否则角色无法被授予该权限，调用直接 403。这是本项目最大的一笔手工成本。

### 原理

`ApiResourceScanner` 注入 Spring 的 `RequestMappingHandlerMapping`，读取**应用真实生效的路由表**
（而非源码解析），因此拿到的路径已包含类级 `@RequestMapping` 前缀拼接结果，与最终对外暴露的一致：

- 权限码：正则 `has(?:Any)?Authority\(\s*'([^']+)'` 从注解中提取，并校验 `module:resource:action` 格式，非法则跳过并告警；
- 中文名：取 `@Operation(summary=...)`，缺失时回退为方法名；
- 唯一键：`METHOD + " " + path`，与表上 `uk_method_path (tenant_id, http_method, path_pattern)` 对齐。

### 接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/system/api-resources/scan` | `system:api:read` | 扫描并预览差异，**不写库** |
| POST | `/api/system/api-resources/scan/sync` | `system:api:sync` | 执行同步写库，**幂等** |

### 同步语义

| 场景 | 行为 |
|---|---|
| 库中无此 (method, path) | 新增 |
| 库中已有，但权限码/资源名/鉴权模式不一致 | **仅更新这 3 个字段**；`status` / `risk_level` 属人工维护，原样保留 |
| 完全一致 | 不触碰（避免无意义刷新 `updated_at`） |
| 库中存在但代码里已无对应接口 | **仅报告为「失效」，不自动删除** |

> **为何不自动删除失效记录**：`sys_role_api` 可能仍引用它，贸然删除会造成授权悬空。
> 需人工确认无角色引用后，在页面上删除。

### 哪些接口不落库

| 类型 | 例子 | 原因 |
|---|---|---|
| 免鉴权接口 | `/api/auth/login`、`/api/auth/refresh` | 不进授权模型，落库会让人误以为可授权 |
| 无权限码接口 | `/api/profile/**` | 走「只能操作自己数据」的行级校验，不参与权限码授权 |

若把这些接口以占位符写入 `permission_code`，会污染授权数据，故由 `shouldPersist()` 统一过滤。

### V11 迁移

`V11__api_resource_seed.sql` 一次性落库 **61 条接口资源 + SUPER_ADMIN 全量授权**。
这一步同时解开了一个死锁：要调用同步接口需要权限，而要有权限得先同步。
种子数据由扫描器真实扫描导出（`INSERT IGNORE` + `SELECT NOT EXISTS`，可重复执行）。
之后的新增接口在页面点「扫描对比 → 同步」即可自助完成。

其他角色的授权仍由「角色管理」页面显式勾选，不受 V11 影响。

## 13. 角色数据权限

控制「同一功能下，某个角色能看到哪些行」，与菜单/接口权限（控制能否发起请求）相互独立。

### 背景：此前是「有表无入口」的半成品

`sys_role_data_scope` / `sys_role_data_scope_org` 两张表自 V1 建表起**零写入方、零种子数据、零配置界面**，
导致 `DefaultDataScopeProvider` 永远查不到规则，`UserService#list` 的数据权限分支形同虚设 ——
表面上「支持数据权限」，实际任何配置都不会生效。本节补齐配置入口并修复两处越权缺陷。

### 五种范围类型

| scopeType | 含义 | 过滤条件 |
|---|---|---|
| `ALL` | 全部数据（默认） | 不加过滤条件 |
| `DEPT_AND_CHILD` | 本部门及下级 | `org_id IN (本部门 + ancestors 含本部门的部门)` |
| `DEPT` | 仅本部门 | `org_id = 本部门` |
| `SELF` | 仅本人 | `id = 当前用户`（**不按机构过滤**） |
| `CUSTOM` | 自定义部门 | `org_id IN (sys_role_data_scope_org 配置集合)` |

合并规则：命中 `ALL` 即整体放行；`SELF` 优先于机构类规则（更严格者胜）并短路；其余取并集。

### 接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/system/roles/{id}/data-scopes` | `system:role:read` | 查询该角色已配置的规则 |
| PUT | `/api/system/roles/{id}/data-scopes` | `system:role:update` | **覆盖式**保存（先删后插） |

复用既有权限码、不新增，与角色 CRUD 的读写分离一致：看数据权限不需改角色，改数据权限才需要。

写入采用覆盖式语义，前端只需提交「当前生效的完整规则」，无需做三态 diff；
表上唯一键 `uk_role_resource` 使重复保存天然幂等。

### 两处已修复的越权缺陷

**1. ancestors 子串匹配**

原实现取下级部门用 `ancestors LIKE '%orgId%'`。但 `ancestors` 是逗号分隔的物化路径（如 `0,1,11`），
子串匹配会让 `orgId=1` 命中 `ancestors='0,11,111'` —— **id=1 的部门能看到 id=11、111 这类无关机构**。

已改为两侧补逗号后匹配完整片段：

```sql
CONCAT(',', ancestors, ',') LIKE '%,1,%'
```

实测：机构 `1 / 11 / 21 / 211 / 2111` 中，旧实现把 `211`、`2111`（`21` 体系，与 `1` 无父子关系）
误判为 `1` 的后代；修复后仅命中真后代 `11`。

**2. SELF 被并入 DEPT**

原实现里 `SELF` 与 `DEPT` 共用一个分支，都只返回机构集合 —— 于是「仅本人」实际变成了「本部门所有人可见」，
比配置意图宽松。同时 `resolveOrgIds` 的「返回空集合 = 不限制」约定无法区分
「查无规则」与「命中 ALL」，解析出错会静默退化为全量可见。

已重构为显式的 `DataScopeResult(type, orgIds)` 记录类型，调用方用 `unrestricted()` / `selfOnly()` 判断，
不再依赖「空集合即不限」的隐式约定。

### 边界处理

| 场景 | 行为 |
|---|---|
| `CUSTOM` 未选部门 | 400「自定义范围必须至少选择一个部门」 |
| `CUSTOM` 选了不存在的部门 | 400 并列出具体 ID（否则规则静默失效，查不到任何数据） |
| `SELF`/`DEPT` 等传了 `orgIds` | 400，避免前端误以为生效 |
| 非法 `scopeType` | 400（枚举解析失败） |
| 角色不存在 | 404 |
| 配了机构规则但解析不出机构（如用户未挂部门） | 降级为不限制 **并打 WARN 日志**，避免整个角色查不到数据 |
| `scope_type` 库中脏数据 | 按 `ALL` 兜底 + 告警，列表接口不 500 |
| 删除角色 | 级联清理其数据权限规则与 CUSTOM 机构关联（该表无外键，漏删会留孤儿行） |

### 当前接入范围

仅 `system:user`（用户列表）实际接入了过滤。
其余资源编码已在 Provider 层预留，但需在对应 Service 中显式调用 `dataScopeProvider.resolve(...)` 才生效。
前端不提供资源自由输入，避免配出后端未接线的编码导致「配了却不生效」。

## 14. 后续建议

1. 补充集成测试覆盖登录、刷新轮换、会签、驳回、转办（使用本地 MySQL 实例，不使用容器）。
2. 接入 Prometheus + Grafana + Alertmanager（均以原生进程部署），沉淀仪表盘与告警规则。
3. 如需更强类型安全，引入 jOOQ 代码生成替换当前动态 DSL（见第 8 节）。
4. `PageQuery.sorts` 已定义但各 Service 尚未消费，排序目前一律硬编码，可考虑接入服务端白名单映射。
5. 数据权限目前仅 `system:user` 接入过滤，其余 11 个 Service 需按需显式调用
   `DataScopeProvider#resolve`；`security/permission/DataScope` 注解仍为零使用，
   可考虑改为 AOP 环绕自动注入，避免每个 Service 手工接线。

## 15. 验证记录（原生 MySQL + Redis，无 Docker）

已在**原生 MySQL 8.4.11 + Redis** 环境完成实际启动验证，全程未使用任何容器：

| 验证项 | 结果 |
|---|---|
| `mvn compile` | 通过，110 个 class |
| 应用启动 | `Started FrameworkApplication in 28.4s`（首次，含建表）/ 14s（后续） |
| Flyway | 应用 V1→V4，schema 至 `v4`（`V4` 为 admin 密码数据修复） |
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

4. **BackgroundJobServer 未注册（任务不真正执行）**：JobRunr 6.x 将配置前缀由 5.x 的 `jobrunr` 改为
   `org.jobrunr`（breaking change）。原 `jobrunr.background-job-server.enabled=true` 被静默忽略，
   导致 BJS 不启动、`jobrunr_backgroundjobservers` 表恒为 0、周期任务永不执行。
   已在 `application.yml` 与 `application-local.yml` 中将 JobRunr 配置块整体迁移到 `org.jobrunr:` 前缀下。
   修复后启动日志确认 `BackgroundJobServer ... using MySqlStorageProvider and 4 BackgroundJobPerformers started successfully`，
   `jobrunr_backgroundjobservers=1`，且 `demo-job`（`*/5 * * * *`）每 5 分钟真实执行（日志可见 `JobRunr 定时任务执行`）。

5. **admin 默认密码不可用**：`V1__baseline.sql` 中 `admin` 的 `password_hash` 是占位死值，
   经 `BCryptPasswordEncoder` 校验对任意常见明文（`password`/`admin`/`123456`/`ChangeMe123!`）均返回 false。
   新增 `V4__fix_admin_password.sql`，将其更新为 `admin123` 的 bcrypt 哈希（`{bcrypt}` 前缀）并刷新 `password_changed_at`。
   修复后 `POST /api/auth/login` 用 `admin / admin123` 返回 `200`，错误密码返回 `AUTH_001`。

### 已知待办（未修复）

- **JobRunr 面板访问**：local 环境面板已在独立端口 `:8000/dashboard` 启动（不受主安全链路 `/dashboard` 路径拦截影响），可直接访问。
- **Flyway 版本**：Flyway 10.10.0 提示 MySQL 8.4 高于其已验证版本（最高 8.1），仅为警告，不影响运行。
- **Flowable 流程校验告警**：`leaveApproval` 的排他网关存在无条件出口流，建议补充默认流。

### 沙箱/隔离环境验证注意事项

- **Redis 必须运行**：`/actuator/health` 聚合了 `redis` 指示器，若 Redis 未启动则整体 `DOWN`（HTTP 503）。
  本地验证前请先 `redis-server --port 6379 --bind 127.0.0.1` 启动实例（本仓库验证用隔离 datadir，不污染用户环境）。
- **Netty DNS 解析**：沙箱内 Netty 异步 DNS 解析器对 `localhost`/`127.0.0.1` 均可能报 `<unresolved>`，
  导致响应式 `RedisReactiveHealthIndicator` 失败。启动应用请加 JVM 参数
  `-Dio.netty.resolver.dns.useJdkResolver=true` 全局回退到 JDK 解析器。
- **本地代理占用端口**：WorkBuddy 沙箱会在 `127.0.0.1:<port>`（IPv4）注入一层 Express 反向代理，
  直接 `curl 127.0.0.1:<port>` 会命中代理并返回 `{"error":{"code":"AUTH_REQUIRED"...}}`（非本应用响应）。
  验证时请改走 IPv6 回环直连：`curl http://[::1]:<port>/... --noproxy '*'`。
