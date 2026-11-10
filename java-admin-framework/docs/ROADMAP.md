# 演进路线图（ROADMAP）

> **定位**：本文件是 `java-admin-framework`（含前端 `pc-admin-vue3-vite`）的**演进总纲**，描述 V1.0.7 之后的新需求与扩展方向。
> 现状事实以 `docs/ARCHITECTURE.md` / `MODULES.md` / `KEY-FLOWS.md` / `USAGE.md` 与 `db/migration` 迁移链为准；本文档仅描述**规划中**的内容，不声称任何候选模块已存在。
> 所有规划项均须沿用既有工程铁律（统一门禁 `check-all.py` 7 步 + 编译校验、`check-conventions` H1–H5、Port 防腐层、DDL 引号等，见 §5）。

---

## 0. 基线现状（V1.0.7）

- **规模**：14 个 Maven 模块（2026-11-08 由 17 收敛，plugin/approval/ai 已并入），包根 `com.acme.scaffold`；里程碑 M1–M50 全部收口。
- **已具备核心能力**：RBAC 权限、JWT/刷新令牌/Token 版本失效、OAuth2 授权+资源服务器、多租户隔离、数据权限、字段脱敏、写接口幂等、操作审计、Flowable 工作流（含管理员治理）、Quartz 调度、WebSocket 实时、可观测探针（Micrometer + N+1/慢查询监听）、MyBatis 持久层、代码生成器（M50）、低代码设计器（M44）、API 文档中心、幂等记录管理、流程实例治理。
- **当前缺口**：作为"脚手架"已功能完整（R12 微前端 ✅ 已落地）；R9 移动端自适应、R10 低代码深化、R11 通用组件库、R13/R14/R16 运维增强、R15 混沌注入已随各迭代落地，详见各章节。

**目标演进**：从「功能完整的模块化单体脚手架」升级为「**可扩展、可观测、可插件化的企业级平台**」。

---

## 1. 四大演进方向总览

| 方向 | 目标 | 当前主要缺口 | 关键交付 |
|---|---|---|---|
| 技术架构演进 | 让平台可被外部扩展而不 fork | 无 SPI/扩展点、无统一事件总线 | 插件机制、领域事件、AI 辅助、多区域 |
| 业务模块扩充 | 提供更多开箱即用业务能力 | CMS/IM/审批门户/看板模板缺失 | 新业务模块（候选） |
| 前端能力增强 | 提升前端复用度与多端覆盖 | 无组件库、无移动端适配、低代码待深化 | 组件库、移动端、低代码深化、微前端 |
| 运维与可观测增强 | 生产级可观测与韧性 | 仅指标探针，缺 trace/APM/混沌 | OTel 追踪、APM 大盘、混沌/压测、配置中心 |

---

## 2. 阶段划分（Phase 0–4）

> 阶段可交错滚动推进；优先级以 **P0（基座/必做）→ P1（近期价值）→ P2（远期/探索）** 标注。

- **Phase 0 · 夯实基座**：补齐扩展点前置能力（R2 事件总线先行，为 R1/R5/R6 解耦打底）；收敛现有 12 模块的"隐式跨模块直连"为 Port/事件；与当前 `feat/V1.0.x` 并行。
- **Phase 1 · 扩展点与事件驱动**（技术架构核心）：R1 插件化 + R2 事件总线，是其余方向的解耦基石。
- **Phase 2 · 业务模块扩充**：R5 CMS / R6 IM / R7 审批门户 / R8 看板模板（依赖 R1/R2）。
- **Phase 3 · 前端能力增强**：R9 移动端 ✅ / R10 低代码深化 ✅ / R11 通用组件库 ✅ / R12 微前端 ✅。
- **Phase 4 · 运维与可观测增强**：R13 追踪 ✅ / R14 APM ✅ / R16 配置中心 ✅ / R15 混沌注入 ✅ 已落地 V1.0.7（压测脚本 `scripts/perf/` 随仓库存在，混沌叠加演练已可行）。

---

## 3. 需求清单

### 3.1 技术架构演进

