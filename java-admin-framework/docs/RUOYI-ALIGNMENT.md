# 若依约定对齐说明

> 本文件记录 `java-admin-framework` / `pc-admin-vue3-vite` 与若依（RuoYi-Vue）
> 项目结构、模块边界、命名规范、通用后台能力约定的差异与处理结论。
> 若依结构取自官方项目结构文档；本仓既有约定取自 `scripts/check-conventions.py`、
> `scripts/api-contract.json` 与 `docs/MODULES.md`。
>
> **硬约束**：保持现有业务功能、数据兼容、接口行为、外部调用行为不变；沿用既有重构约定；
> 避免无关功能重写。凡会改 HTTP 路径 / 参数 / 响应字段、数据库表列、Spring Bean 名称与
> 组件扫描结果、菜单 / 路由、前端可见行为，或需大规模类搬迁的项，一律**只说明、不落地**。

## 1. 五维对照总表

| 维度 | 若依约定 | 本仓现状 | 本轮处理 | 结论 |
|---|---|---|---|---|
| 组织方式 | common / framework / system / quartz / generator / admin；父 POM 仅聚合与版本管理；admin 唯一 Web 入口 | **14 个** `scaffold-*` 模块（1 父 POM + 14 子模块 = 15 个 reactor 项）；admin 已是唯一启动模块；父 POM 已聚合 + 版本管理 | 修 `docs/MODULES.md` 的模块数口径与「规划中未创建」漂移；父 POM 收敛 `zstd-jni` 版本；新增 `docs/DIRECTORY.md`；补 5 份 `package-info.java` | 部分对齐，结构差异保留 |
| 职责边界 | 模块内 domain / mapper / service 分层；framework 集中全局装配 | 扁平特性子包；framework 类散在 `config`/`observability`/`monitor.*`/`chaos` | 用包级说明 + 目录规范写明边界与例外，不改包名 | 文档对齐，代码保留 |
| 公共能力 | `common/constant` 常量包、`common/core/domain` 响应基类、`common/utils` 工具包 | 已有 `api`/`error`/`text`/`tree`/`util` 等单一事实来源，但**无 constant 包**，字面值散落 16 处 | 新增 `common/constant/CommonConstants`，16 处存量改委托且值逐字不变；新增 `CommonConstantsTest`（历史测试，已随测试源码移除） | 已对齐 |
| 配置管理 | admin 集中 `application.yml` / `application-druid.yml` / `logback.xml`；配置类集中 common.config / framework.config | admin 已集中 yml 与 logback；43 个 `@ConfigurationProperties` 分散在 11 个模块特性包 | 生产 yml 旧 `jobrunr:` 段已随 JobRunr → Quartz 迁移删除；配置类归属写入目录规范，不搬迁 | 部分对齐，配置类保留 |
| 交互规范 | AjaxResult / TableDataInfo / BaseController.startPage；`@Log`/`@DataScope` 注解；`src/api` 集中请求 + 统一 axios 拦截器拆包 | `Result<T>` 包络 + `PageResult`/`KeysetPageResult` + `PagingRules`；`api-contract.json` 为字段口径唯一事实来源，`check-api-contract.py` 做静态路由对称；前端 `src/api/http.js` 统一注入鉴权头与拆包 | **不改**：响应体改名会破坏契约；仅把前端根级 `styles.css` 归位到 `styles/base.css` | 保留差异，路径归位 |

> **模块数口径复核（2026-11-10）**：对比报告批次 6 曾记本文件「模块数 17≠14」。
> 已复核消解：根 POM 为 **14 个 `<module>`**（1 父 POM + 14 子模块 = 15 个 reactor 项），
> 上表口径正确；`docs/MODULES.md` 与 `docs/DIRECTORY.md` §2 均以此为唯一事实来源。

## 2. 本轮已落地项（零契约风险）

> 历史快照：下表中的 `CommonConstantsTest` 等测试类已于 `087acdb` 随测试源码全量移除，
> 仅记录当时的落地动作，不代表当前仍有对应测试资产。

| # | 维度 | 改动 | 行为影响 |
|---|---|---|---|
| 1 | 公共能力 | 新增 `common/constant/CommonConstants.java` | 纯新增常量持有者，无运行时副作用 |
| 2 | 公共能力 | `PagingRules` / `PageQuery` / `KeysetPageQuery` / `Result` / `FormSubmissionService` / `ReportService` / `JobCreateDTO` / `JobUpdateDTO` / `RuntimeMetricsCollector` / `NotificationDigestService` / `AuthService` / `ProfileDeviceService` / `WebConfig` / `ProfileController` 共 16 处字面值改委托 | 分页上限、时间格式、头像前缀、成功码逐字不变；公共常量字段保留 |
| 3 | 组织方式 | 新增 5 份 `package-info.java`（`common`、`common.constant`、`common.api`、`config` 等） | 仅 Javadoc；2026-11-08 原 `jooq` 包已并入 `mybatis.dsl`，该处 package-info 随包归并删除 |
| 4 | 组织方式 | 新增 `docs/DIRECTORY.md`；重写 `docs/MODULES.md`；修 `docs/README.md` 索引漂移 | 纯文档，修复死链与过时口径 |
| 5 | 配置管理 | 父 POM 增 `zstd-jni.version` **并在 `dependencyManagement` 注册该依赖**；`scaffold-kafka/pom.xml` 去掉硬编码 `<version>` | 解析到的版本仍是 `1.5.5-11`；定向构建曾报 `version is missing`，补齐注册后复验通过 |
| 6 | 配置管理 | `application-prod.yml` 旧 `jobrunr:` 段随 Quartz 迁移删除 | 运行时已无该配置前缀 |
| 7 | 组织方式 | `src/styles.css` → `src/styles/base.css`，`main.js` import 与 8 处注释同步 | CSS 内容与加载顺序不变，仅路径归位 |
| 8 | 公共能力 | 新增 `CommonConstantsTest`（历史测试，已移除） | 当时用于锁定字面值与委托一致性 |

