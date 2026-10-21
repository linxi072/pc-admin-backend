# 可观测中心（Observability Center）

> 模块路径（前端）：`pc-admin-vue3-vite/src/views/monitor/`（页面 + 5 Tab 组件）、`pc-admin-vue3-vite/src/api/`（5 个 API 封装模块）
> 关联（后端存量）：`observability`（链路追踪）、`kafka`（有序消费监控）、`monitor/detection`（N+1 探测）、`common/idempotency`（幂等记录）、`monitor/alert`（告警收件箱）
> 迁移：`java-admin-framework/src/main/resources/db/migration/V39__observability_center.sql`

## 1. 背景与缺口

M 系列与运维增强轮次已先后交付五个后端运维面模块，均带完整 Service / DTO / Controller 与权限码：

| 后端模块 | 路由前缀 | 权限码 | 既有文档 |
|---|---|---|---|
| 链路追踪（SpanRecorder + ObservabilityService） | `/api/observability` | `system:observability:read` / `:manage` | `observability-recorder.md` |
| Kafka 有序消费监控 | `/api/kafka` | `system:kafka:read` / `:manage` | `kafka-ordered-consumer.md` |
| N+1 查询探测 | `/api/system/monitor/n-plus-one` | `system:monitor:read` | `nplus1-detection.md` |
| 幂等记录管理 | `/api/system/idempotency-records` | `system:idempotency:read` / `:manage` | `idempotency-records.md` |
| 告警收件箱管理 | `/api/monitoring/alerts/inbox` | `system:monitor:read` | `alert-inbox.md` |

但在本轮之前，五个模块**没有任何前端 API 封装或页面**（代码核查确认）：产品界面上无法查看链路
span、Kafka 死信、N+1 疑似事件、幂等记录与告警收件箱——运维能力只能通过 curl 使用，与「全功能
管理后台」定位不符。同时核查迁移链发现一处**历史缺陷**：V35 与 V36 均以 `id=1091` 播种菜单
（`INSERT IGNORE` 主键冲突），V36 的「导入中心」菜单在全新部署中被静默丢弃，路由守卫会把
`/system/import` 重定向到工作台，页面不可达（详见 §6）。

本轮交付：① 前端「可观测中心」统一入口（五 Tab）；② V39 迁移修复菜单 ID 碰撞并播种新菜单。

## 2. 模块结构（前端）

```
pc-admin-vue3-vite/src/
├── api/
│   ├── observability.js      # /api/observability：span 列表/详情/摘要/指标/清空
│   ├── kafkaMonitor.js       # /api/kafka：消费状态/死信分页/重试/丢弃
│   ├── nplusOne.js           # /api/system/monitor/n-plus-one：疑似列表/运行态/清空
│   ├── idempotencyRecord.js  # /api/system/idempotency-records：分页/汇总/清理
│   └── alertInbox.js         # /api/monitoring/alerts/inbox：列表/汇总/清空
└── views/monitor/
    ├── ObservabilityCenterView.vue        # 页面外壳：标题 + scope-hint + el-tabs
    └── components/
        ├── format.js         # 纯函数：epoch/日期/时长/占比格式化 + isForbidden
        ├── TraceTab.vue      # 链路追踪：摘要卡片 + span 筛选表 + 指标快照 + 清空
        ├── KafkaTab.vue      # Kafka 监控：消费状态卡片 + 死信表 + 重试/丢弃确认
        ├── NPlusOneTab.vue   # N+1 疑似：运行态卡片 + 疑似事件表 + 清空
        ├── IdempotencyTab.vue# 幂等记录：汇总卡片 + 多维筛选表 + 分页 + 清理
        └── AlertTab.vue      # 告警收件箱：汇总卡片 + severity/status 筛选 + 清空
```

五个 API 模块均复用 `src/api/http.js`（统一 baseURL、token 注入、`Result<T>` 拆包拦截器、
业务错误 reject 携带 `code`），无新增网络层代码。

## 3. 接口与权限

前端按 Tab 调用的后端端点（后端契约详见各模块手册）：

