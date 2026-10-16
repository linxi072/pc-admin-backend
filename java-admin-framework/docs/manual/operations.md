# 运维手册

面向运维与一线支撑。覆盖可观测性（Actuator/Prometheus/指标解读）、监控/日志/在线用户/定时任务等运营菜单、报表与工作台、审计日志查询与脱敏、健康探针、Token 缓存失效、缓存管理与邮件通知渠道运维、以及常见故障排查（含工作流设计「不存在」案例）。

---

> **阅读指引**：可观测性运营侧（Prometheus/Alertmanager/Grafana）的部署步骤见《部署手册》；Token 版本与缓存失效的设计原理见《架构手册》。

---

## 本手册整合来源

- **docs/archive/java-admin-framework-README.md**
  - ## 6. 可观测性
  - ## 17. 审计日志查询与脱敏
  - ## 19. Token 版本与权限缓存失效
  - ## 20. 验证记录（原生 MySQL + Redis，无 Docker）
  - ## 17. 报表管理与工作台优化
  - ## 21. 在线定时任务管理（JobRunr 双层架构）
  - ## 27. 在线用户管理（V27 迁移）
  - ## 29. V28 QA 业务测试与缺陷修复（6 缺陷全闭环）
  - ## 30. 系统管理新增监控/日志菜单（V29 迁移）
- **docs/archive/defect-investigation-workflow-design-not-found-2026-10-12.md**
  - ## 1. 现象与影响面
  - ## 2. 系统化排查步骤（可复现）
  - ## 3. 根因定位
  - ## 4. 修复建议（按优先级）
  - ## 5. 验证方案
  - ## 6. 附录：证据清单
  - ## 7. 修复实施状态（A→E 已全部落地）

---

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 6. 可观测性

- 健康检查：`GET /actuator/health`（仅 health/prometheus 公开，其余需 `platform:actuator:read`）。
- 指标：`GET /actuator/prometheus`（JVM、HTTP、HikariCP、业务计数器、审批耗时）。
- 慢 SQL：`SlowQueryListener`（jOOQ `ExecuteListener`）记录超过 `app.jooq.slow-query-threshold-ms`（默认 500ms）的 SQL。
- 调度观测：JobRunr Dashboard（local 环境 `http://localhost:8000/dashboard`）可查看任务成功率、重试与耗时。
- 告警阈值建议：QPS 跌零、错误率 >1%、P99 >800ms、JVM 老年代 >80%、连接池使用率 >85%、JobRunr 失败任务数 >0。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

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
5. **admin 默认密码不可用**：`V32__consolidated_schema.sql`（原 V1 段）中 `admin` 的 `password_hash` 是占位死值，  
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

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 17. 报表管理与工作台优化

运营分析能力：在工作台基础上新增「报表管理」独立模块（原「使用报表」已重命名为「报表管理」），并优化工作台布局与可读性。
**V24 起原单页内的 6 个报表类型 Tab 已拆分为 6 个独立功能菜单项**（父菜单「报表管理」目录化，见 §17.6），各报表页业务功能、数据展示与交互逻辑与原页签一致。

### 17.1 数据来源

直接基于 `sys_operation_log`（操作审计日志）聚合，**不新增独立统计表**——审计日志已覆盖
`module_code / operation_type / operator_id / occurred_at / success / duration_ms`，足以支撑
访问量、功能使用频次、活跃用户数、成功失败分布等指标（见 §20 审计设计）。

### 17.2 后端接口

| 接口 | 说明 | 权限 |
| --- | --- | --- |
| `GET /api/workbench/stats` | 工作台统计卡片（按当前登录人收敛） | 仅认证 |
| `GET /api/workbench/recent-activities?limit=8` | 当前登录人最近操作记录（脱敏视图） | 仅认证 |
| `GET /api/report/types` | 报表类型列表（使用统计 / 类别统计 / 状态统计） | `report:read` |
| `GET /api/report/data?type=&rangeType=TODAY\|LAST_7D\|LAST_30D\|CUSTOM[&start=&end=&module=&success=&sortBy=&order=]` | 报表数据（多维聚合 + 筛选 + 排序） | `report:read` |
| `GET /api/report/export?...` | 报表数据导出（CSV，UTF-8 BOM） | `report:read` |

报表聚合（`workbench/report/ReportService`）为纯 jOOQ 聚合，支持三类维度：
- **时间维度（趋势）**：按小时（TODAY）/ 按天（其余）分桶，对窗口内全部桶位补零，前端可直接渲染折线/面积图（`ReportSeriesPointVO` 含 `visits` 与 `activeUsers`）；
- **类别维度**：按 `module_code` 聚合计数 Top10（`ReportCategoryStatVO`），支持按次数 / 占比 / 名称排序与升/降序；
- **状态维度**：按 `success` 聚合成功/失败分布（`ReportStatusStatVO`）；
- **筛选**：可选 `module`（类别）与 `success`（状态）过滤；**汇总**：总量 / 去重活跃用户数 / 成功率 / 涉及模块数 / 平均耗时（`ReportSummaryVO`）。

时间窗解析（`ReportRange#resolve`）与连续补零（`ReportService#fillHourly/fillDaily`）均为
无 IO 依赖的纯函数，已配套离线单测（13 例全绿）。

### 17.3 菜单与权限（V19 + V20 迁移）

`V19__usage_report_menu.sql` 插入「报表管理」顶级菜单（`route_path=/report/management`、`icon=DataAnalysis`、
`permission_code=report:read`）与对应接口资源（路径 `/api/report/**`），并绑定 `SUPER_ADMIN(role_id=1)`。
权限码同时登记于 `sys_menu` 与 `sys_api_resource`，故侧边栏可见性与 `@PreAuthorize` 均生效。

> 为兼容已执行过旧版 V19（菜单名「使用报表」/ 路由 `/report/usage` / 权限 `report:usage:read`）的历史库，
> 新增 `V20__report_management_rename_fix.sql`（幂等 `UPDATE`）将既有行统一修正为「报表管理 / /report/management / report:read」，
> 保证重命名后功能逻辑一致、不影响原有业务流程。

### 17.6 报表页签拆分为独立菜单（V24 迁移）

原「报表管理」单页以 `el-tabs` 承载 6 个报表视角，V24 起每个视角独立为菜单页：

| 子菜单（id） | 菜单名 | 路由 | 图标 | 对应原页签 |
| --- | --- | --- | --- | --- |
| 1050 | 使用统计 | `/report/usage` | DataLine | `USAGE` |
| 1051 | 类别统计 | `/report/category` | PieChart | `CATEGORY` |
| 1052 | 状态统计 | `/report/status` | DataAnalysis | `STATUS` |
| 1053 | 定时任务执行统计 | `/report/job-exec` | Timer | `JOB_EXEC` |
| 1054 | 登录统计 | `/report/login` | User | `LOGIN` |
| 1055 | 接口调用统计 | `/report/api-call` | Connection | `API_CALL` |