#### R1 · 插件化 / 扩展点（SPI）机制 — P0
- **动机**：当前 12 模块强耦合于 `com.acme.scaffold`，二次开发需改源码、重编译，难以独立交付与灰度。
- **方案要点**：在 `common` 定义 `ExtensionPoint` 接口与 `ExtensionRegistry`；业务方以独立 jar 提供 `@Extension` Bean，启动期自动注册；菜单/路由/权限码经扩展点可外部注入；`scaffold-admin` 扫描外部 jar 的 `META-INF/scaffold-extensions`。
- **候选模块**：`scaffold-plugin`（✅ **已落地 V1.0.7**）：`common.plugin` 定义 SPI 契约（`@Extension`/`ExtensionPoint`/`ExtensionRegistry`/`MenuContributor`·`RouteContributor`·`ApiResourceContributor`/`ExtensionContributionProvider` + 菜单/路由/权限码定义）；`scaffold-plugin` 承载 `ExtensionScanner`+`PluginManager`（外部 jar 经 `META-INF/scaffold-extensions` 注入扩展点）；`scaffold-admin` 加 guarded 装配（默认关，需 `scaffold.plugin.enabled=true` 才加载 `scaffold.plugin.dir` 下 `*.jar`）。
- **依赖**：建议与 R2 同批。
- **验收**：独立 jar 注入"外部业务菜单 + 端点"无需改 scaffold 源码；双门禁绿；端到端测试覆盖注入链路。

#### R2 · 领域事件总线 — P0
- **动机**：跨域副作用（用户禁用→踢下线→清缓存→发通知）散布在各 Service，靠 Port 直连，难维护难测试。
- **方案要点**：`common.event` 定义 `DomainEvent` + `EventPublisher`/`EventListener`，默认同步发布、可选经 `scaffold-kafka` 异步桥；`realtime`/`notification` 改为订阅事件而非直连。
  > 2026-11-10 P2-3 批次 11 起，可选的 `scaffold-kafka` 源码按 `docs/DIRECTORY.md` §4 收敛为
  > `monitor` / `consumer` / `gateway` / `support` 四簇（包名 `com.acme.scaffold.kafka.monitor.*` /
  > `.consumer.*` / `.gateway.*` / `.support`）；保序规则、Topic 拓扑与条件装配的
  > HTTP 契约、DDL、配置前缀均未变化。
- **候选**：`common.event` 子包（✅ **已落地 V1.0.7**）：`DomainEvent` / `EventPublisher` / `DomainEventListener` / `DefaultEventPublisher`（错误隔离）/ `EventBridge`+`NoOpEventBridge` 已就位；并以「用户禁用 → security 踢下线」完成端到端收敛示范。若需独立运维再升为 `scaffold-event`。
- **依赖**：Phase 0 先行，供 R1/R5/R6 解耦。
- **验收**：用户禁用事件被 `online`/`notification` 订阅且零直连；端到端测试覆盖。