| Tab | 前端 API | 后端端点 | 权限码 |
|---|---|---|---|
| 链路追踪 | `observability.listSpans/getTrace/getTraceSummary/listMetrics/clearSpans` | `GET /api/observability/traces`、`GET /traces/{traceId}`、`GET /summary`、`GET /metrics`、`DELETE /traces` | `system:observability:read` / `:manage` |
| Kafka 监控 | `kafkaMonitor.getConsumerStatus/pagePoison/retryPoison/discardPoison` | `GET /api/kafka/consumer/status`、`GET /consumer/poison`、`POST /consumer/poison/{id}/retry`、`POST .../discard` | `system:kafka:read` / `:manage` |
| N+1 疑似 | `nplusOne.listSuspects/getNPlusOneStatus/clearSuspects` | `GET /api/system/monitor/n-plus-one/suspects`、`GET /status`、`DELETE /suspects` | `system:monitor:read` |
| 幂等记录 | `idempotencyRecord.pageIdempotencyRecords/getIdempotencySummary/purgeIdempotencyRecords` | `GET /api/system/idempotency-records/page`、`GET /summary`、`POST /purge` | `system:idempotency:read` / `:manage` |
| 告警收件箱 | `alertInbox.listAlerts/getAlertStats/clearAlerts` | `GET /api/monitoring/alerts/inbox`、`GET /inbox/stats`、`DELETE /api/monitoring/alerts/inbox` | `system:monitor:read` |

权限码由 `ApiResourceAutoSyncRunner` 启动扫描 `@PreAuthorize` 自动登记并授予 SUPER_ADMIN，
无需手工播种；菜单需 SQL 播种（见 §4）。

## 4. 菜单与权限模型（V39）

V39 播种菜单 `(1094, 0, 1002, 'system:observability-center', '可观测中心', '/system/observability', 'system/ObservabilityCenter', 'system:monitor:read', 'DataLine')`，
父级为「系统管理」目录（id=1002），并绑定 SUPER_ADMIN（role_id=1）。

**页面级权限取 `system:monitor:read` 的设计考量**：五 Tab 对应三组不同权限码，而前端 SPA 采用
菜单驱动鉴权（路由守卫只校验菜单树、无按钮级权限），一个菜单只能挂一个权限码。取监控域基础权限
`system:monitor:read`（覆盖 N+1 与告警两个 Tab）；链路追踪 / Kafka / 幂等三个 Tab 的接口由后端
按各自权限码独立鉴权，前端对 403 响应展示**降级空态**（`el-empty` + 提示文案），不越权放行、
也不整页报错——这是最小授权原则在「一页多权」场景下的落地方式（见 §5.3）。

## 5. 关键设计点

1. **Tab 懒渲染**：`ObservabilityCenterView` 用 `v-if="activeTab === '...'"` 渲染各 Tab，
   未激活 Tab 不挂载、不发请求。若默认全量挂载，进入页面会同时触发 5 组调用（其中 3 组对普通
   运维角色必然 403），既拖慢首屏又制造控制台噪声。
2. **统一拆包契约**：所有请求经 `http.js` 响应拦截器拆包（`code==='0'` → `data`；否则 reject
   并携带 `code`），页面只面对 `data`；该行为由 `scripts/fe-observability.mjs` 用可编排 axios
   适配器驱动**生产代码**验证，而非重新实现一遍。
3. **403 降级而非报错**：`format.js` 的 `isForbidden(err)` 仅当 `err.response.status === 403`
   为真；各 Tab 捕获后渲染 `el-empty` 降级态（如「无权限查看链路追踪数据」）。其余错误走
   `ElMessage.error` 提示，与既有页面（CacheMonitorView / SmsLogView）一致。
4. **纯函数格式化集中管理**：epoch 毫秒（0/负数/`null` → `--`）、ISO/空格/数组三种日期形态
   （Jackson `write-dates-as-timestamps: false` 下 LocalDateTime 为 ISO 串，部分历史字段为数组）、
   时长（<1000ms 显示 ms，否则秒）、占比（0~1 → 百分比）全部收敛到 `format.js`，Tab 组件零格式
   逻辑；该文件为纯函数，可在 Node 下直接单测（FMT-01~05）。
5. **空值语义约定**：筛选参数为空时置 `undefined` 不传空串（`buildParams()`，与 SmsLogView 一致），
   避免后端把 `""` 当有效筛选值；时间范围用 el-date-picker 的 datetimerange，取 `[start, end]`
   转为起止毫秒。
6. **危险操作二次确认**：Kafka 死信重试/丢弃、清空类操作均经 `ElMessageBox.confirm` 确认后
   执行，与后端 DELETE/POST 语义对应；操作成功/失败均有 `ElMessage` 反馈并刷新当前数据。

## 6. V39 迁移说明（含历史缺陷修复）

V39 做两件事（已应用迁移不可修改，Flyway checksum 会拒绝变更，故修复以新迁移前向追加）：

