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
| P2-3 | cms / im / kafka / workbench / workflow 收敛到簇骨架 | 否 | 否 | 否 | 高 | **已全部落地（批次 8–12，五模块）**：D12 多模块解析 + §4 六张模块簇表；cms 15 / im 12 / workbench 33 / kafka 25 / workflow 54 文件分别收敛为 3/3/3/4/7 簇，累计 **139 个 Java 文件收敛为 20 个业务簇**，连同 `scaffold-system` 36 簇，D12 现为 **56 簇 / 6 模块**。批次 12 另把与 `workflow/` 平级的 `approval/` 根包并入 `workflow/approval/`，`EXPECTED_ROOTS` 同步注销。详见 §3.6 |

> P2-3 属大规模类搬迁范畴，已按报告批次 5 于批次 8–12 逐模块推进完毕、每模块 2 笔独立提交，
> 全程保持「每批次独立可回滚」约束；其余已落地项均保持向后兼容（可空列 / 新旧双挂 / 前向追加迁移）。

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

> **2026-11-10 取证收口补注**：全量表名核实又发现 `dashboard_template`（V78）为最后一张
> 裸名表，已按与 V84 完全同构的方式前向追加 `V85__rename_dashboard_template.sql`
> （详见 §3.7）。两者须合并进同一个停写窗口、同样「先迁移后发布」；V84 头注释中
> 「全仓唯一」是只统计到 V71 时的快照，以 `db/migration/README.md` 历史口径说明为准。

### 3.6 P2-3 分批实施方案（批次 8–12：五模块全部落地）

**实测盘点（2026-11-10，非报告中的估值）**：5 个模块共 **139 个 Java 文件**，目录形态差异很大，
不能用一条正则统一搬迁：

| 模块 | 文件数 | 当前二级目录（文件数） |
|---|---|---|
| `scaffold-cms` | 15 | **已落地**：`article`(7) / `category`(7) / `support`(1) 三簇 |
| `scaffold-im` | 12 | **已落地**：`conversation`(6) / `message`(5) / `support`(1) 三簇 |
| `scaffold-kafka` | 25 | **已落地**：`monitor`(4) / `consumer`(9) / `gateway`(11) / `support`(1) 四簇；`gateway/config/` 2 个配置类路径不变，`OrderingRules` 收敛到 `support/` |
| `scaffold-workbench` | 33 | **已落地**：`overview`(4) / `report`(23) / `dashboard`(6) 三簇；原 `report/` 下 `csv`/`export`/`stats`/`rules` 纯函数统一归 `domain/rule/`，`ReportExportTask` 归 `adapter/`，`DashboardTemplateDO` 归 `domain/model/` |
| `scaffold-workflow` | 54 | **已落地**：`instance`(17) / `definition`(14) / `monitor`(7) / `approval`(6) / `engine`(5) / `admin`(3) / `support`(2) 七簇；原平级根包 `approval/` 6 个类并入 `workflow/approval/`，`approval` 从 `EXPECTED_ROOTS` 注销 |

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
`kafka`(25，已完成) → `workflow`(54，已完成；`approval` 6 个平铺类并入 `workflow/approval/`)。每模块：

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

**批次 11 落地记录（2026-11-10，`scaffold-kafka`）**：