#### R3 · AI 辅助开发集成 — P2
- **动机**：`scaffold-codegen`（M50）为模板驱动，缺语义理解。
- **方案要点**：在 `scaffold-codegen` 增"自然语言→CRUD/查询"入口，对接大模型（经 cloud-service 或本地推理），产出实体/DDL/Service/前端视图草稿，保留人工 review。
- **候选模块**：`scaffold-ai`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.ai` 下 `port`（`LlmProvider` 端口 + `HeuristicLlmProvider` 离线默认 / `CloudLlmProvider` `@ConditionalOnProperty` 可选）/ `service`（`AiScaffoldService` 编排 + `DdlRenderer` 引号小写迁移 SQL）/ `controller`（`AiScaffoldController` 走 `ai:scaffold:generate` 权限码）/ `dto`（TableSpec/ColumnSpec/ScaffoldResult/ScaffoldRequest）/ `config`（AiProperties 成本门禁）；复用 `scaffold-codegen` 的 `JavaTypeMapper` 与 `CodeTemplates.renderAll` 渲染实体/服务/Vue 草稿，零新依赖；启发式解析确定性可过双门禁，云大模型仅条件化可选（默认不激活，避免网络依赖破坏 `mvn -o`）。
- **依赖**：R1 成熟后更易嵌入；注意合规与成本门禁。
- **验收**：输入"订单表含金额/状态"产出可编译骨架 + 迁移 SQL + 前端路由；门禁绿。
- **前端视图**：✅ **已落地 V1.0.7**（ai/ScaffoldView 提示词→表结构/建表 DDL/代码草稿预览[needsReview/provider/estimatedTokens 徽标] + api 契约测试 1 例，i18n 双 locale 补齐；菜单种子 V77 已就绪）。

#### R4 · 多区域 / 多集群部署支撑 — P2
- **动机**：多租户已建（租户列隔离），但单实例；大租户需物理隔离。
- **方案要点**：租户→部署单元映射配置；数据源路由按租户组；每个单元由 DBA 分别执行 `db/init/` 初始化；复用 `TenantContext` 与既有 `RoutingDataSource`。
- **候选模块**：`scaffold-framework/config/multiregion`（✅ **已落地 V1.0.7**）：在既有多数据源路由（`RoutingDataSource` / `DataSourceContextHolder` / `DataSourceRegistry`，位于 `scaffold-framework`）之上叠加「租户→部署单元」解析层 —— `TenantUnitMapping` 解析当前租户归属单元（显式映射 / 租户组区间 / 默认单元三级优先级），`MultiRegionInterceptor`（Web 层，注册在 `TenantInterceptor` 之后）与 `MultiRegionRouter`（非 Web 编程式）按解析结果写入 `DataSourceContextHolder`，使构建在 `RoutingDataSource` 之上的主 `DSLContext`（由 `config.mybatis.MyBatisConfig` 装配）自动路由到对应物理库；原 `UnitAwareFlywayMigrator` 已随 Flyway 移除，各单元由 DBA 分别执行 `db/init/` 初始化；全链路零改 `mybatis.dsl.SqlWriters`（兼容层）。默认 `app.multiregion.enabled=false`，关闭时完全不装配任何 R4 Bean，与单实例行为零回归。
  - **为何落在 framework 而非 common**：既有路由基础设施（DataSourceRegistry / RoutingDataSource / DataSourceContextHolder）已在 `scaffold-framework`，若放入 `common` 将产生 common→framework 反向依赖而破坏 DAG 无环；故 R4 复用 framework 既有能力，未新增模块。
- **依赖**：R2 事件驱动（已完成）+ R16 配置中心（可选，未阻塞）。
- **验收**：`MultiRegionRoutingIntegrationTest` 用两个 H2 内存库模拟两部署单元，验证租户组 A/B 的写入仅落对应物理库（物理隔离）；各单元结构由 `db/init/` 手工初始化，不再有按单元自动迁移的 Runner。

### 3.2 业务模块扩充

#### R5 · 内容管理 CMS — P1
- **动机**：后台普遍需要栏目/文章/富文本/媒体库。
- **候选模块**：`scaffold-cms`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.cms` 下 `domain`（CmsCategoryDO/CmsArticleDO）/`dto`/`service`（栏目树构建 + 文章 CRUD/发布态流转）/`controller`（栏目、文章 REST 端点，@PreAuthorize 权限码经 ApiResourceAutoSyncRunner 自动登记）/`event`（ArticlePublishedEvent 复用 R2 事件总线）；DDL 见 V74__cms.sql（cms_category/cms_article + 菜单种子）；`SqlTables` 新增 `CMS_CATEGORY`/`CMS_ARTICLE` 常量。
  > 2026-11-10 P2-3 批次 8 起，源码按 `docs/DIRECTORY.md` §4 收敛为 `article` / `category` /
  > `support` 三簇（包名 `com.acme.scaffold.cms.article.*` / `.category.*` / `.support`）；
  > 本行保留 V1.0.7 落地时的原始目录快照，HTTP 契约与 DDL 均未变化。
- **依赖取舍**：为保持 DAG 最小且无环，CMS 仅依赖 `common`（经 SqlWriters 持久化、R2 事件总线发发布事件），未引入 `system`/`plugin`；媒体资源以 `cover_url` 等文本列承载，避免跨模块 Service 直连（与「跨域协作经 Port/事件」铁律一致）。
- **验收**：栏目树 + 文章 CRUD + 富文本 + 发布态（DRAFT/PUBLISHED/OFFLINE）+ 浏览计数；双门禁全绿（11 个后端单测覆盖）；菜单种子与权限码自动登记就绪。前端视图 ✅ **已落地 V1.0.7**（cms/CategoryView 树形栏目 + cms/ArticleView 发布/下线/详情抽屉 + api 契约测试 6 例，i18n 双 locale 补齐）。

