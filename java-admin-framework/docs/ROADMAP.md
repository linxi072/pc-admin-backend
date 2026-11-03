# 演进路线图（ROADMAP）

> **定位**：本文件是 `java-admin-framework`（含前端 `pc-admin-vue3-vite`）的**演进总纲**，描述 V1.0.7 之后的新需求与扩展方向。
> 现状事实以 `docs/ARCHITECTURE.md` / `MODULES.md` / `KEY-FLOWS.md` / `USAGE.md` 与 `db/migration` 迁移链为准；本文档仅描述**规划中**的内容，不声称任何候选模块已存在。
> 所有规划项均须沿用既有工程铁律（双门禁、`check-conventions` H1–H5、Port 防腐层、DDL 引号等，见 §5）。

---

## 0. 基线现状（V1.0.7）

- **规模**：12 个 Maven 模块，包根 `com.acme.scaffold`；里程碑 M1–M50 全部收口。
- **已具备核心能力**：RBAC 权限、JWT/刷新令牌/Token 版本失效、OAuth2 授权+资源服务器、多租户隔离、数据权限、字段脱敏、写接口幂等、操作审计、Flowable 工作流（含管理员治理）、JobRunr 调度、WebSocket 实时、可观测探针（Micrometer + N+1/慢查询监听）、jOOQ 持久层、代码生成器（M50）、低代码设计器（M44）、API 文档中心、幂等记录管理、流程实例治理。
- **当前缺口**：作为"脚手架"已功能完整，但**前端无组件库/移动端适配**（R9/R10/R11）、**微前端拆分待评估**（R12）、**混沌/压测体系缺失**（R15）；R13/R14/R16 等运维增强已随各迭代落地，详见各章节。

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
- **Phase 3 · 前端能力增强**：R9 移动端 / R10 低代码深化 / R11 组件库 / R12 微前端。
- **Phase 4 · 运维与可观测增强**：R13 追踪 ✅ / R14 APM ✅ / R16 配置中心 ✅ 已落地 V1.0.7；R15 混沌压测待启动（部分可与 Phase 1–3 并行）。

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
- **候选**：`common.event` 子包（✅ **已落地 V1.0.7**）：`DomainEvent` / `EventPublisher` / `DomainEventListener` / `DefaultEventPublisher`（错误隔离）/ `EventBridge`+`NoOpEventBridge` 已就位；并以「用户禁用 → security 踢下线」完成端到端收敛示范。若需独立运维再升为 `scaffold-event`。
- **依赖**：Phase 0 先行，供 R1/R5/R6 解耦。
- **验收**：用户禁用事件被 `online`/`notification` 订阅且零直连；端到端测试覆盖。

