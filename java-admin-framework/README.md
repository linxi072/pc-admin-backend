# Java 后端基础管理框架（可运行脚手架）

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 1. 技术栈

| 领域       | 选型                                                                  |
| -------- | ------------------------------------------------------------------- |
| JDK      | 17                                                                  |
| Web / 安全 | Spring Boot 3.3.4 / Spring Security 6（OAuth2 Resource Server + JWT） |
| ORM      | **jOOQ 3.19**（Spring Boot 托管版本，动态 DSL）                              |
| 数据库      | MySQL 8.4（Flyway 初始化；JobRunr / Flowable 各自自动建表）                     |
| 缓存       | Redis 7（Spring Data Redis，可选）                                       |
| 工作流      | Flowable 7.1.0                                                      |
| 调度       | **JobRunr 6.3.4**（SQL 存储 + 内嵌 BackgroundJobServer）                  |
| 可观测      | Actuator + Micrometer + Prometheus                                  |
| 文档       | springdoc-openapi（仅 local/dev）                                      |

### 迁移对照

| 维度    | 迁移前                                                                              | 迁移后                                                                            | 影响                                               |
| ----- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------ |
| 持久层   | MyBatis-Plus 3.5.7（BaseMapper / Wrapper / 分页插件 / 逻辑删除 / 乐观锁 / MetaObjectHandler） | jOOQ 动态 DSL（`DSLContext` + `JooqTables` + `JooqWriters` + `SnakeRecordMapper`） | 表结构与 SQL 语义不变；不再依赖 XML/注解映射与代码生成                 |
| 主键    | `IdType.ASSIGN_ID`                                                               | `IdGenerator`（雪花算法，insert 后回填 POJO）                                            | 行为一致，仍为 `BIGINT` 单调 ID                           |
| 逻辑删除  | `@TableLogic`                                                                    | `JooqWriters.notDeleted(table)` 谓词 + `delete(..., true)`                       | `deleted=0/1` 语义不变                               |
| 审计字段  | `MetaObjectHandler` 自动填充                                                         | `JooqWriters` 统一填充 `tenant_id/version/created_at/updated_at`                   | 填充规则不变；null 字段不写入（沿用 MP 的 NOT_NULL 策略，交由数据库默认值）  |
| 分页    | `PaginationInnerInterceptor` + MP `Page`                                         | `JooqWriters.page(...)`（count + limit/offset）                                  | `PageQuery` / `PageResult` 契约不变                  |
| 慢 SQL | `SlowQueryInnerInterceptor`（MyBatis 拦截器）                                         | `SlowQueryListener`（jOOQ `ExecuteListener`）                                    | 阈值改为 `app.jooq.slow-query-threshold-ms`，默认 500ms |
| 调度    | XXL-JOB 执行器（需独立调度中心）                                                             | JobRunr（任务存业务库，应用内嵌执行）                                                         | 去掉调度中心与 9999 端口；提供 `demo-job` 每 5 分钟执行           |

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
#    JobRunr 面板:   http://localhost:8080/dashboard         （仅 local，见第 16 节说明）
```

> **默认账号**：`V1__baseline.sql` 会内置超级管理员 `admin`（角色 `SUPER_ADMIN`），  
> 其原始 `password_hash` 为占位死值（无对应明文）。已通过 `V4__fix_admin_password.sql`  
> 将其重置为可用密码 **`admin123`**（bcrypt，`{bcrypt}` 前缀）。首次登录请使用 `admin / admin123`。

### 建表来源说明

| 表                                                     | 创建方式                                                   |
| ----------------------------------------------------- | ------------------------------------------------------ |
| `sys_*` / `wf_*`（20 张，V1~V4）                          | Flyway：`src/main/resources/db/migration/V1~V4`         |
| `sys_dict_type` / `sys_dict_data` / `sys_config`（3 张） | Flyway：`V5__system_modules.sql`（字典分类/字典数据/系统变量，含种子数据）  |
| `sys_user_role` / `sys_user_org`（2 张，纯 N:N 中间表）     | Flyway：`V15__user_role_org_many_to_many.sql`（回退 V6 单列收敛，可重跑） |
| `ACT_*`（Flowable）                                     | Flowable 自动建表（`flowable.database-schema-update: true`） |
| `jobrunr_*`（调度）                                       | JobRunr 自动建表（`jobrunr.database.skip-create: false`）    |

### 用户-角色 / 用户-部门：纯多对多模型（V15，回退 V6 收敛）

用户与角色、用户与部门均为**纯 N:N**（中间表 `sys_user_role`、`sys_user_org`），不再保留 `sys_user` 上的 `role_id` / `org_id` / `primary_org_id` 单列。

- `V6__user_single_role_org.sql` 曾将多对多收敛为单列（理由：避免权限并集越权）。`V15__user_role_org_many_to_many.sql` 回退该收敛，全程用 `information_schema` 守卫、可重复执行：
  - 幂等建 `sys_user_role`（`uk_user_role(user_id,role_id)` + `idx`）、`sys_user_org`（`uk_user_org(user_id,org_id)` + `idx`）；
  - 把 `sys_user` 的 `role_id` / `org_id` / `primary_org_id` 数据回填进关联表（`is_primary=1`），再 `DROP` 这三列及 `idx_tenant_role` / `idx_tenant_org` 索引。
- **权限聚合取并集**：`PermissionService.roleIdsOf(userId)` 改为从 `sys_user_role` 读取该用户全部角色 ID 取并集；JWT 层 `JwtAuthConverter` 本就按逗号 `split` 成 `Set`，多角色天然就绪。
- **主维度**：`primaryRoleId` / `primaryDeptId` 标记「主」角色 / 部门（传空取集合首位），用于权限聚合与数据范围（如 `DEPT` 类数据权限依赖主部门）。
- **数据权限联动**：`DefaultDataScopeProvider`、`DataScopeConditions` 的部门过滤与 `orgOfUser` 改走 `sys_user_org`（`is_primary=1`）子查询；`AssigneeResolver`、站内信收件解析（`MessageService.resolveReceivers`）同步基于关联表。
- **改动面**：`JooqTables`（注册 `SYS_USER_ROLE` / `SYS_USER_ORG`、移除 `SYS_USER` 三列）、`SysUserDO`、`PermissionService`、`DefaultDataScopeProvider`、`AssigneeResolver`、`MessageService`、`DataScopeConditions`、`ProfileView` / `ProfileService`、`UserView` / `UserService` 及 `CreateUserRequest` / `UpdateUserRequest` DTO。

> 部门树 + 用户多部门归属 + `SUPER_ADMIN` 角色/菜单关联的种子见仓库根 `permission-schema.sql`（第 9–10 节），为可独立执行的初始化脚本，与 Flyway V15 互补。

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

内置流程（均改造为**可编辑设计**，不再依赖 classpath 静态 BPMN）：

| 内置流程 | processKey | 默认节点链 | 受让人 |
|---|---|---|---|
| 请假审批 | `leaveApproval` | 发起 → 会签/或签(ALL) → 主管审批(ALL) → 审批通过（任一驳回即终止） | 写死 USER=1（占位，请改真实审批人/角色） |
| 报销审批 | `expense` | 发起 → 部门主管审批(ALL) → 财务审批(ALL) → 审批通过（任一驳回即终止） | 部门主管=发起人主管；财务=ROLE `FINANCE` |

- 全部内置流程由 `BuiltinWorkflowSeeder` 在应用启动时写入 `wf_workflow_design` 并发布；节点顺序、受让人、审批模式（会签/或签/比例）、驳回策略均可在「工作流设计」后台二次编辑并一键发布即时生效。
- 审批节点为**多实例**：会签阈值由设计解析（`node_<id>_threshold`），驳回通过将 `rejected_<id>=true` 提前结束多实例并走驳回分支。
- 转办：更换任务 assignee 并写入不可变审批记录。
- 认领（claim）：仅适用于**尚未指定办理人（assignee 为空）的池化任务**；已被他人认领返回 409，已是自己认领则幂等成功。认领天然幂等，不强制 `Idempotency-Key`。
- 每次审批/驳回/转办先校验任务归属与 `operationId` 幂等键，再写 `wf_approval_record`（不可变）。
- **前端发起联动**：`InstanceView` 发起表单按 `processKey` 调用 `GET /api/workflow/definitions/published/{key}`；若设计已发布，则隐藏「审批人/主管」选择框（受让人由设计解析），仅当设计未发布（草稿）才显示手动选择框。
- **业务表单动态渲染**：已发布设计携带 `formSchema`（JSON，描述字段 key/标签/类型/必填/选项）。发起表单读取该 schema，按类型（文本/多行/数字/日期/下拉/开关）动态渲染业务字段，提交时将字段值收集进 `formFields` 随流程变量下发，无需在前端硬编码。内置 `leaveApproval`（请假类型/天数/事由）、`expense`（金额/类别/说明）均已预置 schema。
- **动态业务字段必填强制校验**：发起表单提交时，对 `formSchema` 中 `required=true` 的字段执行强制拦截——若必填字段为空或缺失（数字 0 视为有效、开关类须为开启态），阻止提交并以 `ElMessage` 提示具体缺失的字段标签，同时在对应字段下方显示内联错误；待全部必填项校验通过后才会真正发起流程。

```bash
# 发起（受让人/驳回策略来自已发布设计；业务字段经 formFields 透传，键名需与设计 schema 的 field 一致）
curl -X POST http://localhost:8080/api/workflow/instances/start \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"processKey":"leaveApproval","businessType":"LEAVE","businessId":"L001",
       "title":"张三请假",
       "formFields":{"leaveType":"年假","days":3,"reason":"回家探亲"}}'

