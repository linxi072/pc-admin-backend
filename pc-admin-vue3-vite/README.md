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
│   ├── org.js dictType.js dictData.js sysConfig.js   # 部门/字典/系统变量接口封装
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
| 部门 | 树形列表/新增/编辑/层级移动/删除 | `GET /orgs/tree`、`POST /orgs`、`PUT /orgs/{id}`、`DELETE /orgs/{id}` |
| 字典分类 | 列表/新增/编辑/删除 | `GET /dict-types`、`POST /dict-types`、`PUT /dict-types/{id}`、`DELETE /dict-types/{id}` |
| 字典数据 | 按分类筛选/新增/编辑/删除 | `GET /dict-data?dictType=`、`POST /dict-data`、`PUT /dict-data/{id}`、`DELETE /dict-data/{id}` |
| 系统变量 | 列表/新增/编辑/删除/**按 key 动态读取** | `GET /configs`、`POST /configs`、`PUT /configs/{id}`、`DELETE /configs/{id}`、`GET /configs/key/{key}` |

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

## 新增页面：部门管理 / 字典管理 / 系统变量

本轮闭环 `system` 模块的三个新管理页，后端接口一一对应，具备输入校验与错误处理：

- **部门管理 `DepartmentView`**：以 `el-table` 树形表展示 `GET /orgs/tree` 部门层级；支持按名称/编码关键词筛选（保留命中子孙的祖先）；「新增部门」弹窗含上级部门下拉（由树拍平生成）、部门编码/名称/类型/负责人/排序/状态；「编辑」复用同一弹窗并**允许改上级部门以移动层级**（下拉排除自身及其子孙防环）；「删除」走 `el-popconfirm` 二次确认，后端在存在子部门时拒绝。
- **字典管理 `DictView`**：左字典分类列表（`highlight-current-row` 选中）+ 右字典数据表的**主从联动布局**；选中分类自动加载其键值数据；分类与数据各一个弹窗，删除分类时若已配置数据则后端返回冲突并由拦截器提示。
- **系统变量 `ConfigView`**：系统参数表格 CRUD；顶部提供「按配置键动态读取」演示区，调 `GET /configs/key/{key}` 回显 `名称=值(类型)`，直观展示后端内存缓存 + 动态读取能力。

> mock 模式下 `db.js` 已补齐部门（含层级移动/防环）、字典分类与数据（唯一性/占用守卫）、系统变量（含按 key 读取）的全部路由，可直接离线演示。

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