## 3. 明确保留的若依差异（尚未对齐）

| 差异 | 为何不改 | 影响 |
|---|---|---|
| `IXxxService` + `XxxServiceImpl` 命名 | 本仓用单实现类 + `*Port` 防腐层；2026-11-10 已把仅存的 2 处「接口 + Impl」归一为单类（FQN 不变），不引入 `I` 前缀 | 仅命名风格，已收敛 |
| `AjaxResult` / `TableDataInfo` / `BaseController` | `Result<T>` 由 `api-contract.json` 固定为字段口径唯一事实来源；替换会改响应 JSON 字段 | **契约级**，不可改 |
| Controller 集中到 `scaffold-admin` | 本仓 Controller 随业务模块分布；搬迁会改组件扫描与 14 模块依赖方向 | 部署结构差异 |
| `com.acme.scaffold.framework` 包根 | 历史类分布在 4 个顶层包；重命名牵动数百处 import | 形式差异 |
| `common.mybatis` 第二包根 | 同上 | 形式差异（2026-11-08 起为唯一的数据访问包根） |
| 43 个 `@ConfigurationProperties` 分散在特性包 | 集中会改配置类包路径与 `@EnableConfigurationProperties` 引用 | 组织差异，功能等价 |
| `src/api` 69 文件平铺（若依按模块分目录） | 需同步改 69 个文件与全部 import | 大规模搬迁 |
| `src/api/http.js` 与 `apiDoc.js` 两个 axios 实例 | 后者 baseURL 为 `/`、独立超时与鉴权逻辑，仅用于拉 OpenAPI JSON；合并会改变文档请求行为 | 语义不同，刻意保留 |
| `common/util` / `text` / `tree` 三个单文件工具包未合并为 `utils` | 合并会改 FQN | 形式差异 |
| 生产 `application-prod.yml` 中旧 `jobrunr:` 前缀 | 2026-11-08 已随 JobRunr → Quartz 迁移删除并改写为 `app.quartz` 说明 | **已解决** |
| `@Log` / `@DataScope` 等注解命名 | 本仓以 `AuditAspect` + `DataScopeConditions` + Port 实现等价能力 | 命名风格 |
| 权限码动作词 `read/create/update/delete` | 本仓贴合 REST 语义；改成 `list/add/edit/remove` 会动全部 `@PreAuthorize` 与角色授权 | 命名风格，已声明偏离 |
| URL `/api` 前缀 + 复数资源 + 动作式端点 | 由 `api-contract.json` 与 69 个前端 api 文件固定；改路径属契约级 | **契约级**，不可改 |
| 主键 `id`、审计列 `created_at/updated_at`、`deleted`、`sort_no` | 51 张表 + DSL 自动填充 + 40+ 前端视图字段；改列名属破坏性 DDL | **数据级**，不做 |
| `*Mapper.java` + `mapper/*.xml` | 本仓已移除 jOOQ / Flyway，数据访问为自研 DSL（`mybatis/dsl` 38 类），0 个 Mapper XML；回退等于重写 | **架构级**，不做 |
| 菜单字段 `menu_code` / `route_path` / `permission_code` / `menu_type=CATALOG\|MENU` | 本仓多租户扩展（`tenant_id`、`menu_code` 业务唯一键）优于若依；字段名改造牵动种子与前端 | 形式差异 |
| 前端 `views/{Module}XxxView.vue`、`src/api/*.js` 平铺 | 69 个 api 文件 + 全部 import 纯搬迁，无功能收益 | 大规模搬迁，不做 |
| `alert_rule` 表无模块前缀 | 需 `RENAME TABLE` + DSL 常量同步，属结构级变更 | **已修复**（P2-2，见 §3.3 / §3.5）：V84 幂等重命名 + `SqlTables.ALERT_RULE` 指向 `sys_alert_rule` |

