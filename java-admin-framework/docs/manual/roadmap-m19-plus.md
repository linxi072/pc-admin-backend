# 路线图规划：M19+ 后续模块提案（java-admin-framework）

> 状态：**已采纳路线（2026-10-23 确认决策；M19 P1/P2/P3 + M20–M26 + M22 低代码表单(spike+正式立项提交落库) 已全部交付）** ｜ 日期：2026-10-23（2026-10-25 更新 M20–M26 交付状态；2026-10-26 更新 M19 P1/P2 与 M22 正式立项交付状态）｜ 分支：`feat/V1.0.4` ｜ 视角：在 M1–M18 与既有增强项基础上，梳理下一阶段（M19+）可承载的能力模块，供评审与排期决策。
> 方法：直接核查已交付能力（M1 审计 / M2 幂等 / M3 Token 版本 / M4 工作流 claim / M5 缓存 / M6 通知渠道 / M7–M8 已读回执 / M14 免打扰 / M15 退订令牌 / M16 故障转移+有界重试 / M17 聚合摘要 / M18 Webhook HMAC / 导入导出中心 / i18n / 限流注解化 / 敏感数据脱敏通用化 / 多段导入 #651 / 可观测中心前端 / 岗位管理 / 文件管理 / 多数据源 / 编码规范校验脚本），不凭空假设。
> 约束：离线 Maven 仓库可用依赖优先；禁止引入需联网拉取的重型依赖（如 POI，已用零依赖 OOXML 替代）；数据访问以手写 jOOQ DSL（JooqTables + TableRef）为准；新模块须延续「模板注册 / 纯函数规则 / 条件装配 / 自动登记权限码」的既有范式。

---

## 1. 当前能力边界（一句话盘点）

框架已具备：**认证授权 + 数据权限 + 审计 + 幂等 + 多数据源 + 文件管理 + 缓存分键 + 通知五渠道 + 工作流 + 实时推送 + 导入导出中心（含多段结构）+ i18n + 限流 + 脱敏通用化 + 可观测前端 + 岗位/字典/参数等系统模块**。缺口集中在「**多租户隔离、读操作/留存级审计增强、脱敏策略可视化、动态表单/低代码、开放平台/密钥治理**」等增强型基础能力。

---

## 2. M19+ 候选模块矩阵