| 项 | 结果 |
|---|---|
| 搬迁 | 25 个 Java 文件收敛为 `monitor`(4) / `consumer`(9) / `gateway`(11) / `support`(1) 四簇；其中 23 个按 SPEC 移动，`gateway/config/` 两个配置类因「簇名 + 标准层名」恰好重合而路径与 package 均不变（同批次 10 的 `report/dto/`）；12 个 legacy 空目录清空删除（顶层 8 个 + `gateway/` 下 4 个） |
| FQN | 改写 16 个文件（14 个 Java + `application.yml` 注释 + 模块 `pom.xml` 注释），终态旧 FQN 残留 **0**；javadoc 中的 `{@code com.acme.scaffold.kafka.xxx}` 纯文本 FQN 由长串优先替换同步更新 |
| 拆包补 import | 3 处同包隐式引用拆开后补齐：`KafkaRecordSender → RecordSender`、`TopicSpec → TopologyRules`、`KafkaMonitorService → OrderedKafkaConsumer`（后者同时是 javadoc `{@link}`，不补会破坏 doclint） |
| H1 规避 | ① `KafkaOrderedConsumerProperties` 落 `consumer/domain/model/` 而非 `consumer/config/`，避免 `config ↔ application` 双向依赖成环；② `RecordSender` 端口 javadoc 的 `{@link KafkaRecordSender}` 降级为 `{@code ...}` 纯文本，避免 `domain/repository → adapter` 反向 import 成环。独立复核脚本确认包级依赖图 14 节点 **0 环** |
| D12 | 真实门禁 `EXIT=0`：**49 个子域簇 / 5 个模块**（system 36 + cms 3 + im 3 + workbench 3 + kafka 4）；§4 追加第 5 张模块簇表，校验脚本仍无需改动，行为回归仍为 **92 例** |
| 编译 | 根聚合 POM `mvn -o -f pom.xml compile -Dmaven.resources.skip=true` → **BUILD SUCCESS**（35.846 s，15 个 reactor 项全 SUCCESS，一次通过） |
| 行为 | 零 HTTP 契约、零表结构、零前端改动；`@ComponentScan` 基包仍为 `com.acme.scaffold`；`kafka.ordered-consumer.*` / `kafka.gateway.*` 配置前缀、Bean 名与条件装配门禁均未变；`OrderingRules` 只在 `support/` 保留一份 |

**批次 12 落地记录（2026-11-10，`scaffold-workflow`）**：

| 项 | 结果 |
|---|---|
| 前置决策 | `approval/` 原为与 `workflow/` 平级的独立根包（`com.acme.scaffold.approval`），`check-directory.py` 的 `EXPECTED_ROOTS` 曾把它登记在 `scaffold-workflow` 下，而 D12 的 `module_pkg_path()` 只推导到 `.../workflow`。本批次选择**整体并入** `workflow/approval/` 簇（而非扩展 D12 支持多包根），因此彻底消除历史例外、校验脚本连续第 5 个模块无需改动，同时把 `EXPECTED_ROOTS["scaffold-workflow"]` 改为 `{"workflow"}` |
| 搬迁 | 54 个 Java 文件全部按 SPEC 移动，收敛为 `instance`(17) / `definition`(14) / `monitor`(7) / `approval`(6) / `engine`(5) / `admin`(3) / `support`(2) 七簇；原 13 个 `dto/` 按业务归属拆到三个簇，`dto/design/` 5 个整体落 `definition/dto/design/`；15 个 legacy 空目录清空删除（含平级根包 `approval/`） |
| FQN | 改写 21 个 Java 文件 + `scaffold-workflow/pom.xml` 头部结构注释，终态旧 FQN / 旧包路径字符串残留 **0** |
| 拆包补 import | 搬迁前用包级 import 图预扫描，识别 **10 处同包隐式引用**并在搬迁时一次补齐：`approval` 5 处代码引用、`BpmnWorkflowBuilder → WorkflowDesignValidator` 1 处、4 处 javadoc `{@link}`（三个 `*Rules` 文件指向 `ClaimRules` / `WorkflowMonitorRules`）。补完后独立复核确认包级 import 图 **20 节点 0 环** |
| 运行时 FQN | **本批次新增关键项**：`BpmnWorkflowBuilder` 用字符串把 Flowable `serviceTask` 的 implementation 指向 `GenericStepDelegate` / `CcStepDelegate` 两个 delegate 类。这类**运行时类名字符串**必须随包迁移同步改写，否则部署 BPMN 时 ClassNotFound；终态 2 处均已指向 `com.acme.scaffold.workflow.engine.adapter.step.*` |
| D12 | 真实门禁 `EXIT=0`：**56 个子域簇 / 6 个模块**（system 36 + cms 3 + im 3 + workbench 3 + kafka 4 + workflow 7）；§4 追加第 6 张模块簇表，行为回归仍为 **92 例** |
| 编译 | 根聚合 POM `mvn -o -f pom.xml compile -Dmaven.resources.skip=true` → **BUILD SUCCESS**（26.139 s，一次通过） |
| 行为 | 零 HTTP 契约、零表结构、零前端改动；`@SpringBootApplication` 基包仍为 `com.acme.scaffold`，`ApiResourceScanner` 以 `(method, path)` + `getDeclaringClass().getSimpleName()` 登记资源，不持久化包名；Bean 名与 `@RequestMapping` 路径全部不变 |