> **2026-11-10 复查落地**：按《若依规范对齐差异清单与重构方案_20261109.md》批次 1–2
> 修复了 2 条菜单权限码漂移（`system:department:read`→`system:org:read`、
> `system:api-resource:read`→`system:api:read`，见 `V32` 与 `V82`），新增
> `scripts/check-permission.py`（P1 硬性门禁，接入 `check-all.py` 第 5 步与 CI），
> 归一 2 处 Service 双类制、修正前端 3 处命名，并在 `docs/NAMING.md` §6 固化
> 术语 / 权限码 / 跨层命名口径。其余差异按上表结论保留。

### 3.1 明确不做（P3，8 项正式偏离登记）

> 来源：《若依规范对齐差异清单与重构方案_20261109.md》§3 P3 / §5 风险矩阵（批次 6 收口）。
> 判定口径：改造面覆盖契约、数据结构或大规模搬迁，收益仅为命名风格一致；
> 在本文件顶部「硬约束」下一律**明确不做**。后续版本若重启，须单独立项重新评估。

| # | 若依约定 | 不做理由 | 影响面 |
|---|---|---|---|
| 1 | `Result<T>` → `AjaxResult`（HashMap 全 200） | `api-contract.json` 已固化 `code:"0"` 与 `data/message` 字段；替换即改响应 JSON | 契约级 |
| 2 | `TableDataInfo` + PageHelper（`total`/`rows`） | 本仓 `PageResult` / `KeysetPageResult` 支持 keyset 深翻页，改则丢能力 | 契约级 + 能力回退 |
| 3 | `*Mapper.java` + `mapper/*.xml` | jOOQ / Flyway 已移除，数据访问为自研 DSL（`mybatis/dsl`），全仓 0 个 Mapper XML；回退等于重写 | 架构级 |
| 4 | 主键 `user_id` / `role_id` | 51 张表 + DSL 自动填充 + 雪花 ID | 数据级，破坏性 DDL |
| 5 | `created_at` → `create_time` | 全表 + `SqlWriters` 自动填充 + 40+ 前端视图字段名 | 数据级，破坏性 DDL |
| 6 | `deleted` → `del_flag CHAR(1)` | 语义与类型双改，牵动所有查询条件构造 | 数据级，破坏性 DDL |
| 7 | Controller 集中到 `scaffold-admin` | 改组件扫描与 14 模块依赖方向 | 部署结构 |
| 8 | 前端 api 按模块分目录（`api/system/user.js`） | 69 个文件 + 全部 import，纯搬迁无功能收益 | 大规模搬迁 |

配套的**已声明偏离**用词口径见 `docs/NAMING.md` §6.1（org/dept 同义词收敛）、
§6.2（权限码动作词 `read/create/update/delete`，不引入 `list/add/edit/remove`）、
§6.3（URL `/api` 前缀 + 复数资源 + kebab 连字符）。

### 3.2 P1-4 复核结论：不新增 `/api/system` 前缀别名

对比报告把「为 5 个越出 `/api/system` 的端点增加前缀别名」列为**可选、低风险、纯新增**。
2026-11-10 逐文件复核后判定**不落地**，理由如下（均为可核验的代码事实）：

| # | 端点（类级基路径） | 所在 Controller | 复核发现 |
|---|---|---|---|
| 1 | `/api/notification-center` | `notifymgmt/api/NotificationCenterController` | javadoc 明示与 M6 `/api/system/notifications` 字面不同、无碰撞 |
| 2 | `/api/notification-preferences` | `notifypref/api/NotificationPreferenceController` | javadoc 明示「**刻意**使用顶级前缀，表达『我的订阅』非管理域语义」 |
| 3 | `/api/notification-quiet-hours` | `notifyquiet/api/NotificationQuietHoursController` | 同上，「我的作息」非管理域语义 |
| 4 | `/api/profile` | `profile/api/ProfileController` | 同上，避免与 `/api/system/users` 管理员批量操作混淆 |
| 5 | `/api/notification/unsubscribe` | `notification/unsubscribe/api/NotificationUnsubscribeController` | 路径常量为 `NotificationUnsubscribeService.UNSUBSCRIBE_PATH`；**免登录**端点 |

四条阻断理由：

1. **与刻意设计冲突**：2/3/4 三个 Controller 的 javadoc 逐字写明「路径前缀刻意使用顶级
   `/api/...`（而非 `/api/system/...`）」。这不是遗漏，是有意的域语义划分；加别名会让
   「管理域 / 个人域」两类端点在同一前缀下并存，反而放大混淆。
2. **破坏免登录外部链接**：`SecurityConfig.BASE_PUBLIC_API` 只放行
   `GET /api/notification/unsubscribe`（`SecurityConfig.java` L68），而链接由
   `NotificationUnsubscribeService.baseUrl() + UNSUBSCRIBE_PATH + "?token=..."` 生成，
   是邮件 / 短信正文中的 HMAC 签名链接。新增 `/api/system/...` 别名**不在白名单**，
   会得到一个「看似可用却返回 401」的公开端点；若要可用必须同步改安全配置，
   已不属于「纯新增」，触及本文件顶部硬约束。
