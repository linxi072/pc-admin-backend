# M27 报表大屏（后端：运营总览 API + 时间序列趋势）

> 状态：**后端四刀 + 前端配套页均已交付（feat/V1.0.4，后端门禁 1470 例全绿 + `check-conventions` EXIT=0；前端 `npx vite build` EXIT=0）** ｜ 日期：2026-10-26 ｜ 分支：`feat/V1.0.4`
> 来源：用户选定「报表大屏模块」+「前端配套页」后续方向；后端统计接口（§2）在 M27 四刀交付，前端可视化合（§6）在 `pc-admin-vue3-vite` 落地，并补 V52 菜单种子（§6.1）接入侧边栏与路由守卫。
> 第三刀（N+1 疑似跨模块可观测指标接入看板）：通过 `common` 跨模块端口范式，将 `scaffold-framework` 的 `NPlusOneSuspectRegistry` 指标零耦合接入 `DashboardOverview`，`scaffold-system` 不依赖 framework。
> 第四刀（链路追踪指标跨模块接入看板）：沿用第三刀的 `common` 跨模块端口范式，将 `scaffold-framework` 的 `SpanRecorder` 链路追踪指标（启用状态 / 当前留存 / 累计条数 / 错误条数）零耦合接入 `DashboardOverview`；`ObservabilitySnapshot` 由 3 字段扩展为 7 字段，`scaffold-system` 仍不依赖 framework。

---

## 1. 范围与定位

报表大屏的后端能力分两刀：

- **第一刀（运营总览快照）**：聚合系统域核心计数，供前端看板一次性拉取。
- **第二刀（时间序列趋势）**：近 N 日新增用户 / 新增租户 / 活跃租户三条按天聚合序列，缺数据日由规则补齐为连续日期，前端可直接按索引渲染折线/柱状图。

共性约束：纯只读、平台级（控制器标注 `@TenantIgnorable`，跨租户全局统计）；不引 ORM、不引新依赖，沿用手写 jOOQ DSL；任一统计查询失败静默回落（计数类回落 0、趋势类回落空序列），**看板不因局部统计失败整体报错**。

## 2. 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/system/dashboard/overview` | `system:dashboard:read`（自动登记 SUPER_ADMIN） | 返回 `DashboardOverview` 快照 |
| GET | `/api/system/dashboard/trend?days=30` | `system:dashboard:read` | 返回 `DashboardTrend` 时间序列（days 钳制到 [1,90]） |

`DashboardOverview` 字段：

| 字段 | 含义 | 数据来源 |
|---|---|---|
| `tenantCount` | 租户总数 | `sys_tenant` |
| `userCount` | 用户总数（不含已删除） | `sys_user`（notDeleted） |
| `roleCount` | 角色总数 | `sys_role` |
| `menuCount` | 菜单总数 | `sys_menu` |
| `onlineSessionCount` | 当前在线会话 | `sys_online_session` where status=ONLINE |
| `idempotencyRecordCount` | 幂等记录总数 | `sys_idempotency_record` |
| `operationLogCount` | 操作日志（审计）总数 | `sys_operation_log` |
| `loginLogTodayCount` | 今日登录数 | `sys_login_log` where **occurred_at** >= 当日 00:00 |
| `nPlusOneSuspectTotal` | N+1 疑似累计越限次数 | 跨模块端口 `ObservabilityStatsPort`（framework 适配器读 `NPlusOneSuspectRegistry.total()`）；无适配器时回落 0 |
| `nPlusOneSuspectRetained` | N+1 疑似当前留存条数（有界留存，默认 200） | 同上（`registry.size()`）；无适配器时回落 0 |
| `lastBreachAtMillis` | 最近一次 N+1 疑似越限时刻（epoch 毫秒，0=从未） | 同上（`registry.lastBreachAt()`）；无适配器时回落 0 |
| `traceRecorderEnabled` | 链路追踪记录器是否启用 | 跨模块端口 `ObservabilityStatsPort`（framework 适配器读 `SpanRecorder.stats().enabled()`）；无适配器时回落 false |
| `traceRetained` | 链路追踪当前留存 span 条数（有界留存，默认 200） | 同上（`stats.size()`）；无适配器时回落 0 |
| `traceTotal` | 链路追踪累计记录 span 条数（ok + error + unset） | 同上（`stats.okCount() + errorCount() + unsetCount()`）；无适配器时回落 0 |
| `traceErrorTotal` | 链路追踪累计错误 span 条数 | 同上（`stats.errorCount()`）；无适配器时回落 0 |
| `generatedAt` | 快照生成时刻（epoch 毫秒） | 服务端 |

