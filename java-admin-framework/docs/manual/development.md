# 开发手册

面向后端与前端开发者。覆盖本地开发环境、持久层 jOOQ 用法、系统管理模块开发、工作流审批与自定义设计器、调度任务、接口资源扫描、菜单模块与拖拽排序、代码生成器、前端接口规范与页面开发、集成测试与缺陷修复记录。

---

> **阅读指引**：前端接口规范、菜单接口与表结构、前端工程说明等已整入本手册；涉及部署/运维/架构的内容请参见对应手册。

---

## 本手册整合来源

- **docs/archive/java-admin-framework-README.md**
  - ## 5. 工作流审批
  - ## 7. 调度任务（JobRunr）
  - ## 8. 持久层用法（jOOQ）
  - ## 9. 系统管理模块：部门 / 字典 / 系统变量
  - ## 10. 公告 / 站内信 / 系统监控 + 用户单角色单部门收敛
  - ## 12. 接口资源自动扫描登记
  - ## 13. 角色数据权限
  - ## 14. 服务端排序（白名单映射）
  - ## 16. 自定义工作流（动态设计器）
  - ## 18. 幂等性（Idempotency-Key）
  - ## 22. 代码生成器与验证码四则运算
  - ## 23. 菜单拖拽排序（V25 迁移）
  - ## 24. 雪花 ID 前端精度丢失修复（Long → String 序列化）
  - ## 25. 集成测试全量回归修复（9 类 IT，22 例）
  - ## 26. 部门负责人字段改造与「清除不生效」缺陷修复
  - ## 28. 密码链路闭环：重置即用 + 登录页「忘记密码 / 修改密码」
  - ## 31. 菜单重组：移除报表公共统计页，新增「运维管理」（V30 迁移）
  - ## 32. 报表/工作台优化与菜单图标选择器
  - ## 33. 恢复「报表管理」一级菜单（V31 迁移）
- **docs/archive/menu-api-and-schema.md**
  - ## 1. 概述
  - ## 2. 接口总览
  - ## 3. 接口详述
  - ## 4. 统一响应结构 `Result<T>`
  - ## 5. 请求 / 响应数据结构
  - ## 6. 数据库表结构
  - ## 7. 菜单动态加载业务规则
  - ## 8. 前端集成要点（`pc-admin-vue3-vite`）
  - ## 9. 种子数据（迁移 `V17__menu_role_seed.sql`）
  - ## 10. 状态码速查
  - ## 11. 关键文件索引
- **docs/archive/frontend-api-standard.md**
  - ## 1. 总则：后端为唯一数据源
  - ## 2. 接口请求方式（HTTP Method 约定）
  - ## 3. 数据格式规范
  - ## 4. 前端统一调用方式
  - ## 5. 整改记录（2026-10-09 已完成）
  - ## 6. 关键文件索引
- **docs/archive/pc-admin-vue3-vite-README.md**
  - ## 技术栈
  - ## 目录结构
  - ## 快速开始
  - ## 对接真实后端
  - ## 已实现的接口映射
  - ## 两项增强说明
  - ## 新增页面：菜单管理 / 接口资源管理
  - ## 接口资源管理：新增「扫描对比」
  - ## 新增页面：部门管理 / 字典管理 / 系统变量
  - ## 新增页面：系统公告 / 站内信 / 系统监控
  - ## 后端改动

---

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 0. 本地开发环境基线（2026-10-17 核对）

| 项 | 取值 | 说明 |
|---|---|---|
| JDK | **21**（编译基线 `pom.xml` `<java.version>` 为 17，运行时用 21） | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Maven | `/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn` | 仓库内**无 `mvnw`**；离线环境统一加 `-o` |
| Spring Boot | 3.3.4（父 POM `spring-boot-starter-parent`） | **多 Maven 模块**（V1.0.3 拆分，见《模块拆分指南》），包名统一 `com.acme.scaffold` |
| 数据库 | MySQL 8.x，库名 **`scaffold`** | `jdbc:mysql://localhost:3306/scaffold`；Flyway 自动建表 |
| 迁移链 | `scaffold-admin/src/main/resources/db/migration/` `V32`~`V39`，schema 版本 **39** | `validate-on-migrate: true` |
| Redis | `127.0.0.1:6379`（local） | 缓存 / Token 黑名单 / 在线会话 |
| 应用端口 | **8080** | `server.port`（API / Swagger / WebSocket），见下方端口说明 |
| Actuator | `management.server.port` = **9090** | 独立管理端口，`/actuator/health`、`/actuator/prometheus` |
| JobRunr Dashboard | **8000**（仅 local，`JOBRUNR_DASHBOARD_PORT` 可覆盖） | `http://localhost:8000/dashboard`，**不在应用端口上** |
| 前端 | `pc-admin-vue3-vite`，`npm run dev`（5173）/ `npm run build` / `npm run preview`（4173） | `vite.config.js` 代理 `/api`、`/ws` |

### 0.1 常用命令

```bash
# 后端（JDK 21 + 本机 Maven，离线）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_PROFILES_ACTIVE=local
MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn

$MVN -o clean test                 # 全量单元测试（多模块反应堆，当前 857 例全绿）
$MVN -o test -Dtest="*IT"          # 集成测试（需本地 MySQL，触发 Flyway；*IT 均在 scaffold-admin）
$MVN -o spring-boot:run -pl scaffold-admin   # 启动（见下端口说明）
# 单模块快速迭代（-am 连同依赖一起构建）
$MVN -o -pl scaffold-system -am test

# 编码约定校验（零依赖，CI 同款；H1-H4 包级 + H5 模块依赖环）
python3 scripts/check-conventions.py
python3 -m unittest discover -s scripts -p "test_*.py"

# 前端
cd ../pc-admin-vue3-vite && npm install && npm run dev     # 联调
npm run build                                              # 产物校验
```

### 0.2 端口说明

应用对外端口为 **8080**（`server.port`），Actuator 管理端点为独立端口 **9090**（`management.server.port`），二者互不冲突。联调前如需确认实际端口：

```bash
# 查看启动日志中两行端口
grep "Tomcat started on port" logs/java-admin-framework.log | tail -2
# 或修改 vite.config.js 的 /api、/ws 代理目标与后端一致（默认指向 8080）
```

> 完整部署侧端口规划见《部署手册》§3「端口说明」。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 7. 调度任务（JobRunr）

JobRunr 无需独立调度中心：任务以 JSON 持久化在业务库的 `jobrunr_*` 表，由应用内嵌的 `BackgroundJobServer` 执行。

- 周期任务在 `com.acme.scaffold.job.config.JobRunrConfig`（scaffold-job 模块，V1.0.3 由原 `config.jobrunr` 迁入）中注册：
  ```java
  jobScheduler.scheduleRecurrently("demo-job", "*/5 * * * *", demoJob::run);
  ```
- 一次性/延迟任务可直接注入 `JobScheduler`：
  ```java
  jobScheduler.enqueue(() -> demoJob.run());
  jobScheduler.schedule(LocalDateTime.now().plusHours(1), () -> demoJob.run());
  ```
- 多实例部署时 JobRunr 通过数据库锁协调，任务不会重复执行。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 22. 代码生成器与验证码四则运算

### 22.1 代码生成器（`codegen` 包）

按数据库表结构生成 CRUD 骨架：**后端六层**（DO / VO / Query / Controller / Service / ServiceImpl）
+ **前端两文件**（Vue 页面 / api.js）。定位为开发提效工具，生成结果是**文本预览**——服务端不落盘，
由开发者复制或打包 zip 下载后自行入库，业务规则需手工补齐。

| 组件 | 职责 |
| --- | --- |
| `TableSchemaService` | 原生 SQL 读 `information_schema.TABLES/COLUMNS`（`DATABASE()` 限定当前库，避免跨库串表） |
| `JavaTypeMapper` | JDBC 类型 → Java 类型纯函数（`tinyint(1)` → `Boolean` 等），列名 → 字段名/类名推导 |
| `CodeTemplates` | `renderAll(meta, option, props)` 渲染八类文件；文件头注释由各模板自带（无重复头） |
| `CodeGenService` | 编排预览 / zip 打包（内存 `ZipOutputStream`，entry 为建议落盘相对路径） |
| `CodeGenController` | 表清单 / 字段元信息 / 预览 / 下载四端点 |
| `CodeGenConfig` | `@EnableConfigurationProperties(CodeGenProperties.class)`（包名前缀 / 默认作者 / 前端路径提示） |

**安全边界**：SQL 参数经 jOOQ 绑定；表名仅用于 `WHERE TABLE_NAME = ?` 查询，不参与拼表；
生成结果仅以文本 / zip 返回，服务端不写业务磁盘，生成器无权改动源码树。