#### R6 · 站内 IM / 客服 — P1
- **动机**：基于已有 `realtime`(WebSocket) 的会话能力缺失。
- **候选模块**：`scaffold-im`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.im` 下 `domain`（ImConversationDO/ImConversationMemberDO/ImMessageDO）/`dto`/`service`（会话创建[P2P/群组]+成员管理+消息发送/未读计数/已读回执 + 会话列表）/`controller`（会话、消息 REST 端点，@PreAuthorize 权限码经 ApiResourceAutoSyncRunner 自动登记）；DDL 见 V75__im.sql（im_conversation/im_conversation_member/im_message + 菜单种子）。`SqlTables` 复用既有常量；`ImMessageSentEvent` 落地于 `common.event.domain`（与 UserDisabledEvent 同位），由 `realtime` 的 `ImMessagePushListener` 订阅后经 WebSocket 实时推送，二者经事件解耦、无编译期依赖。
  > 2026-11-10 P2-3 批次 9 起，源码按 `docs/DIRECTORY.md` §4 收敛为 `conversation` / `message` /
  > `support` 三簇（包名 `com.acme.scaffold.im.conversation.*` / `.message.*` / `.support`）；
  > 本行保留 V1.0.7 落地时的原始目录快照，HTTP 契约与 DDL 均未变化。
- **依赖取舍**：为保持 DAG 最小且无环，IM 仅依赖 `common`（经 SqlWriters 持久化、R2 事件总线发消息事件）；实时推送由 `realtime` 订阅同一事件完成，IM 不反向依赖 realtime/`system`/`plugin`（与「跨域协作经 Port/事件」铁律一致）。
- **验收**：P2P/群组会话 + 成员管理 + 消息发送 + 未读计数（按成员 last_read_message_id 推导）+ 已读回执 + 会话列表；双门禁全绿（16 个后端单测覆盖 CRUD/树/未读/回执/事件）；菜单种子与权限码自动登记就绪。前端会话组件 ✅ **已落地 V1.0.7**（im/ConversationView 会话管理 + im/MessageView 消息中心[气泡/发送/撤回/已读/未读角标] + api 契约测试 6 例，i18n 双 locale 补齐）。

#### R7 · 审批中心门户 — P1
- **动机**：Flowable 待办/已办/我发起分散，缺统一工作台。
- **候选模块**：`scaffold-approval`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.approval` 下 `ApprovalPortalService`（薄编排层，聚合 workflow 的待办/已办/我发起/可发起 + `WorkflowDesignService` 的已发布流程 + 统计）、`ApprovalPortalView`/`ApprovalStats`（聚合 DTO）、`ApprovalPortalController`（6 个只读 GET 端点 `/api/approval/portal/*`，统一权限码 `approval:portal:view`，@PreAuthorize 经 ApiResourceAutoSyncRunner 自动登记）；菜单种子见 V76__approval_portal.sql（审批中心目录 9501 + 审批工作台 9502）。`wf_task_ext` 补 `activity_name` 快照列（V76），待办/已办列表免回查引擎即可展示可读节点名。
  > 2026-11-10 P2-3 批次 12 起，源码按 `docs/DIRECTORY.md` §4 收敛为 `instance` / `definition` /
  > `monitor` / `approval` / `engine` / `admin` / `support` 七簇；原独立根包
  > `com.acme.scaffold.approval` 整体并入 `com.acme.scaffold.workflow.approval.*`，
  > `check-directory.py` 的 `EXPECTED_ROOTS` 同步注销 `approval`。本行保留 V1.0.7 落地时的
  > 原始目录快照，HTTP 契约、菜单种子与 DDL 均未变化。
- **依赖取舍**：为保持 DAG 最小且无环，approval 仅依赖 `common` + `workflow`（经 `WorkflowService`/`WorkflowDesignService` 编排）；不反向依赖 `system`/`plugin`（与「跨域协作经 Port/事件」铁律一致）。
- **验收**：统一待办列表 + 已办列表（按完成时间倒序 + activity_name 快照）+ 我发起 + 可发起（仅 PUBLISHED）+ 统计；双门禁全绿（workflow 10 个单测覆盖 myDoneTasks、approval 3 个单测覆盖聚合/过滤/透传）；菜单种子与权限码自动登记就绪。前端聚合页 ✅ **已落地 V1.0.7**（approval/PortalView 统计卡 + 四标签页 + 流程详情抽屉[当前任务 + 审批记录时间线] + api 契约测试 5 例，i18n 双 locale 补齐）。

#### R8 · 数据看板模板库 — P2
- **动机**：`workbench` 报表为硬编码，缺可复用模板。
- **候选模块**：`scaffold-workbench` 扩展（✅ **已落地 V1.0.7**）：`com.acme.scaffold.workbench.dashboard` 下 `domain`（DashboardTemplateDO）/`dto`（TemplateSaveRequest/TemplateUpdateRequest/DashboardTemplateVO）/`service`（看板模板 CRUD + 发布态流转 DRAFT/PUBLISHED/OFFLINE + 浏览计数 + layout_json 经 Jackson 合法性校验）/`controller`（看板模板 REST 端点 `/api/dashboard/template/*`，统一权限码 `dashboard:template:manage`，@PreAuthorize 经 ApiResourceAutoSyncRunner 自动登记）；DDL 见 V78__dashboard_template.sql（dashboard_template + 菜单种子：数据看板目录 9701 + 看板模板管理 9702）。`SqlTables` 新增 `DASHBOARD_TEMPLATE` 常量。
  > 2026-11-10 P2-3 批次 10 起，源码按 `docs/DIRECTORY.md` §4 收敛为 `overview` / `report` /
  > `dashboard` 三簇（包名 `com.acme.scaffold.workbench.overview.*` / `.report.*` /
  > `.dashboard.*`；看板模板落 `dashboard/{api,application,domain/model,dto}`）；
  > 本行保留 V1.0.7 落地时的原始目录快照，HTTP 契约与 DDL 均未变化。
