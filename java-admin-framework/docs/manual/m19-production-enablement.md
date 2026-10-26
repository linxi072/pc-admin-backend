# M19 多租户生产启用指引（Production Enablement）

> 状态：**M19 P1/P2/P3 已交付（feat/V1.0.4，门禁 1435 例全绿）** ｜ 日期：2026-10-26 ｜ 分支：`feat/V1.0.4`
> 范围：本文档仅说明「如何把 M19 的多租户强隔离从「就绪但未生效」切换为「生产生效」」，不涉及新的表结构迁移（schema 层自 V32 起已全量带 `tenant_id`）。

---

## 1. 当前状态（为什么需要本指引）

M19 的多租户能力在**代码与数据两层均已就绪**，但为守住「既有单租户部署零回归」的基线，`enforce` 默认关闭：

| 层 | 状态 | 说明 |
|---|---|---|
| Schema | ✅ 就绪 | 自 Flyway `V32` 起，每张业务表（含 6 张平台维表 `sys_tenant/sys_menu/sys_dict_type/sys_dict_data/sys_config/sys_api_resource`）均已带 `tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0` |
| 代码（写入） | ✅ 就绪 | `JooqWriters`（动态 DSL 唯一 CRUD 入口）经 `TenantEnforcement` 单点裁决，在 insert/batchInsert 注入 `tenant_id` |
| 代码（读/改/删） | ✅ 就绪 | 读/改/删经私有 `withTenant(table, cond)` 装饰，追加 `tenant_id` 过滤 |
| 请求解析 | ✅ 就绪 | `TenantInterceptor`（P3）从 请求头 `X-Tenant-Id` / Token Claim `tid` / 默认租户 解析并写入 `TenantContext` |
| 平台维表豁免 | ✅ 就绪 | `platform-tables` 列表内的表始终豁免隔离，跨租户全局可见 |
| **强制开关 `enforce`** | ⚠️ **默认 false** | 仅当 `enforce=true` 时，上述注入/过滤才真正生效；默认关闭以保证零回归 |

> ⚠️ **关键认知**：`enforce=false` 时，所有读写与「单租户」完全一致（行为零差异），因此 1435 例基线测试在默认配置下必然全绿。翻 `enforce=true` 不会触发既有测试失败（测试不依赖该开关），但**会真实改变线上数据访问语义**——这正是本指引要管控的风险点。

---

## 2. 启用前的硬性前置检查（务必逐项确认）

在将 `app.tenant.enforce` 置为 `true` 之前，必须确认：

1. **历史数据已归位**：所有业务表的历史行必须已写入正确的 `tenant_id`。`enforce=true` 后，某租户只能看到 `tenant_id = 当前租户` 的行；若历史行 `tenant_id = 0`（平台租户）且实际属于某租户，该租户将**看不到**这些数据。
   - 单租户存量迁移：将既有全量数据 `UPDATE ... SET tenant_id = <该租户ID> WHERE tenant_id = 0`（按业务归属分批，先备份）。
2. **平台维表保持 `tenant_id = 0`**：`sys_tenant/sys_menu/...` 须维持平台租户（0）且纳入豁免列表，否则平台功能（菜单、字典、权限）将不可见。
3. **租户解析链路打通**：`TenantInterceptor` 已在 `WebConfig` 注册；认证主体须携带 `tid` 或请求须带 `X-Tenant-Id`。未解析到租户的请求回落到 `defaultTenantId`（默认 0/平台租户），落入「平台租户作用域」，请注意这与「平台管理员跨租户」的差异。
4. **异步/定时任务显式 set 作用域**：非请求线程（定时任务、消息消费、`@Async`）未经过 `TenantInterceptor`，`TenantContext` 未设置时 `shouldEnforce` 静默不隔离。此类任务须在其执行入口显式 `TenantContext.setTenantId(...)`，否则读写会以「无过滤」执行（等同 `enforce=false` 的全局可见，存在越权风险）。
5. **平台管理员跨租户通道**：平台管理员跨租户操作须走 `@TenantIgnorable` 旁路（`TenantBypassAspect`），否则会被强制过滤挡在自身租户外。

---

## 3. 启动护栏（fail-fast）

为阻止「配置不全却开启强制隔离」上线，框架在启动期新增启动护栏：

- **`TenantEnforcement.validateConfiguration()`**（common 模块）：仅当 `enforce=true` 时执行校验，校验项：
  1. 配置快照已注入（`TenantEnforcement.configure` 已被调用）；
  2. `defaultTenantId` 合法（非负长整型，不能为 null）；
  3. `platform-tables` 平台维表列表**非空**（为空会导致平台维表被错误追加 `tenant_id` 过滤而不可见）。
  - 任一不通过抛 `IllegalStateException`，信息含可操作修正建议。