**接口**：

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/codegen/tables` | 库内业务表清单（排除视图） | `codegen:read` |
| GET | `/api/codegen/tables/{tableName}` | 表字段元信息（含 Java 映射） | `codegen:read` |
| POST | `/api/codegen/preview` | 生成预览（后端六层 + 可选前端两文件） | `codegen:read` |
| POST | `/api/codegen/download` | zip 打包下载 | `codegen:download` |

**菜单与权限（V22 迁移）**：`V22__codegen_menu.sql` 插入「代码生成」顶级菜单
（`route_path=/dev/codegen`、`icon=MagicStick`、`permission_code=codegen:read`）与四个接口资源，
并绑定 `SUPER_ADMIN(role_id=1)`；全部 `INSERT IGNORE` 幂等。

**前端（pc-admin-vue3-vite）**：`src/api/codegen.js`（下载走 `http.js` 的 `requestRaw` blob）；
`src/views/CodegenView.vue`（表名下拉 + 关键字过滤、模块名/功能名/类名推导、作者、前端生成勾选、
右侧 el-tabs 多文件预览 + clipboard 复制 + zip 下载，工具栏沿用全局 `toolbar-filters/toolbar-actions` 分区）；
路由 `dev/codegen`（meta permission `codegen:read`）。

### 22.2 登录验证码四则运算

`security/captcha/ArithmeticRules` 纯函数出题（默认仍受 `auth.captcha.enabled` 开关控制，默认关闭）：

- 运算类型 `Operator`：`ADD` / `SUB` / `MUL` / `DIV`，默认四则全集，配置 `auth.captcha.operators: ADD,SUB` 可收窄；
- `SUB` 两数随机后**交换保非负**；`DIV` **先定商**再反推被除数，保证整除且除数 ∈ [2, mulMaxNumber]；
- `MUL` / `DIV` 操作数使用独立上界 `auth.captcha.mul-max-number`（默认 10），避免乘积过大；
- 加减操作数上界仍为 `auth.captcha.max-number`（默认 20）；问题文本使用 `×` `÷` 符号；
- `CaptchaService.generate` 改调 `draw(...)`，验证码 record 新增 `operator` 字段，校验链路不变。

### 22.3 验证状态

- 后端 `mvn -o test`：**265/265 全绿**（原 262 + `CodeGenConfigTest` 3 例）；
- `CodeGenConfigTest` 覆盖 `CodeGenProperties` 注册与绑定回归（`ApplicationContextRunner` 离线验证，
  曾遗漏 `@EnableConfigurationProperties` 导致启动即注入失败，已修复并经负向验证：移除注解后 3/3 失败）；
- 生成的 6 个 Java 文件曾以 javac 全量 classpath 实测编译通过；生成的 `.vue` / `.js` 经
  `@vue/compiler-sfc` 与 `node --check` 校验通过；
- 前端 `npm run build` 通过（`CodegenView` chunk 7.53 kB，>500kB 警告为既有 ECharts 体积，非本次引入）。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 23. 菜单拖拽排序（V25 迁移）

菜单管理页（`MenuView.vue`）支持调整菜单展示顺序：**仅同级排序**（跨父级调整仍走「编辑 → 上级菜单」，
与层级移动的环路校验保持同一入口）。

### 排序规则（后端权威）

- 同级内 `sort_no ASC`，**二级键 `id ASC`**：`sortNo` 相同或未设置（默认 0）时按创建先后稳定排列，
  不依赖数据库返回顺序（`MenuService.tree()` 与 `menusForCurrentUser()` 采用同一规则）；
- 前端「我的菜单」与「菜单管理」因此看到一致顺序。

### 交互与持久化

- **拖拽**：菜单名称列手柄（HTML5 DnD）上下拖动；**上移/下移按钮**为点按等价操作（触屏/无拖拽环境可用）；
- **边界**：拖到同级首位之前/末位之后为合法落点；已在首位时「上移」禁用、末位时「下移」禁用；
  拖出表格或按 Esc 取消 → `dragend` 清理拖拽态，无副作用；
- **跨级拒绝**：目标行与源行 `parentId` 不一致时不给放置反馈，误放后提示「仅支持同级菜单排序」；
- **视觉反馈**：拖拽源行半透明；目标行上沿/下沿 2px 主色指示线标识落点（before/after）；
- **空列表**：`empty-text` 明确引导「暂无菜单数据（点击右上角「新增菜单」创建）」；
- **搜索态禁用**：关键字过滤拖拽/移动的是过滤视图而非真实同级集合，手柄置灰并提示先清空关键字；
- **持久化**：本地移动后按 **10 步进**重算该级全部 `sortNo`（留出中间值空间，便于手动编辑插队），
  整级提交 `PUT /api/system/menus/sort`（body `[{id, sortNo}]`，权限 `system:menu:update`，
  与单条编辑同域）；失败即重新拉取列表**回滚**，保证界面与后端一致。

### 实现要点

- `MenuService.reorder()`：事务内逐项 `fetchById` 校验存在（不存在 → 404 整体回滚），
  复制现有行仅覆盖 `sortNo` 再 `updateById`——避免 `SysMenuDO` 字段默认值（`parentId=0`、`status=ACTIVE`
  等非 null 默认值）经 NOT_NULL 策略误覆盖其他列（与 `update()` 同一范式）；
- `MenuSortItem`（`@NotNull id` / `@NotNull @Min(0) sortNo`）+ Controller `List<@Valid MenuSortItem>` 级联校验；
- `V25__menu_sort_api.sql`：登记接口资源 `PUT /api/system/menus/sort`（id=900000000409）并绑定
  SUPER_ADMIN（`INSERT IGNORE` 幂等，与 `ApiResourceScanner` 扫描结果一致）；
- 前端 `api/menu.js` 新增 `sortMenus`；`MenuView.vue` 新增 `dragState` / `siblingList` / `applyReorder`，
  行反馈经 `row-class-name` + scoped `:deep()` 样式实现（el-table 行 class 在子组件渲染，需 `:deep()` 穿透）。

### 验证

- `MenuSortIT`（3 例，真实 MySQL）：批量排序后树接口按新 `sortNo` 返回（c→a→b）；
  同 `sortNo` 按 `id` 升序稳定返回；不存在 id 提交返回 404；
- `mvn -o test` 273/273 全绿；`npm run build` 通过。

> 本机环境备忘：同一 MySQL 实例上 local 库（scaffold）已建 Flowable schema，而 Flowable 的
> `isTablePresent`（`DatabaseMetaData.getTables` 不限定库名）会跨库误判「schema 已存在」，
> 使 IT 库（java_admin）走 upgrade 路径并在读不到版本号时以 `FlowableWrongDbException` 失败。
> 本机已在 java_admin 预建 Flowable schema（common/engine/history + IDM 表，`ACT_GE_PROPERTY` 写入
> common/schema/eventregistry 版本行，`ACT_ID_PROPERTY` 写入 schema.version，均 7.1.0.2）。
> CI 为全新实例，无跨库干扰，`database-schema-update=true` 的标准 create 路径可直接建表，无需此修复。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 24. 雪花 ID 前端精度丢失修复（Long → String 序列化）

### 症状

「添加菜单后，再次编辑提示『菜单不存在』」——**新建**的菜单/用户/部门/字典等实体一编辑就 404，
而迁移种子预置的老实体（id 为 4~12 位小数字）编辑正常。

### 根因

- `IdGenerator`（雪花算法）生成的 id 为 **18 位**（当前 `(ts-EPOCH)<<22` 量级 ≈3.8e17），
  远超 JavaScript `Number.MAX_SAFE_INTEGER`（2^53-1 ≈ 9.0e15，16 位）；
- 相关 VO 的 id / parentId / roleIds 等为裸 `Long`，Jackson 序列化为 JSON **number**；
- 前端 axios `JSON.parse` 后 JS number 末位被 round（如 `385378331056410624 → 385378331056410600`），
  编辑提交失真 id → `MenuService.update()` 的 `fetchById` 查无此行 → `NOT_FOUND "菜单不存在"`；
- 种子 id（4~12 位）在安全区内不失真，故只有新建实体踩雷——与症状完全吻合。

> JobVO / JobLogVO / WorkflowDesignView 早已按 `@JsonSerialize(using = ToStringSerializer.class)`
> 防护（其 javadoc 亦记载此坑），本次是手工编写的其余 VO 与 create 端点漏防护的系统性收口。

### 修复范围

| 层 | 文件 | 改动 |
| --- | --- | --- |
| VO | `MenuTreeVO` | id / parentId → ToStringSerializer |
| VO | `UserView` | id + roleIds / deptIds（contentUsing）→ 字符串 |
| VO | `RoleView` | id + menuIds / apiIds → 字符串 |
| VO | `DictTypeVO` / `DictDataVO` / `ConfigVO` | id → 字符串 |
| VO | `OrgTreeVO` | id / parentId / leaderUserId → 字符串 |
| VO | `ApprovalRecordView` / `TaskView` / `InstanceDetailView` | userId 字段 → 字符串 |
| 端点 | 9 个 `create`（Menu/User/Role/Dict/DictData/Config/Org/Announcement/ApiResource） | `Result<Long>` → `Result<String>`（`String.valueOf`） |

### 前端契约变化与兼容

- 响应中 id 一律为**字符串**（URL 拼接、`el-select` value、el-tree key、JSON body 均天然兼容）；
- 反序列化方向不受影响：`@PathVariable Long` 与请求体 `Long` / `List<Long>` 字段接收数字字符串由
  Jackson / Spring ConversionService 正常 coercion；
- `MenuView.vue` 新增 `parentIdOf(row)` 归一化：顶层 parentId 上链后为字符串 `"0"`（truthy），
  `?? 0` / `|| null` 判断失效会导致顶级菜单拖拽/上下移失灵、上级下拉显示异常，统一归一位 `0`；
- 其余页面（User/Role/Dict/Config/Department/Announcement/ApiResource/Job）的 id 用法均为
  同源比较或 URL 传参，string 化后无需改动。

### 验证

- `mvn -o test` 单测 273/273 全绿；`npm run build` 通过；
- 库内实测：`sys_menu` 新建行 id 均为 18 位（`SELECT CHAR_LENGTH(id)`），修复前 JS 端读取即失真。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 25. 集成测试全量回归修复（9 类 IT，22 例）

上一轮「菜单拖拽排序 + 雪花 ID 字符串化」落地后，首次跑齐全部 9 个 IT 类（22 例）暴露 7 个失败。
逐项定位根因并修复，最终 `Tests run: 22, Failures: 0, Errors: 0` 全绿。

### ① 菜单两个 IT 按 Number 读取 id（ClassCastException）

- **根因**：§24 的雪花 ID 字符串化契约生效后，树 VO 的 `id` 与 create 端点的 `Result<String>`
  均以 JSON 字符串下发，而 `MenuIntegrationIT` / `MenuSortIT` 仍按 `((Number) ...).longValue()`
  强转 → `ClassCastException: String cannot be cast to Number`。
- **修复**：两处 IT 的 id 读取改为 `Long.parseLong(...toString())`（4 处）。
  生产行为正确（字符串化正是 §24 的修复），测试适配新契约。

### ② UserIntegrationIT 密码夹具违反密码策略（BusinessException）

- **根因**：`PasswordPolicy`（P1 安全基线：大写+小写+数字+特殊字符、8–64 位、禁弱口令）
  加固后，IT 夹具 `"Password123"`（无特殊字符）从未同步更新；该 IT 自策略上线后未再跑过，
  直到本次全量回归才暴露（3 例均在 `UserService.create` 抛出「密码必须包含特殊字符」）。
- **修复**：3 处夹具改 `"Password123!"`。

### ③ UserIntegrationIT 空角色守卫反射打到代理（NPE）

- **根因**：`UserService` 带 `@Transactional` 由 CGLIB 代理，
  `ReflectionTestUtils.invokeMethod(userService, "assignRoles", ...)` 对**代理实例**反射调用
  私有方法，方法体内的 `this.dsl` 取到代理类自身从未注入的字段 → null →
  `Cannot invoke "DSLContext.deleteFrom(Table)" because "dsl" is null`。
- **修复**：`AopTestUtils.getTargetObject(userService)` 解包取得目标实例后再调用。
  注意须先将解包结果存入 `UserService` 类型局部变量——泛型多态表达式作参数时，
  编译器按「最具体重载」规则会误选 `invokeMethod(Class<?>, String, Object...)`
  并插入 `checkcast Class`，运行期抛 `ClassCastException`（本次二轮修复实测踩中）。

### ④ AuthFlowIT 401 响应体不可读（HttpRetryException）

- **根因**（两层叠加）：
  1. Spring 6.1 的 `SimpleClientHttpRequest.executeInternal` 对带 body 的请求**总是**调用
     `setFixedLengthStreamingMode/setChunkedStreamingMode`；工厂的
     `setOutputStreaming(false)` / `setBufferRequestBody(true)` 已弃用且**不再生效**，
     JDK `HttpURLConnection.streaming()` 恒为 true；
  2. JDK `HttpURLConnection` 收到 401 且处于 streaming 模式时，无条件抛
     `HttpRetryException("cannot retry due to server authentication, in streaming mode")`
     （源码 `if (respCode == HTTP_UNAUTHORIZED) { if (streaming()) throw ... }`），
     错误密码登录的 401 + `AUTH_001` 响应体无法读取。
- **修复**：`AuthFlowIT` 改用 JDK 内置 `java.net.http.HttpClient`（零依赖，JDK 11+），
  任意状态码响应体均可读取；移除失效的 `SimpleClientHttpRequestFactory` 定制。
- **验证**：反编译实证（`javap -c`）确认 6.1.13 的 `SimpleClientHttpRequest` 恒设流式模式；
  JDK src.zip 确认 401 分支抛出条件；修复后 401 体断言通过。

### 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `mvn -o test` | 273/273 BUILD SUCCESS |
| 集成测试（9 类） | `mvn -o -DskipITs=false test-compile failsafe:integration-test failsafe:verify` | 22/22 BUILD SUCCESS |
| 前端 | `vite build` | 2299 模块转换、构建成功 |

> 注：`mvn verify` 完整生命周期在离线环境下会失败于 `maven-jar-plugin` 依赖缺失
> （本地仓库从未下载 file-management/plexus-archiver 等 11 个构件 JAR，仅 POM），
> 属离线仓库完整性问题而非代码缺陷；IT 经直接调用 failsafe 目标验证，CI（有网）不受影响。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 26. 部门负责人字段改造与「清除不生效」缺陷修复

### 26.1 负责人 ID → 姓名展示

- 后端 `OrgTreeVO` 新增 `leaderUserName`（displayName，为空时回落 username）；
  `OrgService.tree()` 收集全部 `leaderUserId` 后**一次 IN 批量查询**填充姓名映射，
  避免逐节点回查用户表形成 N+1（空集合不发查询）。
- 前端 `DepartmentView.vue` 列表/编辑标签「负责人ID」改「负责人」：列表展示姓名
  （无负责人时显示「未设置」）；编辑项由数字输入框改为 `el-select` 远程搜索下拉，
  复用 `GET /api/system/users/page?username=`（后端 LIKE %kw% 同时匹配 username 与
  displayName），选中后以字符串雪花 ID 回填 `leaderUserId` 提交。
- 编辑态打开时把已选负责人注入候选项，保证 `el-select` 回显姓名而非 ID 原文；
  `leaderIdOf()` 将 null / '0' / 0（历史占位）统一归一为未设置。

### 26.2 缺陷：清除负责人后不生效（根因与修复）

- **现象**：编辑部门时清空负责人（或把负责人改为空）保存成功，但列表负责人仍在。
- **根因**：`JooqWriters.updateById` 采用 NOT_NULL 策略——`columnValues()`
  显式跳过 null 字段，null 不进入 UPDATE 语句。`OrgService.update()` 中
  `org.setLeaderUserId(request.leaderUserId())` 在清除场景传 null，
  导致 `leader_user_id` 列不更新、数据库旧值残留（其余字段均有 null 兜底，
  唯独负责人是裸传，属遗漏而非设计）。
- **实证复现**：`OrgLeaderUpdateIT` 三场景（设置 / 更换 / 清除），修复前
  仅「清除」失败：`expected: <null> but was: <385633953089851392>`。
- **修复**：`request.leaderUserId() == null` 时改走
  `JooqWriters.updateByIdWithNulls(..., Set.of("leader_user_id"))` 显式置空，
  与 `ProfileService` 解绑手机号/邮箱同一范式。
- **前端无需改动**：`el-select` clearable 清空值为 `undefined`
  （`useEmptyValues` 默认 `valueOnClear = undefined`），`form.leaderUserId || null`
  已正确产出 null 上传；链路（提交 → 保存 → `load()` 重拉 → 无缓存）其余环节
  经排查均正常（http.js 无缓存层、RateLimitFilter 仅限登录接口、
  OrgService 无 `@Cacheable`）。

### 26.3 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `mvn -o test` | 273/273 BUILD SUCCESS |
| 集成测试（11 类） | `mvn -o -DskipITs=false test-compile failsafe:integration-test failsafe:verify` | 26/26 BUILD SUCCESS |
| 前端 | `vite build` | 构建成功 |

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 28. 密码链路闭环：重置即用 + 登录页「忘记密码 / 修改密码」

### 28.1 问题与根因

现象：账号被管理员「重置密码」后，用新密码登录仍提示
「请修改初始/重置密码后再登录」（423 AUTH_014），且登录页没有任何
自助入口，用户卡死。

根因（流程闭环断裂，非数据异常）：

1. `UserService.resetPassword` 按设计文档 §5.3 置 `password_expired=1`
   （语义：管理员重置后须再改一次密）；
2. `AuthApplicationService.login` 对该标记返回 423 拦截；
3. 后端虽有会话无关改密接口 `POST /api/auth/change-password`，但
   **前端登录页没有「修改密码」「忘记密码」入口**——被拦用户无路可走。

### 28.2 修复与新增

| 项 | 内容 |
| --- | --- |
| 状态标识修正 | `UserService.resetPassword` 重置后**清除** `password_expired=0`：重置即赋予可用口令，重置后可直接登录。语义区分：`create` 的初始口令仍置 1（首次登录必须由本人更换），管理员重置是「已知会告知用户的新口令」，再强制改密只会把用户拦在登录页外 |
| 忘记密码 | 新接口 `POST /api/auth/forgot-password`（用户名 + 手机号/邮箱二选一核验 + 新密码），无需原密码；成功后与改密链路完全收敛（新口令生效 + `password_expired` 清零 + 密码历史留痕 + `token_version` 递增 + 刷新令牌吊销 + 在线会话置离线） |
| 修改密码 | 复用既有 `POST /api/auth/change-password`（用户名 + 原密码 + 新密码），登录页补入口 |
| 安全 | 防账号枚举（「用户不存在」与「验证信息不匹配」返回同一 `AUTH_001`）；`forgot-password` 纳入 `RateLimitFilter`（与 login 同阈值 10 次/60s/IP）；密码强度与「禁止复用最近 5 次」与改密链路共用 |
| 前端 | `LoginView.vue`：登录按钮下「忘记密码 / 修改密码」链接 + 两个弹窗（新密码两次一致性校验、凭据二选一校验）；登录被 AUTH_014 拦截时**自动弹出修改密码弹窗并预填用户名**，闭合「重置→被拦→改密→登录」环路；`api/auth.js` 增加两个封装 |

### 28.3 文件清单

| 类型 | 文件 |
| --- | --- |
| 新增 | `security/password/ForgotPasswordRules.java`（纯函数核验规则）、`security/dto/ForgotPasswordRequest.java` |
| 改动 | `system/service/UserService.java`（resetPassword 清标记）、`security/application/AuthApplicationService.java`（forgotPassword）、`security/controller/AuthController.java`（接口）、`security/config/SecurityConfig.java`（放行）、`security/ratelimit/RateLimitFilter.java`（限流纳入） |
| 前端 | `views/LoginView.vue`、`api/auth.js` |
| 测试 | `security/password/ForgotPasswordRulesTest.java`（8 例）、`integration/auth/ForgotPasswordIT.java`（9 例） |

### 28.4 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `mvn -o test` | 317/317 BUILD SUCCESS（新增 8 例规则测试） |
| 集成测试（13 类） | `mvn -o test -Dtest="*IT"` | 42/42 BUILD SUCCESS（新增 `ForgotPasswordIT` 9 例） |
| 前端 | `vite build` | 构建成功 |

`ForgotPasswordIT` 覆盖：手机号/邮箱核验双闭环（重置后新密码可登录、
旧密码被拒）、重置后旧令牌 401、防账号枚举同错、未提供凭据 400、
复用最近 5 次密码 422、**管理员重置后可直接登录（修复验证）**、
首次创建仍 423（语义保留）、重置后库状态核对。

### 28.5 排障记录（测试假象，非缺陷）

`ForgotPasswordIT` 首跑全量 401，曾疑 MySQL collation/索引/连接池快照，
逐层排除（CLI 复现正常、同 URL 对照、`HEX()`/`innodb_trx`/autocommit
证据）后实锤为**测试代码自身变量错误**：`createTestUser` 内部与各测试
方法各自调用 `System.nanoTime()` 生成用户名，拼出两个不同的 username，
「库里有的查不到、查不到的偏要登」。修复：用户名统一在 `createTestUser`
内生成并以 `TestUser(id, username)` 句柄返回。教训：测试夹具的标识
必须单一来源生成并随句柄返回，不得由调用方重复拼接。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 31. 菜单重组：移除报表公共统计页，新增「运维管理」（V30 迁移）

### 31.1 变更内容

| 操作 | 对象 |
| --- | --- |
| 软删 | 「报表管理」目录(1030) + 3 个公共统计页：使用统计(1050) / 类别统计(1051) / 状态统计(1052)，`sys_role_menu` 关联同步清理（无孤儿行） |
| 新增 | 「运维管理」一级目录(1070, CATALOG, 图标 Tools)，接手 sort_no=50 |
| 迁入 | 接口资源管理(1014) / 代码生成(1040，原顶级菜单) / 系统监控(1018) / 缓存监控(1061) / 连接池监控(1062) / 登录日志(1063) / 操作日志(1064) |
| 随迁 | 保留的 3 个运维向统计页：定时任务执行统计(1053) / 登录统计(1054) / 接口调用统计(1055) |

### 31.2 运维管理菜单结构（sort_no 顺序）

| sort_no | 菜单 | 路由 path | 权限码 |
| --- | --- | --- | --- |
| 10 | 接口资源管理 | `/system/api-resource` | `system:api-resource:read` |
| 20 | 代码生成 | `/dev/codegen` | `codegen:read` |
| 30 | 系统监控 | `/system/monitor` | `system:monitor:read` |
| 40 | 缓存监控 | `/system/cache` | `system:monitor:read` |
| 50 | 连接池监控 | `/system/datasource` | `system:monitor:read` |
| 60 | 登录日志 | `/system/login-log` | `system:login-log:read` |
| 70 | 操作日志 | `/system/operation-log` | `system:audit:read` |
| 80 | 定时任务执行统计 | `/report/job-exec` | `report:read` |
| 90 | 登录统计 | `/report/login` | `report:read` |
| 100 | 接口调用统计 | `/report/api-call` | `report:read` |

### 31.3 不变性保证

- **权限零影响**：所有菜单 id 与 permission_code 不变 → `sys_role_api` / `sys_api_resource` 授权关系原样保留，接口访问控制无感知；`sys_role_menu` 按 menu_id 关联，id 不变即授权不丢失（1070 已由 V30 补绑 SUPER_ADMIN）。
- **路由零影响**：前端路由 path 全部不变，侧边栏由后端「我的菜单」接口按角色动态渲染（`MenuTree` 递归），parent 变更后自动归组，无需前端菜单配置。
- **业务零影响**：后端 Report/Monitor/Audit/LoginLog 等 Controller、Service 未做任何改动；报表后端 `/api/report/*` 端点保留（统计视角数据仍可查，仅前端 3 个公共视角页面移除）。

### 31.4 前端清理（3 文件删除 + 2 处修正）

- 删除 3 个公共统计页视图：`UsageReportView.vue` / `CategoryReportView.vue` / `StatusReportView.vue` 及 router 对应 3 条路由（`report/usage|category|status`）；
- `report/management` 旧入口重定向由 `/report/usage` 改为 `/report/job-exec`（保留页首个），历史书签不失效；
- 工作台「快捷入口」移除「报表管理」按钮（目标页已不存在）；
- `ReportPanel.vue` 与 `api/report.js` 保留不动——3 个保留统计页仍以其为载体，业务逻辑零变更。

### 31.5 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `mvn -o test` | 317/317 BUILD SUCCESS |
| 集成测试（21 类） | `mvn -o test -Dtest="*IT"` | 98/98 BUILD SUCCESS（V30 随 Flyway 自动下发，`flyway_schema_history` version=30 success=1） |
| 前端 | `vite build` | 2303 modules，构建成功 |
| 菜单树核验 | SQL | 1070 下 10 个子菜单 sort_no 10~100 顺序正确；1030/1050/1051/1052 均 deleted=1 且 `sys_role_menu` 零残留（leaked=0）；SUPER_ADMIN 经 1070 可见全部子菜单 |

> 注：`mvn -o test` 仅跑无 Spring 上下文的单元测试，不触发 Flyway；V30 等数据迁移随 IT（真实上下文）启动自动下发，开发库生效状态以 `flyway_schema_history` 为准。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 32. 报表/工作台优化与菜单图标选择器

### 32.1 菜单图标选择器（IconPicker）

菜单管理「图标」字段原为纯文本输入（需手背 Element Plus 图标名，错配即静默丢失图标）。新增 `components/IconPicker.vue`：

- 输入框只读 + 前置当前图标预览，点击打开选择弹窗；
- 精选 **135 个图标**按 6 组分类（监控运维 / 系统管理 / 工作流协作 / 报表统计 / 通用操作 / 开发工具），支持名称搜索，已用脚本对照 `@element-plus/icons-vue` 真实导出校验（零不存在、零重复）；
- 「监控运维」组覆盖运维管理子树全部在用图标（Odometer/Monitor/Box/Link/Key/Document/Connection/Tools 等），新增运维类菜单从该组选用，保证图标风格统一（均为 Element Plus 线性图标）；
- 支持一键清除；选中即写回 `form.icon`（与后端 `sys_menu.icon`、MenuTree 的 `component :is` 链路一致）。

**MenuTree 防御**：数据库 icon 为自由文本（历史错配/空值会触发 Vue 运行时告警且图标静默丢失），`safeIcon` computed 增加图标名白名单校验，非法/缺失一律不渲染图标仅显示文字，侧边栏渲染稳定。

### 32.2 报表管理优化（ReportPanel）

V30 移除 3 个公共统计页后，ReportPanel 的使用视角（类别/状态/排序筛选、类别分布图、使用视角汇总与趋势）成为死代码，本轮清理并按统计视角收敛：

- **死代码清理**：删除 `MODULE_LABELS`/`moduleOptions`/`categoryOption`、使用视角的 `summaries`/`trendOption` 分支、`module`/`status`/`sortBy`/`order` 筛选项（后端 `sortBy/order` 仅服务类别分布，统计视角不用，Controller 有 defaultValue 兜底）；
- **类型白名单**：`load()` 入口校验 `type ∈ {JOB_EXEC, LOGIN, API_CALL}`，路由/菜单误配时给出可读错误而非静默空页；
- **交互优化**：错误态从纯 alert 改为「alert + 重试按钮」（复用当前筛选条件）；自定义时间范围增加起止关系前端预校验（开始 > 结束时少发一次无效请求）；导出成功 ElMessage 提示文件名；汇总指标 null 值显示 `--` 而非 `undefined`；
- **api/report.js**：注释同步为仅统计视角口径（后端 USAGE/CATEGORY/STATUS 类型与聚合逻辑保留未删，前端入口已移除）。

### 32.3 工作台优化（DashboardView）

- 统计卡与「最近动态」加载失败时，alert 内嵌**重试按钮**（原只能整页刷新）；
- 「最近动态」时间列改**相对时间**（刚刚 / x 分钟前 / x 小时前 / x 天前，超 7 天回退绝对时间），`title` 悬浮保留精确时间；
- 统计卡数值**千分位格式化**（大数值可读性）。

### 32.4 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 前端构建 | `npm run build -- --emptyOutDir=false` | 2303 modules，构建成功（仅 chunk 体积提示） |
| 图标名校验 | node 脚本对照 `@element-plus/icons-vue` 真实导出 | 135 个全部有效、零重复 |
| 死代码自查 | grep `isStatView/moduleOptions/categoryOption/...` | ReportPanel 零残留 |

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 33. 恢复「报表管理」一级菜单（V31 迁移）

### 33.1 变更内容

V30 将 3 个运维向统计页挂到「运维管理」后，报表域缺少一级入口。V31 复活 V30 软删的「报表管理」目录（id=1030，menu_code=report-management）作为一级 CATALOG，并将 3 个统计页从「运维管理」迁回：

| 菜单 | id | parent | sort_no | 权限码 | 路由 path |
| --- | --- | --- | --- | --- | --- |
| 报表管理（一级目录） | 1030 | 0 | 45 | —（目录不鉴权） | — |
| 定时任务执行统计 | 1053 | 1030 | 10 | report:read | /report/job-exec |
| 登录统计 | 1054 | 1030 | 20 | report:read | /report/login |
| 接口调用统计 | 1055 | 1030 | 30 | report:read | /report/api-call |

排序位：接排个人中心(40)之后、运维管理(50)之前，其余一级菜单 sort_no 不变；子菜单 sort_no 按 10/20/30 重排。

### 33.2 顺带修复：侧边栏菜单排序失效（MenuService）

集成测试首跑暴露既有缺陷（`dfac8db` 引入）：`menusForCurrentUser` 中 `expanded` 为 `HashSet`，`included` 直接流式迭代该集合，**输出顺序退化为 id 哈希序**，令上方 `all.sort`（sort_no ASC, id ASC）成为死代码——侧边栏（`/api/system/menus/mine`）顺序与 sort_no 脱节，而管理端（`/api/system/menus/tree`，DB ORDER BY）一直正常。V31 的 sort_no 设计正依赖该顺序，故一并修复：

- `included` 改为从已排序的 `all` 按 `expanded` 过滤，输出与 `tree()` 同序（sort_no ASC，二级键 id ASC）；
- 仅修正展示顺序，菜单集合（授权菜单 + 祖先闭包）完全不变，无业务影响。

### 33.3 不变性保证

- **授权零影响**：3 个统计页菜单 id / 权限码 / route_path 均不变 → `sys_role_api` / `sys_api_resource` 授权原样保留；`sys_role_menu` 按 menu_id 关联，id 不变即授权不丢失（V30 清理过 1030 的 SUPER_ADMIN 绑定，V31 以 INSERT IGNORE 补回）；
- **前端路由零改动**：`/report/job-exec`、`/report/login`、`/report/api-call` 路径不变，侧边栏由后端菜单树驱动自动归组；旧入口 `/report/management` 重定向至 `/report/job-exec` 仍有效；
- **运维管理不受损**：保留其余 7 个功能菜单（接口资源管理 / 代码生成 / 系统监控 / 缓存监控 / 连接池监控 / 登录日志 / 操作日志，sort 10~70 不变）；
- **公共统计页不复活**：V30 软删的使用 / 类别 / 状态统计页（1050/1051/1052）保持 deleted=1；
- **幂等**：复活带 `deleted=1` 原值 WHERE 条件，INSERT IGNORE + NOT EXISTS，重复执行结果一致。

### 33.4 前端同步

- `DashboardView.vue` 快捷入口恢复「报表管理」按钮（跳转 `reportJobExec`，即报表管理下首个统计页），与一级菜单入口互补；
- 路由注释补充 V31 归属说明（无结构变化，path 均不变）。

### 33.5 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单元测试 | `mvn -o test` | 327/327 BUILD SUCCESS |
| V31 集成测试 | `mvn -o test -Dtest="ReportMenuRestoreIT"` | 5/5 BUILD SUCCESS |
| 全量集成测试 | `mvn -o test -Dtest="*IT"` | 103/103 BUILD SUCCESS（V31 随 Flyway 下发，`flyway_schema_history` version=31 success=1） |
| 前端构建 | `npm run build -- --emptyOutDir=false` | 构建成功（仅 chunk 体积提示） |
| 库状态核对 | mysql 直查 `sys_menu` / `sys_role_menu` | 1030 一级 CATALOG sort 45；1053/1054/1055 挂 1030（sort 10/20/30，report:read）；1050~1052 保持软删；1070 保留 7 子项；SUPER_ADMIN 绑定 1030/1053/1054/1055 齐全 |

> 注：`mvn -o test` 仅跑无 Spring 上下文的单元测试，不触发 Flyway；V31 数据迁移随 IT（真实上下文）启动自动下发，开发库生效状态以 `flyway_schema_history` 为准。

> 适用范围：`java-admin-framework`（后端 Spring Boot 3.x + jOOQ）与 `pc-admin-vue3-vite`（前端 Vue 3 + Vite + Element Plus）。
> 文档依据当前磁盘源码与迁移脚本（`V32__consolidated_schema.sql`；原 V1/V15/V17 与 `scaffold_full_init.sql` 已合并/移除）整理，覆盖「按角色动态加载菜单」全链路。
> 最后核对时间：2026-10-09。

---

## 34. 多数据源：配置与运行时切换

> 完整配置项、新增数据源五步、故障排查见《多数据源使用指南》`docs/manual/datasource-guide.md`；架构设计见《架构手册》§9.6。

框架支持连接并切换多个数据库。默认所有查询落**主库（primary）**，未切换时既有服务（注入 `DSLContext` / `DataSource`）行为完全不变；多数据源是增量能力，非破坏性改造。

### 34.1 配置位置

| 文件 | 内容 |
| --- | --- |
| `application.yml` | 声明 `app.datasources.primary`（主库名）；关闭 `DataSourceAutoConfiguration` |
| `application-local.yml` / `application-prod.yml` | 填充 `app.datasources.entries.<name>.{url,username,password}`，凭据走 `${DB_<NAME>_*}` 环境变量（local 可 `:默认值` 回退，prod 无默认值） |

### 34.2 切换方式一：声明式 `@WithDataSource`

标注在**外层编排方法**上，切面（`@Order(HIGHEST_PRECEDENCE)`）先于事务切面生效，方法体内所有 DB 访问落指定库，结束后自动还原：

```java
@WithDataSource("report")
public ReportSnapshot build() {
    var rows = reportRepository.queryFromReportDb();   // → report 库
    var recent = orderService.recentOrders();          // → 主库（orderService 内部未切换）
    return merge(rows, recent);
}
```

### 34.3 切换方式二：编程式 `DataSourceRouter`

注入 `DataSourceRouter`，用 `with(name, task)` 在指定库上下文执行并自动还原，或用 `dsl(name)` 取具名 `DSLContext` 做长链路多库混访：

```java
// 在 report 库上下文执行，自动还原调用前路由
List<Row> rows = router.with("report", () -> reportDsl.selectFrom(REPORT_TABLE).fetch());

// 或显式取具名 DSLContext（不受路由上下文影响）
DSLContext report = router.dsl("report");
DSLContext primary = router.primaryDsl();
```

### 34.4 注意

- 单 `@Transactional` 只能绑定一个物理连接，**跨库业务须拆成多个方法分别切换**，或用 `router.dsl(name)` 显式多库混访；
- 切换必须在 `@Transactional` 开启连接**之前**生效；切勿在被 `@Transactional` 的方法内部才 `set` 路由（会回落主库）；
- Flyway / JobRunr / Flowable 一律走主库，不要在从库/报表库跑迁移或调度；
- prod 环境变量已更名：`DB_URL/DB_USERNAME/DB_PASSWORD` → `DB_PRIMARY_URL/DB_PRIMARY_USERNAME/DB_PRIMARY_PASSWORD`，部署侧同步。

---

## 1. 概述

菜单功能采用 **RBAC（基于角色的访问控制）动态加载** 模式：

```
用户(sys_user) ──1:N──> 用户-角色(sys_user_role) ──N:1──> 角色(sys_role)
                                                              │
                                                   N:M（sys_role_menu）
                                                              │
                                                              下 ──> 菜单(sys_menu 自引用树)
```

- 登录后前端调用 `GET /api/system/menus/mine`，后端按「用户 → 角色 → 菜单」解析出当前用户**角色范围内**的菜单树。
- 菜单数据完全由角色配置驱动，**前端不硬编码**；未授权菜单既不展示也不可通过路由直达。
- 后端持久层已迁移至 **jOOQ**（`JooqWriters` / `JooqTables`），不再依赖 MyBatis-Plus。

---

## 2. 接口总览

| # | 接口 | 方法 | 路径 | 鉴权 | 所需权限（authority） | 说明 |
|---|------|------|------|------|----------------------|------|
| 1 | 我的菜单 | GET | `/api/system/menus/mine` | 需登录 | 无（仅认证） | **动态加载核心**：按当前登录用户角色过滤 |
| 2 | 菜单树 | GET | `/api/system/menus/tree` | 需登录 | `system:menu:read` | 全量菜单树（管理用） |
| 3 | 创建菜单 | POST | `/api/system/menus` | 需登录 | `system:menu:create` | 新建菜单节点 |
| 4 | 删除菜单 | DELETE | `/api/system/menus/{id}` | 需登录 | `system:menu:delete` | 有子菜单时拒绝删除 |
| 5 | 更新菜单 | PUT | `/api/system/menus/{id}` | 需登录 | `system:menu:update` | 含防环校验 |

> 全局安全策略（`SecurityConfig`）：`/api/**` 除登录/刷新等公开端点外，**一律需认证**（Bearer JWT）；接口级权限由 `@PreAuthorize("hasAuthority('xxx')")` 控制。`/mine` 端点无 `@PreAuthorize`，任何已登录用户可访问。

---

## 3. 接口详述

### 3.1 我的菜单（动态加载核心）`GET /api/system/menus/mine`

- **鉴权**：需登录（从 `SecurityContextFacade.requireCurrentPrincipal().userId()` 取当前用户 ID）。
- **请求参数**：无（路径/查询/请求体均无）。
- **响应体**：`Result<List<MenuTreeVO>>`

**业务逻辑（`MenuService.menusForCurrentUser`）**：
1. 由 `userId` 查 `sys_user_role` 得去重 `roleIds`；为空返回 `[]`。
2. 由 `roleIds` 查 `sys_role_menu` 得授权 `menuIds` 集合；为空返回 `[]`。
3. 读取未删除的全部 `sys_menu`，按 `sort_no` 排序。
4. 过滤条件（**两者须同时满足**）：`visible = 1`（显示）且 `status = 'ACTIVE'`。
5. **补全祖先链**：从命中的菜单向上回溯 `parent_id`，保证子菜单的父级/祖父级也被纳入，避免前端树悬空。
6. 按 `parent_id` 分组递归构建菜单树。

**成功响应示例**：
```json
{
  "code": "0",
  "message": "OK",
  "data": [
    {
      "id": 1002, "parentId": 0, "menuCode": "system", "menuName": "系统管理",
      "menuType": "CATALOG", "routePath": null, "componentPath": null,
      "permissionCode": null, "icon": "Setting", "visible": 1, "sortNo": 20,
      "status": "ACTIVE",
      "children": [
        { "id": 1010, "parentId": 1002, "menuCode": "system:user", "menuName": "用户管理",
          "menuType": "MENU", "routePath": "/system/user", "icon": "User",
          "visible": 1, "sortNo": 10, "status": "ACTIVE", "children": [] }
      ]
    }
  ],
  "traceId": "a1b2c3d4",
  "timestamp": "2026-10-09T15:04:05.123Z"
}
```
> 注意：`CATALOG`（目录/分类）节点的 `routePath` 通常为 `null`，仅用于分组；`MENU`（菜单）节点携带可导航的 `routePath`。

### 3.2 菜单树 `GET /api/system/menus/tree`

- **权限**：`system:menu:read`。
- **请求参数**：无。
- **响应体**：`Result<List<MenuTreeVO>>`（返回全部未删除菜单的树，不按角色过滤）。
- 用于「菜单管理」界面展示与编辑全量结构。

### 3.3 创建菜单 `POST /api/system/menus`

- **权限**：`system:menu:create`；并触发审计注解 `@AuditOperation(module="system", type="CREATE", name="创建菜单")`。
- **请求体**：`CreateMenuRequest`（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `parentId` | Long | 否 | 父菜单 ID，缺省 `0`（顶级） |
| `menuCode` | String | **是** `@NotBlank` | 菜单编码（同租户唯一） |
| `menuName` | String | **是** `@NotBlank` | 菜单名称 |
| `menuType` | String | **是** `@NotBlank` | 类型：`MENU` / `CATALOG` |
| `routePath` | String | 否 | 前端路由路径，如 `/system/user` |
| `componentPath` | String | 否 | 前端组件路径 |
| `permissionCode` | String | 否 | 关联权限码，如 `system:user:read` |
| `icon` | String | 否 | 图标名 |
| `visible` | Integer | 否 | 是否显示，缺省 `1`（1显示/0隐藏） |
| `sortNo` | Integer | 否 | 排序号，缺省 `0` |
| `status` | String | 否 | 状态，缺省 `ACTIVE` |

- **响应体**：`Result<Long>`（新建菜单的 `id`）。

### 3.4 删除菜单 `DELETE /api/system/menus/{id}`

- **权限**：`system:menu:delete`；审计 `@AuditOperation(type="DELETE")`。
- **路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long（路径） | 待删除菜单 ID |

- **业务规则**：若目标菜单下存在未删除子菜单，抛出 `BusinessException(CONFLICT, "请先删除子菜单")` → **HTTP 409**。
- **响应体**：`Result<Void>`（`data = null`）。

### 3.5 更新菜单 `PUT /api/system/menus/{id}`

- **权限**：`system:menu:update`；审计 `@AuditOperation(type="UPDATE")`。
- **路径参数**：`id` (Long)。
- **请求体**：`UpdateMenuRequest`（字段同 `CreateMenuRequest`，见 3.3 表）。
- **业务规则（防环）**：
  - 若 `parentId` 变更为目标节点自身 → 409 `不能将菜单挂到自身之下`。
  - 若 `parentId` 变更为目标节点的子孙节点 → 409 `不能将菜单移动到其子菜单之下`（通过 `collectDescendantIds` 判定）。
  - 若新父菜单不存在 → 404 `父菜单不存在`。
  - 目标菜单不存在 → 404 `菜单不存在`。
- 更新时保留 `tenant_id` 与 `version`（避免跨租户串改 / 乐观锁重置）。
- **响应体**：`Result<Void>`。

---

## 4. 统一响应结构 `Result<T>`

所有接口均返回统一包装（`com.acme.scaffold.common.api.Result`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | String | 业务码，`"0"` 表示成功；非 0 为稳定错误码（如 `SYSTEM_MENU_xxx`） |
| `message` | String | 提示信息 |
| `data` | T | 业务数据（见各接口）；失败时为 `null` |
| `traceId` | String | 链路追踪 ID（取自 MDC `traceId`） |
| `timestamp` | Instant | 响应时间（ISO-8601） |

> 约定：HTTP 状态码表达协议结果（400/401/403/404/409/422/429/500），`code` 表达稳定业务错误，**不**为统一而全部返回 200。

---

## 5. 请求 / 响应数据结构

### 5.1 `MenuTreeVO`（响应，递归）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 菜单 ID |
| `parentId` | Long | 父菜单 ID（顶级为 0） |
| `menuCode` | String | 菜单编码 |
| `menuName` | String | 菜单名称 |
| `menuType` | String | `MENU` / `CATALOG` |
| `routePath` | String | 前端路由路径（可为 `null`） |
| `componentPath` | String | 前端组件路径（可为 `null`） |
| `permissionCode` | String | 权限码（可为 `null`） |
| `icon` | String | 图标名 |
| `visible` | Integer | 1 显示 / 0 隐藏 |
| `sortNo` | Integer | 排序号 |
| `status` | String | `ACTIVE` 等 |
| `children` | `List<MenuTreeVO>` | 子菜单（递归，叶子为 `[]`） |

### 5.2 `CreateMenuRequest` / `UpdateMenuRequest`（请求体）

两者字段一致（`menuCode`/`menuName`/`menuType` 带 `@NotBlank`）。详见 §3.3 表格。
`UpdateMenuRequest` 中 `parentId` 由后端用于层级防环校验（见 §3.5）。

---

## 6. 数据库表结构

> 引擎 `InnoDB`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
> 多租户字段 `tenant_id` 默认 `0`；所有表含 `deleted` 逻辑删除（`0` 未删）与 `created_at`/`updated_at` 时间戳。
> 下列 `sys_user_role` 为 **V15 迁移重建后** 的最终结构（含 `is_primary` 多角色模型）。

### 6.1 表关系（ER 摘要）

```
sys_user (id)
   │  1:N
   ▼
sys_user_role (user_id, role_id, is_primary)   ── PK(id), UK(tenant_id,user_id,role_id)
   │  N:1
   ▼
sys_role (id, role_code)
   │  N:M
   ▼  (sys_role_menu: tenant_id, role_id, menu_id)
sys_menu (id, parent_id 自引用)
```

### 6.2 `sys_menu`（菜单表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT UNSIGNED | PK，NOT NULL | 菜单 ID |
| `tenant_id` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 租户 ID |
| `parent_id` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 父菜单 ID（0 = 顶级，自引用） |
| `menu_code` | VARCHAR(64) | NOT NULL | 菜单编码 |
| `menu_name` | VARCHAR(100) | NOT NULL | 菜单名称 |
| `menu_type` | VARCHAR(20) | NOT NULL | 类型：`MENU` / `CATALOG` |
| `route_path` | VARCHAR(255) | NULL | 前端路由路径 |
| `component_path` | VARCHAR(255) | NULL | 前端组件路径 |
| `permission_code` | VARCHAR(128) | NULL | 关联权限码 |
| `icon` | VARCHAR(64) | NULL | 图标名 |
| `visible` | TINYINT UNSIGNED | NOT NULL DEFAULT 1 | 是否显示（1/0） |
| `keep_alive` | TINYINT UNSIGNED | NOT NULL DEFAULT 0 | 路由缓存（1/0） |
| `external_url` | VARCHAR(500) | NULL | 外链地址 |
| `sort_no` | INT | NOT NULL DEFAULT 0 | 排序号 |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | 状态 |
| `deleted` | TINYINT UNSIGNED | NOT NULL DEFAULT 0 | 逻辑删除 |
| `created_at` | DATETIME(3) | NOT NULL DEFAULT CURRENT_TIMESTAMP(3) | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_tenant_menu_code (tenant_id, menu_code)`
- `KEY idx_parent_sort (tenant_id, parent_id, sort_no)`
- `KEY idx_permission (tenant_id, permission_code)`

### 6.3 `sys_role_menu`（角色-菜单关联表，多对多）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `tenant_id` | BIGINT UNSIGNED | PK 组成，NOT NULL DEFAULT 0 | 租户 ID |
| `role_id` | BIGINT UNSIGNED | PK 组成，NOT NULL | 角色 ID |
| `menu_id` | BIGINT UNSIGNED | PK 组成，NOT NULL | 菜单 ID |
| `created_at` | DATETIME(3) | NOT NULL DEFAULT CURRENT_TIMESTAMP(3) | 创建时间 |

**索引**：
- `PRIMARY KEY (tenant_id, role_id, menu_id)`
- `KEY idx_menu_role (tenant_id, menu_id, role_id)`

> 注：Java 实体 `SysRoleMenuDO` 额外声明了 `id` 字段，但该字段未映射到此表（表无 `id` 列），实际持久化以复合主键为准。

### 6.4 `sys_user_role`（用户-角色关联表，多对多，V15 重建）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT UNSIGNED | PK，NOT NULL AUTO_INCREMENT | 关联 ID |
| `tenant_id` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 租户 ID |
| `user_id` | BIGINT UNSIGNED | NOT NULL | 用户 ID |
| `role_id` | BIGINT UNSIGNED | NOT NULL | 角色 ID |
| `is_primary` | TINYINT UNSIGNED | NOT NULL DEFAULT 0 | 是否主角色（1/0） |
| `created_at` | DATETIME(3) | NOT NULL DEFAULT CURRENT_TIMESTAMP(3) | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_user_role (tenant_id, user_id, role_id)`
- `KEY idx_role (tenant_id, role_id)`
- `KEY idx_user (tenant_id, user_id)`

### 6.5 `sys_role`（角色表，相关字段）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT UNSIGNED | PK，NOT NULL | 角色 ID |
| `tenant_id` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 租户 ID |
| `role_code` | VARCHAR(64) | NOT NULL | 角色编码（如 `SUPER_ADMIN`） |
| `role_name` | VARCHAR(100) | NOT NULL | 角色名称 |
| `role_type` | VARCHAR(20) | NOT NULL DEFAULT 'BUSINESS' | 类型（`SYSTEM`/`BUSINESS`） |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | 状态 |
| `sort_no` | INT | NOT NULL DEFAULT 0 | 排序号 |
| `version` | INT UNSIGNED | NOT NULL DEFAULT 0 | 乐观锁版本 |
| `deleted` | TINYINT UNSIGNED | NOT NULL DEFAULT 0 | 逻辑删除 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL | 时间戳 |

**索引**：`PRIMARY KEY (id)`、`UNIQUE KEY uk_tenant_role_code (tenant_id, role_code)`、`KEY idx_status_sort (tenant_id, status, sort_no)`。

### 6.6 `sys_user`（用户表，与菜单链路相关字段）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT UNSIGNED | PK，NOT NULL | 用户 ID |
| `tenant_id` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 租户 ID |
| `username` | VARCHAR(64) | NOT NULL | 登录名 |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | 状态 |
| `deleted` | TINYINT UNSIGNED | NOT NULL DEFAULT 0 | 逻辑删除 |
| `version` | INT UNSIGNED | NOT NULL DEFAULT 0 | 乐观锁版本 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL | 时间戳 |

**索引**：`PRIMARY KEY (id)`、`UNIQUE KEY uk_tenant_username (tenant_id, username)` 等。

> 说明：V15 迁移已将原 `sys_user.role_id/org_id` 单列收敛下线，用户-角色关系改由 `sys_user_role` 关联表承载（N:N，含 `is_primary` 主角色标记）。

### 6.7 索引汇总

| 表 | 索引 | 列 | 类型 |
|----|------|----|------|
| sys_menu | PRIMARY | id | 主键 |
| sys_menu | uk_tenant_menu_code | (tenant_id, menu_code) | 唯一 |
| sys_menu | idx_parent_sort | (tenant_id, parent_id, sort_no) | 普通 |
| sys_menu | idx_permission | (tenant_id, permission_code) | 普通 |
| sys_role_menu | PRIMARY | (tenant_id, role_id, menu_id) | 复合主键 |
| sys_role_menu | idx_menu_role | (tenant_id, menu_id, role_id) | 普通 |
| sys_user_role | PRIMARY | id | 主键（自增） |
| sys_user_role | uk_user_role | (tenant_id, user_id, role_id) | 唯一 |
| sys_user_role | idx_role / idx_user | (tenant_id, role_id) / (tenant_id, user_id) | 普通 |
| sys_role | PRIMARY / uk_tenant_role_code / idx_status_sort | 见 §6.5 | — |
| sys_user | PRIMARY / uk_tenant_username | 见 §6.6 | — |

---

## 7. 菜单动态加载业务规则

| 规则 | 说明 |
|------|------|
| 角色解析 | 用户→`sys_user_role`→`roleIds`（去重） |
| 菜单授权 | `roleIds`→`sys_role_menu`→`menuIds`（集合） |
| 可见性过滤 | 仅 `visible = 1` 且 `status = 'ACTIVE'` 的菜单进入结果 |
| 祖先补全 | 从命中菜单向上补齐父/祖节点，保证树连通 |
| 排序 | 同级按 `sort_no` 升序 |
| 树构建 | 按 `parent_id` 分组递归（`buildChildren`） |
| 空结果 | 用户无角色或无授权菜单时返回 `[]` |

---

## 8. 前端集成要点（`pc-admin-vue3-vite`）

| 文件 | 职责 |
|------|------|
| `src/api/menu.js` | `myMenus()` → `GET /api/system/menus/mine`；另含 `menuTree`/`createMenu`/`deleteMenu`/`updateMenu` |
| `src/store/auth.js` | `authState.menus`、`loadMenus()`（动态 `import('@/api/menu')` 规避循环依赖）、登录后写入 |
| `src/layout/Sidebar.vue` | 按 `authState.menus` 动态渲染 `<MenuTree>`，**无硬编码** |
| `src/layout/MenuTree.vue` | 递归组件：有子节点渲染 `el-sub-menu`（index=menuCode），叶子渲染 `el-menu-item`（index=routePath，配合 `router` 模式导航） |
| `src/layout/AppLayout.vue` | 挂载时若已登录且 `menus` 为空则调用 `loadMenus()` |
| `src/router/index.js` | `collectAuthorizedPaths(menus)` 收集所有 `routePath`；`router.beforeEach` 守卫：未登录跳登录页；菜单加载完成后，凡不在用户菜单树中的受保护路由（非详情页，非 `/`、`/dashboard`）重定向至 `/dashboard` |
| `src/api/mock/db.js` | `USE_MOCK` 模式下 `currentUserMenus()` 镜像后端逻辑（用户→角色→菜单→补祖先），`roles[].menuIds` 已补全；`GET /api/system/menus/mine` 直接返回该结果，支持无后端演示 |

**路由守卫细节**：带路径参数的详情页（如 `/workflow/designer/:id`）通常由已授权列表页进入，予以放行（`isDetail` 判定）；菜单尚未加载的瞬间（首屏/刷新）为避免误拦截也先放行。

---

## 9. 种子数据（迁移 `V17__menu_role_seed.sql`）

首次启动由 Flyway 执行，使动态菜单「开箱即用」：`SUPER_ADMIN`(role_id=1) 绑定全部菜单，admin 登录即可见完整菜单树。

| id | menu_code | menu_name | menu_type | route_path | parent_id | icon | sort_no |
|----|-----------|-----------|-----------|------------|-----------|------|--------|
| 1001 | dashboard | 工作台 | MENU | /dashboard | 0 | Odometer | 10 |
| 1002 | system | 系统管理 | CATALOG | — | 0 | Setting | 20 |
| 1003 | workflow | 工作流 | CATALOG | — | 0 | Share | 30 |
| 1004 | profile | 个人中心 | MENU | /profile | 0 | User | 40 |
| 1010 | system:user | 用户管理 | MENU | /system/user | 1002 | User | 10 |
| 1011 | system:role | 角色管理 | MENU | /system/role | 1002 | Avatar | 20 |
| 1012 | system:menu | 菜单管理 | MENU | /system/menu | 1002 | Menu | 30 |
| 1013 | system:department | 部门管理 | MENU | /system/department | 1002 | OfficeBuilding | 40 |
| 1014 | system:api-resource | 接口资源管理 | MENU | /system/api-resource | 1002 | Connection | 50 |
| 1015 | system:dict | 字典管理 | MENU | /system/dict | 1002 | Notebook | 60 |
| 1016 | system:config | 系统变量 | MENU | /system/config | 1002 | Coin | 70 |
| 1017 | system:notice | 消息中心 | MENU | /system/notice | 1002 | Bell | 80 |
| 1018 | system:monitor | 系统监控 | MENU | /system/monitor | 1002 | Odometer | 90 |
| 1020 | workflow:task | 我的待办 | MENU | /workflow/task | 1003 | Tickets | 10 |
| 1021 | workflow:instance | 我发起的流程 | MENU | /workflow/instance | 1003 | Document | 20 |
| 1022 | workflow:definition | 工作流设计 | MENU | /workflow/definition | 1003 | Edit | 30 |

> 角色-菜单绑定：`INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, id FROM sys_menu WHERE id BETWEEN 1001 AND 1022;`
> 后续可在「菜单管理 / 角色管理」界面灵活调整，前端不硬编码。

---

## 10. 状态码速查

| 场景 | HTTP | code | 说明 |
|------|------|------|------|
| 成功（mine/tree/create/update/delete） | 200 | `0` | 正常返回 |
| 未认证 | 401 | — | 缺/无效 Bearer Token（BearerTokenAuthenticationEntryPoint） |
| 已认证但无权限（tree/create/...） | 403 | — | 缺少对应 `hasAuthority` |
| 菜单不存在（update/delete 目标） | 404 | — | `菜单不存在` |
| 删除有子菜单 | 409 | `CONFLICT` | `请先删除子菜单` |
| 层级防环失败 | 409 | `CONFLICT` | `不能将菜单挂到自身之下` / `不能将菜单移动到其子菜单之下` / `父菜单不存在` |
| 参数校验失败（@NotBlank 为空） | 422 | — | 请求体字段缺失 |
| 服务端异常 | 500 | — | 未预期错误 |

---

## 11. 关键文件索引

| 类型 | 文件 |
|------|------|
| 控制器 | `scaffold-system/src/main/java/com/acme/scaffold/system/controller/MenuController.java` |
| 服务 | `scaffold-system/src/main/java/com/acme/scaffold/system/service/MenuService.java` |
| 响应 VO | `scaffold-system/src/main/java/com/acme/scaffold/system/dto/MenuTreeVO.java` |
| 请求 DTO | `scaffold-system/src/main/java/com/acme/scaffold/system/dto/CreateMenuRequest.java`、`UpdateMenuRequest.java` |
| 实体 | `scaffold-system/src/main/java/com/acme/scaffold/system/entity/SysMenuDO.java`、`SysRoleMenuDO.java`、`SysUserRoleDO.java` |
| 响应封装 | `scaffold-common/src/main/java/com/acme/scaffold/common/api/Result.java` |
| 安全配置 | `scaffold-system/src/main/java/com/acme/scaffold/security/config/SecurityConfig.java` |
| 表迁移 | `db/migration/V32__consolidated_schema.sql`（原 V1：sys_menu/sys_role_menu/sys_user_role/sys_role/sys_user 初始；原 V15：sys_user_role 重建；原 V17：菜单种子） |
| 全量结构参考 | `src/main/resources/db/reference/schema_ddl.sql`（结构分类汇总；可执行迁移为 `db/migration/V32__consolidated_schema.sql`） |
| 前端 | `pc-admin-vue3-vite/src/api/menu.js`、`store/auth.js`、`layout/Sidebar.vue`、`layout/MenuTree.vue`、`router/index.js`、`api/mock/db.js` |

> 适用范围：`pc-admin-vue3-vite`（Vue 3 + Vite + Element Plus）全部页面与组件。
> 配套后端：`java-admin-framework`（Spring Boot 3.x），接口前缀 `/api`，开发期经 `vite.config.js` proxy 转发至 `http://127.0.0.1:8080`。
> 依据当前磁盘源码整理（`src/api/http.js`、`src/api/config.js`、`src/api/*.js`、后端 `Result`/`PageResult`）。最后核对时间：2026-10-09。

---

## 1. 总则：后端为唯一数据源

**所有页面与组件所需的业务数据，必须统一通过后端接口获取。** 前端只负责「请求 → 渲染 → 回传」，不持有业务数据。

### 1.1 硬性禁止

| 禁止项 | 说明 |
|--------|------|
| ❌ 硬编码业务数据 | 禁止在 `.vue` / `.js` 中写死列表、字典项、角色/菜单/用户等业务集合 |
| ❌ 本地模拟数据（mock 数据集） | 禁止内置 `mock/db.js` 之类的本地数据层充当数据源 |
| ❌ 前端造数兜底 | 接口失败应呈现**错误态/空态**，不得用假数据填充界面 |
| ❌ 组件直连 HTTP | 禁止在组件内 `import axios` 或 `fetch`，必须走 `src/api/*` 模块 |
| ❌ localStorage 当数据源 | `localStorage` 仅存 token 等会话凭据，不存业务数据 |

### 1.2 允许

- 纯展示型静态资源：图标名、文案、布局常量。
- **枚举值的展示映射**（如 `ACTIVE → 启用`）：取值集合优先由字典接口下发；暂未接口化的须集中声明并标注 `TODO: 接口化`，禁止散落在业务组件里。

---

## 2. 接口请求方式（HTTP Method 约定）

### 2.1 方法选择

| 场景 | 方法 | 参数位置 | 路径范式 | 示例 |
|------|------|----------|----------|------|
| 分页查询列表 | GET | `params`（query） | `/xxx/page` | `GET /api/system/users/page?page=1&size=20` |
| 查询详情 | GET | 路径 `id` | `/xxx/{id}` | `GET /api/system/users/1` |
| 查询树 / 全量（不分页） | GET | 无或 `params` | `/xxx/tree`、`/mine` | `GET /api/system/menus/mine` |
| 新建 | POST | `data`（JSON body） | `/xxx` | `POST /api/system/users` → 返回新 `id` |
| 更新（全量） | PUT | 路径 `id` + `data` | `/xxx/{id}` | `PUT /api/system/users/1` |
| 删除 | DELETE | 路径 `id` | `/xxx/{id}` | `DELETE /api/system/users/1` |
| 动作 / 状态变更（非 CRUD） | POST | 路径 + 动作子路径（+ `data`） | `/xxx/{id}/动作` | `POST /api/system/users/1/reset-password`、`POST /api/workflow/tasks/{taskId}/claim` |

### 2.2 通用约定

| 项 | 约定 |
|----|------|
| Content-Type | `application/json;charset=UTF-8`（由 axios 自动设置） |
| 参数传递 | GET 用 `params`（拼 query），写操作用 `data`（请求体）；**不得混用** |
| 分页参数 | `page` 从 **1** 开始，`size` 默认 **20**；排序 `sortField` / `sortDirection` |
| 认证 | `Authorization: Bearer <token>`，由请求拦截器统一注入，业务代码不感知 |
| 超时 | 15000 ms（`axios.create({ timeout: 15000 })`） |
| 基础路径 | `API_BASE = '/api'` |
| 幂等 | 需幂等的写操作按后端约定携带 `Idempotency-Key` 请求头 |

---

## 3. 数据格式规范

### 3.1 统一响应封装 `Result<T>`

所有接口返回同一信封（`com.acme.scaffold.common.api.Result`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | String | **`"0"` 表示成功**；非 0 为稳定业务错误码 |
| `message` | String | 提示信息 |
| `data` | T | 业务数据；失败时为 `null` |
| `traceId` | String | 链路追踪 ID（便于后端排障） |
| `timestamp` | String(ISO-8601) | 服务端时间 |

> 约定：HTTP 状态码表达协议结果，`code` 表达业务错误，**不**为统一而全部返回 200。

### 3.2 分页响应 `PageResult<T>`

分页接口的 `data` 固定为 `PageResult`（`page`/`size`/`total`/`records`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `page` | long | 当前页（从 1 开始） |
| `size` | long | 每页条数 |
| `total` | long | 总记录数 |
| `records` | `List<T>` | 当前页数据 |

```json
{ "code": "0", "message": "OK",
  "data": { "page": 1, "size": 20, "total": 42, "records": [ /* ... */ ] } }