| 候选 | 业务价值 | 范围量级 | 离线可行性 | 风险/前置 | 建议优先级 |
|---|---|---|---|---|---|
| **M19 多租户隔离（SaaS 化）** | 高（框架作为可复用基座的核心分水岭） | 大（侵入式） | ✅ 用 jOOQ DSL + TenantContext + 多数据源路由，零新依赖 | 既有表需 `tenant_id` 迁移；跨租户管理视图需独立通道；影响面全局 | **P1 基础层 + P2 租户管理域已交付（feat/V1.0.4）；P3 jOOQ 强制注入隔离已交付（feat/V1.0.4，1435 例全绿）** |
| **M20 操作/审计增强** | 中 | 中 | ✅ 增量扩展 M1 `AuditAspect` | 读操作审计量爆炸需采样/开关 | P2 |
| **M21 数据脱敏策略中心（可视化配置）** | 中 | 中 | ✅ 复用 `common/sensitive` + 规则表 | 运行时解析需缓存与刷新；默认安全需保留 | P2 |
| **M22 低代码/动态表单引擎** | 高 | 大（易膨胀） | ✅ spike（V47 契约+渲染原型）+ **正式立项提交落库（V53）** | 作用域易失控；已按「独立 `form_submission` 表、零运行时 DDL」收口，不侵入业务表 | P3（spike ✅ + 提交落库 ✅） |
| **M23 流程中心增强（M4 续）** | 中 | 中 | ✅ 扩展 Flowable 7.1 监控/版本/超时 | 依赖 M4 既有 claim/transfer/complete | P3 |
| **M24 API 开放平台 / 密钥治理** | 中 | 中 | ✅ 复用 `@RateLimit` + 密钥轮转（M 已有 `JwtKeyRotationService`） | OAuth2 客户端注册可能需 spring-security-oauth2（需离线核验） | P3 |
| **M25 文件管理增强** | 中 | 中 | ✅ 复用既有 `system/file` + 对象存储适配 | 分片/秒传需前端配合；预览需格式库 | P3 → ✅ 已交付（2026-10-25，秒传/引用计数/统计/批量删除） |
| **M26 组织数据导入导出** | 中 | 中 | ✅ 复用导入导出中心模板注册范式（零迁移/零前端） | 导出范围受数据权限约束；部门导入按编码反查父级需树完整 | P3 → ✅ 已交付（2026-10-25，用户/角色/部门/岗位导出 + 岗位/部门导入） |
| **M27 报表大屏（后端四刀 + 前端配套页）** | 高 | 中 | ✅ 手写 jOOQ DSL 聚合系统表计数 + 近 N 日时间序列趋势（零新依赖）；第三刀经 common 跨模块端口范式接入 framework 的 N+1 疑似指标，第四刀同一范式接入 `SpanRecorder` 链路追踪指标（启用/留存/累计/错误），system 始终不依赖 framework | 链路追踪指标已随第四刀交付（端口范式可继续扩展更多维度）；前端配套页（看板页 + M19 租户页）属独立前端仓 | P2 → ✅ 后端四刀已交付（2026-10-26，第一刀运营总览 `/overview` + 第二刀趋势 `/trend?days=` + 第三刀 N+1 疑似指标接入看板 + 第四刀链路追踪指标跨模块接入看板；M27 累计新增 14 例单测（ObservabilitySnapshotTest 5 例 + 第三刀 9 例），全仓 1465→1470 例全绿）；✅ **前端配套页已交付（2026-10-26，pc-admin-vue3-vite）**：看板页 `DashboardView.vue` + 后端 V52 菜单种子（id=1056 挂报表目录 1030，`system:dashboard:read`）+ 路由/i18n 接入；M19 租户前端页 `TenantView.vue` 一并落地（七函数 `tenant.js` + 路由 `system/tenants` + i18n），`npx vite build` EXIT=0 |
| **M28 租户隔离态势诊断页（M19 P3 延伸）** | 中（运维可观测） | 小（只读聚合） | ✅ 复用 M19 P3 的 `TenantEnforcement`/`TenantProperties`/`TenantContext` 静态/配置态，纯函数 `TenantIsolationRules.buildStatus` 聚合，零新依赖 | 仅只读诊断，不写库/不触发隔离逻辑，风险极低 | P3 → ✅ 已交付（2026-10-26）：后端 GET `/api/system/tenant-isolation/status`（权限 `system:tenant-isolation:read` 由 `ApiResourceAutoSyncRunner` 自动登记）+ V54 菜单种子（id=1125 挂系统管理 1002，`system:tenant-isolation:read`）+ 前端诊断页 `TenantIsolationView.vue` + `tenantIsolation.js` + 路由 `system/tenant-isolation` + i18n；新增 8 例单测（common `TenantIsolationRulesTest` 6 + system `TenantIsolationStatusControllerTest` 2），全仓 1483→**1491** 例全绿 + `check-conventions` EXIT=0 + `npx vite build` EXIT=0 |

> 说明：表中 M19–M22 为本轮重点细化候选（与既定规划方向一致）；M23–M25 列入观察清单，待前序模块稳定后再排期。

---

## 3. 推荐落地路线

- **阶段 A（奠基，若 SaaS 为目标）**：M19 多租户隔离。因其侵入全局，应作为独立先行阶段，配套迁移脚本与「平台管理员」跨租户通道。若本期**不**做 SaaS，则将 M19 延后，直接进入阶段 B。
- **阶段 B（治理增强）**：M20 审计增强 → M21 脱敏策略中心。两者共享「审计/脱敏」主题，且 M21 可直接复用 M1 审计落库与 `SensitiveMasker`，协同度高。
- **阶段 C（体验/扩展）**：M23 流程中心增强、M25 文件管理增强（复用既有模块，风险低）。
- **阶段 D（探索）**：M22 低代码表单先以 **spike**（仅契约 + 最小渲染原型）验证可行性，再决定是否正式立项；M24 视是否需要对外_OPEN API_ 决定。

---

## 4. 重点候选细化设计摘要