# 查询某流程是否已发布（前端联动判定依据）
GET /api/workflow/definitions/published/{processKey}

# 待办 / 审批 / 转办 / 认领
GET  /api/workflow/tasks/mine
POST /api/workflow/tasks/complete   {"taskId":"...","action":"APPROVE","opinion":"同意","operationId":"op-xxx"}
POST /api/workflow/tasks/transfer   {"taskId":"...","toUserId":5,"opinion":"转交","operationId":"op-yyy"}
POST /api/workflow/tasks/{taskId}/claim   # 认领池化任务（无办理人）；已属他人→409，已属自己→幂等成功
```

> 内置流程与「自定义工作流」共用同一套设计/发布机制，详见 [第 16 节](#16-自定义工作流动态设计器)。

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

| 方法     | 路径                      | 权限码                 | 说明                       |
| ------ | ----------------------- | ------------------- | ------------------------ |
| GET    | `/api/system/orgs/tree` | `system:org:read`   | 树形查询（含 `ancestors` 物化路径） |
| POST   | `/api/system/orgs`      | `system:org:create` | 新增                       |
| PUT    | `/api/system/orgs/{id}` | `system:org:update` | 编辑 + **层级移动**            |
| DELETE | `/api/system/orgs/{id}` | `system:org:delete` | 删除（存在子部门拒绝）              |

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

| 方法   | 路径                                 | 说明                                            |
| ---- | ---------------------------------- | --------------------------------------------- |
| GET  | `/page`                            | 分页；支持 `keyword`（标题或内容模糊）、`status`、`onlyValid` |
| POST | `/`、`PUT /{id}`                    | 新增 / 编辑（**已发布禁止编辑**，须先下线）                     |
| POST | `/{id}/publish`                    | 发布，可带 `publishAt`（定时生效）与 `expireAt`（有效期）      |
| POST | `/{id}/offline`、`/{id}/toggle-top` | 下线 / 切换置顶                                     |


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
  `ApiResourceAutoSyncRunner` 会在启动时自动登记新接口（如 `system:audit:read`、`system:audit:sensitive`），但**不会自动授权**给任何角色——需管理员在「角色管理」中显式勾选后前端才可访问。

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

| 方法   | 路径                                    | 权限码               | 说明              |
| ---- | ------------------------------------- | ----------------- | --------------- |
| GET  | `/api/system/api-resources/scan`      | `system:api:read` | 扫描并预览差异，**不写库** |
| POST | `/api/system/api-resources/scan/sync` | `system:api:sync` | 执行同步写库，**幂等**   |

### 同步语义

| 场景                    | 行为                                                |
| --------------------- | ------------------------------------------------- |
| 库中无此 (method, path)   | 新增                                                |
| 库中已有，但权限码/资源名/鉴权模式不一致 | **仅更新这 3 个字段**；`status` / `risk_level` 属人工维护，原样保留 |
| 完全一致                  | 不触碰（避免无意义刷新 `updated_at`）                         |
| 库中存在但代码里已无对应接口        | **仅报告为「失效」，不自动删除**                                |

> **为何不自动删除失效记录**：`sys_role_api` 可能仍引用它，贸然删除会造成授权悬空。  
> 需人工确认无角色引用后，在页面上删除。

### 哪些接口不落库

| 类型     | 例子                                    | 原因                        |
| ------ | ------------------------------------- | ------------------------- |
| 免鉴权接口  | `/api/auth/login`、`/api/auth/refresh` | 不进授权模型，落库会让人误以为可授权        |
| 无权限码接口 | `/api/profile/**`                     | 走「只能操作自己数据」的行级校验，不参与权限码授权 |

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

| scopeType        | 含义       | 过滤条件                                       |
| ---------------- | -------- | ------------------------------------------ |
| `ALL`            | 全部数据（默认） | 不加过滤条件                                     |
| `DEPT_AND_CHILD` | 本部门及下级   | `org_id IN (本部门 + ancestors 含本部门的部门)`      |
| `DEPT`           | 仅本部门     | `org_id = 本部门`                             |
| `SELF`           | 仅本人      | `id = 当前用户`（**不按机构过滤**）                    |
| `CUSTOM`         | 自定义部门    | `org_id IN (sys_role_data_scope_org 配置集合)` |

合并规则：命中 `ALL` 即整体放行；`SELF` 优先于机构类规则（更严格者胜）并短路；其余取并集。

### 接口

| 方法  | 路径                                   | 权限码                  | 说明              |
| --- | ------------------------------------ | -------------------- | --------------- |
| GET | `/api/system/roles/{id}/data-scopes` | `system:role:read`   | 查询该角色已配置的规则     |
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

| 场景                         | 行为                                      |
| -------------------------- | --------------------------------------- |
| `CUSTOM` 未选部门              | 400「自定义范围必须至少选择一个部门」                    |
| `CUSTOM` 选了不存在的部门          | 400 并列出具体 ID（否则规则静默失效，查不到任何数据）          |
| `SELF`/`DEPT` 等传了 `orgIds` | 400，避免前端误以为生效                           |
| 非法 `scopeType`             | 400（枚举解析失败）                             |
| 角色不存在                      | 404                                     |
| 配了机构规则但解析不出机构（如用户未挂部门）     | 降级为不限制 **并打 WARN 日志**，避免整个角色查不到数据       |
| `scope_type` 库中脏数据         | 按 `ALL` 兜底 + 告警，列表接口不 500               |
| 删除角色                       | 级联清理其数据权限规则与 CUSTOM 机构关联（该表无外键，漏删会留孤儿行） |

### 当前接入范围

| 资源编码          | 接入位置               | 过滤维度                                      |
| ------------- | ------------------ | ----------------------------------------- |
| `system:user` | `UserService#list` | `org_id IN (可见机构)`；`SELF` 退化为 `id = 当前用户` |
| `system:org`  | `OrgService#tree`  | `id IN (可见机构)`，父节点不可见时升为森林根（避免整棵子树丢失）     |

其余资源编码已在 Provider 层预留，按需加 `@DataScope(resourceCode=...)` 并在查询内调用  
`DataScopeConditions.of(...)` 即可生效（见下「切面自动注入」）。  
前端不提供资源自由输入，避免配出后端未接线的编码导致「配了却不生效」。

### 切面自动注入（DataScopeAspect）

早期实现里 `DataScope` 注解零使用，每个需要过滤的 Service 都要自己调  
`DataScopeProvider#resolve(userId, resourceCode)`、自己判空、自己拼条件 —— 漏一处就是越权。  
现改为 AOP 环绕自动注入：

1. 在查询方法上标注 `@DataScope(resourceCode = "system:xxx")`  
   （支持方法级或类级 `@DataScope`，类级对内部所有公开方法生效）。
2. `DataScopeAspect` 在方法执行前解析范围：
   - 有登录主体 → `dataScopeProvider.resolve(userId, resourceCode)`；
   - 无登录主体（定时任务 / 内部调用）→ 记 DEBUG 日志并按「不限制」处理  
     （这类调用没有「谁在看」的语义，强行套用户范围会让定时任务查不到数据）。  
     解析结果写入 `DataScopeContext`（ThreadLocal），方法结束后 `finally` 清理，不跨请求、不进线程池任务。
3. Service 方法内部只消费条件：`DataScopeConditions.of(table, orgColumn[, userColumn])`  
   把上下文翻译成 jOOQ 查询条件，无需改动任何上层方法签名。

`DataScopeConditions` 的翻译规则（与 `DefaultDataScopeProvider` 解析语义一一对应）：

| 范围                                   | 条件                                                        |
| ------------------------------------ | --------------------------------------------------------- |
| 无上下文 / `ALL`                         | 不加条件                                                      |
| `SELF`                               | 有「本人」列时 `userColumn = 当前用户`；无则退化为「本人所属部门」子查询（用户未挂部门则查不到行） |
| `DEPT` / `DEPT_AND_CHILD` / `CUSTOM` | `orgColumn IN (可见机构集合)`                                   |
| 机构集合为空                               | 不加条件（与 Provider「配空集合降级为不限」一致）                             |
| 表上不存在目标列                             | WARN 不加条件（配置错误可观测，但不让列表 500）                              |

新增一个受控资源只需：① 确保该 `resource_code` 在 Provider 层可被解析；  
② 在目标查询方法加 `@DataScope(resourceCode=...)` 并调用 `DataScopeConditions.of(...)`。  
无需再手工接线 provider / 判空 / 拼条件。

## 14. 服务端排序（白名单映射）

`PageQuery.sorts` 此前已定义但没有任何 Service 消费，各列表排序一律硬编码、前端点了表头也不生效。  
本次接入白名单映射：前端可以指定**字段名**，但**不能直接指定列名**。

### 契约

- 请求参数：`sortField`（camelCase 字段名）+ `sortDirection`（`ASC`/`DESC`，缺省按 `ASC`）。
- 映射声明：每个 Service 内用 `JooqSorts.whitelist("id","id", ...)` 声明「字段名 → 列名」。
- 解析规则（`JooqSorts#resolve`）：

| 场景                         | 行为                                           |
| -------------------------- | -------------------------------------------- |
| 字段在白名单内                    | 映射为列名，生成 `ORDER BY 列 ASC/DESC`               |
| 字段不在白名单 / 表上不存在该列          | **忽略**，回落服务端默认排序（不报错，免得前端默认列把接口打挂）           |
| 同一字段重复出现                   | 只取一次                                         |
| 排序字段超过 3 个                 | 截断为 3 个，避免超长 ORDER BY                        |
| 未传 `sortField`             | 回落默认排序                                       |
| `sortDirection` 非 ASC/DESC | 400（`COMMON_001`「排序方向仅支持 ASC/DESC」），不静默降级成升序 |
| 命中客户端排序                    | 末位追加 `id DESC` 兜底，避免并列行在翻页时重复/漏行             |

### 当前接入范围

| 接口                                   | 白名单字段                                                                    | 默认排序                                            |
| ------------------------------------ | ------------------------------------------------------------------------ | ----------------------------------------------- |
| `GET /api/system/users/page`         | id / username / displayName / status / createdAt                         | `id DESC`                                       |
| `GET /api/system/announcements/page` | id / title / status / isTop / publishAt / expireAt / createdAt           | 置顶优先 + `publish_at DESC NULLS LAST` + `id DESC` |
| `GET /api/system/monitor/error-logs` | id / moduleCode / operationName / operatorName / durationMs / occurredAt | `occurred_at DESC`                              |

其余列表（字典 / 菜单 / 接口资源 / 角色 / 部门）是一次性全量返回、由前端分页排序，不涉及服务端排序。

> 顺带修复：`SnakeRecordMapper` 未处理 JSON 列，`sys_operation_log.request_summary` 映射进 `String`  
> 字段时抛 `argument type mismatch`，导致异常日志接口「一旦有数据就 500」。已按 `JSON#data()` 取文本。

## 15. 后续建议

> 2026-10-08 更新：设计方案 §20 中「审计日志可按 traceId 查询」「写接口幂等」「Token 版本失效」三项
> 已分别落地为第 17、18、19 节；下方为剩余建议。

1. 补充集成测试覆盖登录、刷新轮换、会签、驳回、转办（使用本地 MySQL 实例，不使用容器）。
1.1. 幂等与 Token 版本的**运行期行为**（并发同 key 去重、改角色后旧 token 401）同样依赖 MySQL/Redis，需本机验证。
2. 接入 Prometheus + Grafana + Alertmanager（均以原生进程部署），沉淀仪表盘与告警规则。
3. 如需更强类型安全，引入 jOOQ 代码生成替换当前动态 DSL（见第 8 节）。
4. （已完成）数据权限切面自动注入已落地：`DataScope` 注解 + `DataScopeAspect` + `DataScopeContext` /  
   `DataScopeConditions` 替代逐 Service 手工接线，已接入 `system:user` 与 `system:org`。  
   后续新增受控资源只须在查询方法加 `@DataScope(resourceCode=...)` 并调用 `DataScopeConditions.of(...)`，  
   不再需要手工调用 `DataScopeProvider#resolve`。

## 16. 自定义工作流（动态设计器）

在原有 `leaveApproval` 示例流程之外，新增「自定义工作流」能力：用户可按实际业务在后台动态**新增 / 删除 / 排序审批节点与执行步骤**、配置**条件分支**，保存为草稿并一键发布，发布后对新发起的实例**实时生效**，在途实例沿用原版本不受影响。

### 16.1 能力与节点类型

| 节点类型 | 说明 | 关键配置 |
| --- | --- | --- |
| `START` | 流程发起（内置，唯一） | — |
| `END` | 流程结束（≥1） | — |
| `APPROVAL` | 审批节点 | 审批模式 `ANY`/`ALL`/`RATIO`、受让人 `USER`/`ROLE`/`ORG`/`INITIATOR`/`INITIATOR_MANAGER`、驳回策略 `NONE`/`PREVIOUS`/`END`/`SPECIFIC` |
| `SERVICE` | 执行步骤（自动动作） | `stepType` / `stepConfig`，由 `GenericStepDelegate` 执行 |
| `CC` | 抄送 | `assigneeExpression`（抄送人 ID 列表），由 `CcStepDelegate` 执行 |
| `GATEWAY` | 条件分支（排他网关） | 出边配置 `conditionExpression`（EL）或指定一条 `isDefault` 默认分支 |

节点可通过设计器**上移/下移**调整顺序；连线（edge）决定流转走向，条件分支节点的多条出边可分别配置条件表达式，至多一条为默认分支（无符合条件时走默认）。

### 16.2 架构与生效机制

- **单一可信源**：`wf_workflow_design`（`V13`）存储 `process_key`、节点/连线 JSON、生成的 BPMN、部署信息、版本与状态，**不再写入**遗留的 `wf_definition_ext` / `wf_node_config`（留作历史表，未参与新链路）。
- **运行时生成 BPMN**：`BpmnWorkflowBuilder` 将「节点 + 连线」编译为合法 BPMN 2.0 XML（`APPROVAL` → 多实例 userTask，`SERVICE`/`CC` → serviceTask，`GATEWAY` → 排他网关，驳回策略 → 额外驳回网关分支），经 `WorkflowEnginePort#deploy` 部署为新版本流程定义。
- **实时生效**：同一 `processKey` 多次发布形成版本链；新发起实例经 `WorkflowService#start` 路由到「已发布」设计，由 `WorkflowDesignService#buildStartVariables` 解析各节点受让人、计算会签阈值（`node_<id>_threshold`）并注入业务表单字段；在途实例沿用其原版本。
- **端口/适配器不变**：业务层仍只依赖 `WorkflowEnginePort`；内置 `leaveApproval` / `expense` 本身也是写入 `wf_workflow_design` 的可编辑设计，所有流程（内置/自定义）统一经 `getPublished(processKey)` 路由到「已发布」设计后发起，互不影响。
- **内置流程播种**：`BuiltinWorkflowSeeder`（`@Order(200)`，`seedAll` 注册表式）在启动时为每个内置流程巡检：若已发布则跳过；否则创建草稿，仅当草稿仍 pristine（无节点且 version=0，即未被用户编辑/发布过）时写入内置节点并发布。**新增内置流程只需在 `seedAll` 追加一条 `{key, name, desc, designer}`，并视情况补 `BuiltinWorkflowSeederTest` 断言。**
- **前端发起联动**：`InstanceView` 发起表单按所选 `processKey` 调用 `GET /api/workflow/definitions/published/{key}`，以「设计是否已发布」作为隐藏「审批人/主管」选择框的判定条件——已发布则隐藏（受让人由设计解析，提交时不传 `assigneeUserIds`/`managerUserId`）；未发布（草稿）则显示供手动选择。同一判定还驱动**业务表单动态渲染**：已发布设计携带 `formSchema`，发起表单按字段类型（文本/多行/数字/日期/下拉/开关）动态渲染并收集为 `formFields` 下发。
- **设计器节点受让人预览（接已发布判定 + 用户/角色名反查）**：`WorkflowDesigner` 编辑节点时，属性面板底部「受让人预览」会调用 `getPublishedDefinition(processKey)` 取得权威已发布版本，给出状态提示（已发布 vN 生效中 / 未发布草稿预览），并同时列出**草稿受让人**与（已发布时）**生效版本受让人**的对比，便于发布前确认差异。预览中的受让人标识会经**用户/角色名反查**解析为真实姓名/角色名：设计器加载时调用 `GET /api/system/users/page` 与 `GET /api/system/roles` 构建「用户ID→姓名」「角色编码→角色名」映射，`USER`/`CC` 的 ID 列表与 `ROLE` 的编码列表在预览时替换为 `张三、李四`、`财务` 等可读名称（未命中时回退为原始标识），`INITIATOR`/`INITIATOR_MANAGER` 仍显示语义标签。
- **设计器业务表单字段编辑**：设计器新增「业务表单字段」编辑卡片，可增删字段（字段 key / 标签 / 类型 / 必填 / 占位提示或下拉选项），保存/发布时序列化进 `formSchema` 一并提交，供发起表单动态渲染。
- **动态业务字段必填强制校验**：发起表单 `submitStart` 在提交前对 `formSchema` 中 `required=true` 的字段做强制校验——必填为空或缺失（数字 `0` 视为有效、开关须为开启态）则拦截提交，弹窗式 `ElMessage` 提示具体缺失字段标签，并在字段下方展示内联错误；切换流程定义时重置校验态，待全部必填项通过才真正发起。

### 16.3 关键文件

```
workflow/
├── bpmn/BpmnWorkflowBuilder.java        # 节点+连线 -> BPMN 2.0 XML
├── bpmn/WorkflowDesignValidator.java    # 图结构校验（唯一 START、可达 END、死路、网关条件）
├── assignee/AssigneeResolver.java       # 受让人解析（USER/ROLE/ORG/INITIATOR/INITIATOR_MANAGER）
├── adapter/step/GenericStepDelegate.java# 执行步骤
├── adapter/step/CcStepDelegate.java     # 抄送
├── service/WorkflowDesignService.java   # 草稿 CRUD / 发布 / 取消发布 / 发起变量构建
├── controller/WorkflowDefinitionController.java
└── model/WorkflowNodeType.java
```

### 16.4 接口

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/workflow/definitions` | `workflow:definition:create` | 新建草稿 |
| PUT | `/api/workflow/definitions/{id}` | `workflow:definition:update` | 保存草稿（全量覆盖节点与连线） |
| GET | `/api/workflow/definitions` | `workflow:definition:read` | 列表 |
| GET | `/api/workflow/definitions/{id}` | `workflow:definition:read` | 详情 |
| DELETE | `/api/workflow/definitions/{id}` | `workflow:definition:delete` | 删除草稿 |
| POST | `/api/workflow/definitions/{id}/publish` | `workflow:definition:publish` | 发布（校验→生成 BPMN→部署→生效） |
| POST | `/api/workflow/definitions/{id}/unpublish` | `workflow:definition:publish` | 取消发布 |
| GET | `/api/workflow/definitions/{id}/bpmn` | `workflow:definition:read` | BPMN 预览 |

### 16.5 验证状态

| 验证项 | 结果 | 环境 |
| --- | --- | --- |
| `mvn -o clean compile` | ✅ 通过 | 沙箱（可做） |
| `BpmnWorkflowBuilderTest`（3 例：合法图可解析 / 双 START 拒绝 / 死路拒绝） | ✅ 通过 | 沙箱（可做） |
| `npm run build`（设计器 + 列表视图） | ✅ 通过 | 沙箱（可做） |
| 端到端发布→发起→审批（真实 MySQL + Flowable） | ⏳ 待本机执行 | 本机/外部依赖（沙箱无 MySQL） |

### 16.6 本机端到端验证清单

```bash
# 1) 新建草稿
curl -X POST http://localhost:8080/api/workflow/definitions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"processKey":"purchase","processName":"采购审批","description":"示例"}'

# 2) 保存设计（节点 + 连线；参考 WorkflowDesignView 结构）
curl -X PUT http://localhost:8080/api/workflow/definitions/1 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"processName":"采购审批","description":"示例",
       "nodes":[{"id":"start","type":"START","name":"发起"},
                {"id":"a1","type":"APPROVAL","name":"主管","approvalMode":"ANY","assigneeType":"ROLE","assigneeExpression":"MANAGER","rejectPolicy":"PREVIOUS"},
                {"id":"end","type":"END","name":"结束"}],
       "edges":[{"id":"e1","sourceNodeId":"start","targetNodeId":"a1"},
                {"id":"e2","sourceNodeId":"a1","targetNodeId":"end"}]}'

# 3) 发布
curl -X POST http://localhost:8080/api/workflow/definitions/1/publish -H "Authorization: Bearer $TOKEN"

# 4) 用自定义流程发起（受让人由设计解析，无需传 assigneeUserIds）
curl -X POST http://localhost:8080/api/workflow/instances/start \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"processKey":"purchase","businessType":"PURCHASE","businessId":"P001","title":"采购申请","assigneeUserIds":[1],"managerUserId":1}'
```

## 17. 审计日志查询与脱敏

补齐设计方案 §20「审计日志可按 traceId 查询且无敏感字段」。此前 `sys_operation_log` 由 `AuditAspect`
**只写不查**，管理员无法在系统中追溯操作，只能直接连库。

### 17.1 接口

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/api/system/audit-logs/page` | `system:audit:read` | 分页多条件查询（模块/操作类型/操作人/结果/traceId/关键词/耗时/时间区间） |
| GET | `/api/system/audit-logs/trace/{traceId}` | `system:audit:read` | 一次请求的完整操作链路，按发生时间正序 |
| GET | `/api/system/audit-logs/summary` | `system:audit:read` | 总数/成功/失败/慢操作(>1s)/平均与最大耗时 |

### 17.2 脱敏策略

`SensitiveMasker` 为纯函数工具，按「先抹凭证键、再抹 PII 值」两步处理：

- 凭证键（`password`/`token`/`refresh_token`/`client_secret`/`pin`…）整段值替换为 `******`，
  兼容 camelCase / snake_case / kebab-case 三种命名；
- 手机号 `138****1234`、邮箱 `z***@example.com`、身份证保留后 4 位；
- IP 保留网段 `192.168.*.*`，IPv6 与代理链整体掩码。

摘要默认脱敏；持有 `system:audit:sensitive` 的主体可见原文（IP 仍掩码）。
该表无 `deleted` 列——审计数据不允许逻辑删除，归档由独立运维流程负责。

## 18. 幂等性（Idempotency-Key）

补齐设计方案 §12「写接口支持 Idempotency-Key，**审批类接口必须强制使用**」。

### 18.1 用法

客户端在写请求上带 `Idempotency-Key: <8~128 位字母数字_- >`（建议 UUID）：

- 首次请求：登记 `PROCESSING`，执行业务，成功后写 `SUCCESS` + 响应快照；
- 重复请求（同 key 同内容）：**重放首次响应**，业务不二次执行；
- 处理中重复请求：返回 `409 IDEMPOTENCY_001`；
- 同 key 不同内容：返回 `422 IDEMPOTENCY_003`；
- 业务失败（`BusinessException`）：记 `FAILED` 保留错误码，允许修正后重试；
- 系统异常：删除记录，不阻塞客户端重试。

### 18.2 关键设计

- **记录写在 `REQUIRES_NEW` 独立事务**：与业务共用事务会导致「业务回滚把幂等记录一起抹掉」，
  重试就变成一次真实的新执行，幂等形同虚设；
- **唯一索引 `uk_idem_key_scope` 兜底并发**：应用层判断与插入之间的时间窗由数据库裁决，
  切面捕获 `DataIntegrityViolationException` 后按冲突处理（fail-closed）；
- **切面顺序 `LOWEST_PRECEDENCE - 1000`**：必须包住 `@Transactional`，
  只有事务提交后才写 `SUCCESS`，否则会出现「业务回滚但显示成功」；
- **幂等基础设施故障降级放行**：去重功能故障不阻断业务（去重是增强，不是前提）。

### 18.3 已接入

| 方法 | scope | requireKey |
|------|-------|-----------|
| `WorkflowService#completeTask` | `workflow:task:complete` | 是 |
| `WorkflowService#transfer` | `workflow:task:transfer` | 是 |
| `MessageService#send` | `system:message:send` | 否 |

> 抄送（`sendCcNotification`）走 `doSend` 而非 `send`：它由工作流引擎触发、请求上下文无幂等键，
> 若复用带切面入口会因强制键校验全部失败。

清理：`IdempotencyCleanupJob`（JobRunr，每小时）删除 `sys_idempotency_record` 过期行。

## 19. Token 版本与权限缓存失效

补齐设计方案 §20「权限变更后旧权限缓存与 Token 版本**正确失效**」。
此前 `sys_user.token_version` 建了列、JWT 也带 `tokenVersion` 声明，但**全链路无人递增、无人校验**——
改掉某人角色后其旧 token 仍可通行到自然过期。

### 19.1 闭环

- **递增（bump）**：`UserService#update`（角色或状态变化）、`UserService#resetPassword`、
  `UserService#delete`、`RoleDataScopeService#save`（按角色反查全部持有者）；
- **校验**：`TokenVersionVerifier` 过滤器挂在 Bearer 认证之后，比对声明与库中版本；
- **判定**：`claim >= stored` 即有效——运维手工回退版本不会把用户永久锁死；
- **缓存**：进程内 TTL 30s（缓存「版本号」而非「判定结果」，避免把某个旧 token 的有效误判为全局有效）；
- **失效响应**：`401 AUTH_005 凭证已失效，请重新登录`。

### 19.2 失败策略

版本校验依赖 DB。读不到版本时**一律放行**（fail-open）并记 warn——
基础设施故障不应表现为「全站被登出」；真正判定为落后时才拒绝。

## 20. 验证记录（原生 MySQL + Redis，无 Docker）

已在**原生 MySQL 8.4.11 + Redis** 环境完成实际启动验证，全程未使用任何容器：

| 验证项           | 结果                                                                                           |
| ------------- | -------------------------------------------------------------------------------------------- |
| `mvn compile` | 通过，110 个 class                                                                               |
| 应用启动          | `Started FrameworkApplication in 28.4s`（首次，含建表）/ 14s（后续）                                     |
| Flyway        | 应用 V1→V4，schema 至 `v4`（`V4` 为 admin 密码数据修复）                                                  |
| 建表总数          | 72 张（`sys_*`/`wf_*` 20 张 + Flowable `ACT_*`/`FLW_*` + `jobrunr_*` + `flyway_schema_history`） |
| JobRunr 存储    | 自动迁移 v000→v015，创建 `jobrunr_*` 表                                                              |
| jOOQ 运行时      | 日志确认 `Database version is supported by dialect MYSQL: 8.4.11`                                |
| Actuator      | `/actuator/health` → `{"status":"UP"}`                                                       |
| 登录接口          | 参数校验、全局异常处理、MDC traceId 均正常（返回 `AUTH_001` + traceId）                                         |
| 周期任务          | `demo-job`（`*/5 * * * *`）已持久化到 `jobrunr_recurring_jobs`                                      |

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
- **（已修复）Flowable 流程校验告警**：原 `leaveApproval` 静态 BPMN 的排他网关存在无条件出口流；改造为设计驱动后，驳回网关由 `BpmnWorkflowBuilder` 统一注入默认（正常流转）分支，告警消除。

### 沙箱/隔离环境验证注意事项

- **Redis 必须运行**：`/actuator/health` 聚合了 `redis` 指示器，若 Redis 未启动则整体 `DOWN`（HTTP 503）。  
  本地验证前请先 `redis-server --port 6379 --bind 127.0.0.1` 启动实例（本仓库验证用隔离 datadir，不污染用户环境）。
- **Netty DNS 解析**：沙箱内 Netty 异步 DNS 解析器对 `localhost`/`127.0.0.1` 均可能报 `<unresolved>`，  
  导致响应式 `RedisReactiveHealthIndicator` 失败。启动应用请加 JVM 参数  
  `-Dio.netty.resolver.dns.useJdkResolver=true` 全局回退到 JDK 解析器。
- **本地代理占用端口**：WorkBuddy 沙箱会在 `127.0.0.1:<port>`（IPv4）注入一层 Express 反向代理，  
  直接 `curl 127.0.0.1:<port>` 会命中代理并返回 `{"error":{"code":"AUTH_REQUIRED"...}}`（非本应用响应）。  
  验证时请改走 IPv6 回环直连：`curl http://[::1]:<port>/... --noproxy '*'`。