**风险**：包级 import 变更可能新建 H1 包级环；`@ComponentScan` 基包须保持
`com.acme.scaffold`。批次 12 以搬迁前预扫描 + 搬迁后独立复核（20 节点 0 环）闭环该风险；
跨根包合并前已确认全仓除 `approval/` 自身外无 `com.acme.scaffold.approval` 的 Java 引用，
资源文件与权限登记均不依赖静态包名。五批次**均零 HTTP 契约、零表结构、零前端改动**。

> **为何按模块分批**：报告 §7 明确标注「建议单独立项」，§6 批次 5 要求逐模块独立提交 +
> 每步全量编译与门禁；139 文件一次性搬迁会破坏「每批次独立可回滚」的约束。
> 五模块已按上表全部收口，共 **10 笔独立提交**；`DIRECTORY.md` §4 六张簇表由 D12 统一守护，
> 后续新增子域簇须同步对应模块的簇表，否则门禁阻断。

### 3.7 取证局限收口（2026-11-10 追加批次）

原始《若依规范对齐差异清单与重构方案_20261109.md》文末「附：本次取证的局限」列出 3 项
因当时沙箱 `SIGTERM(137)` 未完成全量核实的结论，另 §2.4 / §2.5 / §2.8 有若干「待确认 /
既有整改项」。本批次逐项取证并处置如下（**实测，非设计预期**）：

| # | 原始待核实项 | 取证结论 | 处置 |
|---|---|---|---|
| 1 | `sys_menu` 全部 `permission_code` 与 `@PreAuthorize` 全集合交叉比对 | 已由 `scripts/check-permission.py`（P1 硬性门禁）常态化守护；实跑 **菜单 47 条 / 接口 129 条（注解 128 + 资源表 52）/ 豁免 1 条 `system:api-docs:read` / `EXIT=0`** | 无新增代码；结论写入本节，同类风险面闭环 |
| 2 | `CreateGroup` / `UpdateGroup` 是否死代码 | 全仓仅 2 处定义、**零引用**（无 `groups = ...`、无实现 / 注入） | 不删：作为 `scaffold-common` 公共分组扩展点刻意保留，补 javadoc 说明并登记为第 10 项偏离 |
| 3 | `alert_rule` 之外是否还有无前缀表 | 对 V32~V84 全部 `CREATE TABLE` 全量抽取得 **58 张表**（sys 45 / wf 6 / cms 2 / im 3 / `alert_rule` 1 / `dashboard_template` 1）；`dashboard_template`（V78）是第二张亦最后一张裸名表 | **已修复**：前向追加 `V85__rename_dashboard_template.sql`，归一为 `sys_dashboard_template` |
| 4 | §2.5 L-2：15 处手写 `LoggerFactory` | 实测 15 个文件仍为 `private static final Logger log = LoggerFactory.getLogger(...)` | **已修复**：统一为 Lombok `@Slf4j`，`LoggerFactory` 全仓零残留；`PluginAutoConfiguration` 的静态内部类单独加注解 |
| 5 | §2.2.5 `v-hasPermi` 缺失 | 前端全仓 `hasPermi` / `v-hasPermi` / `permission.js` **零匹配**，结论成立 | 属按钮级权限架构差异，需独立立项；登记为第 11 项偏离 |
| 6 | §2.8 O-1 V57 断号 / O-3 `coding-standard.md` 漂移引用 | V57 在 `db/migration/README.md`、`db/init/README.md` 与 `02_backfill_tenant_id.sql` 三处已有等价说明；`coding-standard.md` 全仓零引用 | 均已闭环，无需改动 |
| 7 | `docs/USAGE.md` 口径 | 仍写 `V32~V82 / 50 个 SQL`、Flyway `checksum mismatch`、`V60–V82`、`5 个新增列` | **已修复**：同步 `V32~V85 / 53 个 SQL`、`V60–V85`、`6 个新增列 + 2 张表重命名`，并把失效的 Flyway 校验表述改为「版本链可追溯性 + 前向追加」 |

