# 前端接口调用规范（`pc-admin-vue3-vite`）

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