3. **契约门禁不支持多路径**：`scripts/check-api-contract.py` 的类级基路径用
   `first_quoted()` 只取注解属性串中的**首个**字符串字面量，数组式
   `@RequestMapping({old,new})` 会被解析成旧路径，别名不计入后端路由集合；
   报告批次 3 第 13 步「确认后端集合为超集」无法成立，须先改门禁脚本才能验证。
4. **`@Deprecated` 语义错位**：Java 的 `@Deprecated` 只能标在类 / 方法上，类级标注会把
   新旧两个别名**一起**标记为废弃，无法表达「只废弃旧路径」。

**结论**：把「顶级 `/api` 前缀用于个人域 / 免登录端点」登记为第 9 项刻意偏离，
写入 `docs/NAMING.md` §6.3；旧路径全部保留、不改任何 HTTP 行为、不动安全配置。
若后续产品侧确需统一前缀，前置条件是：① 同步扩展 `BASE_PUBLIC_API` 并补回归；
② 增强 `check-api-contract.py` 支持多路径并补行为回归用例；③ 明确废弃策略与前端灰度；
④ 按 P2 级别排期（双挂 ≥1 个版本）。

### 3.3 需确认 / 需排期项的实施状态

| 编号 | 事项 | 改表 | 改接口 | 改前端 | 风险 | 状态与前置 |
|---|---|---|---|---|---|---|
| P0-2 | `sys_menu` 补 `created_by` | DDL | 否 | 否 | 中 | **已落地**：以 `V83__sys_menu_created_by.sql` 前向追加（V32 基表字节不变），沿用「列缺失才补」幂等写法；`SqlTables.SYS_MENU` / `SysMenuDO` / `MenuService.create` 同步写入当前用户。另以 `MenuTypeRules` 纯函数收敛 `menu_type` 取值域，兼容后端全名与前端单字母两套字面量、不归一化 |
| P2-1 | 4 组一路径多 Controller 拆分 | 否 | 双挂 | 否（双挂期） | 中 | **已落地（过渡态）**：6 个从属 Controller 类基路径做 `{旧, 新}` 数组双挂，方法级映射一律未动；旧 URL 全部原样可用，前端 5 个 api 文件仍走旧路径、零行为变更。别名清单与后续「前端灰度切换 → 观察 1 版本 → 删旧」见 §3.4 |
| P2-2 | `alert_rule` → `sys_alert_rule` | 是 | 否 | 否 | 中 | **已落地**：`V84__rename_alert_rule.sql` 幂等 RENAME（V70 字节不变）；`SqlTables.ALERT_RULE` 表名同步；停写窗口、备份与回滚顺序见 §3.5 |
| P2-3 | cms / im / kafka / workbench / workflow 收敛到簇骨架 | 否 | 否 | 否 | 高 | **分批落地中（批次 8/9/10：`scaffold-cms`、`scaffold-im`、`scaffold-workbench` 已完成）**：D12 多模块解析 + §4 逐模块簇表；cms 15 文件迁 `article/category/support`、im 12 文件迁 `conversation/message/support`、workbench 33 文件迁 `overview/report/dashboard`；D12 现为 **45 簇 / 4 模块**，余 2 模块按 §3.6 逐模块独立提交 |

> P2-3 仍属大规模类搬迁范畴，须按报告批次 5 逐模块推进、每模块独立提交；
> 其余已落地项均保持向后兼容（可空列 / 新旧双挂 / 前向追加迁移）。

### 3.4 P2-1 双挂别名清单与后续排期

契约门禁 `scripts/check-api-contract.py` 已增强为支持 `@RequestMapping({"/old","/new"})`
多路径数组（`quoted_paths` / `split_attrs`），每个方法在各基类路径下分别登记路由，
并补 8 例行为回归；这是 P1-4 复核时列出的前置条件之一，现已满足。

| 从属 Controller | 旧类基路径 | 新增别名类基路径 | 方法级映射 |
|---|---|---|---|
| `TenantOverviewController` | `/api/system/tenants` | `/api/system/tenant-overview` | 不变 |
| `TenantGovernanceController` | `/api/system/tenants` | `/api/system/tenant-governance` | 不变 |
| `TenantIsolationStatusController` | `/api/system/tenant-isolation` | `/api/system/tenant-isolation-status` | 不变 |
| `TenantIsolationAuditController` | `/api/system/tenant-isolation` | `/api/system/tenant-isolation-audit` | 不变 |
| `AnnouncementReadController` | `/api/system/announcements` | `/api/system/announcement-reads` | 不变 |
| `MessageReadController` | `/api/system/messages` | `/api/system/message-reads` | 不变 |

**两处刻意偏离报告建议**：报告为「公告阅读 / 消息阅读」建议的子资源路径
`/api/system/announcements/{id}/reads`、`/api/system/messages/{id}/read` 含路径变量，
不能作为类基路径，且这两个 Controller 各自还含 `mine/unread`、`mark-all-read`、
`read-stats/page`、`receipts` 等非子资源端点，统一套一个 `{id}` 前缀会产生错误路由。
故改用独立命名空间前缀；租户隔离两组同理（方法级已有 `/status` / `/audit` 区分）。