**V85 落地方式**：与 §3.5 的 V84 同构 —— `information_schema` 双守卫幂等 `RENAME TABLE`、
`V78` 版本文件字节不变、`SqlTables.DASHBOARD_TEMPLATE` 常量名不变只改表名串、
三份派生 SQL 整链重生成（53 个 SQL / 基线 `V85`）、`DashboardTemplateDO` javadoc 同步。
列 / 索引 / 数据 / 接口 / 前端零改动，V78 建立的 3 个索引随表保留。

> **部署约束**：V84 与 V85 同为结构级不兼容变更，须合并进**同一个停写窗口**，
> 顺序均为「先备份 → 执行迁移 → 确认新表可读 → 再发布应用」；反向顺序会让对应模块查不到表。
> 应用启动不自动执行迁移（Flyway 已移除）。`db/migration/README.md` 历史口径说明已补正
> V84 头注释里「全仓唯一裸名表」这一当时快照表述。

**新增偏离登记**（承接 §3.2 第 9 项；§3.1 的 P3 八项不扩容）：

| # | 项 | 不做理由 |
|---|---|---|
| 10 | 删除 `CreateGroup` / `UpdateGroup` 零引用死代码 | 二者是 `scaffold-common` 对外暴露的 Bean Validation 分组契约；仓内零引用不等于无下游使用，删除属公共 API 破坏。已在类 javadoc 明示零引用状态与保留理由 |
| 11 | 补齐 `v-hasPermi` / `utils/permission.js` 按钮级权限指令 | 需前端权限模型 + 菜单按钮种子 + 40+ 视图改造，属架构级新增能力而非规范对齐；建议单独立项 |

### 3.8 §2 散落结论成文（2026-11-11 追加批次）

§7 的 12 个编号与文末「附：本次取证的局限」已在前述批次全部处置，但 **§2 正文**
仍有 6 处写着「需成文 / 仅需在文档中固化 / 待确认」却从未进入 §7 清单的散落结论，
此前各批次未给处置。本批次逐项取证并收口（**实测，非设计预期**）：

| # | 原始出处 | 实测结论 | 处置 |
|---|---|---|---|
| 1 | §2.2.2 方法命名「属可保留差异，**但需成文**」 | 本仓 Controller 主流 `list/get/create/update/delete`，`getInfo` / `add` / `edit` 全仓零命中；`remove(...)` 有 7 处历史混用（`JobController` + 6 个 `system` Controller），均只影响 Java 方法名 | 成文 `docs/NAMING.md` **§6.4** 的 REST 动词映射表与「新代码统一 / 存量不动」条款；登记第 12 项偏离 |
| 2 | §2.6「仅需在文档中固化本仓不使用 Mapper XML，并在**评审规则**中写明新增 SQL 的唯一写法」 | 全仓 0 个 `@Mapper` 接口、0 个 Mapper XML；但存在 4 个合法 `*Mapper.java`（DSL 行映射内部类 3 + 代码生成类型映射 1），**文件名匹配会误伤** | 成文 `docs/NAMING.md` **§6.5** 的四条评审 checklist；并把纸面结论升级为 `check-directory.py` 的 **D13 硬性门禁**（只查 `mapper/` 目录与 `*Mapper.xml` 路径 + 断言 `SqlTables.java` 存在） |
| 3 | §2.8 O-2 `db/migration` 为 Flyway 遗留目录名、易误导 | Flyway 运行期确已移除；该目录现为 53 个版本文件的「版本化结构源」、`build_reference_modular.py` 的输入，改名会断历史可追溯性与三份派生件路径 | 在 `db/migration/README.md` 补「目录名沿革」说明；登记第 14 项偏离（不改为若依的 `sql/`） |
| 4 | §2.2.4「`sys_oauth` 名称过泛」 | **全仓不存在 `sys_oauth` 表**：实际为 `sys_openapi_client`（V48）、`sys_oauth2_token`（V64）、`sys_oauth2_consent`（V68）三张命名精确的表；原结论系当时检索快照的表述误差 | 取证更正，无需改表；在 `docs/NAMING.md` §6.3 以 58 张表口径替换旧的 51 张快照 |
| 5 | §2.2.5 views 根 4 个散落 View、路由 name / path 残留 | 4 个根级 `XxxView.vue` 各有 `router/index.js` 唯一动态 import；`oauth2token.js` / `NotificationTemplate.vue` 两处已修；`system/announcement` 已补 `name`，`system/message` 是刻意的 query 保留 redirect；复数 path 已普及（远不止原记 2 处） | 校正 `docs/NAMING.md` §6.3 的 `views/{Module}XxxView.vue` 漂移表述；登记第 13 项偏离 |
| 6 | §2.8 O-6 空 `__tests__/` 4 目录 + 失效 `npm test` | 4 目录均为空、**git 完全未追踪**（仅工作区残留），`package.json` 的失效 `test` 脚本已删，假绿问题已闭环；空目录不构成仓库内容 | 不删除、不纳入仓库变更；登记第 15 项偏离 |