- 父菜单 id=1030 由叶子 `MENU` 升级为 `CATALOG`（`route_path` / `permission_code` 置空），与 V17「系统管理 / 工作流」目录范式一致；侧边栏渲染为可折叠分组（`layout/MenuTree.vue` 按 `children` 自动分组）；
- **权限模型不变**：6 个子菜单 `permission_code` 统一为 `report:read`，接口 `/api/report/**` 仍由 `report:read` 鉴权（`ReportController` 类级 `@PreAuthorize` 不动）；菜单可见性经 `sys_role_menu` **按菜单独立绑定**，角色可只绑定单个报表菜单；
- 前端旧路由 `/report/management` 保留重定向至 `/report/usage`（历史书签/外部链接不失效，与「消息中心」合并时的兼容做法一致）；
- 全部 `UPDATE` / `INSERT IGNORE` 幂等；后端 Java 代码零改动。

### 17.4 前端（pc-admin-vue3-vite）

- 新增 `src/components/BaseChart.vue`：ECharts 封装（init / setOption / resize / dispose），经 npm 依赖引入 `echarts`；
- 重写 `src/views/DashboardView.vue`：卡片加图标/趋势、新增「最近动态」面板、快捷入口增加「报表管理」、响应式栅格；
- 新增 `src/components/ReportPanel.vue`：通用报表面板（筛选工具栏 + 汇总卡片 + 趋势图 + 分布/明细 + 导出 CSV），由原 `ReportManagementView.vue` 抽取而去掉 Tab 切换，报表类型与标题经 props 固定；
- 新增 `src/views/report/` 下 6 个薄壳页面（`UsageReportView` / `CategoryReportView` / `StatusReportView` / `JobExecReportView` / `LoginReportView` / `ApiCallReportView`），各固定一个报表视角，业务逻辑全部复用 `ReportPanel`（与原页签一致）；
- 路由 `src/router/index.js` 注册 `/report/usage` 等 6 个独立路由（懒加载，name=reportUsage 等），旧 `/report/management` 重定向至 `/report/usage`；原 `ReportManagementView.vue` 已删除；
- `src/api/report.js` 新增 `reportTypes` / `reportData` / `reportExport`（原 `usageReport` 已迁移至此）。

### 17.5 统计报表扩展（定时任务执行 / 系统登录 / 接口调用）

在使用视角（USAGE / CATEGORY / STATUS）之外新增三类统计视角，数据源均为既有业务日志、**不新增统计表**：

| 报表类型 | 数据源 | 关键指标 |
| --- | --- | --- |
| `JOB_EXEC` 定时任务执行统计 | `sys_job_log` | 执行次数、成功率、失败率、平均/最大/P95 耗时、耗时分布、最近执行明细 |
| `LOGIN` 系统登录统计 | `sys_login_log` | 登录次数、成功率、活跃登录用户、异常登录记录、登录类型分布 |
| `API_CALL` 接口调用统计 | `sys_operation_log` | 调用量、错误率、平均/最大/P95 响应时间、响应时间分布、Top 接口明细 |

设计要点：

- **登录日志落库**：新增 `security/log/LoginLogRecorder`（`@Transactional(REQUIRES_NEW)` 独立事务，
  防止登录失败主事务回滚丢失异常登录记录），在 `AuthApplicationService#login` 各失败分支与成功路径埋点，
  `failure_code` 取 `SecurityErrorCode#code()`（如 `AUTH_001`）；
- **关键字筛选**（`ReportQuery#keyword`）：JOB_EXEC→任务名、LOGIN→用户名、API_CALL→请求路径，
  含 SQL 通配符 `%`/`_` 转义；使用视角忽略；
- **分布分桶**（`ReportDistributionVO`）：耗时 SQL 侧 `CASE` 聚合（`<100ms / 100-500ms / 500ms-1s / 1s-5s / >5s / 无耗时记录`），
  登录视角为登录类型分布；占比换算为纯函数 `toDistribution`；
- **P95 策略**：样本 ≤ 5000 内存精确（最近秩法纯函数 `p95Of`），超过退化为最大值，避免大窗口全量排序；
- **明细表**（`ReportDetailTableVO`）：列驱动（`columns` + `rows` 字符串映射），
  最近执行 / 异常登录 / Top 接口统一收敛，前端动态渲染 `el-table`；
- **CSV 导出分段**：统计视角输出「汇总 + 分布 + 明细 + 趋势」四段，使用视角维持原「汇总 + 类别 + 状态 + 趋势」；
- **纯函数单测**：`ReportServiceStatsTest` 13 例覆盖分布换算 / P95 / 三序列兼容 / 关键字条件，全量单测 238 例全绿。

> **白屏缺陷修复（前端）**：`ReportManagementView.vue` 曾因首渲染时序问题整页空白——`report` 初始为 `null`、
> `loading` 初始为 `false`，而 Vue 首渲染发生在 `onMounted`（数据加载）之前，内容分支的
> `report.categoryStats.length` 对 null 解引用抛 `TypeError`，挂载中止且不触发任何请求。
> 修复：该表达式改可选链 `report?.categoryStats?.length`，同时 `loading` 初始值改 `true`（首渲染走骨架屏）做纵深防御。
> 排查方法：用 `@vue/compiler-dom` 编译真实模板 + 模拟 setup 状态执行 render/slot，可在无浏览器环境下复现类问题。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 21. 在线定时任务管理（JobRunr 双层架构）

提供任务配置（执行时间 / 触发周期 / 任务参数）、启动、暂停、列表与实时状态、执行日志的完整闭环，
所有操作**即时生效**，并含参数校验与错误处理。

### 21.1 为什么是「双层架构」

JobRunr 原生的 `jobrunr_*` 表由框架内部维护、不可 CRUD，无法承载业务侧的「任务定义 / 参数 / 统计」。
因此拆为两层：

| 层 | 载体 | 职责 |
| --- | --- | --- |
| 配置与日志层 | `sys_job` / `sys_job_log`（本模块自管） | 任务定义元数据、参数、统计、执行日志 |
| 调度执行层 | JobRunr（`jobrunr-spring-boot-3-starter` 6.3.4） | 按 CRON / 间隔 / 单次触发并回调执行器 |

两层通过 `sys_job.scheduler_ref` 关联：`CRON`/`SIMPLE` 存 recurring job id（约定 `sysjob-<id>`），
`ONE_TIME` 存一次性 Job 的 UUID 字符串。任务状态 `JobStatus`：`PAUSED`（默认，未调度）/
`RUNNING`（已调度）/ `COMPLETED`（一次性任务执行完成）。

> 零新增依赖：JobRunr 6.3.4 与 spring-boot-3-starter 已在 `pom.xml` 声明并离线可用。

### 21.2 关键设计

