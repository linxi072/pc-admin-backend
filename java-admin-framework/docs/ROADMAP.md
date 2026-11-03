# 演进路线图（ROADMAP）

> **定位**：本文件是 `java-admin-framework`（含前端 `pc-admin-vue3-vite`）的**演进总纲**，描述 V1.0.7 之后的新需求与扩展方向。
> 现状事实以 `docs/ARCHITECTURE.md` / `MODULES.md` / `KEY-FLOWS.md` / `USAGE.md` 与 `db/migration` 迁移链为准；本文档仅描述**规划中**的内容，不声称任何候选模块已存在。
> 所有规划项均须沿用既有工程铁律（双门禁、`check-conventions` H1–H5、Port 防腐层、DDL 引号等，见 §5）。

---

## 0. 基线现状（V1.0.7）

- **规模**：12 个 Maven 模块，包根 `com.acme.scaffold`；里程碑 M1–M50 全部收口。
- **已具备核心能力**：RBAC 权限、JWT/刷新令牌/Token 版本失效、OAuth2 授权+资源服务器、多租户隔离、数据权限、字段脱敏、写接口幂等、操作审计、Flowable 工作流（含管理员治理）、JobRunr 调度、WebSocket 实时、可观测探针（Micrometer + N+1/慢查询监听）、jOOQ 持久层、代码生成器（M50）、低代码设计器（M44）、API 文档中心、幂等记录管理、流程实例治理。
- **当前缺口**：作为"脚手架"已功能完整，但**二次开发需改源码**、**跨域副作用靠 Port 直连**、**缺统一扩展点/事件机制**、**前端无组件库/移动端适配**、**追踪与 APM 不完整**。

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
- **Phase 4 · 运维与可观测增强**：R13 追踪 / R14 APM / R15 混沌压测 / R16 配置中心（部分可与 Phase 1–3 并行）。

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
- **候选**：`scaffold-codegen` 扩展，或独立 `scaffold-ai`（若需独立部署推理服务）。
- **依赖**：R1 成熟后更易嵌入；注意合规与成本门禁。
- **验收**：输入"订单表含金额/状态"产出可编译骨架 + 迁移 SQL + 前端路由；门禁绿。

#### R4 · 多区域 / 多集群部署支撑 — P2
- **动机**：多租户已建（租户列隔离），但单实例；大租户需物理隔离。
- **方案要点**：租户→部署单元映射配置；数据源路由按租户组；Flyway 按单元独立迁移；复用 `TenantContext`。
- **候选**：`common` 多数据源路由子包。
- **依赖**：R2 事件驱动 + R16 配置中心。
- **验收**：两个租户组落不同数据源，租户隔离测试通过。

### 3.2 业务模块扩充

#### R5 · 内容管理 CMS — P1
- **动机**：后台普遍需要栏目/文章/富文本/媒体库。
- **候选模块**：`scaffold-cms`（新），依赖 `scaffold-plugin`(R1) + `scaffold-system`(文件模块)。
- **验收**：栏目树 + 文章 CRUD + 富文本 + 发布态；前端视图 + i18n + 菜单种子。

#### R6 · 站内 IM / 客服 — P1
- **动机**：基于已有 `realtime`(WebSocket) 的会话能力缺失。
- **候选模块**：`scaffold-im`（新）或并入 `realtime`。
- **依赖**：R2 事件（消息已读/离线）、`notification`。
- **验收**：点对点/群组会话、离线消息、已读回执；前端会话组件。

#### R7 · 审批中心门户 — P1
- **动机**：Flowable 待办/已办/我发起分散，缺统一工作台。
- **候选**：`workbench` 扩展 或 `scaffold-approval`（新）。
- **依赖**：`workflow`（已有）。
- **验收**：统一待办列表 + 发起入口 + 图表；前端聚合页。

#### R8 · 数据看板模板库 — P2
- **动机**：`workbench` 报表为硬编码，缺可复用模板。
- **候选**：`workbench` 扩展。
- **依赖**：`workbench`。
- **验收**：模板 CRUD + 拖拽配置 + 权限；复用 `DrillDrawer`。

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
- **候选**：`framework.observability` 扩展。
- **依赖**：无强前置。
- **验收**：跨模块请求 trace 串联并导出 OTLP；Grafana 可见。

#### R14 · APM 指标大盘 — P1
- **动机**：指标与 N+1 监听未可视化。
- **候选**：`monitor` 扩展 / 预置 Grafana dashboard JSON。
- **依赖**：R13。
- **验收**：慢查询/缓存命中/租户维度可见。

#### R15 · 混沌 / 压测体系 — P2
- **动机**：`scripts/perf/PERF-TEST-PLAN.md` 已有压测计划，缺混沌。
- **候选**：接入 chaos + 持续压测流水线。
- **依赖**：R14。
- **验收**：注入故障后自动恢复验证；压测基线报告。

#### R16 · 配置中心接入 — P2
- **动机**：当前配置在 `application` + Flyway，无动态刷新。
- **候选**：`framework.config` 扩展 Nacos/Apollo 适配器（Port）。
- **依赖**：R2 事件（动态刷新）。
- **验收**：配置经中心下发并热更新，无需重启。

---

## 4. 候选模块与依赖 DAG（规划态）

> 下列模块**均处于规划态**，尚未创建；标注以规划 DAG 无环为前提（最终以 `check-conventions` H5 校验为准）。

| 候选模块 | 层 | 依赖（规划） | 关键包 | 对应需求 |
|---|---|---|---|---|
| `scaffold-plugin` | 扩展基座 | common | `plugin.spi`/`plugin.registry` | R1 |
| `common.event` | 基础（子包） | common | `common.event` | R2 ✅ 已落地 |
| `scaffold-cms` | 业务域 | common, system, plugin | `cms.*` | R5 |
| `scaffold-im` | 业务域 | common, realtime, plugin | `im.*` | R6 |
| `scaffold-approval` | 业务域 | common, workflow, plugin | `approval.*` | R7 |
| `scaffold-lowcode` | 业务域 | common, system, plugin | `lowcode.*` | R10 |
| `scaffold-ai` | 工具（可选） | common, codegen | `ai.*` | R3 |
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
| Phase 4 | R13/R14 串联可观测 + R15/R16 至少一项 | Grafana 可见 trace/指标 |

---

## 7. 风险与开放问题

- **`common ↔ jooq` 依赖环**：仍是"新增模块不改动 common"的前提约束；插件机制须避免把扩展逻辑塞回 common。
- **插件化与强类型 jOOQ/Port 的兼容**：外部 jar 如何安全访问领域实体与 DSL，需约定"仅经 Port/事件"，禁止跨 jar 直连 entity。
- **AI 集成的合规与成本**：R3 涉及外部大模型，须有脱敏/审计/成本门禁与离线降级。
- **多区域数据一致性**：R4 跨数据源后，跨单元查询与 Flyway 迁移顺序需专门设计。
- **前端 monorepo 拆分成本**：R11/R12 涉及构建体系改造，需评估与现有 Vite 单仓的兼容。