**别名 URL 形态**：`新类基路径 + 原方法级路径`，例如
`GET /api/system/tenant-overview/overview`、`GET /api/system/announcement-reads/{id}/read`。
旧路径与别名指向同一方法，`@PreAuthorize` 权限码、参数、响应字段完全一致。

**后续排期（本批次不做）**：① 前端 5 个 api 文件灰度切换到别名；② OpenAPI 文档会
在每个双挂期内列出新旧两套 operationId，需在切换后复核网关路由规则；③ 观察 ≥1 个版本后
删除数组中的旧路径，才算真正完成「一路径一 Controller」的拆分。切换前**不得**删旧路径。

### 3.5 P2-2 表重命名的部署约束

`alert_rule` → `sys_alert_rule` 是本批次唯一**结构级不兼容**改动，落地方式与风险如下：

| 项 | 做法 |
|---|---|
| 版本文件 | 新增 `V84__rename_alert_rule.sql`，用 `information_schema.TABLES` 双守卫做幂等 `RENAME TABLE`；`V70__alert_rule.sql` 与 `V32` 基表字节保持不变 |
| 新库 | `V70` 建 `alert_rule` → `V84` 重命名，最终落态为 `sys_alert_rule`；三份派生 SQL 由 `build_reference_modular.py` 整链重生成 |
| 存量库 | `RENAME TABLE` 为原子 DDL，索引 `idx_metric_enabled`、数据、自增状态随表保留；重复执行命中 `DO 0` 无操作 |
| Java | 仅 `SqlTables.ALERT_RULE` 的**表名字符串**改为 `sys_alert_rule`，常量名不变，`SqlAlertRuleStore` 引用无需改动；2 处 javadoc 同步 |
| 前端 / 接口 | 零改动：`/api/monitoring/alert-rules` 路由、参数、响应字段、权限码均不变 |
| 部署顺序 | ① 备份 `alert_rule` ② 停写窗口执行 V84 ③ 确认新表可读 ④ **再发布**含新常量的应用版本。反向顺序会导致告警规则模块查不到表 |
| 回滚 | 先下线新版应用，再执行 `RENAME TABLE sys_alert_rule TO alert_rule`，并把 `SqlTables.ALERT_RULE` 表名改回后重新发布；完整语句见 V84 头注释 |

> 本仓已移除 Flyway，应用启动**不会**自动执行 V84；运维须显式执行 `db/init/` 初始化脚本
> 或单独跑该迁移。这是 P2-2 相对其它条目最需要人工确认的一点。

### 3.6 P2-3 分批实施方案（批次 8–10：`scaffold-cms` / `scaffold-im` / `scaffold-workbench` 已落地）

**实测盘点（2026-11-10，非报告中的估值）**：5 个模块共 **139 个 Java 文件**，目录形态差异很大，
不能用一条正则统一搬迁：

| 模块 | 文件数 | 当前二级目录（文件数） |
|---|---|---|
| `scaffold-cms` | 15 | **已落地**：`article`(7) / `category`(7) / `support`(1) 三簇 |
| `scaffold-im` | 12 | **已落地**：`conversation`(6) / `message`(5) / `support`(1) 三簇 |
| `scaffold-kafka` | 25 | `adapter`(1) / `config`(1) / `controller`(1) / `domain`(3) / `dto`(2) / `gateway`(1+9 子目录) / `port`(1) / `rules`(1) / `service`(4) |
| `scaffold-workbench` | 33 | **已落地**：`overview`(4) / `report`(23) / `dashboard`(6) 三簇；原 `report/` 下 `csv`/`export`/`stats`/`rules` 纯函数统一归 `domain/rule/`，`ReportExportTask` 归 `adapter/`，`DashboardTemplateDO` 归 `domain/model/` |
| `scaffold-workflow` | 54 | `adapter`(4) / `assignee`(1) / `bootstrap`(1) / `bpmn`(2) / `config`(2) / `controller`(5) / `dto`(18) / `entity`(4) / `model`(2) / `port`(1) / `rules`(4) / `service`(4) / **根包 `approval/` 6 个平铺类（与 `workflow/` 平级）** |

**目标骨架**（沿用 §4 记法）：`controller→api`、`service→application`、`dto→簇根 dto/`、
`domain`/`entity`/`model→domain/model`、`port`/`gateway→domain/repository`、`rules→domain/rule`、
`config`/`adapter` 保持层名；`event`/`csv`/`export`/`stats`/`bpmn`/`assignee`/`bootstrap` 需逐簇判定归属。

**前置（批次 8 已全部完成）**：

1. `scripts/check-cluster-layout.py` 已改为多模块解析：`TABLE_MODULE_RE` 依表标题中的
   `` `scaffold-xxx` `` 推导模块包根、`module_pkg_path()` 做模块名→包路径映射，
   `parse_doc()` 返回 `{模块: rows}`，`main()` 逐表校验且错误带 `[module]` 前缀；
   旧式无模块名标题按 `DEFAULT_MODULE = "scaffold-system"` 兜底。