### 4.1 M19 多租户隔离
- **目标**：同一套部署服务多租户，数据按租户隔离，平台方（super admin）可跨租户治理。
- **隔离策略（二选一，待定）**：
  1. **共享表 + `tenant_id` 列**（推荐起步）：所有业务表加 `tenant_id`；`TenantContext`（ThreadLocal）由登录态/请求头解析；jOOQ `VisitListener` 或统一查询构造器自动追加 `tenant_id = :current` 且禁止 `null` 越权查询；主键策略改为 `(tenant_id, id)` 或全局唯一 ID。
  2. **Schema/库 隔离**：每租户独立 schema，经 `AbstractRoutingDataSource` 按 `TenantContext` 路由（复用既有多数据源 `app.datasources.entries.*` 机制）。
- **关键构件**：`TenantResolver`（从 JWT claims / `X-Tenant-Id` 解析，缺失即拒绝或默认平台租户）、`TenantInterceptor`、`TenantAwareJooqConfig`、`V4x__add_tenant_id.sql`（存量表回填默认租户）、`sys_tenant` 租户表 + 租户管理端点。
- **可行性**：✅ 零新依赖（jOOQ + Spring 原生）。**风险**：既有 ~50+ 表迁移、跨租户统计/报表需显式绕过隔离（平台通道）。**建议**：阶段 A 先行，且先交付「写入路径强制隔离 + 平台跨租户通道」最小闭环。

#### 4.1.1 M19 落地进度（feat/V1.0.4）
- **P1 基础层（已交付）**：`common.tenant` 包——`TenantContext`（ThreadLocal：tenantId + platformAdmin 标志）、`TenantProperties`（enabled / defaultTenantId / platformAdminRoleCode / headerName / claimName）、`TenantResolver`（JWT claim `tid` / 请求头 `X-Tenant-Id` / 默认租户解析，纯函数化）、`TenantRules`（租户 ID 解析归一、编码/名称/状态/联系人格式校验，纯函数）；配套 17 例单测全绿。
- **P2 租户管理域（已交付）**：V51 迁移建 `sys_tenant`（tenant_code 唯一 + 状态机 ENABLED/DISABLED + 可选过期）+ 菜单播种（id=1124 挂系统管理 1002 并绑定 SUPER_ADMIN）；`JooqTables.SYS_TENANT`；`SysTenantDO` + `CreateTenantRequest`/`UpdateTenantRequest`/`TenantView`（tenant_code 创建后不可变更）；`TenantService`（CRUD + 整表缓存写失效 + 结构校验同源 `TenantRules` + tenant_code 唯一性裁决）；`TenantController`（`/api/system/tenants`，读 `system:tenant:read` / 写 `system:tenant:manage` 由 `ApiResourceAutoSyncRunner` 自动登记）；配套 13 例单测全绿。全仓 **1399→1412 例**。**前端配套页 `TenantView.vue` 已于 2026-10-26 在 `pc-admin-vue3-vite` 落地**（七函数 `tenant.js` + 路由 `system/tenants` 挂 `system:tenant:read` + i18n），与 M27 看板页同批交付，`npx vite build` EXIT=0。
- **P3 jOOQ 强制注入隔离（已交付）**：采用「`enforce` 默认关闭、配置开启」设计，确保基线 1412 例与线上行为零回归。`TenantInterceptor`（请求级注入 `TenantContext`）+ `TenantBypassAspect`（拦截 `@TenantIgnorable` 置位跨租户旁路）+ `TenantEnforcement`（静态单点裁决）。**架构 pivot**：放弃 jOOQ `VisitListener`（3.19 API 不兼容：接口为空、无 `getTable()`/`addConditions`、SelectQuery 层无 `getFrom()`，AST 注入不可行），改为在 `JooqWriters`（动态 DSL 唯一 CRUD 入口）内经 `TenantEnforcement` 注入——所有读写路径经私有 `withTenant(table, cond)` 装饰，insert/batchInsert 写入 `tenant_id`；`enforce=false` 时行为与原基线完全一致。`JooqConfig.dslContext` 启动时调 `TenantEnforcement.configure(props)`。`sys_tenant`/`sys_menu`/`sys_dict_type`/`sys_dict_data`/`sys_config`/`sys_api_resource` 等平台维表（`platformTables`）豁免隔离；非请求线程/未设作用域/旁路均不强制。配套 15 例单测（TenantContext 旁路 2 + TenantBypassAspect 2 + TenantInterceptor 5 + TenantEnforcement 决策 6），全仓 **1412→1427 例**。
- **P3 生产可用化（已交付，2026-10-26）**：M19 多租户在 schema（V32 起全表 `tenant_id`）与代码层（`JooqWriters` 注入 / `TenantInterceptor` 解析 / `enforce` 门控 / 41 个 `TableRef` 声明）均已就绪；本轮补齐「安全开启护栏 + 上线指引」，不改变默认行为（`enforce` 仍默认 false，零回归）。`TenantEnforcement.validateConfiguration()`（common）在 `enforce=true` 时校验 `defaultTenantId` 合法、`platform-tables` 非空，否则抛中文 `IllegalStateException`；`TenantEnforcementValidator`（framework，`CommandLineRunner`）启动期调用，配置非法则 fail-fast 中止启动（WARN/INFO 日志区分）。新增 `TenantEnforcementTest` 校验用例 4 + `TenantEnforcementValidatorTest` 3 = 7 例，全仓 **1435→1442 例**；`docs/manual/m19-production-enablement.md` 上线指引。门禁：`clean test` 1442 例全绿 + `check-conventions` EXIT=0。