- **依赖取舍**：为保持 DAG 最小且无环，dashboard 仅依赖 `common` + `system`（与 workbench 既有依赖一致，经 SqlWriters 持久化）；布局配置以 `layout_json`（TEXT）承载，由前端约定结构、后端仅做 JSON 语法校验（不做语义约束），避免引入重量级表单引擎；复用现有 `workbench` 模块而非新建模块。
- **验收**：看板模板 CRUD + 配置（layout_json 序列化）+ 发布态流转（DRAFT/PUBLISHED/OFFLINE）+ 浏览计数 + 权限码自动登记；双门禁全绿（9 个后端单测覆盖 CRUD/发布/撤销/计数/更新/逻辑删除/分页筛选/非法 JSON 拒绝/缺失模板）。前端视图 ✅ **已落地 V1.0.7**（dashboard/DashboardTemplateView 分页管理 + 拖拽布局配置器[卡片排序 ⇄ layout_json 双向编辑、JSON 校验阻断提交] + 预览抽屉网格渲染 + api 契约测试 7 例，i18n 双 locale 补齐）。

### 3.3 前端能力增强

#### R9 · 移动端 / 小程序适配 — P1
- **动机**：当前仅 PC 后台；管理场景需要移动端。
- **候选**：`pc-admin-vue3-vite` 增加响应式断点 / 独立 `mobile-admin`（若有独立部署诉求）。
- **依赖**：后端 OAuth2 资源服务器（已有）。
- **验收**：移动端布局自适应、触摸交互；或小程序壳对接 REST。
- **进度**：✅ **已落地 V1.0.7**（`pc-admin-vue3-vite`）：新增 `useBreakpoint` composable（纯函数可单测，SSR 安全），`AppLayout` 在 <768px 切换为 `el-drawer` 抽屉式侧边栏（汉堡唤起/收起、菜单选中自动收起、footer 移动端隐藏）；各视图既有 `@media` 响应式样式继续生效，触摸交互由 Element Plus 原生支持。

#### R10 · 低代码表单设计器深化 — P1
- **动机**：M44 设计器基础能力已有，需脚本/校验/联动/数据源绑定。
- **候选**：前端 `lowcode` 模块 + 后端 `scaffold-lowcode`（可选）。
- **依赖**：表单元数据存储（复用现有）。
- **验收**：设计器产出可运行表单，联动/校验生效，数据落库。
- **进度**：✅ **已落地 V1.0.7**（`pc-admin-vue3-vite`）：`formSchema.js` 纯函数统一校验与条件可见性（正则 pattern、数字 min/max、长度、条件可见 visibleWhen 九种运算符、栅格列宽 colSpan）；`DynamicForm` 接入可见字段过滤与规则并采用双列响应式栅格；`FormDesigner` 支持编辑上述属性；`validateForm` 提供结构化错误，已配套 15 例单测。

#### R11 · 通用业务组件库沉淀 — P2
- **动机**：Table/Form/抽屉/图表在各视图重复实现。
- **候选**：`packages/ui`（monorepo）或独立 npm 包（本期先以 `src/components` 内可复用原语 + `src/utils` 纯函数切入，待 R12 微前端时再评估独立包）。
- **依赖**：无。
- **验收**：组件库独立构建 + 文档站；被 `pc-admin` 复用。
- **进度**：✅ **已落地 V1.0.7**（`pc-admin-vue3-vite`）：`src/utils/stat.js` 纯函数（`formatStat` 数值千分位/去尾零/单位、`trendOf` 趋势三态，含边界用例修复）；新增 `StatCard.vue`（指标卡，消费 `formatStat`/`trendOf`，支持 positive/finance 双色趋势与骨架加载）与 `CopyText.vue`（一键复制，含 `navigator.clipboard` + `execCommand` 兜底与 i18n 反馈）；建立 `src/components/index.js` barrel 统一导出既有 8 个组件 + 2 个新组件；i18n 补 `common.copy/copied/copyFailed/copyEmpty` 双 locale。已配套 9 例 `stat.test.js` 单测，前端双门禁（`node --test` 39 例全绿 + `vite build` 通过）。