2. `docs/DIRECTORY.md` §4 已改为「一模块一张子域簇一览表」，新增 `scaffold-cms` 3 簇表
   （`article` 7 / `category` 7 / `support` 1，含 C1 文件数 / C2 层集合 / C3 子目录集合）。
3. `C5` 维持原 `FORBIDDEN_FLAT` 清单；`event` 判定为业务分层名（`article/domain/event`），
   不列入禁用平铺层；跨簇共享枚举 `CmsStatus` 只保留 `support/` 一份。
4. `scripts/test_check_cluster_layout.py` 由 11 例扩为 **14 例**（新增多模块解析 3 例），
   全仓行为回归口径 **89 例 → 92 例**。

**批次顺序与验证**（报告批次 5）：`cms`(15，已完成) → `im`(12，已完成) → `workbench`(33，已完成) →
`kafka`(25) → `workflow`(54，最后处理 `approval` 6 个平铺类)。每模块：

```
git mv 保历史 → 全量 FQN 替换 → 同包隐式引用补 import
→ mvn -o -f pom.xml compile -Dmaven.resources.skip=true
→ python3 scripts/check-all.py --only conventions,naming,directory,cluster-layout,script-tests
→ 独立提交（不与其他模块混在一笔）
```

**批次 8 落地记录（2026-11-10，`scaffold-cms`）**：

| 项 | 结果 |
|---|---|
| 搬迁 | 15 个 Java 文件按 SPEC 落到 `article/{api,application,domain/model,domain/event,dto}`、`category/{api,application,domain/model,dto}`、`support/`；旧 `controller/` `service/` `domain/` `dto/` `event/` 五个扁平目录清空删除 |
| FQN | 改写 4 个文件（`CmsArticleController` / `CmsCategoryController` / `CmsCategoryService` / `CmsArticleService`），含 `CmsArticleService.java` 内联 FQN `com.acme.scaffold.cms.category.domain.model.CmsCategoryDO.class`；终态旧 FQN 残留 **0** |
| D12 | 真实门禁 `EXIT=0`：**39 个子域簇 / 2 个模块**（`scaffold-system` 36 + `scaffold-cms` 3），簇表与磁盘完全一致 |
| 编译 | 根聚合 POM `mvn -o -f pom.xml compile -Dmaven.resources.skip=true` → **BUILD SUCCESS**，15 个 reactor 项全 SUCCESS |
| 行为 | 零 HTTP 契约、零表结构、零前端改动；`@ComponentScan` 基包仍为 `com.acme.scaffold`；`article/application` → `category/domain/model` 为单向引用，无新增 H1 包级环 |

**批次 9 落地记录（2026-11-10，`scaffold-im`）**：

| 项 | 结果 |
|---|---|
| 搬迁 | 12 个 Java 文件按 SPEC 落到 `conversation/{api,application,domain/model,dto}`、`message/{api,application,domain/model,dto}`、`support/`；旧 `controller/` `service/` `domain/` `dto/` 四个扁平目录清空删除 |
| FQN | 改写 4 个文件（2 个 Controller + 2 个 Service），含跨簇 `application → 对方 domain/model` 的双向引用；终态旧 FQN 残留 **0**，12 个 package 声明与目录逐项一致 |
| D12 | 真实门禁 `EXIT=0`：**42 个子域簇 / 3 个模块**（system 36 + cms 3 + im 3）；§4 新增 `scaffold-im` 簇表，**校验脚本本身无需改动**（多模块解析已由批次 8 完成），行为回归仍为 92 例 |
| 编译 | 根聚合 POM `mvn -o -f pom.xml compile -Dmaven.resources.skip=true` → **BUILD SUCCESS**（28.313 s，15 个 reactor 项全 SUCCESS） |
| 行为 | 零 HTTP 契约、零表结构、零前端改动；两个 Service 互引对方 DO 但方向恒为 `application → domain/model`，包级依赖图仍为 DAG（H1 无环）；`ImStatus` 只在 `support/` 保留一份 |

**批次 10 落地记录（2026-11-10，`scaffold-workbench`）**：

| 项 | 结果 |
|---|---|
| 搬迁 | 33 个 Java 文件收敛为 `overview`(4) / `report`(23) / `dashboard`(6) 三簇；14 个文件按 SPEC 移动（顶层 `controller`/`service`/`dto` → `overview/{api,application,dto}`，`report/{controller,service}` → `report/{api,application}`，4 个纯函数 → `report/domain/rule/`，`ReportExportTask` → `report/adapter/`，`DashboardTemplateDO` → `dashboard/domain/model/`），19 个 `report/dto/` + `dashboard/dto/` 文件因簇名与原特性包名一致而**路径与 package 均不变**；11 个扁平目录清空删除 |
| FQN | 改写 9 个文件，终态旧 FQN 残留 **0**；清理 2 处搬迁后产生的同包冗余 import。**关键陷阱**：`ReportExportTask` 与 `ReportSheets` 原本同在 `report/export/`、属同包隐式引用且无 import，拆分到 `adapter/` 与 `domain/rule/` 后须补 `import ...domain.rule.ReportSheets`，否则编译报「找不到符号 ReportSheets」；静态扫描另外 6 处命中均为 javadoc `{@code ...}` 纯文本，不可误补 import |
| D12 | 真实门禁 `EXIT=0`：**45 个子域簇 / 4 个模块**（system 36 + cms 3 + im 3 + workbench 3）；§4 追加第 4 张模块簇表，**校验脚本仍无需改动**（`module_pkg_path("scaffold-workbench")` 自动推导包根），行为回归仍为 92 例 |
| 编译 | 首次因上述同包隐式引用失败（`EXIT=1`）；补 import 后根聚合 POM `mvn -o -f pom.xml compile -Dmaven.resources.skip=true` → **BUILD SUCCESS**（24.535 s，15 个 reactor 项全 SUCCESS） |
| 行为 | 零 HTTP 契约、零表结构、零前端改动；4 个零 Spring / 零 DB 纯函数落 `domain/rule/`，`adapter → application` 单向；H3 按包路径最后一段判层，`adapter`/`rule`/`dto`/`api` 均为 neutral，三簇之间零跨簇引用，包级依赖图为 DAG（H1 无环） |

