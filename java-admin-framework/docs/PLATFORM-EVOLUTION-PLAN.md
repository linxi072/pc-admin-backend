# 平台化演进方案：独立组件库包 / 网关层 / 多区域一致性（V1.2 规划）

> 本方案基于 V1.1.0 代码现状（feat/V1.0.9 + 工程可信度整改）制定，含改动范围、
> 配置项、接口影响与验收标准。三项可独立立项，按 P1/P2/P3 优先级推进。
>
> **状态说明（2026-11-24）**：方向 A（独立组件库包 `@acme/admin-ui`）与 i18n 引擎均已整体移除，
> 以下方向 A 内容为历史规划记录，不再执行；组件由主应用 `src/components/` 直接提供，文案内联中文。

## 方向 A：前端独立组件库包（P1，已移除）

**现状**：`pc-admin-vue3-vite` 为单仓单包，通用组件（ProTable、查询表单、状态标签、
描述列表、弹窗 CRUD 组合件等）散落在 `src/components/`，与业务视图强耦合在同一 package.json。

**改动项**（优先级从高到低）：

| # | 修改项 | 需变更文件 |
|---|--------|-----------|
| A1 | 抽取 `@acme/admin-ui` 组件库包（workspace 子包，独立 package.json + vite lib mode 构建） | 新建 `pc-admin-ui/`（package.json、vite.config.ts、src/index.ts）；主应用 package.json 改 workspaces |
| A2 | 组件清单冻结：先迁 8–10 个零业务依赖组件（ProTable/QueryForm/StatusTag/DescriptionList/ConfirmDialog/DictSelect 等） | `pc-admin-ui/src/components/**`；主应用 `src/components` 保留薄 re-export 以免一次性改全 |
| A3 | 样式令牌（design tokens）随包发布：CSS 变量 + Element Plus 主题对齐 | `pc-admin-ui/src/styles/tokens.css` |
| A4 | CI 增加 `ui-lib` job：lib 构建 + 组件 smoke 测试 | `.github/workflows/ci.yml` |

**配置项**：`vite.config` 的 `build.lib`（入口/外部化 vue、element-plus）；主应用 `resolve.alias`
可切换「源码直引」与「包引用」两种模式，便于联调。

**接口影响**：对后端零影响；对前端仅 import 路径变化（经 re-export 兼容层平滑迁移）。

**验收标准**：① `npm run build:ui` 产出可发布的 ESM+CJS 包；② 主应用全量构建/测试/构建通过；
③ 门禁（check-sfc/i18n-leak-scan）在库包源码上同样通过；④ 至少 1 个业务视图完成切换且交互无回归。

## 方向 B：网关层（P2）

**现状**：前端 Vite dev server 代理直连 scaffold-admin（Spring Boot 单体），生产部署未定义
统一入口；无统一限流/鉴权前置/灰度路由。

**改动项**：

| # | 修改项 | 需变更文件 |
|---|--------|-----------|
| B1 | 引入 Spring Cloud Gateway（或轻量 Nginx+Lua 二选一，建议前者与 Java 栈一致） | 新建 `scaffold-gateway` Maven 模块 + 根 pom `<modules>` 登记 |
| B2 | 路由表：`/api/**` → scaffold-admin；WebSocket `/ws/**` → realtime；actuator 仅内网 | `scaffold-gateway/src/main/resources/application.yml` |
| B3 | 网关级能力：全局限流（Redis token bucket）、JWT 透传校验（不重复解析业务权限）、CORS 收敛到网关、请求日志与 traceId 注入 | gateway filter 包 `com.acme.scaffold.gateway.filter` |
| B4 | 前端 base URL 改为相对路径 `/api`（消除直连 host 配置） | `pc-admin-vue3-vite/.env.*`、`src/api/http.js` |
| B5 | 文档：部署拓扑图与网关配置手册 | `docs/ARCHITECTURE.md`、`docs/manual/deployment.md` |

**配置项**（新增 `gateway.*` 前缀）：路由目标 `gateway.routes.admin-uri`、限流
`gateway.ratelimit.capacity/refill-per-second`、`gateway.cors.allowed-origins`、
JWT 公钥路径（复用现有 `secret.*` 体系）。限流 fail 行为对齐现有「fail-closed」原则。

**接口影响**：业务 API 路径与契约不变（网关只做转发）；变更点：① OPTIONS 预检由网关终结，
后端 CORS 配置可下线；② 客户端真实 IP 语义变化（后端日志/审计需读 `X-Forwarded-For`，
`AuditRecorder` 取 IP 逻辑需同步）；③ WebSocket 握手经网关需开启 upgrade 透传。

**验收标准**：① 网关模块过全部既有门禁（依赖 DAG 白名单需登记 gateway → common/security）；
② 压测工具走网关路径，QPS 损耗 < 10%；③ 限流触发返回 429 且前端有可理解提示；
④ 直连后端的旧路径保留一版过渡期（`双挂`），监控确认无流量后下线。

## 方向 C：多区域一致性（P3，远期）

**现状**：`scaffold-common` 已有多区域数据源路由雏形与租户上下文；无跨区域写一致性协议。

**改动项**：

| # | 修改项 | 需变更文件 |
|---|--------|-----------|
| C1 | 明确一致性等级契约：单区域强一致（默认）+ 跨区域最终一致（异步），写操作仅落主区域 | `docs/ARCHITECTURE.md` 新章节；`RegionRoutePolicy` 注释与实现校准 |
| C2 | 跨区域复制总线：复用 `scaffold-kafka`（domain event → 跨区 topic → 对端回放），新增幂等消费（复用 common 幂等件） | `scaffold-kafka` 新 `replication` 包；`scaffold-common` 事件总线补 region 头 |
| C3 | 冲突处理：版本表（已有）+ LWW（last-writer-wins）策略声明；读己之写（read-your-writes）经 sticky session 路由 | `scaffold-common/region/**` |
| C4 | 演练与监控：复制延迟指标、回放失败告警（对接 monitoring 出库后的运维体系） | `scaffold-monitor` 指标注册 |

**配置项**（`region.*` 前缀）：`region.current`、`region.peers[].name/topic/bootstrap-servers`、
`region.replication.enabled`（默认 false，灰度开启）、`region.read-preference`（primary/local）。

**接口影响**：业务 API 不变；内部影响：① 领域事件结构新增 `region`/`occurredAt` 头字段
（旧消费者需兼容）；② 幂等键跨区域需带 region 前缀防碰撞。

**验收标准**：① `region.replication.enabled=false` 时行为与现网完全一致（回归零差异）；
② 沙箱双区演练：写入主区 → 秒级出现在对区只读副本；③ 回放失败进入毒队列并有告警；
④ 复制延迟 P99 < 5s（演练环境）；⑤ 门禁与双检（DDL/权限码）对复制表同样生效。

## 优先级与依赖关系

```
A 组件库包（P1）─── 无依赖，可立即立项
B 网关层（P2）  ─── 依赖部署仓库恢复制品（monitoring/deploy 出库后由运维侧承接）
C 多区域（P3）  ─── 依赖 B（跨区入口统一）与 C0 事件总线成熟度；建议 1.3.0+
```

## 统一验证方式

- 每项均须通过 `scripts/check-all.py` 全部门禁（新模块登记：DAG 白名单/簇布局/权限码）；
- 前端改动过 `npm test` + `npm run build` + i18n 泄漏扫描；
- B/C 涉及网关与复制的改动需附演练记录（压测/双区沙箱）作为验收材料。
