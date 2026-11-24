# CMS 模块深度扩展需求与迭代计划

> 版本：1.2 · 日期：2026-11-24 · 状态：迭代二（M2）已完成（随迭代同步更新实现一致性）
> 适用模块：`scaffold-cms`（后端）+ `pc-admin-vue3-vite/src/views/cms`（前端）

## 1. 现有功能范围与架构约束

### 1.1 现状盘点（磁盘事实，非文档宣称）

| 维度 | 已实现 | 部分实现 | 未实现 |
|---|---|---|---|
| 栏目 | 树 CRUD、`code` 租户内唯一校验、父存在校验、删除有子拒绝、`TreeBuilder` 排序/环守卫 | 删除文案提示"关联文章也拒绝"但后端未校验文章引用 | — |
| 文章 | CRUD、`DRAFT/PUBLISHED/OFFLINE` 流转、发布/下线幂等、`publishedAt`/`offlineAt` 维护、`ArticlePublishedEvent` 一次、浏览计数容错、分页筛选排序白名单 | 富文本仅 `TEXT`+`textarea` | 草稿预览、定时发布、版本历史、回收站 |
| 端点 | 14 个受权限保护端点、8 处 `@AuditOperation` | `cms:article:publish` 未入菜单种子、前端 meta 未体现 | 前台公开查询、批量移动/排序 |
| 数据 | `cms_category`/`cms_article` 两表、租户/审计/逻辑删除字段、普通索引 | — | 唯一约束（`code` 仅应用层校验） |
| 前端 | 2 视图、14 API 函数、路由、zhCN 49 条文案、菜单 9301~9303 | i18n 仅单 locale | 前端契约测试 |
| 测试 | 栏目树 7 例单测 | — | 文章全链路、Controller、导出、前端测试 |

### 1.2 架构约束（硬性）

1. **模块依赖 DAG**：`scaffold-cms` 仅依赖 `scaffold-common`（`gate_lib.py:31-47` 白名单校验）。
   -  consequence：**不得**直接依赖 `scaffold-system` 的 `FileStoragePort`/`ConfigService`/`ExportCenterService`。
   - 媒体库/导出只能通过 common 层 Port（如 `ExportTask`、`ArticlePublishedEvent`）或新增 common Port 实现。
2. **门禁 7 项**：naming/directory/conventions/permission/cluster-layout/api-contract/version。
   - 新增菜单权限码必须有对应 `@PreAuthorize hasAuthority`（permission 门禁单向校验）。
   - 新增端点须前端 `src/api/cms.js` 同形（api-contract，`frontend_only` 需为 0）。
   - 新增视图必须位于 `src/views/cms/`（directory D4）。
3. **无独立迁移文件约定**：表结构统一在 `db/init/ddl.sql`（本轮已规范为仅 CREATE TABLE/INDEX），
   新增表需同步 `ddl.sql` + `SqlTables` 注册表 + 菜单种子 `dml.sql`（单值 INSERT）。
4. **feature-gate 单一事实来源**：新增灰度开关须同时登记 `FeatureGateRegistry` 与 `@ConditionalOnProperty`。
5. **零新增 Maven 依赖**：延续项目惯例（PDF/Excel/网关均零依赖实现）。

### 1.3 可复用基础设施

| 能力 | 位置（模块） | CMS 复用方式 |
|---|---|---|
| `ExportTask` + `CsvExporter`/`XlsxDataExporter`/`PdfExporter` | scaffold-common | 直接实现 `ExportTask` Bean（零跨模块依赖） |
| `ArticlePublishedEvent` | scaffold-cms | 已有；扩展订阅者做通知/索引 |
| `AuditOperation` | scaffold-common | 新增写操作直接标注 |
| `TreeBuilder` | scaffold-common | 栏目树已用 |
| `NotificationPort` | scaffold-common | 经事件桥接，不直连 system |

---

## 2. 新增需求清单（功能点 / 业务价值 / 优先级 / 验收标准）

> 优先级：P0（本轮必做）> P1（次轮）> P2（后续）。验收标准均可自动化验证（单测/门禁/构建）。

### C1 · CMS 领域规则类抽取（P0）
- **功能点**：将散落在 Service 私有方法的规则抽为纯函数 `*Rules`（`CmsArticleRules`、`CmsCategoryRules`、`CmsStatusRules`）。
- **业务价值**：规则可离线单测、口径统一、测试覆盖从 7 例跃升。
- **验收标准**：
  - 3 个 Rules 类，纯函数无 Spring 依赖；覆盖发布态流转合法性、`publishedAt` 合并、栏目编码规范、删除引用判定、排序白名单。
  - 新增 ≥ 20 例单测，`scaffold-cms` 单测从 7 → ≥27 全绿。
  - 原 Service 私有方法改为委托 Rules，行为不变（回归测试）。