> **口径说明**：第 4 项是原始清单自身的取证误差，不是「刻意不做」的差异，故不占偏离编号；
> 本批次把 4 项真正需要「保留 + 说明理由」的结论登记为第 12–15 项。

**新增偏离登记**（承接 §3.7 第 10 / 11 项；§3.1 的 P3 八项不扩容）：

| # | 项 | 不做理由 |
|---|---|---|
| 12 | 把 Controller / Service 方法改为若依 CRUD 命名（`getInfo` / `add` / `edit` / `remove`） | REST 动词已与权限码动作词、HTTP 方法、URL 复数资源成体系；改名不动契约但牵动调用方与审计文案，零功能收益。7 处存量 `remove` 与新代码 `delete` 的边界已在 `NAMING.md` §6.4 写明 |
| 13 | 把 `views/` 根 4 个 `XxxView.vue` 归位到模块目录并统一 `router.path` 单复数 | 归位需同步 router 动态 import 与菜单 `component_path` 字符串，属纯搬迁且会动路由可见行为；复数形态已是当前主流，强行改回单数才是破坏性变更 |
| 14 | `db/migration/` 改名为若依的 `sql/` | 纯形式改动，但会切断 53 个版本文件的历史可追溯性、生成脚本输入路径与三份派生 SQL 中的版本注释；已在 README 写明「Flyway 遗留命名 / 当前语义为版本化结构源」消除误导 |
| 15 | 删除 4 个空 `__tests__/` 目录 | 目录为空且 git 未追踪，删除只是本机清理、不产生仓库变更；失效 `npm test` 脚本移除后已无假绿风险。待前端单测立项时直接复用占位即可 |

**D13 的边界**：它是 §2.6「评审规则」的可执行化，而非新的风格偏好 —— 规则只断言
路径形态与 `SqlTables.java` 存在性，**零内容扫描、不读 Java 源码、不按 `*Mapper.java`
文件名匹配**，因此不会误伤 DSL 内部类，也不增加门禁耗时。行为回归
`test_check_directory.py` 由 29 例增至 **33 例**，全仓 `script-tests` 由 **92 例增至 96 例**。

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
   **96 例全绿**（`test_check_naming.py` 4 + `test_check_cluster_layout.py` 14 +
   `test_check_permission.py` 12 + `test_check_directory.py` 33 +
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
**D1–D13**：新增包根 / 模块、配置散落到非启动模块、根级 CSS 复活、规范文档索引漏挂或死链、
子模块写死版本、main/test 源码树长出新的一级目录、测试期配置越界，以及
**新增 Mapper XML / `mapper/` 目录或删除 `SqlTables.java`**（D13）都会阻断；脚本只读、
不参与编译与运行时。`test_check_directory.py` 早期用例曾随精简移除，2026-11-10 已重建为
29 例并纳入统一门禁，2026-11-11 随 D13 扩为 33 例。
又补齐两处门禁闭环：修复 `scripts/check-api-contract.py` 在 14 模块拆分后仍指向单体
`ROOT/src/main/java` 的过期目录假设（后端扫描恒为空、197 条前端调用全为假阳性，现按
`scaffold-*/src/main/java` 自动发现源码根），并新增 `scripts/check-all.py` 统一门禁入口，
把约定 / 目录 / 契约 / 校验器单测编排成一次可复现运行（历史「全量 86 例」口径已失效）。
2026-11-10 起统一门禁为 **7 步**，新增 P1 权限码一致性硬性步骤；现存行为回归
**96 例**（`test_check_naming.py` 4 + `test_check_cluster_layout.py` 14 +
`test_check_permission.py` 12 + `test_check_directory.py` 33 +
`test_check_api_contract.py` 33），由 `unittest discover` 自动发现，新增 `test_*.py` 无需改编排。
若依的响应体命名、Controller 集中、包根重命名、接口前缀命名等属于**契约级或大规模搬迁**差异，
在不改变接口行为与避免无关重写的约束下明确保留，并集中于本文件说明，供后续版本按独立变更评估。

