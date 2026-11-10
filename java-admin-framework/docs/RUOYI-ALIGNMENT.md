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
| `alert_rule` 表无模块前缀 | 需 `RENAME TABLE` + DSL 常量同步，属结构级变更 | **待评估**（P2-2） |

> **2026-11-10 复查落地**：按《若依规范对齐差异清单与重构方案_20261109.md》批次 1–2
> 修复了 2 条菜单权限码漂移（`system:department:read`→`system:org:read`、
> `system:api-resource:read`→`system:api:read`，见 `V32` 与 `V82`），新增
> `scripts/check-permission.py`（P1 硬性门禁，接入 `check-all.py` 第 5 步与 CI），
> 归一 2 处 Service 双类制、修正前端 3 处命名，并在 `docs/NAMING.md` §6 固化
> 术语 / 权限码 / 跨层命名口径。其余差异按上表结论保留。

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
   **81 例全绿**（`test_check_naming.py` 4 + `test_check_cluster_layout.py` 11 +
   `test_check_permission.py` 12 + `test_check_directory.py` 29 +
   `test_check_api_contract.py` 25）；`check-all.py` 第 7 步即调用该命令。
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
**81 例**（`test_check_naming.py` 4 + `test_check_cluster_layout.py` 11 +
`test_check_permission.py` 12 + `test_check_directory.py` 29 +
`test_check_api_contract.py` 25），由 `unittest discover` 自动发现，新增 `test_*.py` 无需改编排。
若依的响应体命名、Controller 集中、包根重命名、接口前缀命名等属于**契约级或大规模搬迁**差异，
在不改变接口行为与避免无关重写的约束下明确保留，并集中于本文件说明，供后续版本按独立变更评估。