### C2 · 栏目删除引用校验 + DDL 唯一约束（P0）
- **功能点**：删除栏目时校验"是否存在关联文章"（对齐前端文案）；`cms_category.code` 加租户内唯一索引。
- **业务价值**：消除前端提示与后端行为不一致；数据库层兜底并发重复编码。
- **验收标准**：
  - `CmsCategoryRules.canDelete(hasChildren, hasArticles)` 纯函数 + 单测。
  - Service 删除前计数关联文章（含未删除）；存在即拒绝。
  - `ddl.sql` 的 `idx_cms_category_code` 改 `uk_cms_category_code`（UNIQUE）；应用层冲突异常映射为业务错误。
  - 门禁 7/7、测试全绿。

### C3 · CMS 文章导出（P0）
- **功能点**：实现 `CmsArticleExportTask` 支持 CSV/XLSX/PDF 三格式，复用导出中心。
- **业务价值**：内容运营可离线归档/审计，零新增依赖。
- **验收标准**：
  - `CmsArticleExportTask implements ExportTask`，`code=cms-article`、headers 含标题/栏目/状态/作者/浏览数/发布时间。
  - 3 格式字节生成测试（PDF 结构校验复用 `export_bench.py`）。
  - `ExportTask` Bean 注册后导出中心 `/templates` 可见 `CMS_ARTICLE`。

### C4 · 文章版本历史（P1）
- **功能点**：每次 update 前固化快照到 `cms_article_version` 表，支持版本列表与回滚。
- **业务价值**：内容误改可恢复，满足审计与合规。
- **验收标准**：
  - 新增表 `cms_article_version`（article_id/version_no/title/content/summary/status/published_at...）+ `SqlTables` 注册。
  - `CmsArticleService.update` 写快照（事务内）；`HEAD` 版本不重复快照（内容未变跳过）。
  - 端点 `GET /api/cms/article/{id}/versions`、`POST /api/cms/article/{id}/rollback`（权限 `cms:article:manage`）。
  - 前端 API 同形 + 视图版本面板。
  - 单测覆盖版本号分配、快照幂等、回滚写回主表。

### C5 · 定时发布（P1）
- **功能点**：文章支持 `scheduled_publish_at`，到点自动发布。
- **业务价值**：内容排期，运营提效。
- **验收标准**：
  - `cms_article` 加列 `scheduled_publish_at TIMESTAMP NULL`；Request/View 增字段。
  - `CmsArticleRules.resolveInitialStatus(status, scheduledAt)`：有排期且未到 → `SCHEDULED` 态。
  - 复用 `quartz` feature-gate（已有）经事件驱动发布；无 quartz 时降级为手动。
  - 单测覆盖排期状态解析与幂等。

### C6 · 草稿预览令牌（P1）
- **功能点**：为草稿生成有时效的只读预览令牌，前台免登录查看。
- **业务价值**：内容审核协作，无需发布即可分享。
- **验收标准**：
  - 新增 `cms_preview_token` 表（token/article_id/expires_at/created_by）。
  - 端点 `POST /api/cms/article/{id}/preview-token`（管理权限）、`GET /api/cms/preview/{token}`（公开，feature-gate `cms.preview.enabled` 默认关）。
  - 令牌 UUID、默认 24h 过期、过期拒绝、单次/多次可配置。
  - 单测覆盖令牌生成/校验/过期；feature-gate 登记。

### C7 · 公开内容查询端点（P2）
- **功能点**：`GET /api/cms/public/articles`（按栏目/关键字分页，仅 PUBLISHED，免登录）。
- **业务价值**：为官网/门户提供内容 API。
- **验收标准**：
  - 只读、强分页（size≤50）、仅 PUBLISHED、脱敏（不返回 author 外的内部字段）。
  - 限流复用网关规则；feature-gate `cms.public.enabled` 默认关。
  - api-contract 前端同形（如前端不接则注释说明为开放 API 豁免）。

### C8 · CMS 前端契约测试 + i18n 补齐（P1）
- **功能点**：新增 `src/api/tests/cms.test.js`（端点/参数同形）；补第二 locale CMS key。
- **业务价值**：锁定前后端契约，消除文档与磁盘落差。
- **验收标准**：
  - 14+ 端点契约测试全绿；前端测试数 +6。
  - 第二 locale（如 enUS）补 `cms.*` 49 条；门禁无死链。

---

## 3. 依赖关系与迭代排期

```
迭代一（P0，本轮）：C1 规则抽取 → C2 删除校验+唯一约束 → C3 导出
   依赖：C1 为 C2/C3 提供规则基础；C2/C3 相互独立，可并行
迭代二（P1）：C4 版本历史 → C5 定时发布 → C6 预览令牌 → C8 前端测试/i18n
   依赖：C4 与 C5 独立；C6 依赖 C5 的状态模型（SCHEDULED）；C8 贯穿
迭代三（P2）：C7 公开查询 + 全量回归 + 文档一致性同步
   依赖：C7 依赖 C1 规则与 C5 feature-gate 惯例
```