**风险**：包级 import 变更可能新建 H1 包级环；`@ComponentScan` 基包须保持
`com.acme.scaffold`；`approval` 与 `workflow` 平级，合并前须先定 `approval` 是否独立簇。
本批次**零 HTTP 契约、零表结构、零前端改动**。

> **为何按模块分批**：报告 §7 明确标注「建议单独立项」，§6 批次 5 要求逐模块独立提交 +
> 每步全量编译与门禁；139 文件一次性搬迁会破坏「每批次独立可回滚」的约束。
> 余下 `kafka` / `workflow` 两模块待逐模块确认后按上表推进（建议下一轮回复「继续搬迁 kafka」）。

## 4. 回归验证要点

> **状态说明（2026-11-10）**：后端测试源码已于 `087acdb` 全量移除，前端 4 个
> `__tests__/` 目录亦为空，故 `ApiContractConsistencyTest` / `CommonConstantsTest` /
> `result.test.js` / `PagingRulesTest` 等**当前均不存在**，`npm test` 133 例与
> 「全量 86 例」同为历史口径，汇报不得再引用旧的「全绿」表述。

### 当前可执行的验证（提交前必过）

1. **统一门禁**：`python3 scripts/check-all.py`（**7 步**：conventions / naming /
   directory / cluster-layout / permission / api-contract（信息性）/ script-tests），
   硬性项全部 `EXIT=0`；受限环境加 `--test-tmpdir <dir>`。
2. **行为回归**：`python3 -m unittest discover -s scripts -p "test_*.py"`，当前
   **92 例全绿**（`test_check_naming.py` 4 + `test_check_cluster_layout.py` 14 +
   `test_check_permission.py` 12 + `test_check_directory.py` 29 +
   `test_check_api_contract.py` 33）；`check-all.py` 第 7 步即调用该命令。
3. **编译**：`mvn -o -f pom.xml clean compile`，15 个 reactor 项 BUILD SUCCESS。
   测试源码已移除，`mvn test` / `verify` 为空跑，不作为通过标准。
4. **前端构建**：`npx vite build`（验证 `styles/base.css` 路径可解析）；另有
   `node scripts/check-sfc.mjs <file.vue>` 与 `node scripts/i18n-leak-scan.mjs src`
   两个静态检查。`npm test` 当前不可用（`test` 脚本已移除、无用例）。
5. **契约口径**：`scripts/api-contract.json` 为 `Result<T>` 字段唯一事实来源；
   `scripts/check-api-contract.py` 做前后端路由静态对称（信息性，`--strict` 才阻断）。

### 历史资产（已移除，勿引用）

原 1–6 项记录的 `ApiContractConsistencyTest` / `CommonConstantsTest` /
`PagingRulesTest` / `PageResultTest` / `JsonTruncatorTest` / `result.test.js`、
`npm test` 133 例与 `mvn -o -f pom.xml clean test` 全量回归，均为 2026-11-08 当时的
测试资产；业务单测覆盖恢复需单独立项。

## 5. 兼容性风险与未解决项

| 风险 | 等级 | 说明 / 建议 |
|---|---|---|
| `application-prod.yml` 旧 `jobrunr:` 前缀 | 中 | 已删除；当前调度参数统一为 `app.quartz.enabled` / `app.quartz.thread-count`（环境变量 `QUARTZ_ENABLED` / `QUARTZ_THREAD_COUNT`） |
| 前端样式路径变更 | 低 | 已用 `git mv` 保留文件历史，全仓核实仅 1 处 import + 8 处注释引用；构建门禁验证路径解析 |
| `package-info.java` 与跨模块同包 | 低 | 5 份说明放在无同包冲突的位置；`com.acme.scaffold.monitor.*` 在 framework 与 monitor 两模块共存，刻意未加包级说明，避免 split-package 的 `package-info` 冲突 |
| 常量集中后的编译期耦合 | 低 | 6 个目标模块原本均已依赖 `scaffold-common`，未新增 Maven 依赖边，H5 DAG 不变 |
| 文档与 POM 再次漂移 | 低 | `docs/MODULES.md` 已写明「以根 POM 为准并修表」；后续新增模块须同步更新 `DIRECTORY.md` |

