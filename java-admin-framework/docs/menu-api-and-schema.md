# 菜单功能 — 接口与数据库表结构说明文档

> 适用范围：`java-admin-framework`（后端 Spring Boot 3.x + jOOQ）与 `pc-admin-vue3-vite`（前端 Vue 3 + Vite + Element Plus）。
> 文档依据当前磁盘源码与迁移脚本（V1/V15/V17 及 `scaffold_full_init.sql`）整理，覆盖「按角色动态加载菜单」全链路。
> 最后核对时间：2026-10-09。

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
| 控制器 | `src/main/java/com/acme/scaffold/system/controller/MenuController.java` |
| 服务 | `src/main/java/com/acme/scaffold/system/service/MenuService.java` |
| 响应 VO | `src/main/java/com/acme/scaffold/system/dto/MenuTreeVO.java` |
| 请求 DTO | `src/main/java/com/acme/scaffold/system/dto/CreateMenuRequest.java`、`UpdateMenuRequest.java` |
| 实体 | `src/main/java/com/acme/scaffold/system/entity/SysMenuDO.java`、`SysRoleMenuDO.java`、`SysUserRoleDO.java` |
| 响应封装 | `src/main/java/com/acme/scaffold/common/api/Result.java` |
| 安全配置 | `src/main/java/com/acme/scaffold/security/config/SecurityConfig.java` |
| 表迁移 | `db/migration/V1__baseline.sql`（sys_menu/sys_role_menu/sys_user_role/sys_role/sys_user 初始）、`V15__user_role_org_many_to_many.sql`（sys_user_role 重建）、`V17__menu_role_seed.sql`（菜单种子） |
| 全量结构参考 | `src/main/resources/db/scaffold_full_init.sql` |
| 前端 | `pc-admin-vue3-vite/src/api/menu.js`、`store/auth.js`、`layout/Sidebar.vue`、`layout/MenuTree.vue`、`router/index.js`、`api/mock/db.js` |