1. **修复 V35/V36 菜单 ID 碰撞（1091）**：V35「岗位管理」先占 1091，V36「导入中心」同 id 被
   `INSERT IGNORE` 静默丢弃。V39 以未占用 id=**1093** 重新播种「导入中心」及其 SUPER_ADMIN
   绑定；`uk_tenant_menu_code (tenant_id, menu_code)` 唯一键保证幂等——已手工补过的环境不会
   产生重复行。
2. **播种「可观测中心」菜单**：id=1094，`sort_no=103`（接在导入中心 101 之后，预留 102 空位
   便于后续插入），`component_path='system/ObservabilityCenter'` 对应前端路由注册的懒加载
   组件。

> 教训（已记入《开发手册》同类案例）：多迁移共用「手工 id + INSERT IGNORE」播种菜单时，必须
> 先全量核对已占用 id；唯一键 `uk_tenant_menu_code` 只防 menu_code 重复，不防主键 id 冲突。

## 7. 测试

`pc-admin-vue3-vite/scripts/fe-observability.mjs`（node:test，25 例，全绿）：

| 编号段 | 例数 | 覆盖 |
|---|---|---|
| OBS-01~05 | 5 | span 列表默认参数/筛选透传、traceId 编码、清空、业务错误（2xx 但 code≠0）reject 携 code |
| KFK-01~04 | 4 | 消费状态、死信分页与 key 筛选、重试/丢弃 id 编码 |
| NP1-01~03 | 3 | 疑似列表 limit 透传、运行态、清空 |
| IDM-01~04 | 4 | 分页参数透传（空值保持 undefined）、时间范围、同口径 summary、purge |
| ALR-01~03 | 3 | severity/status/limit 透传、stats 路由（不与列表混淆）、清空 |
| XUT-01 | 1 | 403 拒绝：reject 携带 `response.status===403`（页面降级空态的依据） |
| FMT-01~05 | 5 | format.js 纯函数：epoch/日期/时长/占比/403 判定 |

测试基建要点：

- **element-plus 虚拟模块**：其完整依赖图在 Node 沙箱下实测会被 kill（SIGTERM），故用
  `module.registerHooks` 替换为无操作桩（`ElMessage`/`ElMessageBox`）；Vue 本体可原生 import。
- **模块解析钩子**：`@/...` 别名映射 `src/`、扩展名省略的相对导入失败时补 `.js` 重试
  （镜像 vite 行为）；`localStorage` 打桩（`store/auth` 初始化即读）。
- **可编排 axios 适配器**：`axios.defaults.adapter` 按路由表返回脚本化响应，**不发起真实网络**
  却端到端驱动生产 `http.js` 拦截器链；非 2xx 必须自行抛 `AxiosError`（新版 axios 的 settle
  逻辑在内置适配器内，自定义适配器返回非 2xx 会被当作成功，导致 403 判定失效——XUT-01 守护
  此契约）。与 `fe-connectivity.mjs`（真实 HTTP 传输层契约）互补：本文件聚焦 API 形状契约。
- 运行前提：`npm install` 已完成、Node ≥ 22.15（`module.registerHooks`）。

```bash
cd pc-admin-vue3-vite
node --test scripts/fe-observability.mjs    # 25 例全绿
node --test scripts/fe-connectivity.mjs    # 存量联通测试回归（5 例）
python3 ../java-admin-framework/scripts/check-api-contract.py   # 静态契约对称校验
```

## 8. 离线构建与验证（本轮门禁结果）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 前端契约测试 | `node --test scripts/fe-observability.mjs` | 25/25 通过 |
| 前端存量回归 | `node --test scripts/fe-connectivity.mjs` | 5/5 通过 |
| 前后端契约对称 | `python3 ../java-admin-framework/scripts/check-api-contract.py` | 未发现契约漂移 |
| 前端离线构建 | `npm run build -- --outDir /tmp/fe-obs-build --emptyOutDir` | 成功（ObservabilityCenterView 懒加载 chunk 31.07 kB / gzip 9.23 kB） |
| 后端全量单测 | `mvn -o clean test`（JAVA_HOME=21） | 848/848 通过（本轮无 Java 变更，基线不变；2026-10-22 运行时联调轮新增 4 例后为 852/852） |
| 编码约定校验 | `python3 scripts/check-conventions.py` | EXIT=0 |

无新增外部依赖、无配置变更；前端新增 1 个路由（`/system/observability`，meta
`permission: 'system:monitor:read'`）与 1 条菜单 SQL。