#### 4.1.2 M28 租户隔离态势诊断页（已交付，2026-10-26）
- **定位**：延伸 M19 P3，补齐「运维只读诊断」能力——把隔离的「配置态 + 运行态 + 当前执行作用域 + 配置校验结果」聚合成单一只读端点，供诊断页展示。**只读、零新依赖、不写库、不触发任何隔离逻辑**，仅做聚合，不复制任何裁决逻辑（唯一事实来源仍是 M19 P3 的 `TenantEnforcement`/`TenantProperties`/`TenantContext`）。
- **后端（common + system）**：
  - `common/tenant/TenantIsolationStatusView`（不可变 record：isolationEnabled/enforceEnabled/configured/defaultTenantId/platformAdminRoleCode/headerName/claimName/platformTables/scopeSet/currentTenantId/currentPlatformAdmin/currentBypass/validationStatus/validationMessage）。
  - `common/tenant/TenantIsolationRules`（纯函数 `buildStatus(props)`：聚合配置态（`TenantProperties`）+ 运行态（`TenantEnforcement.isEnabled()`/`isConfigured()`）+ 当前执行作用域（`TenantContext`）+ 配置校验（包裹 `validateConfiguration()` 得 OK/INVALID/DISABLED 三态，INVALID 含中文修正建议），可离线单测）。
  - `system/tenant/TenantIsolationStatusController`（`GET /api/system/tenant-isolation/status`，`@PreAuthorize system:tenant-isolation:read`；直接实例化可测、无 DB 依赖；权限码由 `ApiResourceAutoSyncRunner` 自动登记）。
- **后端迁移（V54）**：菜单种子 id=1125 挂系统管理 1002、`route_path=/system/tenant-isolation`、`component_path=system/TenantIsolationView`、`permission=system:tenant-isolation:read`、`icon=Connection`、sort_no=114（紧随 1124 的 113）；`INSERT IGNORE` + 绑定 `sys_role_menu(role_id=1)`。纯 SQL 种子，不增后端单测（门禁保持）。
- **前端（pc-admin-vue3-vite）**：`src/views/system/TenantIsolationView.vue`（四区诊断卡：隔离总览 / 配置态 / 当前执行作用域 / 配置校验，复用 `.page-card`/`.stat-grid`/`.stat-card` + `el-tag`/`el-alert`，loading/error/空态齐全）+ `src/api/tenantIsolation.js`（`getTenantIsolationStatus`）+ 路由 `system/tenant-isolation` + i18n（zh-CN `租户隔离态势`/en-US `Tenant Isolation Status`）。
- **测试 8 例**（TenantIsolationRulesTest 6 + TenantIsolationStatusControllerTest 2）；门禁：全仓 **1483→1491** 例全绿 + `check-conventions` EXIT=0 + `npx vite build` EXIT=0（验证目录 `.fe-verify` 已清理不提交）。

