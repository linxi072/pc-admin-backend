# M27 报表大屏（后端第一刀：运营总览 API）

> 状态：**后端首刀已交付（feat/V1.0.4，门禁 1450 例全绿）** ｜ 日期：2026-10-26 ｜ 分支：`feat/V1.0.4`
> 来源：用户选定「报表大屏模块」+「前端配套页」后续方向；本文件仅覆盖**后端**运营总览统计接口，前端可视化合与「前端配套页」在 `pc-admin-vue3-vite` 另行落地。

---

## 1. 范围与定位

报表大屏的首个后端切片：**平台级运营总览快照**。从既有系统表与监控注册表聚合核心计数，供前端看板一次性拉取，避免前端并发打多个统计接口。

- 纯只读、平台级（控制器标注 `@TenantIgnorable`，跨租户全局统计）。
- 不引 ORM、不引新依赖，沿用手写 jOOQ DSL（`JooqTables` + `DSLContext#fetchCount`）。
- 任何单一统计查询失败均静默回落 0，**看板不因局部统计失败整体报错**。

## 2. 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/system/dashboard/overview` | `system:dashboard:read`（自动登记 SUPER_ADMIN） | 返回 `DashboardOverview` 快照 |

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
| `loginLogTodayCount` | 今日登录数 | `sys_login_log` where login_at >= 当日 00:00 |
| `generatedAt` | 快照生成时刻（epoch 毫秒） | 服务端 |

## 3. 代码结构（scaffold-system）

| 类 | 职责 |
|---|---|
| `dashboard/DashboardOverview` | 不可变快照 record |
| `dashboard/DashboardRules` | 纯函数：非负钳制、时间戳回落、字段顺序 |
| `dashboard/SystemStatsPort` | 统计数据源端口（便于单测以假实现替换） |
| `dashboard/JooqSystemStatsAdapter` | 端口的 jOOQ 实现（fetchCount，异常兜底 0） |
| `dashboard/DashboardService` | 聚合各计数 → `DashboardRules.buildOverview` |
| `dashboard/DashboardController` | `GET /overview`，`@PreAuthorize` + `@TenantIgnorable` |

## 4. 测试

- `DashboardRulesTest`（5 例）：负数钳制、时间戳回落、字段顺序。
- `DashboardServiceTest`（3 例）：聚合正确性、负数输入钳制、全零兜底（假 `SystemStatsPort`，无 DB/Spring）。

## 5. 后续（待办，未含于本切片）

- **N+1 疑似 / 链路追踪指标**：来自 `scaffold-framework` 的 `NPlusOneSuspectRegistry` 等；`scaffold-system` 当前不依赖 framework，需引入跨模块统计端口后再补齐。
- **趋势/时间序列**：按日聚合的登录、活跃租户等时序接口（需新增统计表或物化视图）。
- **前端看板页** + **租户管理页 / 脱敏策略中心前端**（用户选定的「前端配套页」）在 `pc-admin-vue3-vite` 落地。