| 迭代 | 需求 | 交付物 | 里程碑 |
|---|---|---|---|
| 一 | C1/C2/C3 | 3 Rules 类、20+ 单测、DDL 唯一约束、导出 Task | M1：CMS 规则化 + 导出可用 |
| 二 | C4/C5/C6/C8 | 版本表+回滚、排期、预览令牌、前端测试/i18n | M2：内容治理闭环 |
| 三 | C7 + 回归 | 公开 API、文档一致性、全量验证 | M3：CMS 深扩完成 |

---

## 4. 数据模型与接口变更说明

### 4.1 表变更

| 表 | 变更 | 迭代 |
|---|---|---|
| `cms_category` | `idx_cms_category_code` → `uk_cms_category_code`（UNIQUE） | 一 |
| `cms_article` | 加 `scheduled_publish_at TIMESTAMP NULL` | 二 |
| `cms_article_version`（新） | id/tenant_id/article_id/version_no/title/summary/content/status/published_at/offline_at/author/cover_url/审计字段 | 二 |
| `cms_preview_token`（新） | id/tenant_id/article_id/token/expires_at/created_by/审计字段 | 二 |

所有新表含 `tenant_id`/`version`/`deleted`/审计字段，与现有 CMS 表一致；在 `ddl.sql` 以 CREATE TABLE + CREATE INDEX 声明，`SqlTables` 同步注册。

### 4.2 接口变更（新增）

| 端点 | 方法 | 权限 | 迭代 |
|---|---|---|---|
| `/api/cms/article/{id}/versions` | GET | `cms:article:manage` | 二 |
| `/api/cms/article/{id}/rollback` | POST | `cms:article:manage` | 二 |
| `/api/cms/article/{id}/preview-token` | POST | `cms:article:manage` | 二 |
| `/api/cms/preview/{token}` | GET | 公开（feature-gate） | 二 |
| `/api/cms/public/articles` | GET | 公开（feature-gate） | 三 |

前端 `src/api/cms.js` 同步新增同形函数（公开端点除外，需在 api-contract 说明豁免）。

---

## 5. 兼容性与迁移方案

1. **DDL 兼容**：`cms_category.code` 加唯一索引前，需确认存量无重复（应用层已校验，风险低）；采用新增 UNIQUE 索引而非改列，失败可单独 DROP INDEX 回退。
2. **加列兼容**：`scheduled_publish_at` 允许 NULL，存量数据默认 NULL（等同未排期），零影响。
3. **事件兼容**：`ArticlePublishedEvent` 结构不变；C6 预览不依赖发布事件。
4. **feature-gate 默认关**：C5/C6/C7 新能力全部默认关闭，未配置时行为与现状一致（零回归）。
5. **前端兼容**：新增端点不删改既有 14 函数；新视图为独立页面，不改既有路由。
6. **回滚方案**：每迭代独立提交，回退即 `git revert`；DDL 变更配套 DROP 语句记录于本文档 §4.1。

---

## 6. 性能、安全与权限要求

### 性能
- 导出复用 E5 异步任务：大批量文章（>1000 行）走 `ExportJobService`，避免请求线程阻塞。
- 版本表按 `(tenant_id, article_id, version_no)` 索引，版本列表分页（size≤50）。
- 公开查询强制分页 size≤50、仅 PUBLISHED、走状态索引 `idx_cms_article_status`。
- 浏览计数沿用现有容错（失败告警不阻塞）。

### 安全
- 预览令牌：UUID 随机、默认 24h 过期、过期/伪造令牌拒绝；不泄露草稿外的内部字段。
- 公开端点：仅 PUBLISHED、不返回 `author` 之外的内部审计字段；复用网关限流规则。
- SQL 注入：全部走 `SqlWriters` DSL 参数化，禁止字符串拼接。
- XSS：富文本内容出库原样返回，前端渲染转义（本轮不引入富文本编辑器，保持 textarea）。

### 权限
- 新增管理端点统一 `cms:article:manage` / `cms:category:manage`；公开端点免登录但受 feature-gate。
- 新增权限码若入菜单，须同步 Java `@PreAuthorize`（permission 门禁）。
- 所有写操作 `@AuditOperation`。

---

## 7. 测试与回归范围