#### R3 · AI 辅助开发集成 — P2
- **动机**：`scaffold-codegen`（M50）为模板驱动，缺语义理解。
- **方案要点**：在 `scaffold-codegen` 增"自然语言→CRUD/查询"入口，对接大模型（经 cloud-service 或本地推理），产出实体/DDL/Service/前端视图草稿，保留人工 review。
- **候选模块**：`scaffold-ai`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.ai` 下 `port`（`LlmProvider` 端口 + `HeuristicLlmProvider` 离线默认 / `CloudLlmProvider` `@ConditionalOnProperty` 可选）/ `service`（`AiScaffoldService` 编排 + `DdlRenderer` 引号小写迁移 SQL）/ `controller`（`AiScaffoldController` 走 `ai:scaffold:generate` 权限码）/ `dto`（TableSpec/ColumnSpec/ScaffoldResult/ScaffoldRequest）/ `config`（AiProperties 成本门禁）；复用 `scaffold-codegen` 的 `JavaTypeMapper` 与 `CodeTemplates.renderAll` 渲染实体/服务/Vue 草稿，零新依赖；启发式解析确定性可过双门禁，云大模型仅条件化可选（默认不激活，避免网络依赖破坏 `mvn -o`）。
- **依赖**：R1 成熟后更易嵌入；注意合规与成本门禁。
- **验收**：输入"订单表含金额/状态"产出可编译骨架 + 迁移 SQL + 前端路由；门禁绿。

#### R4 · 多区域 / 多集群部署支撑 — P2
- **动机**：多租户已建（租户列隔离），但单实例；大租户需物理隔离。
- **方案要点**：租户→部署单元映射配置；数据源路由按租户组；Flyway 按单元独立迁移；复用 `TenantContext` 与既有 `RoutingDataSource`。
- **候选模块**：`scaffold-framework/config/multiregion`（✅ **已落地 V1.0.7**）：在既有多数据源路由（`RoutingDataSource` / `DataSourceContextHolder` / `DataSourceRegistry`，位于 `scaffold-framework`）之上叠加「租户→部署单元」解析层 —— `TenantUnitMapping` 解析当前租户归属单元（显式映射 / 租户组区间 / 默认单元三级优先级），`MultiRegionInterceptor`（Web 层，注册在 `TenantInterceptor` 之后）与 `MultiRegionRouter`（非 Web 编程式）按解析结果写入 `DataSourceContextHolder`，使构建在 `RoutingDataSource` 之上的主 `DSLContext`（即 `JooqConfig.dslContext`）自动路由到对应物理库；`UnitAwareFlywayMigrator` 对每个注册数据源独立执行迁移；全链路零改 `JooqWriters` / `JooqConfig`。默认 `app.multiregion.enabled=false`，关闭时完全不装配任何 R4 Bean，与单实例行为零回归。
  - **为何落在 framework 而非 common**：既有路由基础设施（DataSourceRegistry / RoutingDataSource / DataSourceContextHolder）已在 `scaffold-framework`，若放入 `common` 将产生 common→framework 反向依赖而破坏 DAG 无环；故 R4 复用 framework 既有能力，未新增模块。
- **依赖**：R2 事件驱动（已完成）+ R16 配置中心（可选，未阻塞）。
- **验收**：`MultiRegionRoutingIntegrationTest` 用两个 H2 内存库模拟两部署单元，验证租户组 A/B 的写入仅落对应物理库（物理隔离）；`UnitAwareFlywayMigratorTest` 验证每个单元独立迁移；门禁全绿。

### 3.2 业务模块扩充

#### R5 · 内容管理 CMS — P1
- **动机**：后台普遍需要栏目/文章/富文本/媒体库。
- **候选模块**：`scaffold-cms`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.cms` 下 `domain`（CmsCategoryDO/CmsArticleDO）/`dto`/`service`（栏目树构建 + 文章 CRUD/发布态流转）/`controller`（栏目、文章 REST 端点，@PreAuthorize 权限码经 ApiResourceAutoSyncRunner 自动登记）/`event`（ArticlePublishedEvent 复用 R2 事件总线）；DDL 见 V74__cms.sql（cms_category/cms_article + 菜单种子）；`JooqTables` 新增 `CMS_CATEGORY`/`CMS_ARTICLE` 常量。
- **依赖取舍**：为保持 DAG 最小且无环，CMS 仅依赖 `common`（经 JooqWriters 持久化、R2 事件总线发发布事件），未引入 `system`/`plugin`；媒体资源以 `cover_url` 等文本列承载，避免跨模块 Service 直连（与「跨域协作经 Port/事件」铁律一致）。
- **验收**：栏目树 + 文章 CRUD + 富文本 + 发布态（DRAFT/PUBLISHED/OFFLINE）+ 浏览计数；双门禁全绿（11 个后端单测覆盖）；菜单种子与权限码自动登记就绪。前端视图（cms/CategoryView、cms/ArticleView）与 i18n 为后续前端补充项。