- **`TenantEnforcementValidator`**（framework 模块，`CommandLineRunner`）：应用启动完成后调用上述校验；`enforce=true` 且配置非法时，包装为 `IllegalStateException("多租户强制隔离配置校验失败，应用启动中止：...")` 向上传播，**应用 fail-fast 中止启动**。
  - `enforce=true` 且配置合法：打 **WARN** 日志提示隔离已生效、平台维表豁免、请确认数据归位。
  - `enforce=false`（默认）：打 **INFO** 日志，不阻断启动。

> 护栏对 `enforce=false` 完全无副作用——既有单租户部署启动行为不变。

---

## 4. 启用步骤（推荐灰度）

### 4.1 配置

```yaml
app:
  tenant:
    enabled: true
    enforce: true                 # 唯一改变线上语义的开关
    default-tenant-id: 0          # 平台租户；未解析到租户时回落（请按 2.3 评估风险）
    platform-tables:              # 豁免隔离的平台维表（默认已含 6 张，勿清空）
      - sys_tenant
      - sys_menu
      - sys_dict_type
      - sys_dict_data
      - sys_config
      - sys_api_resource
    platform-admin-role-code: PLATFORM_ADMIN
    header-name: X-Tenant-Id
    claim-name: tid
```

### 4.2 上线顺序（建议）

1. **预发环境**置 `enforce=true`，跑通全链路 + 数据归位核对脚本。
2. **单租户试点**（仅一个租户流量）置 `enforce=true`，观察读写隔离与日志 WARN。
3. **全量**：确认无「跨租户可见异常」告警后全量开启。

### 4.3 回滚

将 `enforce` 改回 `false` 重启即可——读写语义立即回落到单租户一致行为，**无需数据回滚**（已写入的 `tenant_id` 保留，不影响后续再开启）。

---

## 5. 验证（自测清单）

- **单元层**：`TenantEnforcementTest` 覆盖 `validateConfiguration` 四类场景（关闭放行 / 开启合法放行 / 默认租户非法抛错 / 平台维表空抛错）；`TenantEnforcementValidatorTest` 覆盖启动 fail-fast。
- **端到端层**：`JooqWritersTenantEnforcementTest` 用 jOOQ `MockConnection` 验证 `enforce=true` 时读/改/删/写 SQL 追加 `tenant_id`、平台维表豁免、`enforce=false` 零回归。
- **门禁**：`mvn -o clean test` 全绿（默认 `enforce=false`，不依赖该开关）；`scripts/check-conventions.py` EXIT=0。

---

## 6. 风险与边界

| 风险 | 触发条件 | 后果 | 缓解 |
|---|---|---|---|
| 历史数据未归位 | `enforce=true` 但业务表仍有 `tenant_id=0` 的本应属于某租户的数据 | 该租户看不到数据 | 上线前数据归位脚本 + 核对 |
| 异步/定时任务未 set 作用域 | 非请求线程无 `TenantContext` | `shouldEnforce` 静默不隔离，等同全局可见 | 任务入口显式 `TenantContext.setTenantId` |
| 平台维表被误过滤 | `platform-tables` 被清空 | 菜单/字典/权限不可见 | 启动护栏拦截（空列表抛错）；勿清空 |
| 平台管理员越权被挡 | 跨租户操作未走 `@TenantIgnorable` | 看不到他租户数据 | 跨租户接口标注 `@TenantIgnorable` |

---

## 7. 相关代码与文档索引

- 裁决核心：`scaffold-common/.../common/tenant/TenantEnforcement.java`（`validateConfiguration` / `shouldEnforce` / `configure`）
- 配置：`scaffold-common/.../common/tenant/TenantProperties.java`（`enforce` / `platformTables` / `defaultTenantId`）
- 启动护栏：`scaffold-framework/.../config/tenant/TenantEnforcementValidator.java`
- 注入点：`scaffold-common/.../jooq/dsl/JooqWriters.java`（`withTenant` / `putIfPresent`）
- 解析：`scaffold-framework/.../config/web/TenantInterceptor.java`；`scaffold-common/.../common/tenant/TenantResolver.java`
- 测试：`TenantEnforcementTest`、`TenantEnforcementValidatorTest`、`JooqWritersTenantEnforcementTest`
- 路线图与缺口：`docs/manual/roadmap-m19-plus.md`、`docs/manual/feature-gap-analysis.md`
