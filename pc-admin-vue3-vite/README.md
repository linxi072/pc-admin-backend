# 运营管理后台前端（Vue3 + Element Plus + Vite）

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
    ├── system/ApiResourceView.vue   # 接口资源管理（筛选 + 分页 + 注册 + 编辑 + 删除）
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
| 角色 | 列表/新建/编辑/删除/**权限树** | `GET /roles`、`POST /roles`、`PUT /roles/{id}`、`DELETE /roles/{id}` |
| 菜单 | 树形列表/关键词筛选/新增/编辑/删除 | `GET /menus/tree`、`POST /menus`、`PUT /menus/{id}`、`DELETE /menus/{id}` |
| 接口资源 | 列表/名称·权限·方法筛选/分页/注册/编辑/删除 | `GET /api-resources`、`POST /api-resources`、`PUT /api-resources/{id}`、`DELETE /api-resources/{id}` |
| 待办 | 审批/驳回/转办 | `GET /tasks/mine`、`POST /tasks/complete`、`POST /tasks/transfer` |
| 流程 | 发起/我发起的/审批记录 | `POST /instances/start`、`GET /instances/mine`、`GET /instances/{pid}/records` |

## 两项增强说明

### ① 用户硬删除
- 前端：用户列表「删除」按钮使用 `el-popconfirm` 二次确认后调用 `DELETE /api/system/users/{id}`，成功后刷新列表。
- 后端（本次新增）：`UserController` 增加 `DELETE /api/system/users/{id}`，`UserService.delete(id)` 调用 `JooqWriters.delete(..., false)` 做**物理删除**并清理 `SYS_USER_ROLE` 关联（与既有逻辑删除语义区分）。

### ② 角色权限树联动
- 角色编辑/新建弹窗内置两棵 `el-tree`（菜单树来自 `GET /menus/tree`、接口资源树来自 `GET /api-resources`，均带复选框）。
- 打开编辑时，通过 `getRole(id)` 取回该角色已分配的 `menuIds / apiIds`，用 `setCheckedKeys` 回填勾选状态（联动）。
- 保存时收集 `getCheckedKeys()` 一并提交，后端 `RoleService` 已支持 `menuIds / apiIds` 的持久化与回填。

## 新增页面：菜单管理 / 接口资源管理

为闭环 `system` 模块，新增两个与后端接口一一对应的管理页，现已支持**新增 / 编辑 / 删除**全链路闭环：

- **菜单管理 `MenuView`**：以 `el-table` 树形表展示 `GET /menus/tree` 返回的层级菜单；支持按名称/编码关键词筛选（保留命中子孙的祖先）；「新增菜单」弹窗含上级菜单下拉（由树拍平生成）、菜单编码/名称/类型（目录/菜单/按钮）/路由/组件/权限/图标/显示/排序/状态；「编辑」复用同一弹窗（**允许改上级菜单以移动层级**，下拉已排除自身及其子孙防止环路，回填 `MenuTreeVO` 扩展出的 `componentPath / icon / visible`）；「删除」走 `el-popconfirm` 二次确认后调 `DELETE /menus/{id}`，后端有子菜单时拒绝。
- **接口资源管理 `ApiResourceView`**：`GET /api-resources` 一次性返回全量列表，前端做名称/权限/方法的客户端筛选 + `el-pagination` 分页；「注册接口资源」弹窗含资源名/权限标识/HTTP 方法/路径模式/鉴权模式/风险等级；「编辑」复用同一弹窗回填 `ApiResourceView` 全部字段；「删除」调 `DELETE /api-resources/{id}`。

> mock 模式下 `db.js` 已补齐菜单嵌套树与接口资源的创建/编辑/删除路由，可直接离线演示（含「有子菜单时拒绝删除」逻辑）。

## 后端改动（需你确认提交）

为支持「用户硬删除」「菜单·接口资源编辑」，在后端 `java-admin-framework` 做了若干本地改动（尚未提交，按约定待你确认后再 commit/push）：

- `src/main/java/com/acme/scaffold/system/controller/UserController.java`：新增 `DELETE /api/system/users/{id}`。
- `src/main/java/com/acme/scaffold/system/service/UserService.java`：新增 `delete(Long id)` 物理删除实现。
- `src/main/java/com/acme/scaffold/system/dto/UpdateMenuRequest.java`（**新增**）：菜单编辑请求体。
- `src/main/java/com/acme/scaffold/system/dto/UpdateApiResourceRequest.java`（**新增**）：接口资源编辑请求体。
- `src/main/java/com/acme/scaffold/system/dto/MenuTreeVO.java`：扩展 `componentPath / icon / visible` 字段，支撑前端编辑回填。
- `src/main/java/com/acme/scaffold/system/controller/MenuController.java`：新增 `PUT /api/system/menus/{id}`（`@PreAuthorize("hasAuthority('system:menu:update')")` + 审计）。
- `src/main/java/com/acme/scaffold/system/controller/ApiResourceController.java`：新增 `PUT /api/system/api-resources/{id}`（`@PreAuthorize("hasAuthority('system:api:update')")` + 审计）。
- `src/main/java/com/acme/scaffold/system/service/MenuService.java`：新增 `update(id, req)`（`fetchById` 校验存在后 `JooqWriters.updateById` 选择性更新；`parentId` 允许变更（移动层级）并做防环校验——不能挂到自身或自身子孙之下、父菜单须存在；`version / tenantId` 维持原值）。
- `src/main/java/com/acme/scaffold/system/service/ApiResourceService.java`：新增 `update(id, req)`（`fetchById` 校验存在后 `JooqWriters.updateById` 选择性更新；`tenantId` 维持原值）。

已通过 `mvn -o compile` 离线编译验证。其余接口与 DTO 契约保持不变。