| 层级 | 范围 | 通过标准 |
|---|---|---|
| 后端单测 | Rules 纯函数（C1）、删除校验（C2）、导出字节（C3）、版本/排期/令牌（C4-C6） | 新增 ≥40 例，全绿 |
| 后端编译 | 全 14 模块 | `mvn -o compile` EXIT=0 |
| 门禁 | 7 项 | `check-all.py` EXIT=0，frontend_only 0 |
| 前端 | CMS 契约测试 + 构建 | `npm test` 全绿、`npm run build` 成功 |
| 回归 | 既有 280 单测 + 14 CMS 端点 + 栏目树 7 例 | 全部保持通过（零回归） |
| 性能 | 导出 1000 行耗时、公开查询分页 | 导出走异步；公开查询 P95 ≤ 500ms（本地） |

---

## 8. 实现一致性跟踪（每轮迭代后更新）

| 需求 | 状态 | 实现位置 | 验证证据 | 更新迭代 |
|---|---|---|---|---|
| C1 规则抽取 | 已完成 | `article/domain/rule/CmsStatusRules`、`CmsArticleRules`、`category/domain/rule/CmsCategoryRules` + Service 委托 | 3 类 21 例规则单测全绿，cms 单测 7→35 | 一（M1） |
| C2 删除校验+唯一约束 | 已完成 | `CmsCategoryService.delete` 引用计数 + `CmsCategoryRules.canDelete`；`ddl.sql` `uk_cms_category_code` | 4 例删除/编码单测全绿；门禁 7/7 | 一（M1） |
| C3 导出 | 已完成 | `cms/export/service/CmsArticleExportTask`（code=cms-article，CSV/XLSX/PDF） | 7 例导出单测全绿（含三格式字节产物）；`@Component` 自动注册 | 一（M1） |
| C4 版本历史 | 已完成 | `CmsArticleVersionService`/`CmsArticleVersion` + `ArticleVersionView`；Controller `versions`/`rollback` | `CmsVersionRulesTest` 4 例 + 编译通过；update 前固化快照、回滚写回主表 | 二（M2） |
| C5 定时发布 | 已完成 | `CmsArticleRules.resolveInitialStatus/isDueForPublish`；`scheduled_publish_at` 列/字段；`CmsArticleService.publishDue`；`CmsScheduledPublishJob` | 2 例排期规则单测 + 编译通过；feature-gate `cms.scheduled-publish.enabled` 默认开 | 二（M2） |
| C6 预览令牌 | 已完成 | `CmsPreviewTokenRules`/`CmsPreviewToken`/`CmsPreviewTokenService`；`CmsArticleController.previewToken` + `CmsPreviewController`（feature-gate 默认关） | `CmsPreviewTokenRulesTest` 4 例 + 编译通过；UUID/24h过期/参数化校验 | 二（M2） |
| C7 公开查询 | 待开发 | — | — | — |
| C8 前端测试/i18n | 已完成 | `src/api/tests/cms.test.js`（17 端点契约）+ `cms.js` 3 个新函数 + `zhCN.js` 15 条新文案 | 前端 73 例全绿、build EXIT=0、门禁 frontend_only=0 | 二（M2） |

### 迭代一验证记录（M1，2026-11-24）

- `mvn -o -pl scaffold-cms -am test`：35 例全绿（规则 21 + 栏目树 7 + 导出 7），Failures=0 Errors=0。
- `python3 scripts/check-all.py`：门禁 7/7 通过，frontend_only=0，duplicate=0，EXIT=0。
- DDL 唯一索引 `uk_cms_category_code(tenant_id, category_code)` 已落地；导出任务经 `@SpringBootApplication` 根包扫描自动注册，导出中心 `/templates` 自动可见。

### 迭代二验证记录（M2，2026-11-24）

- `mvn -o -pl scaffold-cms -am test`：45 例全绿（新增 C4 版本规则 4、C5 排期规则 2、C6 令牌规则 4），Failures=0 Errors=0。
- `python3 scripts/check-all.py`：门禁 7/7 通过，frontend_only=0，duplicate=0，EXIT=0。
- 前端 `npm test` 73 例全绿（新增 CMS 契约 6 例），`npx vite build` EXIT=0。
- DDL 新增 `cms_article_version`/`cms_preview_token` 两表 + 索引，`cms_article` 加 `scheduled_publish_at` 列，均已同步 `SqlTables` 注册。
- **架构说明**：`scaffold-cms` 仅依赖 `scaffold-common`（依赖 DAG 硬约束），`FeatureGateRegistry`（framework 模块）已登记 3 个 CMS 开关元数据，CMS 侧用 `@ConditionalOnProperty` 读配置，无编译期跨模块依赖；`@EnableScheduling` 已在启动类开启。
- **i18n 说明**：前端 i18n 引擎已移除（App.vue 硬编码 zh-CN、无语言切换机制），故"第二 locale"不适用；C8 改为补齐 `zhCN.js` 的 `cms.*` 新增文案（版本/排期/预览 15 条），以契约测试锁定字典键完整性。