`DashboardTrend` 字段：

| 字段 | 含义 |
|---|---|
| `days` | 回溯天数（已钳制 [1,90]） |
| `newUsers` | 每日新增用户（连续日期、长度=days） |
| `newTenants` | 每日新增租户（连续日期、长度=days） |
| `activeTenants` | 每日活跃租户（按 occurred_at 去重 tenant_id，连续日期、长度=days） |
| `generatedAt` | 快照生成时刻（epoch 毫秒） |

> 实现说明：趋势原始查询按 MySQL `DATE(expr)` 按天分组（仅返回有数据的日期），连续日期补齐由 `DashboardRules.fillDailyGaps` 负责；`days`、负数值、时间戳回落均由纯函数规则归一。

## 3. 代码结构（scaffold-system）

| 类 | 职责 |
|---|---|
| `dashboard/DashboardOverview` | 不可变快照 record |
| `dashboard/DashboardTrend` | 趋势快照 record（三序列 + days + 时间戳） |
| `dashboard/TrendPoint` | 单日趋势点 record（date + value） |
| `dashboard/DashboardRules` | 纯函数：非负钳制、时间戳回落、字段顺序、天数钳制、日期缺口补齐、趋势快照构建 |
| `dashboard/SystemStatsPort` | 统计数据源端口（计数 + 趋势，便于单测以假实现替换） |
| `dashboard/JooqSystemStatsAdapter` | 端口的 jOOQ 实现（fetchCount / 日期分组，异常兜底） |
| `dashboard/DashboardService` | 聚合 → `DashboardRules.buildOverview` / `buildTrend` |
| `dashboard/DashboardController` | `GET /overview`、`GET /trend`，`@PreAuthorize` + `@TenantIgnorable` |

### 3.1 跨模块可观测指标端口范式（common + framework）

第三刀将 `scaffold-framework` 的 N+1 疑似指标接入看板，但**不允许 `scaffold-system` 依赖 framework**（避免逆向依赖、保证 system 单测零 Spring/framework 耦合）。采用「端口 + 可选注入 + 兜底」三件套：

| 层 | 类 | 职责 |
|---|---|---|
| `scaffold-common` | `observability/ObservabilitySnapshot` | 不可变 snapshot record（N+1 三字段 `nPlusOneSuspectTotal` / `nPlusOneSuspectRetained` / `lastBreachAtMillis` + 链路追踪四字段 `traceRecorderEnabled` / `traceRetained` / `traceTotal` / `traceErrorTotal`，共 7 字段），含 `EMPTY` 哨兵（全零 + 未启用）与 `clampNonNegative()`（long 钳非负、boolean 原样保留） |
| `scaffold-common` | `observability/ObservabilityStatsPort` | 跨模块统计端口接口，`snapshot()` 无数据时返回 `EMPTY`（不得返回 null） |
| `scaffold-framework` | `monitor/observability/FrameworkObservabilityAdapter` | `@Component` 实现端口，构造注入 `NPlusOneSuspectRegistry`，读取累计越限 / 留存条数 / 最近越限时刻并经 `clampNonNegative()` 归一 |
| `scaffold-system` | `dashboard/DashboardService` | 保留构造注入 `SystemStatsPort`，**新增 `@Autowired(required=false) setObservabilityPort`** setter；port 为 null 或调用抛异常时回落 `ObservabilitySnapshot.EMPTY` |