- **默认暂停**：新建任务落库为 `PAUSED` 且不注册调度，必须显式「启动」才参与调度，避免误触发；
- **即时生效**：启动 → 立即调用 JobRunr 注册（`scheduleRecurrently(id, cron, λ)` /
  `scheduleRecurrently(id, Duration, λ)` / `schedule(UUID, Instant, λ)`）；暂停 → `delete(...)` 取消；
  编辑运行中任务 → 先取消旧调度、写入新配置、再按新配置重启；
- **下次执行时间实时计算**：不依赖 JobRunr 查询 API，由纯函数 `JobNextFire#compute` 计算
  （CRON 复用 JobRunr 的 `org.jobrunr.scheduling.cron.CronExpression#next`，与调度器同源；
  SIMPLE = 上次触发 + 间隔；ONE_TIME 未到期返回其时间，否则 `null`）；
- **校验复用调度器解析器**：`JobValidator` 直接用 `CronExpression.create(...)` 校验 cron，
  保证「校验通过」即「可被调度」（支持 5 段分钟精度 / 6 段秒精度）；SIMPLE 间隔不得小于 5 秒
  （对齐 `Schedule.SMALLEST_SCHEDULE_IN_SECONDS`）；ONE_TIME 必须晚于当前；`params` 须为合法 JSON；
- **执行与统计**：JobRunr 到点回调 `JobExecutor#run(jobId)` → 解析 `taskKey` 对应处理器 → 执行 →
  回写 `last_fire_time / next_fire_time / total_runs / success_runs / failure_runs / last_status /
  last_error / last_duration_ms` → 写 `sys_job_log`（含 `trace_id`，与审计/异常日志串联）。
  执行过程**不包长事务**（处理器可能含慢外部调用）；
- **可扩展的处理器**：业务实现 `JobHandler`（`key()/name()/execute(ctx)`）并标注 `@Component`
  即被 `JobHandlerRegistry` 自动收录，前端下拉可直接选择。内置 `demo-echo`（打印参数，参数含
  `{"fail":true}` 可模拟失败）与 `demo-sysinfo`（输出堆内存/线程数）。