```

### 3.3 字段与时间格式

| 类别 | 规范 |
|------|------|
| 字段命名 | **camelCase**（后端 record 字段直出），前端不做下划线转换 |
| 时间 | ISO-8601 字符串（如 `2026-10-09T15:04:05.123Z`） |
| 枚举 / 状态 | 字符串码（`ACTIVE` / `MENU` / `CATALOG`），展示标签由字典接口或集中映射表转换 |
| 布尔 / 开关 | 后端 `TINYINT` → 前端按 `1` / `0` 判定（如 `visible`、`isPrimary`） |
| 空值 | 允许 `null`；前端渲染须做兜底显示，但**不得改用假数据** |

### 3.4 错误响应与状态码

| 场景 | HTTP | `code` | 前端行为 |
|------|------|--------|----------|
| 成功 | 200 | `0` | 返回 `data` |
| 未认证 / token 失效 | 401 | — | 清会话、跳登录页 |
| 已认证但无权限 | 403 | — | 提示无权限 |
| 资源不存在 | 404 | — | 提示「不存在」 |
| 业务冲突（如删除有子菜单、重复认领） | 409 | `CONFLICT` | 提示冲突原因 |
| 参数校验失败 | 422 | — | 展示字段级错误 |
| 限流 | 429 | — | 提示稍后重试 |
| 服务端异常 | 500 | — | 通用错误提示 + `traceId` |

---

## 4. 前端统一调用方式

### 4.1 分层架构（禁止跨层）

```
视图组件 (.vue)
    ↓  仅调用具名 API 函数