#### R12 · 微前端 / 模块联邦 — P2
- **动机**：大型前台按团队拆分独立部署。
- **候选**：Vite Module Federation。
- **依赖**：R11 组件库。
- **验收**：独立子应用挂载到主壳，独立部署。
- **进度**：✅ **已落地 V1.0.7**（`pc-admin-vue3-vite`）：采用**离线安全自研主壳方案**（离线仓库无 `@originjs/vite-plugin-federation`，不引入外部依赖）——`views/micro/MicroFrontendHost.vue` 经 `import.meta.glob('../../micro-apps/*/index.js', { eager:false })` 懒加载子应用，按 `mount(container, ctx)` / `unmount(ctx)` 契约调度（挂载/卸载生命周期对称、主壳兜底清空容器、挂载状态同步 URL query 可恢复）；子应用目录结构驱动清单（新增子应用零主壳改动）；演示子应用 `micro-apps/hello`（原生 DOM 计数器，验证任意技术栈可挂载）与 `micro-apps/notes`（localStorage 便签，验证状态独立与卸载不丢）；菜单种子 V79__micro_frontend.sql（微前端目录 9801 + 宿主 9802，权限码复用已登记的 `system:monitor:read`，参照 V42 health 先例）。配套 7 例契约测试（子应用契约/主壳调度/V79 同源校验），前端双门禁全绿。

### 3.4 运维与可观测增强

#### R13 · 分布式追踪（OpenTelemetry）— P1
- **动机**：`framework` 已含 Micrometer 指标，缺 trace 串联。
- **候选模块**：`framework.observability`（✅ **已落地 V1.0.7**）：零依赖 OTLP/HTTP 自研导出器（`OtlpSpanExporter`；离线仓库无 opentelemetry-sdk，故不自引官方 SDK）+ `OtlpTracingFilter`（每请求生成 server span，复用 MDC `traceId` 与 W3C traceparent 对齐）+ `SpanRecorder`（内存有界缓冲，双 sink 之一）+ `ObservabilityController`（`/api/observability` 回溯/摘要/指标快照）。`observability.otlp.enabled` 默认关闭，开启即导出至 Collector；`recorder` 默认开启供本地回溯。权限码 `system:observability:read|manage` 经 `ApiResourceAutoSyncRunner` 自动登记。
- **依赖**：无强前置。
- **验收**：跨模块请求 trace 串联（MDC traceId 贯穿，单体进程内多模块共享同一 trace）+ 零依赖导出 OTLP；具备 Collector 时 Grafana/Jaeger 可见。

#### R14 · APM 指标大盘 — P1
- **动机**：指标与 N+1 监听未可视化。
- **候选模块**：`scaffold-monitor` 扩展（✅ **已落地 V1.0.7**）：`MonitorController`（`/api/system/monitor`）暴露实时运行指标（CPU/内存/磁盘/JVM）、进程内缓存统计（命中率，经 `StatisticsCacheManager`）、数据库连接池、在线会话/用户、异常日志与聚合；`NPlusOneController` + `NPlusOneService` 暴露 N+1/慢查询疑似事件（`NPlusOneSuspectRegistry` 有界留存）；`framework.monitor.detection` 的 `SlowQueryListener` / `NPlusOneListener` + `NPlusOneMetricsBridge` 将探针数据桥接进 `MetricsRegistry`。依赖 R13 的 trace 上下文。
- **依赖**：R13（已完成）。
- **验收**：慢查询（N+1 疑似）/ 缓存命中率 / 连接池 / 在线会话均可见（管理前端可对接）。**租户维度指标已于 V1.0.7 补全**：`TenantMetrics`（`framework.observability.recorder`，作为 `SpanExporter` sink）按租户聚合请求数/错误率/平均时延，经 `TenantMetricsController`（`/api/observability/tenant-metrics`）暴露，支持 APM 大盘按租户下钻；无租户上下文的 span 自动跳过、内存有界（默认 256 租户）。预置 Grafana dashboard JSON 为可选交付，按需补充。
- **前端视图（租户维度指标 Tab）**：✅ **已落地 V1.0.7**（monitor/components/TenantMetricsTab 接入可观测中心第 7 个 Tab[请求数/错误数/错误率/平均时延/最近活跃]，复用 `system:observability:read`，403 降级空态 + api 契约测试 1 例，i18n 双 locale 补齐）。
- **前端视图（平台跨租户治理通道）**：✅ **已落地 V1.0.7**（`TenantGovernanceController` 后端三端点 `GET /api/system/tenants/data-overview`、`POST /api/system/tenants/backfill`、`POST /api/system/tenants/reassign` 权限 `system:tenant:manage` 此前无前端视图）。补齐 `api/tenantGovernance.js`（3 函数）+ `views/system/TenantGovernanceDialog.vue`（数据总览表 + 存量归位/跨租户迁移表单 + 强确认对话框，破坏性操作二次 ElMessageBox.confirm 拦截）+ `views/system/TenantView.vue` 接入「租户治理」按钮与对话框；i18n 双 locale 补齐 `system.tenantGovernance.*` / `system.reassign*` / `system.backfill*`；api 契约测试 3 例。未新增菜单种子（作为租户管理页对话框接入，复用既有 `/system/tenants` 菜单与 `system:tenant:manage` 权限）。