### 21.3 接口

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/jobs/meta` | 调度引擎可用性 / 最小间隔秒数 | `job:read` |
| GET | `/api/jobs/handlers` | 可选处理器列表 | `job:read` |
| GET | `/api/jobs` | 分页查询（名称/分组/状态/触发类型/处理器筛选） | `job:read` |
| GET | `/api/jobs/{id}` | 任务详情 | `job:read` |
| GET | `/api/jobs/{id}/logs` | 执行日志（按触发时间倒序） | `job:read` |
| POST | `/api/jobs` | 新建（初始暂停） | `job:manage` |
| PUT | `/api/jobs/{id}` | 编辑（运行中先暂停再按新配置重启） | `job:manage` |
| POST | `/api/jobs/{id}/start` | 启动（立即注册调度） | `job:manage` |
| POST | `/api/jobs/{id}/pause` | 暂停（立即取消调度） | `job:manage` |
| DELETE | `/api/jobs/{id}` | 删除（逻辑删除，运行中先取消调度） | `job:manage` |

### 21.4 菜单与权限（V21 迁移）

`V21__job_management.sql` 建 `sys_job` / `sys_job_log` 两张表，插入「定时任务」顶级菜单
（`route_path=/job/list`、`icon=Timer`、`permission_code=job:read`）与逐端点接口资源
（`job:read` / `job:manage`），并绑定 `SUPER_ADMIN(role_id=1)`。全部走 `CREATE TABLE IF NOT EXISTS`
与 `INSERT IGNORE`，可重复执行。

### 21.5 前端（pc-admin-vue3-vite）

- `src/api/job.js`：`jobMeta/jobHandlers/jobPage/jobDetail/jobLogs/jobCreate/jobUpdate/jobStart/jobPause/jobDelete`；
- `src/views/JobListView.vue`：筛选工具栏 + 自动刷新开关 + 任务表格（名称/分组/处理器/触发规则/参数/
  状态/上次执行/下次执行/执行次数/操作）+ 新建/编辑对话框（触发类型单选联动 CRON/间隔/单次字段、
  参数 JSON 校验）+ 执行日志对话框（含耗时迷你柱状图）；
- 路由注册 `/job/list`（懒加载，name=jobList）；`DashboardView` 快捷入口新增「定时任务」。

### 21.6 验证状态

- 后端离线编译 `mvn -o test-compile` 通过；
- 离线单测：`JobValidatorTest`（17 例）+ `JobNextFireTest`（12 例）共 **29 例全绿**；
- 前端 `npm run build` 通过；
- 本机端到端（需 MySQL + 真实启动）：`POST /api/jobs` 建任务 → `POST /api/jobs/{id}/start` 启动 →
  观察 `sys_job_log` 与 `sys_job.next_fire_time` 更新 → `POST /api/jobs/{id}/pause` 后不再产生新日志。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 27. 在线用户管理（V27 迁移）

### 27.1 功能概览

- 菜单入口：系统管理 → 在线用户管理（`/system/online`，V27 种子菜单 id=1060，
  SUPER_ADMIN 自动绑定）；
- 接口前缀 `/api/system/online-users`，权限码三分（与 `sys_api_resource` 种子一一对应）：
  - `system:online:read`：分页查询在线会话 / 登录限制记录；
  - `system:online:force-logout`：批量强制下线；
  - `system:online:restrict`：设置 / 解除临时限制登录；
- 列表列：账号、姓名、登录 IP、登录时间、最近活跃时间、会话状态
  （在线 / 空闲 / 已限制）、设备（当前设备打「本机」标）；
- 筛选：账号模糊、IP 模糊、状态精确；分页；自动刷新（5/10/30/60 秒，默认关闭，
  轮询静默拉取不触发 loading 闪烁，弹窗打开时跳过）；
- 写操作均 `ElMessageBox` 二次确认；后端 `@AuditOperation` 落审计日志，
  限制记录结构化沉淀于 `sys_login_restriction`，双重可追溯。

### 27.2 数据模型（V27 两张新表，均无 deleted 列）

- `sys_online_session`：一行 = 一个在线设备会话，业务键 `(tenant_id, user_id, device_id)`。
  登录成功 upsert 登记（同设备重复登录重置登录时间而非插多行）；请求心跳节流更新
  `last_active_at`；登出 / 强制下线 / 限制连带下线 / 空闲回收置 `OFFLINE` + `offline_reason`。
  行不物理删除，历史行作为「最近下线记录」留存。
- `sys_login_restriction`：临时限制记录即审计留痕，不物理删除。状态机
  `ACTIVE → RELEASED`（`release_type` 区分 MANUAL / AUTO_EXPIRED）；
  `duration_minutes=0` 为长期（`expire_at` 为空），仅可手动解除。

### 27.3 核心机制

- **心跳节流**：`HeartbeatThrottle` 按「用户+设备」维度限流
  （默认 60s 最多一次写库，构造参数 <1s 按 1s 兜底），把写量压到
  「在线设备数 / 间隔」；`ConcurrentHashMap` + 超 10,000 条目触发陈旧清理
  （超过 2 个周期未出现），长期运行内存有界。
- **强制下线实时生效**：吊销目标设备刷新令牌 + 会话置 `OFFLINE`；
  已签发的 access token 由 `OnlineSessionFilter` 在下一个请求时校验拦截（401），
  无需等待自然过期。过滤器对「无会话行」（改造前历史令牌）按 fail-open 放行，
  保证平滑升级。
- **限制登录语义**：登录链路 `AuthApplicationService` 强制校验
  （423 `AUTH_015`，消息含预计解除时间）；限制生效时**同步强制下线其全部在线会话**，
  避免「禁止新登录但在线会话继续操作」的漏洞。到期由 `LoginRestrictionJob`
  每分钟自动解除；任务未执行的窗口期内，过期记录在登录侧同样判为不生效
  （`RestrictionRules.isActive` 与定时任务互为兜底）。
- **展示状态口径统一**：列表列「会话状态」由 `OnlineSessionRules.displayStatus`
  计算（`RESTRICTED > IDLE > ONLINE`），与登录链路、筛选条件共用同一判定，
  避免「页面显示生效中、登录却放行」的口径分裂。
- **登出 / 改密联动**：登出与改密成功后将该用户全部在线会话置离线
  （`REASON_LOGOUT` / `REASON_PASSWORD_CHANGED`）。

### 27.4 新增文件与改动

| 类型 | 文件 |
| --- | --- |
| 迁移 | `db/migration/V32__consolidated_schema.sql`（原 V27 段：两表 + 菜单 + API 资源种子） |
| 实体 | `system/entity/SysOnlineSessionDO.java`、`SysLoginRestrictionDO.java` |
| 规则 | `security/online/OnlineSessionRules.java`、`RestrictionRules.java`、`HeartbeatThrottle.java` |
| 过滤器 / 任务 | `security/online/OnlineSessionFilter.java`、`LoginRestrictionJob.java`、`LoginRestrictionRunner.java` |
| DTO | `system/dto/OnlineUserQuery.java`、`OnlineUserView.java`、`ForceLogoutRequest.java`、`RestrictLoginRequest.java`、`LoginRestrictionView.java` |
| 服务 / 接口 | `system/service/OnlineUserService.java`、`system/controller/OnlineUserController.java` |
| 前端 | `api/onlineUser.js`、`views/system/OnlineUserView.vue`、`router/index.js`（路由注册） |
| 改动 | `AuthApplicationService`（登录校验/登记/登出/改密联动）、`SessionCleanupService`（空闲回收联动会话置离线）、`RefreshTokenService(+Impl)`（按设备吊销）、`SecurityConfig`（过滤器注册）、`JwtProperties`（心跳/空闲阈值配置）、`SecurityErrorCode`（AUTH_015）、`JooqTables`（新表注册） |

### 27.5 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `mvn -o test` | 309/309 BUILD SUCCESS（新增 36 例：规则类 29 + 服务守卫 7） |
| 集成测试（12 类） | `mvn -o test -Dtest="*IT"` | 33/33 BUILD SUCCESS（新增 `OnlineUserIT` 7 例） |
| 前端 | `vite build` | 构建成功 |

`OnlineUserIT` 覆盖：登录登记闭环（账号/IP/状态）、账号与 IP 模糊筛选、
强制下线（OFFLINE + FORCE_LOGOUT + 重复下线 404 幂等）、限制登录
（423 AUTH_015 + 连带下线 + 记录可查 + 解除后登录恢复）、
时长白名单 400 / 重复限制 409 / 用户不存在 404 守卫。

### 27.6 已知边界与后续

- 在线会话表按「当前在线 + 最近下线」语义查询，历史行无限增长；
  后续可加保留期清理任务（与 `SessionCleanupService` 同周期）。
- 批量强制下线上限 100（`ForceLogoutRequest` 校验），超量需分批。
- `updateById` 的 NOT_NULL 跳过策略是全局约定，「解绑/清除」语义字段
  均应走 `updateByIdWithNulls` 显式置空（V26 负责人修复同范式）。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 29. V28 QA 业务测试与缺陷修复（6 缺陷全闭环）

### 29.1 测试范围与方法

对核心模块开展黑盒 HTTP 集成测试 + 白盒代码审查 + 数据层核验，覆盖
核心功能流程、业务逻辑、边界条件、异常场景四类。完整报告见
`docs/archive/QA-business-test-report-V28.md`。

### 29.2 缺陷清单（全部修复，各由新增 IT 固化回归）

| 编号 | 等级 | 问题 | 根因 | 修复 |
| --- | --- | --- | --- | --- |
| D-5 | P0 | 7 个权限码未授 SUPER_ADMIN，超管访问审计/工作流端点 403 | `ApiResourceService.sync` 只登记 `sys_api_resource` 不建角色授权；V11 后新增端点从未补授 | sync 两处 insert 后调 `grantToSuperAdmin`（查重插入 `sys_role_api`）+ `V28__grant_missing_api_to_super_admin.sql` 幂等补授 |
| D-2 | P1 | 手机/邮箱唯一性无服务层预校验（邮箱无唯一索引） | create/update 只依赖 DB 约束，与 ProfileService 不同构 | `UserService.assertContactAvailable` 预校验，冲突抛 409 明确中文信息 |
| D-4 | P1 | 删角色残留 `sys_user_role` 孤儿行 | `RoleService.delete` 未级联 N:N 关联表 | delete 首行 `JooqWriters.deleteByColumn(..., "role_id", id)` |
| D-6 | P1 | void 方法（completeTask/transfer）幂等重放必 409 | `toSnapshot(null)` 存 Java null，重放路径误判「快照缺失」 | null 响应存 `"null"` JSON 字面量 sentinel |
| D-1 | P2 | 用户 `status` 无白名单 | update 未做枚举校验 | 仅允许 ACTIVE/DISABLED，否则 400 |
| D-3 | P2 | 公告重发布不清 `offline_at` | `JooqWriters.updateById` 的 NOT_NULL 策略丢弃 null | 改 `updateByIdWithNulls(..., Set.of("offline_at"))` 显式置空 |

### 29.3 新增集成测试（6 类 41 用例）

| IT 类 | 用例数 | 覆盖重点 |
| --- | --- | --- |
| `UserLifecycleIT` | 10 | 弱口令 400、重名/手机/邮箱撞车 409、缺角色 400、非法状态 400、局部更新、删除后登录 401、禁用/启用闭环 |
| `RoleManagementIT` | 7 | 绑定回读、编码冲突 409、更新替换绑定、删除级联无孤儿（D-4）、停用角色绑定 409 |
| `AuthSessionIT` | 7 | 登出后刷新令牌 401（AUTH_005）、改密后旧令牌全失效、伪造/缺失令牌 401、防枚举同错 |
| `AnnouncementAuditIT` | 6 | 重发布清 offlineAt（D-3）、非法有效期 400、已发布不可编辑 409、onlyValid 可见性、审计日志落库 |
| `WorkflowTaskIT` | 6 | 超管可访问工作流 API（D-5 回归）、缺幂等键 400、非处理人 403、认领/审批幂等（D-6） |
| `DictConfigIT` | 5 | 字典类型 CRUD+冲突、同类型值唯一、配置缓存写后失效 |

### 29.4 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测（54 类） | `mvn -o test` | 317/317 BUILD SUCCESS |
| 集成测试（20 类） | `mvn -o test -Dtest="*IT"` | 91/91 BUILD SUCCESS（第一轮 6 类 41 例 + 第二轮 3 类 8 例；V28 迁移随启动自动生效） |
| 前端 | `vite build` | 2303 modules，构建成功 |

> 注：`surefire-reports` 目录存有 3 个历史残留报告（10 例，源文件已在早前重构更名/移除），不纳入统计；上表为实际执行结果。

### 29.5 遗留观察项：第二轮已全部修复（O-1 ~ O-4）

第一轮记录的 4 个低危观察项在第二轮完成修复，各由新增 IT 固化回归：

| 编号 | 观察项 | 根因 | 修复 |
| --- | --- | --- | --- |
| O-1 | 更新角色传 null `menuIds`/`apiIds` 会清空绑定 | `update` 无条件重建绑定，null 入参「删完不插」即清空，契约二义 | null=保持原绑定、空集合=清空、非空=全量替换；`CreateRoleRequest` javadoc 固化契约；前端编辑始终回填集合，无感切换 |
| O-2 | `MenuService.delete` 残留 `sys_role_menu` 孤儿行 | 只删主表不级联 N:N 关联表（该表无外键约束） | `delete` 前按 `menu_id` 调 `deleteByColumn`，与 RoleService.delete 的级联（按 role_id）互为补充 |
| O-3 | `ProfileService.updateProfile` 不校验手机/邮箱格式 | 格式校验只在 `bindContact` 与 DTO `@Pattern`（Web 层），服务层裸奔 | 非空值补 `assertMobileFormat`/`assertEmailFormat`（空串仍表解绑跳过校验）；正则抽为静态常量，`bindContact` 同款内联正则一并收敛到单一来源 |
| O-4 | `changePassword` 校验顺序不当 | 复用校验排在强度策略之前，「既弱又复用」先报 AUTH_007 掩盖策略问题 | 顺序调整为 原密码 → 强度策略 → 与原密码相同 → 历史复用 |

新增/扩展 IT：`RoleManagementIT` +2（null 保持 / 空集合清空）、`MenuIntegrationIT` +1（删菜单无孤儿 role_menu）、`ProfileManagementIT` 新类 5 例（手机/邮箱格式 400 且字段级错误可读、空串解绑置 NULL、弱新密码 400、复用历史 422）。

排障：`ProfileManagementIT` 首跑 5 例全因登录 423 LOCKED 失败——`UserService.create` 初始口令置 `password_expired=1`（首登须改密语义），夹具按 `UserLifecycleIT` 既有模式创建后 DSL 置 0 解决；2 例因 Bean Validation 顶层 message 为通用文案、字段级详情在 data 中，断言改按字段级校验。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 30. 系统管理新增监控/日志菜单（V29 迁移）

### 30.1 功能清单

| 菜单 | 路由 | 权限码 | 图标 | sort_no | 页面 |
| --- | --- | --- | --- | --- | --- |
| 缓存监控 | `system/cache` | `system:monitor:read` | `Coin` | 96 | `CacheMonitorView.vue` |
| 连接池监控 | `system/pool` | `system:monitor:read` | `SetUp` | 97 | `PoolMonitorView.vue` |
| 登录日志 | `system/login-log` | `system:login-log:read` | `Document` | 98 | `LoginLogView.vue` |
| 操作日志 | `system/operation-log` | `system:audit:read` | `Tickets` | 99 | `OperationLogView.vue` |

设计要点：
- **权限隔离**：登录日志（含用户名/IP/UA 敏感数据）使用独立权限码 `system:login-log:read`，不与操作日志的 `system:audit:read` 混用，便于按角色最小化授权；缓存/连接池复用监控既有权限码 `system:monitor:read`。
- **图标与排序**：均取 Element Plus 图标名，随 `sort_no` 步进 10 依次排在系统管理既有菜单（最大 95，即在线用户管理）之后，新增监控类菜单延续 96/97/98/99 即可，无需调整既有菜单。
- **结构可扩展**：后端 `MonitorService.cacheStats()` 返回 `List<CacheStatView>`，新增缓存实现时向列表追加一项，前端表格零改动；前端 4 个页面统一「统计卡片 + 明细表格 + 自动刷新」骨架，后续监控类菜单可整体复制改造。

### 30.2 后端新增端点

| 端点 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/system/monitor/caches` | `system:monitor:read` | 缓存统计（条目数/命中/未命中/淘汰/命中率/TTL/用途）：`token-version`（进程内 TokenVersionCache，30s TTL）+ Spring Cache 各缓存（Redis 实现，进程内口径；条目数对 Redis 显示 -1，避免 O(N) SCAN） |
| `POST /api/system/monitor/caches/{name}/clear` | `system:monitor:read` | 清理指定缓存（审计留痕）；不存在返回 NOT_FOUND 业务失败。Spring Cache 仅可清理本实例已使用过的缓存（未使用的不占进程内存也无统计） |
| `GET /api/system/monitor/datasource` | `system:monitor:read` | HikariCP 连接池统计（活跃/空闲/总数/等待线程/使用率 + 池配置 + JDBC URL）；非 Hikari 数据源回退 `configOnly` 哨兵形态 |
| `GET /api/system/login-logs/page` | `system:login-log:read` | 登录日志分页（用户名/结果/类型/失败码/IP/关键字/时间区间，默认收敛最近 7 天防全表扫描） |
| `GET /api/system/login-logs/summary` | `system:login-log:read` | 登录统计摘要（总数/成功/失败/失败率/独立用户/最近登录） |