- **零耦合**：`system` 仅依赖 `common` 的端口契约，不 import framework；framework 不在 classpath 时，setter 不触发注入，看板自动回落全零指标，行为等同未接入。
- **单测安全**：`DashboardServiceTest` 用假 `ObservabilityStatsPort`（可模拟抛异常）验证「有 port / 无 port / port 抛异常」三种回落，无需 Spring 容器。
- `DashboardRules.buildOverview` 将 `ObservabilitySnapshot` 的 7 个字段全部映射到 `DashboardOverview`（现 15 个业务字段 + `generatedAt` 共 16）；`metricOrder()` 由 11 项扩为 15 项（追加四个 trace 字段 `traceRecorderEnabled` / `traceRetained` / `traceTotal` / `traceErrorTotal`）。`generatedAt` 经 `DashboardOverview` 构造置于末位，保持既有字段顺序稳定。

## 4. 测试

- `DashboardRulesTest`（12 例）：负数钳制、时间戳回落、字段顺序、clampDays 边界、fillDailyGaps 补齐/负数钳制、buildTrend 钳制与回落；**第三刀新增 3 例**：`buildOverviewWithObservabilityAppliesSnapshot`、`buildOverviewWithObservabilityNullFallsBackToEmpty`、`buildOverviewWithObservabilityClampsNegative`（第四刀扩展断言覆盖四个 trace 字段，`metricOrder` 由 11→15 项）。
- `DashboardServiceTest`（8 例）：overview 聚合、负数钳制、全零兜底、trend 连续序列、（trend 天数越界钳制），均以假 `SystemStatsPort`，无 DB/Spring；**第三刀新增 3 例**：`overviewIncludesObservabilityWhenPortPresent`、`overviewFallsBackToEmptyWhenNoPort`、`overviewFallsBackToEmptyWhenPortThrows`（含可抛异常的假 `ObservabilityStatsPort` 内部类，第四刀扩展断言覆盖四个 trace 字段）。
- `ObservabilitySnapshotTest`（5 例，scaffold-common）：`EMPTY` 哨兵全零 + 未启用、带四个 trace 字段构造、未启用承载计数、`clampNonNegative` 钳制 long 保留 boolean、合法值原样保留（不可变新实例断言 `assertNotSame`）。
- `FrameworkObservabilityAdapterTest`（3 例，scaffold-framework）：构造注入 `SpanRecorder`，snapshot 反映 `NPlusOneSuspectRegistry` + `SpanRecorder.stats()`（`traceRecorderEnabled`/`traceRetained`/`traceTotal`/`traceErrorTotal`，`traceTotal=ok+error+unset`）、N+1 无越限与 trace 未启用时全零、容量淘汰后留存=容量且累计单调。

> 注：jOOQ 日期分组 SQL 仅在运行时对真实数据库生效；本仓禁用 Docker，`clean test` 以假 port 验证聚合与规则，适配器 SQL 由编译期类型检查 + 上线前对真实库验证保障。

## 5. 后续（待办，未含于本切片）

- **链路追踪指标（跨模块）**：✅ 已交付（第四刀）——沿用第三刀端口范式，`ObservabilitySnapshot` 扩展 `traceRecorderEnabled` / `traceRetained` / `traceTotal` / `traceErrorTotal` 四个字段，framework 适配器注入 `SpanRecorder.stats()`，`scaffold-system` 仍不依赖 framework；看板 `DashboardOverview` 现含 15 业务字段。

---

## 6. 前端配套页（pc-admin-vue3-vite，feat/V1.0.4）

> M27 后端四刀交付后，前端此前仅有 `/api/system/dashboard/*` 契约、无对应视图与菜单入口；本切片补齐看板页 + 后端 V52 菜单种子。租户管理前端页（M19 配套）一并落地（同仓，§6.2）。