#### R15 · 混沌 / 压测体系 — P2
- **动机**：`scripts/perf/PERF-TEST-PLAN.md` 已有压测计划，缺混沌。
- **候选模块（混沌）**：`framework.chaos`（✅ **已落地 V1.0.7**）：进程内故障注入，`ChaosRegistry`(内存故障规则表) + `ChaosService`(登记/解除/查询) + `ChaosFilter`(请求链路按 Ant 路径匹配并注入 LATENCY/ERROR/EXCEPTION/FAILURE 四类故障，概率触发、自动恢复) + `ChaosController`(`/api/system/chaos`，权限码 `system:chaos:manage` 自动登记)。`app.chaos.enabled` 默认 **false**，关闭时完全不装配任何 R15 Bean，零回归；故障状态仅存进程内存，重启即清空，演练安全。
- **候选（压测）**：`scripts/perf/` 压测脚本与基线计划已随仓库存在，Chaos 模块补齐后可在压测基线上叠加故障演练。
- **依赖**：无强前置（R14 已先行落地，二者可并行）。
- **验收**：注入 LATENCY/ERROR 等故障后由 `ChaosFilter` 在请求链路生效、解除后自动恢复；压测基线报告由 `scripts/perf/` 产出。
- **前端视图（混沌工程治理面）**：✅ **已落地 V1.0.7**（chaos/ChaosView 故障列表 + 注入表单[类型/路径匹配/延迟/错误码/概率] + 单条解除/全部清除，feature-gate 友好降级 + api 契约测试 4 例；菜单种子 V80 新增 `system:chaos:read`）。

#### R16 · 配置中心接入 — P2
- **动机**：当前配置在 `application` + 版本化结构链（`db/migration/` → `db/init/`），无动态刷新。
- **候选模块**：`framework.config.center`（✅ **已落地 V1.0.7**）：`ConfigCenterPort`（防腐层，定义读取/快照/刷新/动态刷新能力）+ `LocalConfigCenterAdapter`（默认实现，读取 Spring `Environment` 的 `app.*` 命名空间，零新依赖）+ `ConfigCenterService`（刷新后发布 `ConfigRefreshedEvent` 经 R2 事件总线，订阅方可热重载运行态）+ `ConfigCenterController`（`/api/system/config-center` 状态/快照/刷新，权限码 `system:config-center:read|manage` 自动登记）。`app.config-center.enabled` 默认 false，关闭时完全不装配任何 R16 Bean，与既有行为零回归；远端类型（nacos/apollo）的 `ConfigCenterPort` 实现由外部 jar 经 R1 插件机制提供，避免离线仓库引入其 SDK。
- **依赖**：R2 事件（动态刷新，已完成）。
- **验收**：本地适配器下配置可快照与刷新并触发事件（热更新订阅链路已打通）；远端中心接入只需提供 `ConfigCenterPort` Bean，无需改动业务代码。无需重启即可在「本地 reload + 事件通知」语义下热刷新。
- **前端视图（配置中心页）**：✅ **已落地 V1.0.7**（system/ConfigCenterView 状态卡 + 快照表[前缀过滤] + 热刷新，feature-gate 友好降级 + api 契约测试 3 例；菜单种子 V80 新增 `system:config-center:read`）。

---

## 4. 候选模块与依赖 DAG（规划态）

> 下列模块**均处于规划态**，尚未创建；标注以规划 DAG 无环为前提（最终以 `check-conventions` H5 校验为准）。