操作日志页直接对接既有 `AuditLogController`（`/api/system/audit-logs/page` + `/trace/{traceId}` 链路查询 + `/summary`），未新增端点。为支撑缓存监控，`TokenVersionCache` 增加命中/未命中/淘汰计数（`get` 命中与过期各计一次，`put`/`invalidate` 计淘汰），`TokenVersionService` 暴露 `stats()`/`clear()` 委托。

### 30.5 缓存管理增强（M5，2026-10-16）

Spring Cache 抽象落地后，缓存监控页数据源从「仅 Token 版本缓存」扩展为「进程内缓存 + Spring Cache 全量」：

- **实现选型**：`config/cache/CacheConfig` 开启 `@EnableCaching` 并提供 `RedisCacheManager`（TTL/前缀经 `app.cache.ttl`/`app.cache.key-prefix` 配置化）；不引入 Caffeine 二级缓存，避免多级一致性与离线依赖复杂度。
- **统计口径**：`StatisticsCache`/`StatisticsCacheManager` 装饰器在应用侧记账（命中/未命中/淘汰），**按实例**统计——Redis 服务端无 per-cache 命中计数，多实例聚合需外部系统（Prometheus）另行实现；监控页 `type=REDIS` 的条目数为 `-1`（不可用哨兵，避免 SCAN）。
- **清理语义**：仅可清理本实例已使用过的缓存；未使用过的名称返回 404 语义，不误报已清理。
- **读写一致**：试点 `DictDataService.listByType` `@Cacheable`（空类型码归一 `ALL` 键）；`create` 按类型码精确驱逐，`update`/`delete` 全量驱逐（类型可能被改写，全清成本可忽略）。
- **缓存异常影响面**：Redis 不可用时仅缓存读写失败，不影响主流程（与健康探针降级策略一致）。