### 4.2 M20 操作/审计增强
- **目标**：在 M1 写操作审计基础上，补「读操作审计（可开关/采样）」「审计导出」「留存与清理」「按角色脱敏展示」。
- **构件**：`AuditAspect` 增 `auditReads` 开关 + 采样率；复用既有 `system/export` 注册 `AuditExportTask`（零迁移、零前端，自动进 `/templates`）；`AuditRetentionJob`（按 `app.audit.retention-days` 清理，复用 `IdempotencyCleanupJob` 范式）；审计详情展示经 `SensitiveMasker` 按角色决定明文/掩码。
- **可行性**：✅ 高，主要增量。**风险**：读审计量需采样避免写放大。

### 4.3 M21 数据脱敏策略中心（可视化配置）
- **目标**：将当前「代码驱动 + 安全默认掩码」升级为「运行时可配置脱敏策略」——按 字段/端点/角色 组合决定脱敏类型与可见范围，DB 存规则，管理 UI 配置。
- **构件**：`sys_mask_strategy` 规则表（`scope`=全局/端点/字段、`target`=字段表达式、`sensitive_type`、`visible_roles`）+ `MaskStrategyService`（加载/缓存/热刷新）+ `MaskStrategyResolver`（在序列化/导出前按当前角色命中规则并委托 `SensitiveMasker`）+ 管理端点与前端表单。**默认安全**：无任何规则时回落到既有类型驱动默认掩码，未授权角色一律掩码。
- **可行性**：✅ 复用 `common/sensitive`，零新依赖。**风险**：规则解析性能（需缓存 + 兜底）。**与 M20 协同**：审计详情脱敏可直接消费本中心策略。

### 4.4 M22 低代码/动态表单引擎
- **目标**：以 JSON Schema 描述表单（字段、校验、布局、数据源绑定），前端按 schema 渲染，后端按 schema 校验并落库（绑定既有表或通用 `form_submission` 表）。
- **阶段一 · spike（V47，已交付）**：`FormDefinition`（`code` + JSON schema + 绑定目标）+ `FormRenderController`（`/schema/{code}`）+ 前端 `DynamicForm.vue`（schema→element-plus 渲染）。**决策点结论**：① 落库目标选 **独立 `form_submission` 表**（不侵入业务表、不做运行时 DDL）；② 渲染采用**零依赖手写最小渲染器**。
- **阶段二 · 正式立项提交落库（V53，已交付 2026-10-26）**：`sys_form_submission` 表 + `FORM_SUBMISSION` TableRef 注册；`FormSubmissionDO`/`FormSubmissionView`/`FormSubmissionRules`（按 schema 校验：必填/类型/长度/选项白名单/未知字段拒绝）；`FormSubmissionService`（校验通过才 `JooqWriters.insert` 落库、分页查询）+ `FormRenderController` 增 `POST /{code}/submit` 与 `GET /{code}/submissions`（权限 `system:form-submission:create/read` 由 `ApiResourceAutoSyncRunner` 自动登记并授予 SUPER_ADMIN）；前端 `FormPilotView.vue` 增「提交」与「提交记录」页；新增 13 例单测（rules 10 + service 3），全仓 1470→**1483 例**全绿。
- **可行性**：✅ 已按「独立提交表 + 纯函数校验 + 端口/动态 DSL 范式」收口，作用域受控、零新依赖。

---

## 5. 横切约束（所有 M19+ 须遵守）

1. **离线优先**：仅用离线仓库已缓存依赖；需联网引入的（OAuth2、schema 渲染库等）先在本机/联网环境核验再立项。
2. **数据访问范式**：手写 jOOQ DSL（`JooqTables` + `TableRef`），不引 ORM；多租户隔离亦走 jOOQ 拦截，不破坏既有 DSL。
3. **纯函数规则**：校验/策略/转换逻辑沉淀到 `*Rules` 纯函数类（如 `MaskStrategyRules`、`TenantRules`），单测覆盖，与 M1–M18 一致。
4. **条件装配 + 权限自动登记**：新端点经 `@ConditionalOnProperty` 可控开启；权限码由 `ApiResourceAutoSyncRunner` 自动登记（参照 M7/M8/幂等记录）。
5. **零迁移/零前端膨胀优先**：能复用「模板注册 / 自动列举」模式（导入导出中心、导出任务）的，不新增页面与迁移。
6. **测试门禁**：每模块落地须配单测（规则纯函数 + 服务 + E2E 桩），保持 `mvn -o clean test` 全绿、`scripts/check-conventions.py` EXIT=0；新模块纳入 JaCoCo `-Pcoverage` 门禁。