### 6.1 后端 V52 菜单种子

`scaffold-admin/src/main/resources/db/migration/V52__dashboard_menu.sql`：在「报表管理」目录（id=1030）下挂菜单 id=1056（sort_no=40，避开已占用 1031/1053–1055），`route_path='/report/dashboard'`、`component_path='report/DashboardView'`、`permission_code='system:dashboard:read'`、`icon='DataAnalysis'`、`menu_code='system:dashboard'`；`INSERT IGNORE` + 绑定 `sys_role_menu(role_id=1)`。

- 路由守卫 `beforeEach` 校验 `to.path` 是否在本用户菜单树 `routePath` 集合内 → `route_path` 必须与前端路由（report/dashboard）严格一致，否则重定向到工作台。
- 权限码 `system:dashboard:read` 已由 `ApiResourceAutoSyncRunner` 扫描 `@PreAuthorize` 自动落库并授予 SUPER_ADMIN，此处仅补前端菜单入口。

### 6.2 前端新增/修改文件

| 文件 | 动作 | 说明 |
|---|---|---|
| `src/api/dashboard.js` | 新建 | `dashboardOverview()` → `GET /api/system/dashboard/overview`；`dashboardTrend(days)` → `GET /api/system/dashboard/trend?days=`（days 经后端钳制 [1,90]） |
| `src/views/report/DashboardView.vue` | 新建 | 运营概览（8 系统计数卡）+ 可观测指标（N+1 疑似三卡 + 链路追踪四卡）+ 时间序列趋势（newUsers / newTenants / activeTenants 三折线图，7/14/30 天单选）；错误态 `el-alert`+重试、loading `el-skeleton`、空态 `el-empty`；复用全局 `.page-card`/`.stat-grid`/`.stat-card`，图表走 `BaseChart` 按需引入 |
| `src/api/tenant.js` | 新建 | `tenantPage`/`tenantList`/`getTenant`/`createTenant`/`updateTenant`/`removeTenant`/`toggleTenant` 七函数，对应 `/api/system/tenants/*`（M19 后端 V51 配套） |
| `src/views/system/TenantView.vue` | 新建 | 完整 CRUD 页（表格+分页+排序+对话框+校验+删除确认），mirror `PostView.vue` 范式；状态列用 `statusTag()`（ENABLED/DISABLED），过期时间 `LocalDateTime` 字符串与 `value-format=YYYY-MM-DD HH:mm:ss` 互转、空值「长期」 |
| `src/router/index.js` | 修改 | `report/api-call` 后插入 `report/dashboard`（`reportDashboard` → `report/DashboardView`，`permission:'system:dashboard:read'`）；`system/import` 后插入 `system/tenants`（`tenant` → `system/TenantView`，`permission:'system:tenant:read'`） |
| `src/i18n/messages.js` | 修改 | zh-CN `route.*` 增 `reportDashboard:'报表大屏'`、`tenant:'租户管理'`；en-US 增 `reportDashboard:'Report Dashboard'`、`tenant:'Tenant Management'` |

### 6.3 前端自测

- `npx vite build --outDir .fe-verify`（`rm -rf .fe-verify` 清理验证目录后）**EXIT=0**，产物含 `DashboardView`/`TenantView` 对应 chunk，确认两视图编译通过、路由与 i18n 入口生效。
- 错误处理范式：`request()` 拆包 `Result{code,message,data}`，错误由拦截器统一 `ElMessage` 提示，视图层不重复 toast；两页所有请求均在 `try/catch` 中仅置错误态文本，不重复弹窗。
- 数据契约对齐：前端读取字段（`tenantCount`/`userCount`/.../`traceErrorTotal` 与 `newUsers`/`newTenants`/`activeTenants` 三序列的 `date`/`value`）与后端 `DashboardOverview`/`DashboardTrend` 完全一致。