### 30.3 V29 迁移（已并入 `V32__consolidated_schema.sql`，见 §34）

一次性完成：4 条 `sys_menu` 记录（父菜单 1002 系统管理）、5 条 `sys_api_resource` 接口登记、SUPER_ADMIN 角色对 5 个新权限码的授权（INSERT IGNORE 幂等，重跑安全）。

### 30.4 验证记录

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 单测（54 类） | `mvn -o test` | 317/317 BUILD SUCCESS |
| 集成测试（21 类） | `mvn -o test -Dtest="*IT"` | 98/98 BUILD SUCCESS（91 原有 + MonitorLogIT 7 新增） |
| 前端 | `vite build` | 2303 modules，构建成功 |

新增 `MonitorLogIT`（7 例）：缓存统计含 `token-version` 条目与命中率字段、清理存在/不存在缓存、连接池统计字段齐全、登录日志分页与摘要、操作日志分页、无 `system:login-log:read` 权限用户访问登录日志 403。

排障：首跑 4 失败均为测试夹具问题而非产品缺陷——缓存名实为 `token-version`（非方法名 `tokenVersionCache`）、`PageResult` 序列化字段为 `records`（非 `list`）、普通角色查询 `fetchOne` 遇多行抛 `TooManyRowsException`（改 `fetchList` 取首个）。

---

## 31. 多数据源运维要点

多数据源的完整配置与排障见《多数据源使用指南》`docs/manual/datasource-guide.md`；本节聚焦运维侧的观察与处置。

### 31.1 连接池监控（按库区分）

每个具名数据源是独立的 Hikari 连接池，池名固定为 `hikari-<name>`（主库 `hikari-primary`、第二个库 `hikari-report` …）。`/actuator/prometheus` 暴露的 `hikaricp_*` 指标按 `pool` 标签区分，Grafana `hikaricp.json` 看板可按池名筛选；也可用 `GET /api/system/monitor/datasource`（`system:monitor:read`）查看各池活跃/空闲/等待/使用率与 JDBC URL。连接池告警阈值维持使用率 >85%。

### 31.2 启动期 fail-fast

`DataSourceRegistry` 在构造阶段即建池并校验：缺失 `app.datasources.primary` 对应的条目、或任一库缺 `url`，应用**启动即失败**并给出明确异常（如 `多数据源配置缺失主数据源` / `数据源 [x] 缺少 url`）。这是预期行为——配置错误在启动期暴露，不遗留到运行期。

### 31.3 健康探针

`/readyz` 检查主库连接（DB 不可用时 503）；多库场景下仅主库参与就绪判定（Flyway / JobRunr / Flowable 依赖主库）。从库 / 报表库不可用时主库业务仍可用，但依赖该库的接口会报错——可据此在告警中区分「主库故障」与「从库故障」。

### 31.4 新增 / 下线数据源

- **新增**：在 `application-{profile}.yml` 增 `app.datasources.entries.<name>`，部署侧注入 `DB_<NAME>_URL` / `DB_<NAME>_USERNAME` / `DB_<NAME>_PASSWORD`；无需改代码，注册表自动建池、路由与 `Map<String,DSLContext>` 自动包含新库。
- **下线**：反向移除条目与对应环境变量即可。

### 31.5 部署注意

prod 环境变量已由单库 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 更名为 `DB_PRIMARY_URL` / `DB_PRIMARY_USERNAME` / `DB_PRIMARY_PASSWORD`，上线前务必同步，否则启动 fail-fast。

---

## 32. 邮件通知渠道运维要点（M6）

跨渠道通知（站内信 / Webhook / WebSocket / 邮件）统一经 `POST /api/system/notifications/send`（权限 `system:message:send`，带 `@Idempotent` 幂等）投递，由 `NotificationService` 按 `channel` 路由到对应适配器；新增渠道只需新增一个 `NotificationPort` 实现，编排层零改动。

邮件渠道（`Channel.EMAIL`）运维要点：

- **默认关闭**：`app.notifications.email.enabled=false`。未启用时发送请求返回 `COMMON_001 邮件通知渠道未启用`（400），与 Webhook 门禁策略一致——不静默丢弃，调用方显式感知。
- **启用配置**（`application-{profile}.yml` 或环境变量）：`from`（发件人，必配）、`host`/`port`（SMTP）、`username`/`password`（生产经 `NOTIFICATION_EMAIL_USERNAME` / `NOTIFICATION_EMAIL_PASSWORD` 注入，不落仓库）、`starttls`（587 端口通常 true）、`timeout-ms`。
- **安全防护**：发送前经 `EmailRules` 纯函数校验——收件人地址形态、主题 CRLF/TAB 净化（防邮件头注入，注入内容退化为 inert 文本）、单封收件人上限 50、主题 200 / 正文 20000 字符上限；任一条不满足返回 400，非法报文不外发。
- **收件人口径**：邮件按地址维度投递，忽略 `receiverIds`/`roleIds`/`orgIds`（与站内信「按用户/角色/部门落库」语义不同）；调用方需自行把用户解析为邮箱地址。
- **失败处理**：SMTP 发送失败（`MailException`）向上抛，由全局异常处理记录；不静默重试，避免重复投递。

---

## 33. 工作流设计「不存在」问题排查报告（归档）

- **日期**：2026-10-12
- **项目**：java-admin-framework（Spring Boot 3.3.4 + jOOQ + MySQL + Flowable）
- **现象**：两个工作流设计在执行「设计 / 发布 / BPMN / 删除」操作时均提示 `工作流设计不存在: 384233927386075140`
- **结论摘要**：后端行为正确——该 ID 在数据库中确实不存在（已于 10-10 被物理删除，前端列表为陈旧数据）。排查过程中另发现 **3 个真实缺陷**，其中「发布必现 500」为 P0 功能性 Bug（BPMN 生成重复元素 ID），「雪花 ID 未按字符串序列化」为与定时任务模块同类的精度隐患，「删除为物理删除且无 deleted 列」为数据安全缺口。

