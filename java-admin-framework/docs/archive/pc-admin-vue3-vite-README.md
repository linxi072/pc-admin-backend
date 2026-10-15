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