**2026-11-10 批次 6 收口**：§3 已升级为正式偏离清单 —— §3.1 登记 P3 八项「明确不做」、
§3.2 给出 P1-4 增别名的逐文件复核结论（判定不落地）、§3.3 列出 P0-2 / P2-1 / P2-2 / P2-3
四项待确认与排期状态；§1 补齐模块数口径复核，`docs/NAMING.md` §6.3 与
`docs/DIRECTORY.md` §4 同步交叉引用。至此《若依规范对齐差异清单与重构方案_20261109.md》
§7 中除 P0-2 与 P2 四项外，各条目均已有明确处置结论；当时剩余四项须逐项取得确认后再落地
（该四项已由批次 7 与批次 8–12 全部落地，见下）。

**2026-11-10 批次 7 收口**：P0-2（`V83` 前向追加 `sys_menu.created_by` + `MenuTypeRules`）、
P2-1（契约门禁支持多路径数组 + 6 个从属 Controller 新旧双挂）、P2-2（`V84` 幂等重命名为
`sys_alert_rule`）均已落地，部署约束见 §3.5；行为回归由 81 例增至 89 例。

**2026-11-10 批次 8 收口（P2-3 首模块）**：把 D12 从「仅 `scaffold-system`」扩展为
多模块逐表校验，`docs/DIRECTORY.md` §4 改为「一模块一张簇表」，并完成 `scaffold-cms`
15 个 Java 文件向 `article` / `category` / `support` 三簇的搬迁；D12 实跑
**39 个子域簇 / 2 个模块**，编译 BUILD SUCCESS，行为回归由 89 例增至 **92 例**。
本批次零 HTTP 契约、零表结构、零前端改动，详见 §3.6。当时余下 4 模块按「逐模块独立提交、
每模块单独编译与门禁」推进，已由批次 9–12 全部收口（见下）。

**2026-11-10 批次 9 收口（P2-3 第二模块）**：`scaffold-im` 12 个 Java 文件收敛为
`conversation` / `message` / `support` 三簇，§4 追加第 3 张模块簇表；D12 实跑
**42 个子域簇 / 3 个模块**，编译 BUILD SUCCESS，行为回归口径仍为 **92 例**（本批次未增删用例）。
校验脚本无需改动，印证批次 8 的多模块扩展是可复用的：后续每模块只需追加一张 §4 表 + 搬迁源码。

**2026-11-10 批次 10 收口（P2-3 第三模块）**：`scaffold-workbench` 33 个 Java 文件收敛为
`overview` / `report` / `dashboard` 三簇，§4 追加第 4 张模块簇表；D12 实跑
**45 个子域簇 / 4 个模块**，编译 BUILD SUCCESS（补 1 处同包拆分后的隐式引用 import），
行为回归口径仍为 **92 例**（本批次未增删用例）。`report` 簇内 4 个零依赖纯函数统一归
`domain/rule/`、导出中心模板归 `adapter/`，`DashboardTemplateDO` 归 `domain/model/`；
三簇之间零跨簇引用，H1 包级无环。余下 `kafka` / `workflow` 两模块由批次 11 / 12 收口（见下）。

**2026-11-10 批次 11 收口（P2-3 第四模块）**：`scaffold-kafka` 25 个 Java 文件收敛为
`monitor` / `consumer` / `gateway` / `support` 四簇，§4 追加第 5 张模块簇表；D12 实跑
**49 个子域簇 / 5 个模块**，编译 BUILD SUCCESS（一次通过），行为回归口径仍为 **92 例**
（本批次未增删用例）。该模块原为「消费侧 9 个顶层扁平包 + 生产侧 `gateway/` 5 个子目录」
的双子系统形态，拆包后 3 处同包隐式引用补 import、2 处 H1 环风险通过落位与 javadoc
降级规避；`application.yml` 注释旧 FQN 与 `scaffold-kafka/pom.xml` 头部结构描述同步更新。
P2-3 最后一模块 `scaffold-workflow` 由批次 12 收口（见下）。