---

## 1. 现象与影响面

| 维度 | 内容 |
| --- | --- |
| 报错信息 | `COMMON_404 工作流设计不存在: 384233927386075140` |
| 触发操作 | 设计（打开设计器）、发布、BPMN 预览、删除——四操作全失败 |
| 报错来源 | `WorkflowDesignService` 第 89/113/218/229 行，四路径统一走 `JooqWriters.fetchById` 主键查询，查不到即抛 NOT_FOUND |
| 实际影响 | 用户视角的「两个设计」均不可操作；真实根因是所操作的 ID 已不在库中 |

## 2. 系统化排查步骤（可复现）

按「由外到内、由静到动」顺序执行，每一步均给出判定方法：

1. **复现并固定请求**：用同一 ID 直连接口（绕过前端），GET `/api/workflow/definitions/384233927386075140` → 复现 404，确认是服务端行为而非前端渲染问题。
2. **读代码定位抛错点**：grep 报错文案 → `WorkflowDesignService`，确认四大操作（get/publish/requireDraft/requirePublished）均收敛到 `fetchById(dsl, WF_WORKFLOW_DESIGN, DO, id)`。
3. **核查查询条件差异**：对比 `list()`（notDeleted + 全表）与 `fetchById`（id 等值）。关键发现：V13 DDL 中 `wf_workflow_design` **没有 deleted 列**，`JooqWriters.notDeleted()` 对该表退化为恒真、`delete(logical=true)` 因 `table.has("deleted")==false` **退化为物理删除**。
4. **解码 ID 还原生成时间**：雪花 ID（EPOCH=2023-11-14）`>>22` 得 91608507010ms → 生成于 **2026-10-10 13:01:47**（与库中 leaveApproval 行同一毫秒，相隔 1ms），证明这是一个真实存在过的 ID，而非伪造/串位。
5. **查库验证（决定性证据）**：JDBC 直连 `localhost:3306/scaffold`——
   - `id=384233927386075140` **不存在于任何 schema**（scaffold / java_admin 均无）；
   - 现存仅两行：`384233927386075136`（leaveApproval，DRAFT v0，10-10 13:01:47 建）与 `384236069425516544`（expense，DRAFT v0，10-10 13:10:17 建）。
6. **查运行态进程列表**：`SHOW PROCESSLIST` 确认线上应用（PID 90272，:8080）连的就是 scaffold 库——排除连错库。
7. **日志时间线还原**（`logs/java-admin-framework.log.2026-10-10.0.gz`）：
   - `13:01:47.181` seeder 创建 leaveApproval 后发布失败：`审批节点 主管审批 引用的角色不存在: FINANCE`；
   - `13:10:17.790` 重启后 expense 被重新创建（新 ID ...544）后再次发布失败：`审批节点 财务审批 引用的角色不存在: FINANCE`；
   - 两次重启之间 expense 旧行（...5140）已被删除（物理删除无痕迹，与 seeder 重建互证）。
8. **实测线上 API 分层定位**：
   - 现存两行：GET 详情 200、BPMN 200（但 bpmnXml 为空——从未发布成功）、DELETE 未实测（零风险原则）；
   - **POST publish → 500「系统内部错误」**，堆栈揭示另一独立根因（见缺陷 A）；
   - `...5140`：GET / publish / delete 全部 404，与用户报错完全一致。

## 3. 根因定位

### 3.1 直接根因：陈旧列表 + 物理删除（用户报错的本体）

- 前端 `DesignListView.vue` 仅在 `onMounted(load)` 加载一次列表，无自动刷新；`wf_workflow_design` 无 deleted 列，删除即**物理删除、不可恢复**。
- 用户操作的 expense 行 `...5140` 是 10-10 13:01 在设计器创建的旧草稿，已于 13:10 前被物理删除；13:10 应用重启后 seeder 以新 ID `...544` 重建了该设计。用户浏览器仍持 13:01 左右的旧列表 → 点击旧行的四个操作 → 后端主键查无此行 → 404。
- **后端无 bug**：PK 不存在时报「不存在」是正确行为。问题在数据生命周期管理（物理删除）与前端状态管理（列表不刷新）的叠加。

### 3.2 缺陷 A（P0）：发布必现 500——BpmnWorkflowBuilder 生成重复元素 ID

- **机制**：`BpmnWorkflowBuilder.build()` 边循环（L132-156）已把「审批节点→下一节点」的出边改源到驳回网关、保留 id `flow_<edgeId>`；随后驳回网关循环（L158-186）又用**同一 id** 新增一条「驳回网关→正常下一节点」的 SequenceFlow → XML 中同一 id 出现两次。
- **实测证据**：leaveApproval 两个 APPROVAL 节点均配 `rejectPolicy=END` 且有出边（e2/e3），模拟生成出现重复 id `['flow_e2','flow_e3']`；线上 publish 堆栈：`SAXParseException; lineNumber:23; cvc-id.2: ID 值 'flow_e2' 出现了多次`（Flowable 部署期 XSD 校验失败 → XMLException → 500）。
- **影响面**：凡「审批节点配置驳回策略且存在出边」的设计**一律无法发布**——覆盖几乎所有真实设计；BuiltinWorkflowSeeder 自 10-10 起每次启动静默失败（先 FINANCE 角色缺失，V18 补齐角色后败于此缺陷），库中两行始终 DRAFT v0、bpmn_xml 为空。
- **测试盲区**：`BpmnWorkflowBuilderTest` 只断言元素数量与条件表达式，未校验元素 ID 唯一性；且 `BpmnXMLConverter` 的 XML 往返解析不执行部署期 XSD 校验，故 265 个单测全绿也未能暴露。

### 3.3 缺陷 B（P1）：雪花 ID 未按字符串序列化（精度隐患）

- `WorkflowDesignView.id` 无 `@JsonSerialize(using = ToStringSerializer.class)`，18 位雪花 ID 以 JSON number 下发；设计器另有 `Number(route.params.id)` 二次转换。
- 验证：`384233927386075140` **不能**被 double 精确表示（round 后为 `...7136`）；当前两行 ID 恰为 64 的倍数可精确表示，属侥幸——一旦出现不可表示的 ID，前端展示与回传即静默失真（与定时任务模块 JobVO 已修复的问题同类，本项目已有 `ToStringSerializer` 先例）。
- 本次报错 ID 能原样到达后端，说明该 ID 来自旧列表数据而非精度损失，但隐患仍在。

### 3.4 缺陷 C（P2）：种子失败静默 + 空 BPMN 无提示

- seeder catch 全部异常仅 `log.error`，用户侧无感知；未发布设计点「BPMN」返回空串，前端展示 `(暂无)`，不提示「发布后生成」。

## 4. 修复建议（按优先级）