---

## 6. 开放问题 / 待确认决策

请在评审时确认以下决策点（将决定 M19+ 的起始模块与顺序）：

1. **是否以 SaaS 多租户为本期目标？** 若是 → M19 先行（阶段 A）；若否 → M19 延后，直接从 M20/M21 治理增强起步。
2. **M19 隔离策略选「共享表 + tenant_id」还是「Schema 隔离」？** 前者迁移轻、跨租户统计易；后者隔离强、但存量与路由复杂。
3. **M22 低代码是否本期正式立项，还是仅做 spike？** ✅ 已决议：先 spike（V47）验证可行性，随后正式立项提交落库（V53 `form_submission` 表 + schema 校验 + 前后端提交/记录），均已完成。
4. **M24 API 开放平台是否本期需要？** 仅当需要对外暴露_OPEN API_ 时立项（涉及 OAuth2 离线核验）。
5. **优先级与排期**：上述 P1/P2/P3 是否与业务方预期一致？是否需插入其他候选（如报表大屏、任务依赖编排）？

> **已确认决策（2026-10-23）**：
> 1. **M19 多租户延后**：本期不做 SaaS 隔离；隔离策略已倾向「共享表 + `tenant_id` 列」（jOOQ 拦截强制隔离），留作未来立项基线，无需本期投入。
> 2. **本期先做治理增强（阶段 B）：M20 审计增强 → M21 脱敏策略中心**。两者协同度高（M21 复用 M1 审计落库与 `SensitiveMasker`）。
> 3. **M22 低代码已交付**：spike（V47 契约+渲染原型）+ 正式立项提交落库（V53 `form_submission` 独立表，按 schema 校验后落库、分页查询、前后端提交/记录页）均已完成，全仓 1470→1483 例全绿。
> 4. **M24 API 开放平台本期需要**：涉及 OAuth2 客户端注册与密钥治理，立项前需离线核验 `spring-security-oauth2` 等依赖可用性。
>
> **M24 离线核验结论（2026-10-25）**：离线仓库仅有 `spring-security-oauth2-core/jose/resource-server` 6.3.3（与 Boot 3.3.4 匹配），**不含** `spring-security-oauth2-client`（`RegisteredClient`/`RegisteredClientRepository` 所在）与 `spring-security-oauth2-authorization-server`（授权服务器所在）——完整 OAuth2 授权服务器（authorization code / `/oauth2/token`）离线不可建。据此 M24 收口为**自包含的「客户端注册 + 密钥治理」**（自有领域模型 + 复用 `SecurityConfig` 的 `DelegatingPasswordEncoder` 哈希，零新增依赖），V48 `sys_openapi_client` 已落地；协议级开放（token 端点、资源服务器过滤器）待联网补齐依赖后另行立项。
>
> **M23 流程中心增强已交付（2026-10-25）**：随 M20/M21/M22/M24 之后落地——V49 迁移零表结构变更（仅播种菜单 id=1123），`WorkflowEnginePort` 扩展超时任务分页与定义版本链查询，`WorkflowMonitorService` 提供运行实例/超时任务/统计摘要只读面 + 催办唯一写操作（实时推送 + 最小间隔防轰炸，催办记录复用 `wf_approval_record`），前端 `MonitorView.vue` 三 Tab。
>
> **M25 文件管理增强已交付（2026-10-25）**：观察清单最后一项候选落地，复用既有 `system/file` 零新页面膨胀——V50 迁移为 `sys_file` 幂等增加 `content_hash CHAR(64)` + `idx_content_hash`（存量行 hash 为 NULL 不参与去重，不设默认值避免全表回填）；`FileDedupRules` 纯函数（SHA-256 十六进制计算与 64 位十六进制归一、id 保序去重、引用计数删物理判定 `refCount<=1`）+ `FileService` 扩展：上传第三流算 contentHash，命中且物理文件仍在则复用 `stored_name` 不落盘（二次校验 `FileStorageService.exists`，孤儿记录回退正常落盘），删除前 `COUNT(*) WHERE stored_name=? AND deleted=0`（含自身）>1 时仅逻辑删记录、保留物理文件防误删共享文件，`stats()` 按 `source_module` groupBy 聚合（空表自然零值），`batchDelete` 100 上限 + 部分成功语义（私有 `deleteOne` 规避 Spring 自调用不走代理，@Transactional 内单项失败不传播即不回滚）；`FileController` 增 `GET /dedup-check`、`GET /stats`、`POST /batch-delete`（复用既有权限码不新增）；`FileView.deduplicated` 双重语义（上传响应=本次命中秒传 / 列表行=物理文件共享组，由一次 `GROUP BY stored_name IN (本页名集合)` 标记）；前端 `FileManagementView.vue` 增存储统计卡、批量删除、秒传列与 Web Crypto 上传预检（`crypto.subtle` 仅安全上下文可用，非安全上下文静默降级不阻断）；新增 21 例单测，全仓 **1318→1339 例**全绿。
>
> **M26 组织数据导入导出已交付（2026-10-25）**：在既有导入导出中心「模板注册」范式（Spring Bean 自动发现、前端两中心页从 `/templates` 动态拉取、新任务零前端改动）上新增组织域四导出两导入——`UserExportTask`（10 列，翻页 PAGE_SIZE=200 × MAX_ROWS=10000 截断 + 筛选透传）/ `RoleExportTask`（6 列全量）/ `DeptExportTask`（7 列，`TreeBuilder.flatten` 深度优先父先子 + 上级回显 + 数据权限过滤占位）/ `PostExportTask`（6 列翻页 + 筛选）；`PostImportTask`（4 列无状态列，编码形态预校验，固定 ACTIVE）/ `DeptImportTask`（4 列，按上级部门编码经 `TreeBuilder.flatten` 建 code→id 映射反查父级，缺失 NOT_FOUND 且不写入，与导出闭环「先导出改完再导入」）；`TreeBuilder` 新增泛型 `flatten(roots, childrenOf)` 与 `build` 互逆；导出范围=列表页可见范围（`UserService.list`/`OrgService.tree` 的 `@DataScope` 在导出线程内同样生效）；新增 43 例单测，全仓 **1339→1382 例**全绿。至此 M19+ 已采纳路线中的 M20/M21/M22(spike)/M23/M24/M25/M26 全部交付；M19 多租户按既定决策延后，留作未来立项基线。
>
> 后续：将 M20 / M21 / M22(spike) / M24 拆为 `module-plan` 形式的 Mxx 任务卡（范围/契约/迁移/测试/门禁），按关注点分批在 `feat/V1.0.3` 落地；本文件已更新为「已采纳路线」。
>
> **M22 低代码表单 · 正式立项提交落库已交付（2026-10-26，V53）**：在 V47 spike（契约 + 渲染原型）验证可行后，正式收口「独立 `form_submission` 表、零运行时 DDL」决策——V53 迁移建 `sys_form_submission`（含 `data_json` 整段提交值序列化 + `target_type` 沿用 `FORM_SUBMISSION`）；`JooqTables.FORM_SUBMISSION` 注册；`FormSubmissionDO`/`FormSubmissionView`/`FormSubmissionRules`（按 schema 字段校验：必填/类型 INPUT·SELECT·SWITCH·TEXTAREA·DATE / 长度 min·max / 选项白名单 / 未知字段拒绝）；`FormSubmissionService`（校验通过才 `JooqWriters.insert` 落库、分页查询 mapping）+ `FormRenderController` 增 `POST /{code}/submit` 与 `GET /{code}/submissions?page=&size=`（权限 `system:form-submission:create/read`，`ApiResourceAutoSyncRunner` 自动登记并授予 SUPER_ADMIN）；前端 `FormPilotView.vue` 增「提交」按钮（校验后提交）与「提交记录」分页列表，更新 hint 为 M22 已正式立项；新增 13 例单测（rules 10 + service 3，mockStatic(JooqWriters) 离线可跑），全仓 **1470→1483 例**全绿 + check-conventions EXIT=0。M22 至此 spike 与正式立项均交付。