## 6. 结论

本轮完成的是**目录结构与公共能力的低风险对齐**：把散落的通用字面值收敛到若依约定位置、
用包级说明与目录规范固化职责边界、修正文档漂移、统一版本管理、归位前端样式文件。
后续追加 `scripts/check-directory.py` 把这些约定从「文档要求」升级为可执行守护，并逐步扩展到
**D1–D11**：新增包根 / 模块、配置散落到非启动模块、根级 CSS 复活、规范文档索引漏挂或死链、
子模块写死版本、main/test 源码树长出新的一级目录、测试期配置越界都会阻断；脚本只读、
不参与编译与运行时。`test_check_directory.py` 早期用例曾随精简移除，2026-11-10 已重建为
29 例并纳入统一门禁。
又补齐两处门禁闭环：修复 `scripts/check-api-contract.py` 在 14 模块拆分后仍指向单体
`ROOT/src/main/java` 的过期目录假设（后端扫描恒为空、197 条前端调用全为假阳性，现按
`scaffold-*/src/main/java` 自动发现源码根），并新增 `scripts/check-all.py` 统一门禁入口，
把约定 / 目录 / 契约 / 校验器单测编排成一次可复现运行（历史「全量 86 例」口径已失效）。
2026-11-10 起统一门禁为 **7 步**，新增 P1 权限码一致性硬性步骤；现存行为回归
**92 例**（`test_check_naming.py` 4 + `test_check_cluster_layout.py` 14 +
`test_check_permission.py` 12 + `test_check_directory.py` 29 +
`test_check_api_contract.py` 33），由 `unittest discover` 自动发现，新增 `test_*.py` 无需改编排。
若依的响应体命名、Controller 集中、包根重命名、接口前缀命名等属于**契约级或大规模搬迁**差异，
在不改变接口行为与避免无关重写的约束下明确保留，并集中于本文件说明，供后续版本按独立变更评估。

**2026-11-10 批次 6 收口**：§3 已升级为正式偏离清单 —— §3.1 登记 P3 八项「明确不做」、
§3.2 给出 P1-4 增别名的逐文件复核结论（判定不落地）、§3.3 列出 P0-2 / P2-1 / P2-2 / P2-3
四项待确认与排期状态；§1 补齐模块数口径复核，`docs/NAMING.md` §6.3 与
`docs/DIRECTORY.md` §4 同步交叉引用。至此《若依规范对齐差异清单与重构方案_20261109.md》
§7 中除 P0-2 与 P2 四项外，各条目均已有明确处置结论；剩余四项须逐项取得确认后再落地。

**2026-11-10 批次 7 收口**：P0-2（`V83` 前向追加 `sys_menu.created_by` + `MenuTypeRules`）、
P2-1（契约门禁支持多路径数组 + 6 个从属 Controller 新旧双挂）、P2-2（`V84` 幂等重命名为
`sys_alert_rule`）均已落地，部署约束见 §3.5；行为回归由 81 例增至 89 例。

**2026-11-10 批次 8 收口（P2-3 首模块）**：把 D12 从「仅 `scaffold-system`」扩展为
多模块逐表校验，`docs/DIRECTORY.md` §4 改为「一模块一张簇表」，并完成 `scaffold-cms`
15 个 Java 文件向 `article` / `category` / `support` 三簇的搬迁；D12 实跑
**39 个子域簇 / 2 个模块**，编译 BUILD SUCCESS，行为回归由 89 例增至 **92 例**。
本批次零 HTTP 契约、零表结构、零前端改动，详见 §3.6。余下 4 模块仍须逐模块独立提交，
每模块单独编译与门禁。

**2026-11-10 批次 9 收口（P2-3 第二模块）**：`scaffold-im` 12 个 Java 文件收敛为
`conversation` / `message` / `support` 三簇，§4 追加第 3 张模块簇表；D12 实跑
**42 个子域簇 / 3 个模块**，编译 BUILD SUCCESS，行为回归口径仍为 **92 例**（本批次未增删用例）。
校验脚本无需改动，印证批次 8 的多模块扩展是可复用的：后续每模块只需追加一张 §4 表 + 搬迁源码。

**2026-11-10 批次 10 收口（P2-3 第三模块）**：`scaffold-workbench` 33 个 Java 文件收敛为
`overview` / `report` / `dashboard` 三簇，§4 追加第 4 张模块簇表；D12 实跑
**45 个子域簇 / 4 个模块**，编译 BUILD SUCCESS（补 1 处同包拆分后的隐式引用 import），
行为回归口径仍为 **92 例**（本批次未增删用例）。`report` 簇内 4 个零依赖纯函数统一归
`domain/rule/`、导出中心模板归 `adapter/`，`DashboardTemplateDO` 归 `domain/model/`；
三簇之间零跨簇引用，H1 包级无环。余下 `kafka` / `workflow` 两模块。