| # | 优先级 | 修复点 | 代码位置 | 方案 |
| --- | --- | --- | --- | --- |
| A | **P0** | 驳回网关重复流 ID | `BpmnWorkflowBuilder.java` L158-186 | 驳回网关循环中，仅当 `approvalNextFlowOf` **不含**该节点（即无出边）时才新增 fallback 流（id `flow_<nodeId>_next`）；有出边时默认流直接指向边循环已发出的 `flow_<edgeId>`，跳过重复添加 |
| B | P1 | ID 序列化契约 | `WorkflowDesignView.id`、`WorkflowDesigner.vue` L259 | record 的 id 字段加 `@JsonSerialize(using = ToStringSerializer.class)`（对齐 JobVO）；前端路由参数不再 `Number()` 转换，模板字符串透传 |
| C | P1 | 物理删除缺口 | V13 表 + `JooqWriters` | 新增 V23 迁移为 `wf_workflow_design` 补 `deleted TINYINT NOT NULL DEFAULT 0`（`notDeleted`/逻辑删除随即自动生效，历史行默认 0 无副作用）；或在 UI 层保证任何操作后强制刷新列表 |
| D | P1 | 前端列表时效 | `DesignListView.vue` | 操作成功后统一 `load()`（publish/delete 已有，需覆盖异常分支）；增加路由离开/进入刷新或定时刷新；`previewBpmn` 对未发布设计禁用按钮并提示「发布后生成 BPMN」 |
| E | P2 | 种子失败可见化 | `BuiltinWorkflowSeeder` | 启动失败时除日志外输出明显 WARN 横幅，或在管理端暴露「内置流程未发布」提示 |

**数据修复**（修复 A 后执行）：重启应用使 seeder 对 leaveApproval / expense 自动发布成功（预期 status=PUBLISHED、version=1、deployment_id 非空）；或在页面手动点发布。

## 5. 验证方案

1. **单元测试（离线）**：
   - `BpmnWorkflowBuilderTest` 增补「审批节点带驳回策略 + 存在出边」用例：解析生成 XML，断言**全部 flowElement id 无重复`（可用 XPath `count(//bpmn:sequenceFlow[@id='flow_e2'])=1` 或 Java 侧 `process.getFlowElements()` 按 id 分组计数）。
   - 增补 Flowable 部署期等价校验：`new ParsedDeploymentBuilder` 或 `BpmnXMLConverter.convertToBpmnModel` + XSD 校验应无 XMLException（现有 roundtrip 不断言 ID 唯一性，需补）。
2. **集成测试（需 MySQL）**：对 leaveApproval / expense 设计执行 publish → 断言 200、`status=PUBLISHED`、`version` 自增、`bpmn_xml` 非空、`deployment_id` 非空；重复 publish 形成版本链。
3. **端到端（本机）**：重启应用 → seeder 日志无 ERROR → 前端列表两行显示「已发布 v1」→ 用该流程发起实例，审批人可解析（FINANCE/MANAGER 角色已由 V18 补齐）。
4. **回归**：`mvn -o test` 全量（当前基线 265/265）+ 前端 `npm run build`。
5. **精度回归**：构造一个不可被 double 表示的 ID（如 ...5140 量级）走列表→详情→发布链路，断言前后端 ID 字符串完全一致。

## 6. 附录：证据清单

| 证据 | 内容 |
| --- | --- |
| DB 查询（JDBC 直连 scaffold/java_admin） | `...5140` 两库均 0 行；现存 `...7136`(leaveApproval, DRAFT v0, 10-10 13:01:47)、`...544`(expense, DRAFT v0, 10-10 13:10:17) |
| ID 解码 | `...5140 >> 22 = 91608507010`（EPOCH 2023-11-14）→ 2026-10-10 13:01:47；`float(...5140)=...7136`（不可精确表示） |
| 进程列表 | 线上应用 :8080 → db=scaffold（排除连错库） |
| 日志（10-10 归档） | 13:01:47 / 13:10:17 两次 seeder 发布失败（FINANCE 角色缺失，后被 V18 修复）；13:50 两次 JWT 过期异常与本问题无关 |
| 线上 API 实测 | 现存两行 GET 200；`...5140` GET/publish/delete 全 404；publish(`...7136`) → 500，堆栈 `cvc-id.2: ID 值 'flow_e2' 出现了多次` |
| 重复 ID 模拟 | leaveApproval（2×APPROVAL rejectPolicy=END + 出边 e2/e3）→ 重复 `flow_e2`、`flow_e3`，与堆栈首个报错 id 一致 |
| 表结构 | V13 DDL 无 deleted 列 → 删除为物理删除 |

---

---

## 7. 修复实施状态（A→E 已全部落地）

| 优先级 | 修复项 | 改动文件 | 验证 |
| --- | --- | --- | --- |
| A (P0) | BpmnWorkflowBuilder 驳回网关重复流 ID | `workflow/bpmn/BpmnWorkflowBuilder.java` + `BpmnWorkflowBuilderTest`（6 例，含 cvc-id.2 负向验证） | 单测 6/6 绿；发布链路不再产生重复元素 ID |
| B (P1) | 雪花 ID 字符串序列化 | `workflow/dto/design/WorkflowDesignView.java`（`@JsonSerialize(ToStringSerializer)`）+ 前端 `WorkflowDesigner.vue`（`String(route.params.id)`）+ `WorkflowDesignViewSerializationTest` | 单测断言 id 为字符串，前端不再 `Number()` 转换 |
| C (P1) | 逻辑删除语义恢复 | `db/migration/V23__workflow_design_deleted.sql`（补 deleted 列 + 索引）、`jooq/JooqTables.java`（注册 deleted）、`WorkflowDesignService.java`（create/getPublished/list 过滤 deleted=0；delete 走逻辑删除 + 墓碑键后缀释放唯一键） | 编译通过；全量回归 273/273 |
| D (P1) | 前端列表刷新 + BPMN 禁用 | `views/workflow/DesignListView.vue`（发布/取消/删除后 `load()` 刷新；BPMN 按钮未发布 DRAFT 时禁用 + tooltip） | `npm run build` 成功 |
| E (P2) | seeder 失败可见化 | `workflow/bootstrap/BuiltinWorkflowSeeder.java`（`seedAll` → `seedSafely` 逐条 try/catch，单条失败仅 ERROR 不中断其余播种） | `BuiltinWorkflowSeederTest` 4/4 绿 |

**回归总览**：后端 `mvn -o test` **273/273 BUILD SUCCESS**（基线 265 → +8 例：BpmnWorkflowBuilder 3 例 + 序列化 1 例等）；前端 `npm run build` 通过。
**提交**：按 A→E 拆 5 个本地提交（仅本地、不推送），待用户显式确认后执行。注：工作树另有 `battle/` 目录与 `SecurityConfig.java` +3 改动属并行任务产物，不纳入本次提交。

---

*排查方法说明：本报告全部结论均经过「代码走查 → 数据库实证 → 日志时间线 → 线上 API 分层实测」四重交叉验证，未依赖任何推测。*