| 候选模块 | 层 | 依赖（规划） | 关键包 | 对应需求 |
|---|---|---|---|---|
| `scaffold-plugin` | 扩展基座 | common | `plugin.spi`/`plugin.registry` | R1 |
| `common.event` | 基础（子包） | common | `common.event` | R2 ✅ 已落地 |
| `scaffold-cms` | 业务域 | common | `cms.*` | R5 ✅ 已落地 V1.0.7 |
| `scaffold-im` | 业务域 | common | `im.*` | R6 ✅ 已落地 V1.0.7 |
| `scaffold-approval` | 业务域 | common, workflow | `workflow.approval.*`（批次 12 并入） | R7 ✅ 已落地 V1.0.7 |
| `scaffold-workbench/dashboard` | 业务域扩展 | common, system | `workbench.dashboard.*` | R8 ✅ 已落地 V1.0.7 |
| `scaffold-lowcode` | 业务域 | common, system, plugin | `lowcode.*` | R10 |
| `scaffold-ai` | 工具（可选） | common, codegen | `ai.*` | R3 ✅ 已落地 V1.0.7 |
| `scaffold-framework/config.multiregion` | 框架装配（子包） | framework | `config.multiregion` | R4 ✅ 已落地 V1.0.7 |
| `scaffold-framework/config.center` | 框架装配（子包） | framework, common | `config.center` | R16 ✅ 已落地 V1.0.7 |
| `packages/ui` | 前端（可选） | 无 | UI 组件库 | R11 ✅ 已落地 V1.0.7（src/components + src/utils 沉淀，独立包待 R12 评估） |

---

## 5. 与现有工程约定的衔接（铁律不变）

1. **统一门禁 + 编译校验**：任一规划项落地均须 `python3 scripts/check-all.py`（7 步硬性项 EXIT=0）+ `mvn -o -f pom.xml -pl <module> -am clean compile` BUILD SUCCESS（须 `dangerouslyDisableSandbox` 且 `timeout>=600000`）。后端测试源码已移除，`mvn test` 为空跑，不再作为验收口径。
2. **依赖无环（H5）**：新增模块须保持 DAG 无环；特性模块不互相直连 Service，协作经 **Port 防腐层** 或上层 `admin` 编排。
3. **分层（H3）**：`controller → service → domain`；纯函数规则类随数据域走。
4. **持久层**：统一走 `SqlWriters`，DDL 标识符须显式加引号保留 snake_case 小写；索引用独立 `CREATE INDEX`。
5. **i18n**：新子组须塞进父命名空间内部；前端新增端点须同步补路由 + i18n 键 + 菜单种子（版本化结构链）+ API 客户端。
6. **权限码**：新 `@PreAuthorize` 端点由 `ApiResourceAutoSyncRunner` 自动登记，无需手工维护。
7. **提交门禁**：按关注点拆 `type: 中文描述` 提交；**绝不 push `main`/远程**，由本机推送；`scripts/check-conventions.py` 超时需 `dangerouslyDisableSandbox + timeout>=600000`。

---

## 6. 验收与里程碑建议

| 阶段 | Done 定义 | 退出准出 |
|---|---|---|
| Phase 0 | R2 事件总线落地；现存隐式直连收敛为 Port/事件 | 双门禁绿 + `check-conventions` 无新增环 |
| Phase 1 | R1 插件机制可用（外部 jar 注入菜单+端点） | 端到端注入测试通过 |
| Phase 2 | R5/R6/R7 至少一个上线 + R8 设计 | 前端视图 + 菜单种子 + i18n 齐备 |
| Phase 3 | R9 移动端自适应 + R10 低代码深化可用 | 移动端冒烟 + 表单落库 |
| Phase 4 | R13/R14/R16 可观测与配置中心落地 | Grafana 可见 trace/指标；配置可热刷新（本地 reload + 事件通知） |

---

## 7. 风险与开放问题

- **`common ↔ mybatis` 依赖环**：仍是"新增模块不改动 common"的前提约束；插件机制须避免把扩展逻辑塞回 common。
- **插件化与强类型 MyBatis/Port 的兼容**：外部 jar 如何安全访问领域实体与 DSL，需约定"仅经 Port/事件"，禁止跨 jar 直连 entity。
- **AI 集成的合规与成本**：R3 涉及外部大模型，须有脱敏/审计/成本门禁与离线降级。
- **多区域数据一致性**：R4 跨数据源后，跨单元查询与 `db/init/` 初始化顺序需专门设计。
- **前端 monorepo 拆分成本**：R11/R12 涉及构建体系改造，需评估与现有 Vite 单仓的兼容。