API 模块 (src/api/*.js)          —— 声明「方法 + 路径 + 参数」，不含业务逻辑
    ↓  统一入口
请求封装 (src/api/http.js)       —— token 注入、拆包、错误提示、401 处理
    ↓
axios → 后端 /api/**
```

### 4.2 统一入口 `request(config)`（`src/api/http.js`）

签名：`request({ method, url, params, data, headers })`

| 能力 | 实现 |
|------|------|
| 请求拦截 | 从 `localStorage.pc_admin_token` 取 token，注入 `Authorization: Bearer <token>` |
| **响应拆包** | `code === '0'` → **直接返回 `body.data`**（业务层拿到裸数据，无需再判 code）；否则 `ElMessage.error(message)` 并 reject（错误对象挂载 `code`） |
| 401 处理 | 调用 `clearAuth()` 清会话并跳转 `/login` |
| 网络 / 其他错误 | 统一 `ElMessage.error`（优先取后端 `message`）并 reject |
| 超时 | 15s |

> 关键收益：API 模块与组件**永远只处理 `data`**，不感知 `Result` 信封与 HTTP 细节。

### 4.3 API 模块编写规范

每个资源一个模块（`src/api/<resource>.js`），导出**具名函数**，注释标明「方法 + 路径 + 返回类型」：

```js
import { request } from './http'

// GET /api/system/users/page?page&size&username&status -> PageResult<UserView>
export const pageUsers = (query) =>
  request({ method: 'GET', url: '/api/system/users/page', params: query })

// POST /api/system/users -> Long
export const createUser = (body) =>
  request({ method: 'POST', url: '/api/system/users', data: body })

// PUT /api/system/users/{id} -> void
export const updateUser = (id, body) =>
  request({ method: 'PUT', url: `/api/system/users/${id}`, data: body })

// DELETE /api/system/users/{id} -> void
export const deleteUser = (id) =>
  request({ method: 'DELETE', url: `/api/system/users/${id}` })
```

规范要点：
- GET 一律 `params`，写操作一律 `data`；URL 用模板串拼路径参数。
- 一个后端接口对应一个导出函数，禁止在组件内拼 URL。
- 新增页面前先补 API 模块，再写组件。

### 4.4 组件调用范式

统一「loading / error / data」三态，禁止裸写无状态请求：

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { pageUsers } from '@/api/user'

const loading = ref(false)
const error = ref('')
const records = ref([])
const total = ref(0)
const query = ref({ page: 1, size: 20 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await pageUsers(query.value)   // 已拆包，直接是 PageResult
    records.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch (e) {
    error.value = e.message || '加载失败'        // 失败呈现错误态，不造假数据
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
```

要点：
- 失败必须落到 `error` 态并在界面可见，**禁止 `catch` 后填入默认/示例数据**。
- 列表渲染一律绑定 `records`，禁止模板内字面量数组。
- 下拉选项来自接口（字典/关联资源），禁止内联 `options = [...]` 业务数组。

### 4.5 自检清单（提交前）

- [ ] 页面无任何硬编码业务数组 / 字典项
- [ ] 组件内无 `axios`、`fetch` 直接调用
- [ ] 新增接口已补 `src/api/*.js` 具名函数与注释
- [ ] GET 用 `params`、写操作用 `data`
- [ ] 失败有错误态，未用假数据兜底
- [ ] 无本地 mock 数据层参与

---

## 5. 整改记录（2026-10-09 已完成）

按本规范核查后发现的违反项，已全部整改完毕：

| # | 问题 | 位置 | 整改结果 |
|---|------|------|----------|
| 1 | **本地模拟数据开关开启**（`USE_MOCK = true`，所有请求走前端 mock 而非后端） | `src/api/config.js` | ✅ 已移除 `USE_MOCK`，仅保留 `API_BASE`，全部请求经后端 |
| 2 | 本地模拟数据集（users / roles / menus 等业务数据） | `src/api/mock/db.js` | ✅ 已删除整个 `src/api/mock/` 目录 |
| 3 | 请求封装含 mock 分支 | `src/api/http.js` | ✅ 已删除 `mockRequest` 分支，`request()` 统一走 axios |
| 4 | 登录页 Mock 提示 | `src/views/LoginView.vue` | ✅ 已移除提示与 `USE_MOCK` 引用 |
| 5 | 工作台「数据模式」展示 | `src/views/DashboardView.vue` | ✅ 已移除该描述项 |
| 6 | 顶栏实时消息 mock 分支 | `src/layout/Topbar.vue` | ✅ 已移除分支，统一走 WebSocket + 接口权威值，轮询兜底恒开启 |
| 7 | 实时模块模拟推送 | `src/api/realtime.js` | ✅ 已移除 `connectMock`，统一真实 WebSocket（心跳 30s / 断线 5s 重连） |
| 8 | **工作台统计数字写死**（今日新增用户 12 / 待办审批 2 / 运行中流程 5 / 在线接口 36） | `src/views/DashboardView.vue` | ✅ 已移除写死统计与统计卡片 |

**校验结果**：全量 grep 无 `USE_MOCK` / `mockRequest` / `@/api/mock` 残留；`node --check` 通过；`npm run build` 通过（✓ built）。

**遗留 → 已解决**：第 8 项移除的统计卡片已改由后端新接口 `GET /api/workbench/stats` 提供数据，前端 `DashboardView` 已接入渲染（含加载 / 空 / 错误三态），不再写死任何数字。契约见下：

### 5.1 工作台统计接口契约（前后端字段严格一致）

`GET /api/workbench/stats` → `Result<WorkbenchStatsVO>`，拆包后：

```
WorkbenchStatsVO { cards: StatCardVO[] }
StatCardVO {
  key           String  卡片标识 NEW_USER_TODAY | NEW_INSTANCE_TODAY | MY_TODO_TASKS | RUNNING_INSTANCE | ACTIVE_API
  label         String  展示名称（后端下发，前端不二次映射）
  value         Number  当前值
  previousValue Number  上一周期值（快照型指标与 value 相同，仅占位）
  changeRate    Number  变化率百分比（1 位小数；上一周期为 0 时记 0 或 100）
  trend         String  UP | DOWN | FLAT（FLAT = 持平或无历史基线）
  unit          String  单位（人 / 个 / 条）
}
```

指标分两类：**周期型**（今日新增用户 / 今日发起流程，今日 vs 昨日，含真实趋势）与**快照型**（我的待办 / 运行中流程 / 在线接口，无历史基线，trend 恒为 FLAT）。前端据 `trend` 决定是否展示「较昨日 X」。

**已确认合规的部分**：
- `src/api/http.js` 的拦截器拆包（`code==='0'` → 返回 `data`）、token 注入、401 处理、统一错误提示，现已是**唯一数据通道**。
- `src/api/*.js`（16 个模块：user / menu / role / dict / config / workflow …）均为「具名函数 + `request()`」范式，符合规范。
- 已扫描 `src/views/**`，未发现 `(const|let) xxxOptions = [...]` 形式的硬编码业务选项数组。

---

## 6. 关键文件索引

| 职责 | 文件 |
|------|------|
| 统一请求入口（拦截器 / 拆包 / 401） | `src/api/http.js` |
| 全局开关与基础路径 | `src/api/config.js` |
| 各资源 API 模块 | `src/api/user.js`、`menu.js`、`role.js`、`dictType.js`、`dictData.js`、`sysConfig.js`、`workflow.js`、`monitor.js`、`message.js`、`announcement.js`、`apiResource.js`、`org.js`、`profile.js`、`auth.js`、`realtime.js`、`workbench.js` |
| 后端响应封装 | `com.acme.scaffold.common.api.Result` |
| 后端分页封装 | `com.acme.scaffold.common.api.PageResult`（`page/size/total/records`） |
| 接口清单（菜单为例） | `docs/../java-admin-framework/docs/menu-api-and-schema.md` |

基于 **Vue 3 + Element Plus + Vite** 的标准工程化后台管理前端，对接后端 `java-admin-framework`（jOOQ / JobRunr / Flowable）。工程由单文件演示页面改造而来，覆盖 RBAC（用户/角色/菜单/接口资源）、工作流审批（待办/我发起的流程）等模块，并实现「列表 / 分页 / 搜索筛选 / 增删改查」与「角色权限树联动」「用户硬删除」「菜单·接口资源编辑」等增强。

## 技术栈

- Vue 3.4（`<script setup>` 组合式 API）
- Vue Router 4（登录与鉴权守卫）
- Element Plus 2.8（全量引入 + 图标全局注册）
- Axios（统一请求封装，自动注入 JWT、拆包 `Result<T>`、401 跳登录）
- Vite 5（含开发期 `/api` 代理）

## 目录结构

```
src/
├── main.js                # 应用入口，注册 Element Plus 与全部图标
├── App.vue                # 路由挂载点
├── styles.css             # 全局样式与布局变量
├── router/index.js        # 路由表 + 导航守卫
├── store/auth.js          # 登录态（reactive + localStorage）
├── api/
│   ├── config.js          # USE_MOCK 开关、API_BASE
│   ├── http.js            # request() 统一封装（mock / 真实二选一）
│   ├── auth.js user.js role.js menu.js apiResource.js workflow.js
│   ├── org.js dictType.js dictData.js sysConfig.js   # 部门/字典/系统变量接口封装
│   ├── announcement.js message.js monitor.js          # 公告/站内信/系统监控接口封装
│   └── mock/db.js         # 前端内置 mock 数据层（模拟全部后端接口）
├── layout/
│   ├── AppLayout.vue       # 顶部栏 + 侧边栏 + 主区 + 底部栏
│   ├── Sidebar.vue         # 菜单（含折叠）
│   └── Topbar.vue          # 面包屑 / 通知 / 用户下拉
└── views/
    ├── LoginView.vue
    ├── DashboardView.vue
    ├── system/UserView.vue          # 用户管理（含硬删除）
    ├── system/RoleView.vue          # 角色管理（含权限树联动）
    ├── system/MenuView.vue          # 菜单管理（树形列表 + 新增 + 编辑 + 删除）
    ├── system/DepartmentView.vue    # 部门管理（树形层级 + 增删改 + 层级移动）
    ├── system/DictView.vue          # 字典管理（分类主从联动 + 键值配置）
    ├── system/ApiResourceView.vue   # 接口资源管理（筛选 + 分页 + 注册 + 编辑 + 删除）
    ├── system/ConfigView.vue        # 系统变量（参数配置 + 按 key 动态读取）
    ├── system/AnnouncementView.vue  # 系统公告（关键词搜索 + 定时生效/下线 + 置顶）
    ├── system/MessageView.vue       # 站内信（单条/批量发送 + 已读未读 + 未读徽标）
    ├── system/MonitorView.vue       # 系统监控（运行指标 + 在线会话 + 异常日志 + 刷新）
    └── workflow/TaskView.vue        # 我的待办（审批/驳回/转办）
        workflow/InstanceView.vue     # 我发起的流程（发起/审批记录）
```

## 快速开始

```bash
npm install      # 安装依赖
npm run dev      # 启动开发服务器（默认 http://localhost:5173）
npm run build    # 生产构建（输出 dist/）
npm run preview  # 预览构建产物
```

> 默认 `USE_MOCK=true`，**无需后端即可体验全部功能**（内置 mock 数据，账号 `admin / admin123`）。

## 对接真实后端

1. 将 `src/api/config.js` 中的 `USE_MOCK` 改为 `false`。
2. 本地启动 `java-admin-framework`（默认端口 8080，Spring Security 鉴权）。
3. `vite.config.js` 已配置 `/api` 代理到 `http://127.0.0.1:8080`，`npm run dev` 即可联调。
4. 登录后 `accessToken` 存入 localStorage，Axios 拦截器自动携带 `Authorization: Bearer <token>`。

> 业务代码在 mock / 真实两种模式下完全一致，切换只需改一个开关。

## 已实现的接口映射

| 模块 | 前端动作 | 后端接口 |
|---|---|---|
| 认证 | 登录/登出 | `POST /api/auth/login`、`POST /api/auth/logout` |
| 用户 | 分页/筛选/新建/编辑/重置密码/**硬删除** | `GET /users/page`、`POST /users`、`PUT /users/{id}`、`POST /users/{id}/reset-password`、`DELETE /users/{id}` |
| 角色 | 列表/新建/编辑/删除/**权限树**/**数据权限** | `GET /roles`、`POST /roles`、`PUT /roles/{id}`、`DELETE /roles/{id}`、`GET /roles/{id}/data-scopes`、`PUT /roles/{id}/data-scopes` |
| 菜单 | 树形列表/关键词筛选/新增/编辑/删除 | `GET /menus/tree`、`POST /menus`、`PUT /menus/{id}`、`DELETE /menus/{id}` |
| 接口资源 | 列表/名称·权限·方法筛选/分页/注册/编辑/删除/**扫描对比/同步** | `GET /api-resources`、`POST /api-resources`、`PUT /api-resources/{id}`、`DELETE /api-resources/{id}`、`GET /api-resources/scan`、`POST /api-resources/scan/sync` |
| 待办 | 审批/驳回/转办 | `GET /tasks/mine`、`POST /tasks/complete`、`POST /tasks/transfer` |
| 流程 | 发起/我发起的/审批记录 | `POST /instances/start`、`GET /instances/mine`、`GET /instances/{pid}/records` |
| 部门 | 树形列表/新增/编辑/层级移动/删除 | `GET /orgs/tree`、`POST /orgs`、`PUT /orgs/{id}`、`DELETE /orgs/{id}` |
| 字典分类 | 列表/新增/编辑/删除 | `GET /dict-types`、`POST /dict-types`、`PUT /dict-types/{id}`、`DELETE /dict-types/{id}` |
| 字典数据 | 按分类筛选/新增/编辑/删除 | `GET /dict-data?dictType=`、`POST /dict-data`、`PUT /dict-data/{id}`、`DELETE /dict-data/{id}` |
| 系统变量 | 列表/新增/编辑/删除/**按 key 动态读取** | `GET /configs`、`POST /configs`、`PUT /configs/{id}`、`DELETE /configs/{id}`、`GET /configs/key/{key}` |
| 系统公告 | 搜索/分页/新增/编辑/**发布**/**下线**/**置顶** | `GET /announcements/page`、`POST /announcements`、`PUT /announcements/{id}`、`POST /announcements/{id}/publish`、`POST /announcements/{id}/offline`、`POST /announcements/{id}/toggle-top` |
| 站内信 | 收件箱/未读数/**单条·批量发送**/标记已读/已发 | `GET /messages/mine`、`GET /messages/unread-count`、`POST /messages/send`、`POST /messages/{id}/read`、`POST /messages/read-all`、`GET /messages/sent` |
| 系统监控 | 运行指标/在线会话/**异常日志(时间范围)**/刷新 | `GET /monitor/metrics`、`GET /monitor/online-summary`、`GET /monitor/online-sessions`、`GET /monitor/error-logs`、`GET /monitor/samples`、`POST /monitor/sample` |

## 两项增强说明

### ① 用户硬删除
- 前端：用户列表「删除」按钮使用 `el-popconfirm` 二次确认后调用 `DELETE /api/system/users/{id}`，成功后刷新列表。
- 后端（本次新增）：`UserController` 增加 `DELETE /api/system/users/{id}`，`UserService.delete(id)` 调用 `JooqWriters.delete(..., false)` 做**物理删除**（与既有逻辑删除语义区分）。

### ② 角色权限树联动
- 角色编辑/新建弹窗内置两棵 `el-tree`（菜单树来自 `GET /menus/tree`、接口资源树来自 `GET /api-resources`，均带复选框）。
- 打开编辑时，通过 `getRole(id)` 取回该角色已分配的 `menuIds / apiIds`，用 `setCheckedKeys` 回填勾选状态（联动）。
- 保存时收集 `getCheckedKeys()` 一并提交，后端 `RoleService` 已支持 `menuIds / apiIds` 的持久化与回填。

### ③ 角色数据权限

角色列表新增「数据权限」列与操作按钮，控制该角色**能看到哪些行数据**（与菜单/接口权限相互独立）。

- **概览标签**：列表页展示已配置的范围类型（如「本部门及下级」）；未配置显示灰色「未配置（全部数据）」。
  概览随角色列表一并加载，单个角色查询失败会被 catch 并跳过，不影响整表渲染。
- **配置弹窗**：资源下拉当前固定为「用户数据」（`system:user`）—— 前端不提供自由输入，
  避免配出后端尚未接线过滤的资源编码，导致「配了却不生效」。
- **五种范围**：全部数据 / 本部门及下级 / 仅本部门 / 仅本人 / 自定义部门，
  单选切换，下方实时显示该范围的具体含义说明。
- **自定义部门**：仅 `CUSTOM` 时展示部门树（`GET /orgs/tree`），保存时取 `getCheckedKeys(true)`
  只提交叶子节点，避免把父节点也写进规则；未勾选任何部门时前端拦截并提示。
- **覆盖式保存**：调 `PUT /roles/{id}/data-scopes` 提交完整规则，后端先删后插，
  因此前端无需做新增/修改/删除三态 diff。保存成功后局部更新列表标签，无需重载整表。

### ④ 列表服务端排序

用户列表 / 公告列表 / 异常日志三处表格的表头排序接到了后端白名单（其余列表为全量返回 + 前端分页排序）。

- 列上加 `sortable="custom"`，表格监听 `@sort-change`，把 `prop` 与 `order` 转成
  `sortField` + `sortDirection`（`ASC`/`DESC`）随分页参数一起提交。
- 排序走服务端，因此翻页后顺序一致；取消排序（第三次点击）会清空两个参数，回落后端默认排序。
- 前端**不拼 SQL、不传列名**，只传字段名；后端白名单外的字段会被忽略（详见后端 README 第 14 节）。
- mock 模式下 `db.js` 的 `SORT_FIELDS` 与后端白名单一一对应，`applyServerSort()` 复刻「白名单外忽略、
  空值排末尾、未指定时用默认排序」的行为，离线演示与真实后端表现一致。

## 新增页面：菜单管理 / 接口资源管理

为闭环 `system` 模块，新增两个与后端接口一一对应的管理页，现已支持**新增 / 编辑 / 删除**全链路闭环：

- **菜单管理 `MenuView`**：以 `el-table` 树形表展示 `GET /menus/tree` 返回的层级菜单；支持按名称/编码关键词筛选（保留命中子孙的祖先）；「新增菜单」弹窗含上级菜单下拉（由树拍平生成）、菜单编码/名称/类型（目录/菜单/按钮）/路由/组件/权限/图标/显示/排序/状态；「编辑」复用同一弹窗（**允许改上级菜单以移动层级**，下拉已排除自身及其子孙防止环路，回填 `MenuTreeVO` 扩展出的 `componentPath / icon / visible`）；「删除」走 `el-popconfirm` 二次确认后调 `DELETE /menus/{id}`，后端有子菜单时拒绝。
- **接口资源管理 `ApiResourceView`**：`GET /api-resources` 一次性返回全量列表，前端做名称/权限/方法的客户端筛选 + `el-pagination` 分页；「注册接口资源」弹窗含资源名/权限标识/HTTP 方法/路径模式/鉴权模式/风险等级；「编辑」复用同一弹窗回填 `ApiResourceView` 全部字段；「删除」调 `DELETE /api-resources/{id}`。**工具栏另有「扫描对比」入口**，详见下节。

## 接口资源管理：新增「扫描对比」

后端新增扫描器后，新增接口不再需要手工逐条登记权限码。本页工具栏的「扫描对比」按钮调 `GET /api/system/api-resources/scan` 拉取差异，用 `el-drawer` 展示：

- **统计卡**：扫描接口总数 / 待新增 / 待更新 / 无变化 / 库中失效；
- **三个 Tab**：待新增、待更新（均含方法、路径、权限标识、资源名称、源码位置）、库中失效；
- **同步按钮**：`POST /scan/sync` 写库，写入前用 `ElMessageBox` 二次确认并说明「会覆盖权限标识与资源名称，状态与风险等级保留」；无待同步项时按钮禁用。

失效记录只提示不自动删除（`sys_role_api` 可能仍引用，删除会造成授权悬空），并在该 Tab 内以 `el-alert` 说明这一点。

> mock 模式下 `db.js` 的 `scanApiResourceDiff()` 以「已同步 / 少量新增 / 个别变更 / 一条失效」的场景模拟差异，同步会真实写回 `db.apiResources`，可完整演示「扫描 → 预览 → 同步 → 再次扫描已一致」的闭环。

> mock 模式下 `db.js` 已补齐菜单嵌套树与接口资源的创建/编辑/删除路由，可直接离线演示（含「有子菜单时拒绝删除」逻辑）。

## 新增页面：部门管理 / 字典管理 / 系统变量

本轮闭环 `system` 模块的三个新管理页，后端接口一一对应，具备输入校验与错误处理：

- **部门管理 `DepartmentView`**：以 `el-table` 树形表展示 `GET /orgs/tree` 部门层级；支持按名称/编码关键词筛选（保留命中子孙的祖先）；「新增部门」弹窗含上级部门下拉（由树拍平生成）、部门编码/名称/类型/负责人/排序/状态；「编辑」复用同一弹窗并**允许改上级部门以移动层级**（下拉排除自身及其子孙防环）；「删除」走 `el-popconfirm` 二次确认，后端在存在子部门时拒绝。
- **字典管理 `DictView`**：左字典分类列表（`highlight-current-row` 选中）+ 右字典数据表的**主从联动布局**；选中分类自动加载其键值数据；分类与数据各一个弹窗，删除分类时若已配置数据则后端返回冲突并由拦截器提示。
- **系统变量 `ConfigView`**：系统参数表格 CRUD；顶部提供「按配置键动态读取」演示区，调 `GET /configs/key/{key}` 回显 `名称=值(类型)`，直观展示后端内存缓存 + 动态读取能力。

> mock 模式下 `db.js` 已补齐部门（含层级移动/防环）、字典分类与数据（唯一性/占用守卫）、系统变量（含按 key 读取）的全部路由，可直接离线演示。

## 新增页面：系统公告 / 站内信 / 系统监控

本轮新增三个页面（用户表单的「角色 / 部门」已回退为**多选**，见下方专门小节）：

- **系统公告 `AnnouncementView`**：关键词搜索（标题/内容）+ 状态筛选 + 分页浏览；列表支持**置顶**（置顶行前移）；「发布」弹窗可设 `publishAt`（**定时生效**）与 `expireAt`（**有效期**），已发布行展示「未生效 / 已过期」标记；「下线」与「删除」均有二次确认。状态机为 草稿 → 已发布 → 已下线。
- **站内信 `MessageView`**：收件箱带**未读徽标**与标题红点，打开详情即落**已读回执**并记录回执时间；支持「全部已读」与未读数查询；「发送」弹窗可切**单条 / 批量**，批量时按**角色与部门筛选接收人**（二者为**或关系**，前端校验至少选一项）；「已发送」列表可回查投递与阅读情况。
- **系统监控 `MonitorView`**：4 个使用率指标卡（CPU / JVM 内存 / 物理内存 / 磁盘）+ 运行时详情（堆、线程、类加载、JVM 版本、运行时长、主机信息）；在线用户 / 会话 / 令牌三卡与在线会话明细；**异常日志按时间范围查询**（from / to）并可按模块与关键词过滤；支持 30s **自动刷新开关**与手动刷新（手动刷新同时触发一次指标采样落库）。

> mock 模式下 `db.js` 已补齐三模块的种子数据与全路由分发（含公告状态流转、投递去重、未读统计、异常日志时间范围过滤等校验），可直接离线演示。

### ⑤ 用户多角色 / 多部门（回退 V6 单选，纯 N:N）

用户与角色、用户与部门均改为**纯多对多**：一个用户可绑定多个角色、归属多个部门，权限取**角色并集**。

- **用户表单**：角色 / 部门均为 `el-select multiple` 多选（至少 1 个，必填校验）；另各提供「主角色 / 主部门」单选（`primaryRoleId` / `primaryDeptId`），不选则取集合首位，用于数据范围（如 `DEPT` 类数据权限依赖主部门）。
- **列表展示**：部门列、角色列分别渲染 `deptNames` / `roleNames`（标签列表），`SUPER_ADMIN` 角色标签以 `danger` 高亮。
- **接口契约**：`POST /users` 提交 `roleIds` / `deptIds` / `primaryRoleId?` / `primaryDeptId?`；`PUT /users/{id}` 部分更新同字段；`UserView` 返回 `roleIds` / `roleNames` / `roleCodes` / `deptIds` / `deptNames`。
- **Mock 一致**：`src/api/mock/db.js` 的用户种子与 `GET /users/page`、`POST /users`、`PUT /users/{id}` 全部按多对多读写，与后端 `V15__user_role_org_many_to_many.sql` 回退 V6 的单列收敛语义一致。
- **个人中心**：`/api/profile` 返回 `roleNames` / `orgNames`（List），`ProfileCard` 改为以「/」连接展示多角色 / 多部门。

## 后端改动

本轮新增的三个模块对应后端改动（`java-admin-framework`，已随本轮提交落库）：

- **部门管理（复用既有机构 `sys_org` 表，不新增冗余表）**：
  - `system/controller/OrgController.java`：新增 `PUT /api/system/orgs/{id}`（`system:org:update` + 审计）与 `DELETE /api/system/orgs/{id}`（`system:org:delete` + 审计，存在子部门拒绝）。
  - `system/service/OrgService.java`：新增 `update(id, req)`（`parentId` 变更时防环校验并 `recomputeSubtreeAncestors` 重算物化路径）与 `delete(id)`（子节点计数守卫 + 逻辑删除）。
  - `system/dto/UpdateOrgRequest.java`（新增）、`system/dto/OrgTreeVO.java`（补 `leaderUserId`）。
- **字典管理（新建 `sys_dict_type` / `sys_dict_data`）**：
  - `system/entity/SysDictTypeDO.java`、`system/entity/SysDictDataDO.java` 及各 DTO/VO。
  - `system/service/DictTypeService.java`（编码唯一、存在数据时拒绝删除）、`system/service/DictDataService.java`（分类+值唯一）。
  - `system/controller/DictTypeController.java`、`system/controller/DictDataController.java`（`system:dict:*`）。
- **系统变量（新建 `sys_config`）**：
  - `system/entity/SysConfigDO.java` 及各 DTO/VO。
  - `system/service/ConfigService.java`：`ConcurrentHashMap` 内存缓存 + `getByKey` 动态读取，写操作同步失效缓存。
  - `system/controller/ConfigController.java`（`system:config:*`，含 `GET /configs/key/{key}`）。
- **表注册与迁移**：`jooq/JooqTables.java` 注册 `SYS_DICT_TYPE`/`SYS_DICT_DATA`/`SYS_CONFIG`；新增 `db/migration/V5__system_modules.sql`（建三表 + 种子数据，含 `sys.title`/`sys.max.login.fail`/`sys.captcha.enabled`）。

后端已通过 `mvn -o compile` 离线编译验证（131 个 class，BUILD SUCCESS）；前端新增/改动文件均通过 `node --check` 语法校验。`USE_MOCK` 与真实后端两种模式下行为一致。

---

## 附录 D. 历史缺陷修复、代码审查与模块规划（综述）

以下结论整合自已归档的工作记录（完整报告见 `docs/archive/` 下对应文件）。

**缺陷修复清单（defect-fix-report）**：共 10 项，按严重度分级。
- High：① 数据权限多角色合并短路（N:N 引入）；② 工作流审批节点无可用受让人时静默失效。
- Medium：③ 内置请假流程受让人写死 USER id=1；④ 前端 mock 消息群发引用已移除字段；⑤ 集成测试无法在沙箱验证且可能基于旧单角色假设。
- Low：⑥ 工作流 CC/执行步骤节点为占位；⑦ 多处注释停留在「V6 收敛/单角色单部门」；⑧ ApiResourceScanner 不自动触发；⑨ DemoJob 为示例任务；⑩ 部门数据权限「用户未挂部门」时降级为不限。

**代码审查（review-java-admin-framework）**：P0（运行期阻断/数据安全）无；P1（重要正确性/安全缺陷，建议本迭代修复）若干；P2（功能缺失/明确 bug，建议近期处理）若干；P3（可优化/文档与健壮性）若干。

**后续模块规划（module-plan）**：M1 审计日志查询与脱敏（P1，已落地）、M2 幂等 Idempotency-Key（P1，已落地）、M3 Token 版本与权限缓存失效（P2，已落地）、M4 工作流任务认领（P2，已落地）、**M5 缓存管理**（P2，已落地：`config/cache` + 监控联动 + 字典/菜单缓存，菜单树含 `user-menus` 按用户键与跨服务驱逐）、**M6 通知渠道**（P3，已落地：`Channel.EMAIL` 邮件 + `Channel.SMS` 短信双渠道，均默认关闭；短信网关经 `provider=LOG/HTTP` 切换，发送记录中心 `system/smslog` 脱敏落库可查询对账）；后续候选含更多运营与分析能力，统一约定与现有实现保持一致。

### M5 缓存管理使用指南（开发者）

- **开启与配置**：`config/cache/CacheConfig` 已全局开启 `@EnableCaching` 并提供 Redis 实现的 `StatisticsCacheManager`；默认 TTL 30m、key 前缀 `acme:cache:`，经 `app.cache.ttl` / `app.cache.key-prefix` 调整。缓存为惰性能力，未使用 `@Cacheable` 不产生 Redis 流量。
- **接入一个热点读**：方法加 `@Cacheable(cacheNames = "xxx", key = "...")`；key 表达式可用 `T(类全名).静态方法(#参数)` 复用归一逻辑（参考 `DictDataService.cacheKey`：空值归一、trim，保证「同口径同键」）。
- **写路径同步失效**：`@CacheEvict`——键可精确推导时按 key 驱逐（如 `DictDataService.create`）；键可能变化（如类型码可改）或删除前不回查时用 `allEntries = true`（数据量小的缓存全清成本可忽略）。**注意 `@CacheEvict` 默认 `beforeInvocation = true`**，方法体抛异常时缓存同样已被驱逐（对只读缓存是安全方向）。
- **跨服务驱逐与按用户键**：参考 `MenuService`——`menu-tree`（管理端菜单树，单一 `ALL` 键）/ `user-menus`（用户菜单树，`#userId` 键）；菜单写操作（`MenuService` 增/删/改/排序）整缓存驱逐两个缓存，角色菜单绑定（`RoleService` 创建/更新/删除）与用户角色变更（`UserService.update/delete`，按 `#id` 精确驱逐）分别按语义驱逐 `user-menus`。跨服务驱逐时引用缓存名常量（`MenuService.USER_MENUS_CACHE`），避免字符串字面量分叉；菜单树仅驱动侧边栏展示，授权判定始终实时查库，缓存陈旧不影响安全（最坏多展示一个 TTL 周期）。
- **可观测**：`GET /api/system/monitor/caches` 自动出现新缓存统计（进程内口径：命中/未命中/淘汰/命中率）；`POST /api/system/monitor/caches/{name}/clear` 清理（仅本实例已使用过的缓存可清理，未使用返回 404）。
- **约束**：Spring Cache 代理对同类内自调用（self-invocation）不生效——缓存方法必须经 Spring 代理调用（控制器/其它 Bean 调用）；返回值应为不可变或调用方不得改写（`DictDataService` 用 `Stream.toList()` 返回不可变列表兜底）。
- **测试模式**：轻量 Spring 上下文（`@EnableCaching` + `ConcurrentMapCacheManager`）+ `mockStatic(JooqWriters.class)` 打库替身，通过 `CacheManager` 可观测状态断言缓存行为；**同一上下文多个用例共享 CacheManager，必须 `@BeforeEach` 清缓存**（参考 `DictDataServiceCacheTest`）。

### M6 邮件通知渠道使用指南（开发者）

- **发送**：`POST /api/system/notifications/send`，`channel=EMAIL`，`emails` 传收件人地址列表（单封上限 50），`title`/`content` 作主题/正文。
- **启用**：`app.notifications.email.enabled=true` 并配置 `from`/`host`/`port`（`username`/`password`/`starttls` 依 SMTP 服务商）；未启用返回 400 不外发。凭据经 `NOTIFICATION_EMAIL_USERNAME`/`NOTIFICATION_EMAIL_PASSWORD` 环境变量注入。
- **新增渠道**：实现 `NotificationPort`（`supports` + `dispatch`）并声明为 Spring Bean，`NotificationService` 自动收集路由，编排层与控制器零改动；渠道特有的纯函数规则放 `rules` 语义的静态类（参考 `kafka.rules.OrderingRules` / `EmailRules`）便于离线单测。

### M6 续：短信通知渠道使用指南（开发者）

- **发送**：`POST /api/system/notifications/send`，`channel=SMS`，`mobiles` 传接收手机号列表（单次上限 100），`content` 作短信正文；短信无主题，`title` 仅作审计标识不随短信发送（DTO 层仍要求非空，前端传内容前缀或「短信通知」）。
- **启用**：`app.notifications.sms.enabled=true`；未启用返回 400 不外发（短信按条计费，与邮件门禁策略一致——不静默丢弃）。
- **网关二选一**（`app.notifications.sms.provider`，条件装配互斥）：
  - `LOG`（默认）：`LoggingSmsGateway` 仅输出脱敏日志（手机号掩码中间四位，复用 `SensitiveMasker.maskPhone`），开发/联调/CI 无需网关凭据与费用；
  - `HTTP`：`HttpSmsGateway` POST JSON 到 `gateway-url`（厂商无关最小契约 `appId`/`appKey`/`mobiles`/`content`），凭据经 `NOTIFICATION_SMS_APP_KEY` 环境变量注入。
- **号码归一**：`SmsRules` 容忍 `+86`/`86` 国家码前缀与空格/连字符分隔（通讯录复制常见形态），归一为 11 位后校验 `1[3-9]\d{9}`；非法号码整批拒绝不外发。
- **签名拼接**：`app.notifications.sms.signature`（如【Acme】）由 `SmsRules.appendSignature` 净化 CR/LF 后拼到正文头部；半开括号自动补齐，超长截 32 字符。
- **对接具体厂商**（阿里云/腾讯云等）：以 `HttpSmsGateway` 为模板新增一个 `SmsGateway` 实现并扩展 `SmsProperties.Provider`，规则层与编排层零改动；前端「消息中心 → 发送短信」入口已就绪（渠道未启用时后端返回 400，前端保持弹窗可修正）。

### M6 续：短信发送记录中心（开发者）

- **定位**：`system/smslog` 镜像登录日志（`system/log`）全链——V37 迁移建表 + `SYS_SMS_RECORD` TableRef + `SysSmsRecordDO` + `SmsRecordRules`（脱敏纯函数）+ `SmsRecordRecorder`（写入）+ `SmsRecordService`（查询）+ `SmsRecordController`（`/api/system/sms-records`，权限 `system:sms-log:read`）+ 前端 `SmsLogView` + V37 菜单种子（id=1092，parent 1002，icon Message，sort 102）。
- **写入时机**：`SmsNotificationAdapter` 在网关返回后落库，**成功与失败均记录**（`status=SUCCESS/FAILED`，失败带 `error_message` 截断）；批次按接收号码拆分逐条记录，`batch_size` 记单批号码数。写入失败仅记 WARN 日志**不反噬发送结果**（网关已计费/已触达，发送结论以网关为准）。
- **脱敏存储**：`mobile` 列存掩码值（`138****8000`，复用 `SensitiveMasker.maskPhone`）——记录中心用于运维排查与对账，**不存明文 PII**；查询按掩码值 LIKE 匹配（前端传明文时服务端先掩码再查）。`content` 截 500 字符（与短信正文上限一致）。
- **查询能力**：分页（`page`/`size`，钳制同 `PageQuery`）+ 筛选（`mobile` LIKE / `status` / `provider` / 时间范围闭区间）+ 排序方向白名单（`ASC/DESC`，非法值回退 `DESC`，防注入）；`GET /summary` 返回总数/成功/失败/失败率/最近发送时间，失败率按百分数保留两位（0~100，总数 0 时 0.00）。
- **统计口径**：失败率 = failure/total（非 success 口径），分母为 0 时返回 0.00 而非 NaN——`SmsRecordRules.failureRate` 为纯函数，离线可测。

### Bean Validation 校验消息键化（开发者）

- **目的**：将 DTO 约束注解的 `message` 从硬编码中文改为资源束键，交由 Spring `MessageSource` 按当前线程 `Locale` 插值，统一支撑中/英等多语言校验提示，杜绝散落各 DTO 的硬编码文案（原 17 个 DTO、56 处硬编码提示已全量键化）。
- **机制**：`common/i18n/ValidationMessageConfig` 声明 `LocalValidatorFactoryBean`，经 `setValidationMessageSource(messageSource)` 将 Hibernate Validator 的插值器委托给应用级 `MessageSource`（基名含 `i18n/validation`，与 `I18nConfig` 同一套）；插值发生在 `ConstraintViolation#getMessage()`，故 `GlobalExceptionHandler` 透出的即本地化文本，**异常处理零改动**。
- **键引用方式（必须花括号）**：`message = "{validation.<键>}"`——Hibernate 仅在 `{}` 包裹时才按资源束键查找，遗漏花括号会被当作字面量原样返回（常见坑）。通用约束（用户名/密码/标题/内容）不带域前缀以便跨 DTO 复用；域特有约束用 `validation.<域>.<字段>[.<约束>]`（如 `validation.dataScope.code.size`）。
- **键存放位置**：`src/main/resources/i18n/validation.properties`（兜底，内容与 `_zh_CN` 一致）/ `validation_zh_CN.properties` / `validation_en_US.properties`，三件套键集必须一致、值不得为空。`ValidationMessageBundleConsistencyTest` 守护「代码引用键 ⊆ 三束键集」与「zh/en 键集对称」，漏配任一语言束测试即失败。
- **新增一个键化消息四步**：① DTO 字段加 `@NotBlank(message = "{validation.xxx}")` 等；② 在 `validation.properties` / `_zh_CN` / `_en_US` 三文件各加同一键（中文默认 + 英文翻译）；③ 运行 `mvn -o test -Dtest=ValidationMessageBundleConsistencyTest` 自检键齐套；④ 如需断言，参考 `ValidationMessageInterpolatorTest` 经最小 Spring 上下文验证插值与嵌套传播。
- **本地化切换**：同 `i18n` 体系——运行时由 `SessionLocaleResolver` + `LocaleChangeInterceptor`（`?lang=xx`）写入 `LocaleContextHolder`，校验插值自动跟随；语言未命中专属束时回退 `validation.properties` 兜底（不抛 `MissingResourceException`）。
- **缺失键行为**：键在资源束中不存在时，Hibernate 返回带花括号的原始模板 `{validation.xxx}`（不抛异常），前端可据此识别漏配键；不要依赖该行为做正常文案。

### 业务异常消息键化（开发者）

- **目的**：将 `BusinessException` 由错误码默认消息派生的提示统一走资源束键化，交由 `I18nService`（底层 `MessageSource`）按当前线程 `Locale` 解析，使业务异常提示与框架错误、DTO 校验提示一样支持中/英多语言，且无需改动约 180 处 `new BusinessException(...)` 调用点。
- **机制**：`ErrorCode` 接口新增默认方法 `messageKey()`，返回 `error.<CODE>`（如 `AUTH_001` → `error.AUTH_001`），四个错误码枚举（`CommonErrorCode` / `SecurityErrorCode` / `IdempotencyErrorCode` / `FileErrorCode`，共 33 个）自动继承，零枚举改动。`BusinessException` 由错误码默认消息构造时记录该键（`getMessageKey()` 非空），`GlobalExceptionHandler.handleBusiness` 据此经 `i18n.getMessage(ex.getMessageKey())` 解析出本地化文案；`getMessage()` 始终为中文兜底（利于日志，不受语言影响）。
- **向后兼容（自定义消息透传）**：由显式消息构造（`BusinessException.of(ec, "...")` / `conflict(...)` / `badRequest(...)`）时 `getMessageKey()` 为 `null`，响应原样回传该中文文案，行为与键化前完全一致——因此内联硬编码提示的调用点无需改动即可工作，后续可按需逐步将其迁移为错误码默认消息。
- **键存放位置**：`src/main/resources/i18n/messages.properties`（兜底，中文）/ `messages_zh_CN.properties` / `messages_en_US.properties`，键名形如 `error.COMMON_401` / `error.AUTH_001` / `error.IDEMPOTENCY_001` / `error.FILE_001`，三件套键集对称、值非空。`BusinessExceptionMessageKeyingTest` 守护「所有错误码键在 zh/en 束齐备且对称」与「zh_CN 值 == 枚举中文」，漏配任一语言束测试即失败。
- **新增一个键化错误提示两步**：① 若尚无对应 `ErrorCode` 常量，在相应枚举加一项（code / 中文 message / httpStatus），键自动为 `error.<CODE>`；② 在 `messages.properties` / `_zh_CN` / `_en_US` 三文件各加同一键（中文默认 + 英文翻译），运行 `mvn -o test -Dtest=BusinessExceptionMessageKeyingTest` 自检。
- **缺失键行为**：键在资源束不存在时 `I18nService` 返回键本身（如 `error.XXX`），不抛异常，前端可据此识别漏配；不要依赖该行为做正常文案。
- **本地化切换**：同 `i18n` 体系——运行时由 `SessionLocaleResolver` + `LocaleChangeInterceptor`（`?lang=xx`）写入 `LocaleContextHolder`，业务异常提示自动跟随。

**QA 业务测试（V28，QA报告）**：6 项缺陷全部闭环——D-5（P0）七个权限码未授 SUPER_ADMIN；D-2（P1）手机/邮箱唯一性无预校验；D-4（P1）删除角色残留孤儿 user_role 行；D-6（P1）void 方法幂等重放必 409；D-1（P2）用户状态无白名单；D-3（P2）公告重发布不清 offline_at；并固化多轮回归测试。

---

## 前后端联通测试（连通探针 / 错误码映射 / 超时 / 契约对称）

> 目标：验证前端与后端之间的接口通信稳定可靠，覆盖四类场景——① 正常请求/响应流程；② 数据格式与字段一致性校验；③ 常见错误码与异常场景处理；④ 网络超时。
> 分层策略：后端用 standalone MockMvc 装配**真实** `GlobalExceptionHandler` + **真实** `I18nService` 验证「HTTP→全局异常映射→统一包络」链路；前端用 `node:test` + 本地 stub（或 `API_BASE` 真实后端模式）验证 axios 拆包契约；Python 提供静态契约对称扫描与**运行时 HTTP/WebSocket 探针**（stub ↔ probe 真实跨进程 + 真实 socket，本机后端就绪后可直接指向真实后端）。

### 1. 后端探针端点与契约

- 端点一：`GET /api/connectivity/ping`（`common/controller/ConnectivityController`），零数据库依赖，返回统一包络 `Result<ConnectivityProbe>`。
- `ConnectivityProbe`（`common/api/dto/ConnectivityProbe`）：`serverTime`（后端 UTC 时钟，用于时钟漂移粗校验）、`receivedLocale`（后端经 `LocaleContextHolder` 回显的实际语言标签，验证多语言协商）、`nonce`（每次随机串，防缓存误判联通）、`framework`（固定 `java-admin-framework`）。
- 端点二：`GET /api/connectivity/slow?delayMs=<0-10000>`（2026-10-21 新增）——**超时探针**，闭环真实 504 链路：不传参默认休眠 5000ms（**刻意大于探针/前端默认超时阈值**，客户端侧必先超时）；`delayMs` 经纯函数 `resolveDelayMs` 解析（非数字 → 400/`COMMON_001` 原样文案；越界收敛到 `[0,10000]`，防线程耗尽型滥用），生效值回显于 `ConnectivitySlowProbe.delayMs`。
- 前端模块：`src/api/connectivity.js` 的 `ping()` 经 `src/api/http.js` 拆包后直接拿到 `data`。

### 2. 后端联通测试（`ConnectivityControllerTest`，19 例）

- 用 `MockMvcBuilders.standaloneSetup(connectivityController, diagnosticController).setControllerAdvice(realHandler)` 装配**真实** `GlobalExceptionHandler`（构造**真实** `I18nService`，读取 `i18n/messages*` 资源束）。
- `DiagnosticController` 为测试内 `@RestController`，刻意抛出各类异常，逐一对齐状态码/错误码/本地化文案：
  - 400 `COMMON_001`（参数校验，`data` 为字段错误 Map）｜ 401 `COMMON_401` ｜ 403 `COMMON_403` ｜ 404 `COMMON_404` ｜ 405 `COMMON_405` ｜ 409 `COMMON_409` ｜ 422 `COMMON_422` ｜ 429 / 500 `COMMON_500` ｜ **504 `COMMON_504`（新增超时映射）**。
- 正常流：断言包络 `code=="0"` / `message=="OK"` / `data.*` 字段齐备 / `traceId` / `timestamp` 存在；`nonce` 每次不同（防缓存）。
- 多语言：经 `Accept-Language` 头驱动，断言中文 `请求超时，请稍后重试` 与英文 `Request timed out, please retry later`；另以直接调用 `handler.handleTimeout(...)` 的确定性单测锁住 Locale 解析。
- **超时探针 4 例（2026-10-21 新增）**：`resolveDelayMs` 纯函数三例（默认 5000ms 且 > 探针默认阈值；越界收敛到 `[0,10000]`；非数字 → 400/`COMMON_001` 中文案经真实 handler 映射）+ `delayMs=0` 快路径一例（立即返回成功包络并回显生效延迟，不拖慢套件）。

### 3. 新增超时错误码与全局映射

- `CommonErrorCode.GATEWAY_TIMEOUT("COMMON_504", "请求超时，请稍后重试", 504)`。
- `GlobalExceptionHandler` 新增 `@ExceptionHandler(TimeoutException.class)` → 504 + `Result.error(COMMON_504, i18n.getMessage("error.gateway_timeout"))`；资源束三件套补 `error.gateway_timeout` 与 `error.COMMON_504`（中/英对称）。
- 此前全局异常处理器**缺失超时映射**，本次补全，使网络超时有稳定错误码可识别。
- **真实 504 链路闭环（2026-10-21）**：`connectivity-spec.json` 的 `timeoutEndpoint` 指向 `/api/connectivity/slow`，此前后端只有 `/ping`，探针打真实后端只会 404 而非超时；`/slow` 落地后，「客户端超时（真实 socket 读超时）」与「服务端 504 映射」两端各自可验证。

### 4. 前端联通测试（`scripts/fe-connectivity.mjs`，离线 5 例 / 真实后端模式 3 通过 + 2 跳过）

- **离线模式（默认）**：用 `node:http` 起本地 stub 服务端模拟后端统一包络（沙箱无后端、无出网）；`node:test` + `axios` 验证四类场景：
  1. ping 正常流程：拆包后得到 `data` 且 `framework`/`serverTime`/`nonce`/`receivedLocale` 与契约一致；
  2. 数据格式一致性：成功包络含 `code/message/data/traceId/timestamp`；
  3. 常见错误码：2xx 但 `code!=0` 提取 `code` 并 `reject`（如 `AUTH_001`）；5xx 回传后端错误码 `COMMON_500`；
  4. 网络超时：后端慢响应触发 axios `ECONNABORTED`。
- **真实后端模式（2026-10-21 新增）**：设置 `API_BASE` 环境变量即跳过本地 stub 直连真实后端（如 `API_BASE=http://localhost:8080`）；ping / 包络字段 / 超时三例走真实链路（超时依赖后端 `/slow` 默认 5000ms > 客户端 300ms 阈值），两个错误码用例因后端未实现对应探针端点以 `test(..., {skip})` 明确跳过（错误码映射由后端 `ConnectivityControllerTest` 覆盖），**不留假绿**。
- 客户端 `request()` **镜像** `src/api/http.js` 的响应拦截拆包逻辑（`code==='0'` 返回 `data`，否则 `reject(Error)` 且 `Error.code = body.code`），确保验证的就是前端真实依赖的契约。
- 运行：`cd pc-admin-vue3-vite && node --test scripts/fe-connectivity.mjs`（需已 `npm install`）；真实模式加 `API_BASE=<后端地址>` 前缀。

### 5. Python 契约对称与运行时探针（后端仓 `scripts/`）

- `check-api-contract.py`：**静态**扫描后端 Controller 注解与前端 `api/*.js` 调用，归一化（小写/去查询串/参数无关 `*`）后比对，输出 `matched / backend_only / frontend_only`；`frontend_only` 即「前端调用但后端无路由」的契约漂移风险。`--strict` 时 drift 非空退出码 1。已兼容后端**无参** `@GetMapping`/`@PostMapping`（路径继承类级 `@RequestMapping`）与前端**拼接 URL**（`url: '/api/x/y/' + id` 视作 `/api/x/y/{id}`），当前全量扫描 `frontend_only=0`。
- `connectivity-probe.py` + `connectivity-spec.json`：**运行时** HTTP 探针，对真实后端发请求断言包络字段与超时（urllib，零依赖）；后端不可达时打印清晰 SKIP 而非崩溃（连接失败经 `(TimeoutError, socket.timeout)` 双捕，兼容 Python ≤3.9）。
- `connectivity-stub.py`：后端联通接口本地 stub（模拟 `Result` 包络 + 各错误码 + 慢响应端点），可在无后端环境离线验证探针。
- `realtime-ws-stub.py` + `realtime-ws-probe.py`（2026-10-21 新增）：**运行时 WebSocket 探针**。stub 镜像后端 WS 契约（`/ws?token=<JWT>`，有效 token 默认 `stub-jwt`，其余 403 + JSON 包络；连接后按 `--push-delay-ms` 推送 `RealtimeMessage` 信封 `{type,payload,ts}`；收到 `ping` 回 `pong`）；probe 为**手写 RFC 6455 客户端**（零依赖），五用例：WS-01 握手 101 + `Sec-WebSocket-Accept` 校验；WS-02 `ping`→`pong` 真实帧往返；WS-03 推送信封字段校验（`--expect-push yes/no`）；WS-04 缺 token 拒绝；WS-05 篡改 token 拒绝。握手不可达时返回 status 0 干净失败（连不上 ≠ 拒绝，不可误判通过）。
- 本机联调命令：
  ```bash
  # HTTP 联通
  python3 scripts/connectivity-stub.py &                      # 默认 8080
  python3 scripts/connectivity-probe.py --base-url http://127.0.0.1:8080

  # WebSocket 联通（stub ↔ probe 真实 socket）
  python3 scripts/realtime-ws-stub.py --port 18091 --push-delay-ms 150 &
  python3 scripts/realtime-ws-probe.py --base-url ws://127.0.0.1:18091 --token stub-jwt
  ```

### 6. 真实运行环境联调（2026-10-21 沙箱内实测全链路）

> 本轮已在沙箱内启动**真实 MySQL 8.4.11 + Redis + JDK 21 后端实例**，完成「探针端点 → 登录链路 → 三类联通探针 → vite 代理链路」全要素验证，并修复 2 个仅真实环境暴露的 P0。以下为实测证据与启动方法。

#### 6.1 离线环境启动真实后端的唯一可行路径（`java -cp` 法）

离线 Maven 仓库缺少打包插件依赖（`maven-jar-plugin:3.4.2` 的传递依赖、`spring-boot-maven-plugin:3.3.4` 的 buildpack 依赖等），`clean package` 与 `spring-boot:run` 均不可用；改用 surefire booter classpath 直接启动：

1. 提取 classpath（**必须 `-X` 模式**，booter 目录才留存；普通 `mvn test` 后目录被清理）：
   ```bash
   mvn -o -X -pl scaffold-admin -am test -Dtest='ObservabilityPropertiesTest' \
     -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
   # 解析 scaffold-admin/target/surefire/surefirebooter*.jar manifest 的 Class-Path（折行拼接）
   # 生成 scaffold-admin/target/dev-classpath.txt（205 项绝对路径，0 missing，含 11 模块 target/classes）
   ```
2. 启动（**三个关键坑**，缺一不可）：
   ```bash
   cd scaffold-admin
   env -u SERVER__PORT -u SERVER__HOST JWT_SECRET="<32字节base64>" \
     $JAVA_HOME/bin/java -cp "$(cat target/dev-classpath.txt)" \
     com.acme.scaffold.FrameworkApplication --spring.profiles.active=local --server.port=8080
   ```
   - **坑 1：`SERVER__PORT` 环境变量劫持**。后台任务环境会注入 `SERVER__PORT=<随机5位>`（联动 `CODEBUDDY_SERVICE_PROXY_URL`），Spring Boot relaxed binding 将双下划线映射为 `server.port`，**覆盖 yml 的 8080**，表现为每次启动随机高位端口（实测 50280/50860）。对策：`env -u SERVER__PORT -u SERVER__HOST` + 显式 `--server.port=8080`。
   - **坑 2：JobRunr Dashboard 8000 端口冲突**。重启前必须先 `lsof -ti :8080/:9090/:8000` 杀残留实例，否则新实例 `BindException: Address already in use` 启动失败。
   - **坑 3：沙箱代理**。访问 localhost 必须 `curl --noproxy '*'` 或 `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY no_proxy="*"`（沙箱注入 `http_proxy=http://127.0.0.1:50910`）。
3. 启动验证：`Started FrameworkApplication in ~15s`；V32~V39 迁移全 green 应用（此前库仅到 V38）；`/livez`、`/readyz` UP。

#### 6.2 真实环境暴露并修复的 2 个 P0

| # | 现象 | 触发条件 | 根因 | 修复 | 守护测试 |
|---|---|---|---|---|---|
| P0-1 | `APPLICATION FAILED TO START ... required a single bean, but 2 were found: spanRecorder, otlpSpanExporter` | local 默认组合（otlp=false + recorder=true）必现；单测用 Mockito 手工构造不暴露 | `ObservabilityConfig.otlpSpanExporter` 无条件注册，与 `SpanRecorder` 形成单点注入歧义 | `otlpSpanExporter` 加 `@Primary`（双 sink 经 `OtlpTracingFilter` 的 `List<SpanExporter>` 聚合分发语义不变） | `ObservabilityWiringTest` 3 例（`AnnotationConfigApplicationContext` + `MapPropertySource` 驱动三种开关组合） |
| P0-2 | 探针 ping/slow 返回 401 空体（`WWW-Authenticate: Bearer`） | 无 token 直连 `/api/connectivity/*` | `SecurityConfig.BASE_PUBLIC_API` 白名单遗漏联通探针端点（联调契约要求匿名可访问） | 白名单插入两条 GET matcher | `SecurityPublicApiTest` 2 例（反射读 `BASE_PUBLIC_API`；helper 显式 `setServletPath(uri)`——`MockHttpServletRequest` 默认空 servletPath 会使 `AntPathRequestMatcher` 恒 false） |

#### 6.3 登录链路（真实 MySQL）

- 验证码为四则运算，**乘法用全角 `×`、除法用 `÷`**（如 `3 × 4 = ?`），客户端须替换后再求值；`LoginCommand` 字段为 `captchaAnswer`（非 `captcha`，误发报 400 `AUTH_008`）。
- 登录成功取得 JWT（后续 WS 探针复用）。

#### 6.4 三类联通探针实测结果（真实后端 8080）

| 探针 | 命令 | 结果 |
|---|---|---|
| HTTP | `python3 scripts/connectivity-probe.py --base-url http://localhost:8080` | **2/2 PASS**（ping code=0 全字段；slow 真实触发 `TimeoutError`） |
| WebSocket | `python3 scripts/realtime-ws-probe.py --base-url ws://localhost:8080 --token <JWT>` | **4 PASS / 0 FAIL / 1 SKIP**（WS-03 推送信封因无业务事件合理跳过） |
| 前端 Node | `API_BASE=http://localhost:8080 node --test scripts/fe-connectivity.mjs` | **3 PASS / 0 FAIL / 2 SKIP**（两个错误码用例后端无对应探针端点，由 `ConnectivityControllerTest` 覆盖，不留假绿） |

#### 6.5 vite 代理链路（真实 dev server 5173）

- 启动：`node node_modules/vite/bin/vite.js --port 5173 --host 127.0.0.1`（须用受管后台任务前台化启动；`nohup ... &` 的脱离进程会被沙箱回收，实测 5173 起不来）。
- 实测：`/api/connectivity/ping` 经 5173 代理 → 8080 返回完整包络（16ms）；SPA 首页 200「运营管理后台」；`/src/main.js` 模块转换 200；WS 经 `/ws` 代理 4 PASS / 1 SKIP。前端 WS 客户端连同源 `/ws` 的链路由此闭合。

#### 6.6 本轮回归门禁（零副作用确认）

- `mvn -o clean test`：**857/857 全绿**（852 基线 + `ObservabilityWiringTest` 3 例 + `SecurityPublicApiTest` 2 例；12 模块 SUCCESS）。
- `scripts/check-conventions.py`：EXIT=0（11 源码根 / 507 文件）。
- 约定校验器 Python 自测（`scripts/test_check_conventions.py`）：17/17 OK。
- 三类探针 + vite 代理：见上表，全绿。
- 历史证据链（Python stub ↔ probe 跨进程、WS stub ↔ probe 真实 RFC 6455 帧、Node ↔ Python stub）仍有效，见 §5。

### 7. JaCoCo 覆盖率门禁（profile 已就绪，联网环境验证）

- `pom.xml` 内置 `coverage` profile（默认**不激活**）：`org.jacoco:jacoco-maven-plugin:0.8.12`，三个 execution——`prepare-agent`（initialize 阶段挂 agent）/ `report`（test 阶段出报告）/ `check`（verify 阶段卡门禁），规则 **LINE ≥ 0.10 / BRANCH ≥ 0.05**（起步阈值，随覆盖提升逐步收紧）。已用 Python `xml.etree` 结构化校验 12 项全部通过（profile/插件/三 execution/阈值/默认不激活）。
- **离线仓库未缓存该插件**，`mvn -o -Pcoverage validate` 实测 EXIT=1（`Cannot access ... in offline mode`）——这是离线环境的预期失败，非配置缺陷。
- 联网环境验证命令：
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  /Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -Pcoverage test      # 仅跑单测 + 出覆盖率报告
  /Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -Pcoverage verify    # 全量（含 IT，需 MySQL）+ check 门禁
  /Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -Pcoverage verify -DskipITs   # 跳过 IT 的快速门禁
  ```
  报告产物：`<各模块>/target/site/jacoco/index.html`（按模块独立出报告与卡门禁，BUNDLE 级；V1.0.3 起父 POM profile 被 11 个模块继承，无跨模块聚合报告）。
- CI 可直接套用的 job 片段（当前 `ci.yml` 四任务未含 coverage，需要时添加）：
  ```yaml
  coverage-gate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Unit tests with JaCoCo gate
        run: mvn -Pcoverage verify -DskipITs
  ```
  （门禁只在 `verify` 阶段触发；`-DskipITs` 使 CI 不必等 MySQL service，IT 由既有 integration-test 任务覆盖。）

---

## 可观测中心前端契约测试（`scripts/fe-observability.mjs`，25 例，离线可跑）

> 目标：为「可观测中心」五合一运维入口（链路追踪 / Kafka 监控 / N+1 疑似 / 幂等记录 / 告警收件箱）建立 API 形状契约守护。与上文联通测试互补：`fe-connectivity.mjs` 验证**传输层**（真实 HTTP + 包络 + 超时），本文件验证**各运维面 API 的形状**（method/url/params）与**403 降级可判定性**，且端到端驱动生产 `src/api/http.js` 拦截器链而非重新实现契约。

### 1. 被测对象与用例分布

- 5 个 API 模块：`src/api/observability.js`（OBS-01~05）、`kafkaMonitor.js`（KFK-01~04）、`nplusOne.js`（NP1-01~03）、`idempotencyRecord.js`（IDM-01~04）、`alertInbox.js`（ALR-01~03）。
- 纯函数 `src/views/monitor/components/format.js`（FMT-01~05：epoch/日期/时长/占比/`isForbidden`）。
- 横切 XUT-01：403 拒绝时 reject 必须携带 `response.status===403`（页面据此走 Tab 级降级空态）。

### 2. 测试基建（Node ≥ 22.15）

- **`module.registerHooks` 虚拟 element-plus**：其完整依赖图在 Node 沙箱下实测被 kill（SIGTERM），故替换为无操作桩（`ElMessage` / `ElMessageBox`）；Vue 本体可原生 import。
- **解析钩子**：`@/x/y` 映射 `src/x/y.js`（镜像 vite alias）；扩展名省略的相对导入（`./config`）解析失败时补 `.js` 重试。
- **`localStorage` 打桩**：`store/auth` 模块初始化即读。
- **可编排 axios 适配器**：`axios.defaults.adapter` 按路由表返回脚本化响应，不发起真实网络却驱动生产拦截器链；**非 2xx 必须自行抛 `AxiosError`（附 response）**——新版 axios 的 settle/validateStatus 在内置适配器内，自定义适配器返回非 2xx 会被当作成功，页面将永远拿不到 `response.status`（XUT-01 守护此契约，曾因此返工）。

### 3. 运行方式

```bash
cd pc-admin-vue3-vite
node --test scripts/fe-observability.mjs    # 25 例
node --test scripts/fe-connectivity.mjs     # 存量联通回归 5 例
```

需已执行 `npm install`（依赖装在本机 managed workspace）。配套门禁：

```bash
python3 ../java-admin-framework/scripts/check-api-contract.py   # 前端 api ↔ 后端 Controller 路由静态对称
npm run build -- --outDir /tmp/fe-obs-build --emptyOutDir        # 离线构建（沙箱 shim 拦截清 dist，故换目录）
```

### 4. 新增运维面前端封装的标准动作

1. 在 `src/api/` 加模块（复用 `http.js`，只声明 method + url + 参数，空值置 `undefined` 不传空串）；
2. 在 `views/monitor/components/` 加 Tab 组件（汇总卡 + 筛选 + 表格；`isForbidden` 403 降级；危险操作 `ElMessageBox.confirm`）；
3. 在 `fe-observability.mjs` 补该面用例（正常透传 / 业务错误 / 403 三类至少各一）；
4. 若为新菜单，补 V 迁移（`INSERT IGNORE` + `uk_tenant_menu_code` 幂等，**id 先全量核对防碰撞**，见 V39 对 V35/V36 id=1091 碰撞的修复）；
5. 跑 §3 三件套门禁。
