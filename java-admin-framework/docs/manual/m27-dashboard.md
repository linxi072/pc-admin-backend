# M27 报表大屏（后端：运营总览 API + 时间序列趋势）

> 状态：**后端两刀均已交付（feat/V1.0.4，门禁 1456 例全绿）** ｜ 日期：2026-10-26 ｜ 分支：`feat/V1.0.4`
> 来源：用户选定「报表大屏模块」+「前端配套页」后续方向；本文件仅覆盖**后端**统计接口，前端可视化合与「前端配套页」在 `pc-admin-vue3-vite` 另行落地。

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

## 4. 测试

- `DashboardRulesTest`（9 例）：负数钳制、时间戳回落、字段顺序、clampDays 边界、fillDailyGaps 补齐/负数钳制、buildTrend 钳制与回落。
- `DashboardServiceTest`（5 例）：overview 聚合、负数钳制、全零兜底、trend 连续序列、（trend 天数越界钳制），均以假 `SystemStatsPort`，无 DB/Spring。

> 注：jOOQ 日期分组 SQL 仅在运行时对真实数据库生效；本仓禁用 Docker，`clean test` 以假 port 验证聚合与规则，适配器 SQL 由编译期类型检查 + 上线前对真实库验证保障。

## 5. 后续（待办，未含于本切片）

- **N+1 疑似 / 链路追踪指标**：来自 `scaffold-framework` 的 `NPlusOneSuspectRegistry` 等；`scaffold-system` 当前不依赖 framework，需引入跨模块统计端口后再补齐。
- **前端看板页** + **租户管理页 / 脱敏策略中心前端**（用户选定的「前端配套页」）在 `pc-admin-vue3-vite` 落地。