#### R6 · 站内 IM / 客服 — P1
- **动机**：基于已有 `realtime`(WebSocket) 的会话能力缺失。
- **候选模块**：`scaffold-im`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.im` 下 `domain`（ImConversationDO/ImConversationMemberDO/ImMessageDO）/`dto`/`service`（会话创建[P2P/群组]+成员管理+消息发送/未读计数/已读回执 + 会话列表）/`controller`（会话、消息 REST 端点，@PreAuthorize 权限码经 ApiResourceAutoSyncRunner 自动登记）；DDL 见 V75__im.sql（im_conversation/im_conversation_member/im_message + 菜单种子）。`JooqTables` 复用既有常量；`ImMessageSentEvent` 落地于 `common.event.domain`（与 UserDisabledEvent 同位），由 `realtime` 的 `ImMessagePushListener` 订阅后经 WebSocket 实时推送，二者经事件解耦、无编译期依赖。
- **依赖取舍**：为保持 DAG 最小且无环，IM 仅依赖 `common`（经 JooqWriters 持久化、R2 事件总线发消息事件）；实时推送由 `realtime` 订阅同一事件完成，IM 不反向依赖 realtime/`system`/`plugin`（与「跨域协作经 Port/事件」铁律一致）。
- **验收**：P2P/群组会话 + 成员管理 + 消息发送 + 未读计数（按成员 last_read_message_id 推导）+ 已读回执 + 会话列表；双门禁全绿（16 个后端单测覆盖 CRUD/树/未读/回执/事件）；菜单种子与权限码自动登记就绪。前端会话组件为后续前端补充项。

#### R7 · 审批中心门户 — P1
- **动机**：Flowable 待办/已办/我发起分散，缺统一工作台。
- **候选模块**：`scaffold-approval`（✅ **已落地 V1.0.7**）：`com.acme.scaffold.approval` 下 `ApprovalPortalService`（薄编排层，聚合 workflow 的待办/已办/我发起/可发起 + `WorkflowDesignService` 的已发布流程 + 统计）、`ApprovalPortalView`/`ApprovalStats`（聚合 DTO）、`ApprovalPortalController`（6 个只读 GET 端点 `/api/approval/portal/*`，统一权限码 `approval:portal:view`，@PreAuthorize 经 ApiResourceAutoSyncRunner 自动登记）；菜单种子见 V76__approval_portal.sql（审批中心目录 9501 + 审批工作台 9502）。`wf_task_ext` 补 `activity_name` 快照列（V76），待办/已办列表免回查引擎即可展示可读节点名。
- **依赖取舍**：为保持 DAG 最小且无环，approval 仅依赖 `common` + `workflow`（经 `WorkflowService`/`WorkflowDesignService` 编排）；不反向依赖 `system`/`plugin`（与「跨域协作经 Port/事件」铁律一致）。
- **验收**：统一待办列表 + 已办列表（按完成时间倒序 + activity_name 快照）+ 我发起 + 可发起（仅 PUBLISHED）+ 统计；双门禁全绿（workflow 10 个单测覆盖 myDoneTasks、approval 3 个单测覆盖聚合/过滤/透传）；菜单种子与权限码自动登记就绪。前端聚合页（approval/PortalView）为后续前端补充项。

#### R8 · 数据看板模板库 — P2
- **动机**：`workbench` 报表为硬编码，缺可复用模板。
- **候选模块**：`scaffold-workbench` 扩展（✅ **已落地 V1.0.7**）：`com.acme.scaffold.workbench.dashboard` 下 `domain`（DashboardTemplateDO）/`dto`（TemplateSaveRequest/TemplateUpdateRequest/DashboardTemplateVO）/`service`（看板模板 CRUD + 发布态流转 DRAFT/PUBLISHED/OFFLINE + 浏览计数 + layout_json 经 Jackson 合法性校验）/`controller`（看板模板 REST 端点 `/api/dashboard/template/*`，统一权限码 `dashboard:template:manage`，@PreAuthorize 经 ApiResourceAutoSyncRunner 自动登记）；DDL 见 V78__dashboard_template.sql（dashboard_template + 菜单种子：数据看板目录 9701 + 看板模板管理 9702）。`JooqTables` 新增 `DASHBOARD_TEMPLATE` 常量。
- **依赖取舍**：为保持 DAG 最小且无环，dashboard 仅依赖 `common` + `system`（与 workbench 既有依赖一致，经 JooqWriters 持久化）；布局配置以 `layout_json`（TEXT）承载，由前端约定结构、后端仅做 JSON 语法校验（不做语义约束），避免引入重量级表单引擎；复用现有 `workbench` 模块而非新建模块。
- **验收**：看板模板 CRUD + 配置（layout_json 序列化）+ 发布态流转（DRAFT/PUBLISHED/OFFLINE）+ 浏览计数 + 权限码自动登记；双门禁全绿（9 个后端单测覆盖 CRUD/发布/撤销/计数/更新/逻辑删除/分页筛选/非法 JSON 拒绝/缺失模板）。前端视图（dashboard/DashboardTemplateView）与拖拽配置器为后续前端补充项。

### 3.3 前端能力增强

#### R9 · 移动端 / 小程序适配 — P1
- **动机**：当前仅 PC 后台；管理场景需要移动端。
- **候选**：`pc-admin-vue3-vite` 增加响应式断点 / 独立 `mobile-admin`（若有独立部署诉求）。
- **依赖**：后端 OAuth2 资源服务器（已有）。
- **验收**：移动端布局自适应、触摸交互；或小程序壳对接 REST。

#### R10 · 低代码表单设计器深化 — P1
- **动机**：M44 设计器基础能力已有，需脚本/校验/联动/数据源绑定。
- **候选**：前端 `lowcode` 模块 + 后端 `scaffold-lowcode`（可选）。
- **依赖**：表单元数据存储（复用现有）。
- **验收**：设计器产出可运行表单，联动/校验生效，数据落库。

#### R11 · 通用业务组件库沉淀 — P2
- **动机**：Table/Form/抽屉/图表在各视图重复实现。
- **候选**：`packages/ui`（monorepo）或独立 npm 包。
- **依赖**：无。
- **验收**：组件库独立构建 + 文档站；被 `pc-admin` 复用。

#### R12 · 微前端 / 模块联邦 — P2
- **动机**：大型前台按团队拆分独立部署。
- **候选**：Vite Module Federation。
- **依赖**：R11 组件库。
- **验收**：独立子应用挂载到主壳，独立部署。

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
- **验收**：慢查询（N+1 疑似）/ 缓存命中率 / 连接池 / 在线会话均可见（管理前端可对接）。**遗留项**：租户维度指标（按租户聚合的请求数/慢查询/缓存命中）尚未独立建模，列入后续增强；预置 Grafana dashboard JSON 为可选交付，按需补充。

#### R15 · 混沌 / 压测体系 — P2
- **动机**：`scripts/perf/PERF-TEST-PLAN.md` 已有压测计划，缺混沌。
- **候选**：接入 chaos + 持续压测流水线。
- **依赖**：R14。
- **验收**：注入故障后自动恢复验证；压测基线报告。

#### R16 · 配置中心接入 — P2
- **动机**：当前配置在 `application` + Flyway，无动态刷新。
- **候选模块**：`framework.config.center`（✅ **已落地 V1.0.7**）：`ConfigCenterPort`（防腐层，定义读取/快照/刷新/动态刷新能力）+ `LocalConfigCenterAdapter`（默认实现，读取 Spring `Environment` 的 `app.*` 命名空间，零新依赖）+ `ConfigCenterService`（刷新后发布 `ConfigRefreshedEvent` 经 R2 事件总线，订阅方可热重载运行态）+ `ConfigCenterController`（`/api/system/config-center` 状态/快照/刷新，权限码 `system:config-center:read|manage` 自动登记）。`app.config-center.enabled` 默认 false，关闭时完全不装配任何 R16 Bean，与既有行为零回归；远端类型（nacos/apollo）的 `ConfigCenterPort` 实现由外部 jar 经 R1 插件机制提供，避免离线仓库引入其 SDK。
- **依赖**：R2 事件（动态刷新，已完成）。
- **验收**：本地适配器下配置可快照与刷新并触发事件（热更新订阅链路已打通）；远端中心接入只需提供 `ConfigCenterPort` Bean，无需改动业务代码。无需重启即可在「本地 reload + 事件通知」语义下热刷新。

---

## 4. 候选模块与依赖 DAG（规划态）

> 下列模块**均处于规划态**，尚未创建；标注以规划 DAG 无环为前提（最终以 `check-conventions` H5 校验为准）。

| 候选模块 | 层 | 依赖（规划） | 关键包 | 对应需求 |
|---|---|---|---|---|
| `scaffold-plugin` | 扩展基座 | common | `plugin.spi`/`plugin.registry` | R1 |
| `common.event` | 基础（子包） | common | `common.event` | R2 ✅ 已落地 |
| `scaffold-cms` | 业务域 | common | `cms.*` | R5 ✅ 已落地 V1.0.7 |
| `scaffold-im` | 业务域 | common | `im.*` | R6 ✅ 已落地 V1.0.7 |
| `scaffold-approval` | 业务域 | common, workflow | `approval.*` | R7 ✅ 已落地 V1.0.7 |
| `scaffold-workbench/dashboard` | 业务域扩展 | common, system | `workbench.dashboard.*` | R8 ✅ 已落地 V1.0.7 |
| `scaffold-lowcode` | 业务域 | common, system, plugin | `lowcode.*` | R10 |
| `scaffold-ai` | 工具（可选） | common, codegen | `ai.*` | R3 ✅ 已落地 V1.0.7 |
| `scaffold-framework/config.multiregion` | 框架装配（子包） | framework | `config.multiregion` | R4 ✅ 已落地 V1.0.7 |
| `scaffold-framework/config.center` | 框架装配（子包） | framework, common | `config.center` | R16 ✅ 已落地 V1.0.7 |
| `packages/ui` | 前端（可选） | 无 | UI 组件库 | R11 |

---

## 5. 与现有工程约定的衔接（铁律不变）

1. **双门禁**：任一规划项落地均须 `mvn -o -f pom.xml -pl <module> -am clean test` 全绿 + `scripts/check-conventions.py` exit=0（须 `dangerouslyDisableSandbox` 且 `timeout>=600000`）。
2. **依赖无环（H5）**：新增模块须保持 DAG 无环；特性模块不互相直连 Service，协作经 **Port 防腐层** 或上层 `admin` 编排。
3. **分层（H3）**：`controller → service → domain`；纯函数规则类随数据域走。
4. **持久层**：统一走 `JooqWriters`，DDL 标识符须显式加引号保留 snake_case 小写；索引用独立 `CREATE INDEX`。
5. **i18n**：新子组须塞进父命名空间内部；前端新增端点须同步补路由 + i18n 键 + 菜单种子（Flyway）+ API 客户端。
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

- **`common ↔ jooq` 依赖环**：仍是"新增模块不改动 common"的前提约束；插件机制须避免把扩展逻辑塞回 common。
- **插件化与强类型 jOOQ/Port 的兼容**：外部 jar 如何安全访问领域实体与 DSL，需约定"仅经 Port/事件"，禁止跨 jar 直连 entity。
- **AI 集成的合规与成本**：R3 涉及外部大模型，须有脱敏/审计/成本门禁与离线降级。
- **多区域数据一致性**：R4 跨数据源后，跨单元查询与 Flyway 迁移顺序需专门设计。
- **前端 monorepo 拆分成本**：R11/R12 涉及构建体系改造，需评估与现有 Vite 单仓的兼容。