**2026-11-10 批次 12 收口（P2-3 收官，`scaffold-workflow`）**：54 个 Java 文件收敛为
`instance` / `definition` / `monitor` / `approval` / `engine` / `admin` / `support` 七簇，
§4 追加第 6 张模块簇表；D12 实跑 **56 个子域簇 / 6 个模块**，编译 BUILD SUCCESS（一次通过），
行为回归口径仍为 **92 例**（本批次未增删用例）。该模块原含与 `workflow/` 平级的独立根包
`approval/`，本批次整体并入 `workflow/approval/` 并注销 `EXPECTED_ROOTS` 中的 `approval`，
彻底消除历史包根例外；搬迁前预扫描补 10 处同包隐式引用，并首次处理 **Flowable BPMN
delegate 运行时类名字符串**（2 处随包迁移同步）。至此《若依规范对齐差异清单与重构方案_20261109.md》
P2-3 五模块 **139 个 Java 文件全部收敛**，批次 8–12 共 10 笔独立提交，全部零 HTTP 契约 /
零表结构 / 零前端改动。

**2026-11-11 §2 散落结论成文批次（收官）**：§7 编号与文末「取证局限」之外，§2 正文另有
6 处「需成文 / 待确认」未纳入排期的散落结论，本批次逐项取证收口（详见 §3.8）：
① REST 方法命名成文 `NAMING.md` §6.4；②「新增 SQL 唯一写法」成文 §6.5 **并升级为
`check-directory.py` 的 D13 硬性门禁**（29 → 33 例回归，全仓 92 → 96 例）；
③ `db/migration/README.md` 补目录名沿革；④ 更正原清单「`sys_oauth` 名称过泛」的取证误差
（该表不存在）；⑤ 校正 `NAMING.md` §6.3 的 views 命名口径漂移；⑥ 确认空 `__tests__/`
目录未被 git 追踪、假绿已闭环。新增第 12–15 项刻意偏离，P3 八项不扩容。
至此原始清单 §2 全部结论均已有明文档案或可执行守护。

### 当前终态（截至 §2 散落结论成文批次）

本节上方各批次段落按时间顺序记录当时的推进状态，其中「剩余 / 余下 / 待确认」等措辞均指
该批次收口时的快照，**不代表当前仍有待办**。《若依规范对齐差异清单与重构方案_20261109.md》
§7 全部条目现均已处置完毕：P0-1 / P0-2、P1-1 / P1-2 / P1-3、P2-1 / P2-2 / P2-3 已落地，
P1-4 经逐文件复核判定不落地，登记为第 9 项刻意偏离（见 §3.2 结论与
`docs/NAMING.md` §6.3）；P3 八项在 §3.1 登记为「明确不做」。文末「附：取证局限」
另由 §3.7 全量复核收口，派生出 V85 表名归一、15 处 `@Slf4j` 统一、`USAGE.md` 口径修正
三项落地与第 10 / 11 项偏离登记；§2 正文残余的 6 处散落结论由 §3.8 成文收口，
派生出 D13 门禁与第 12–15 项偏离登记。**原始清单已无未处置条目**。

当前校验口径：统一门禁 **7 步**（硬性项全部 `EXIT=0`，`directory` 步骤含 D1–D13）、
行为回归 **96 例全绿**、D12 **56 个子域簇 / 6 个模块**、SQL 迁移 **V32~V85 共 53 个**、
后端根聚合 POM 15 个 reactor 项编译 BUILD SUCCESS。契约门禁为信息性步骤，稳定输出
`matched=196 / backend_only=180 / frontend_only=1`，唯一 `GET /v3/api-docs` 为既有已知风险。

仍需人工决策、不属于代码待办的仅有四项：① V84 / V85 两个表重命名须合并同一停写窗口，
「先执行迁移、后发布应用」并先备份；② P2-1 别名处于新旧双挂过渡态，前端 URL 何时切换；
③ 前端 `vite build` 因本机缺 `node_modules` 与 lock 漂移未执行；④ 本地提交推送时机
（全程未执行 `git push`、未推 `main`）。
