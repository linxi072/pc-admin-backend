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
| 组织方式 | common / framework / system / quartz / generator / admin；父 POM 仅聚合与版本管理；admin 唯一 Web 入口 | 17 个 `scaffold-*` 模块；admin 已是唯一启动模块；父 POM 已聚合 + 版本管理 | 修 `docs/MODULES.md` 的 12→17 模块与「规划中未创建」漂移；父 POM 收敛 `zstd-jni` 版本；新增 `docs/DIRECTORY.md`；补 5 份 `package-info.java` | 部分对齐，结构差异保留 |
| 职责边界 | 模块内 domain / mapper / service 分层；framework 集中全局装配 | 扁平特性子包；framework 类散在 `config`/`observability`/`monitor.*`/`chaos` | 用包级说明 + 目录规范写明边界与例外，不改包名 | 文档对齐，代码保留 |
| 公共能力 | `common/constant` 常量包、`common/core/domain` 响应基类、`common/utils` 工具包 | 已有 `api`/`error`/`text`/`tree`/`util` 等单一事实来源，但**无 constant 包**，字面值散落 16 处 | 新增 `common/constant/CommonConstants`，16 处存量改委托且值逐字不变；新增 `CommonConstantsTest` | 已对齐 |
| 配置管理 | admin 集中 `application.yml` / `application-druid.yml` / `logback.xml`；配置类集中 common.config / framework.config | admin 已集中 yml 与 logback；43 个 `@ConfigurationProperties` 分散在 11 个模块特性包 | 生产 yml 旧 `jobrunr:` 前缀仅加注释标注（不改键值）；配置类归属写入目录规范，不搬迁 | 部分对齐，配置类保留 |
| 交互规范 | AjaxResult / TableDataInfo / BaseController.startPage；`@Log`/`@DataScope` 注解；`src/api` 集中请求 + 统一 axios 拦截器拆包 | `Result<T>` 包络 + `PageResult`/`KeysetPageResult` + `PagingRules`；`api-contract.json` 前后端共同断言；前端 `src/api/http.js` 统一注入鉴权头与拆包 | **不改**：响应体改名会破坏契约测试；仅把前端根级 `styles.css` 归位到 `styles/base.css` | 保留差异，路径归位 |

## 2. 本轮已落地项（零契约风险）

| # | 维度 | 改动 | 行为影响 |
|---|---|---|---|
| 1 | 公共能力 | 新增 `common/constant/CommonConstants.java` | 纯新增常量持有者，无运行时副作用 |
| 2 | 公共能力 | `PagingRules` / `PageQuery` / `KeysetPageQuery` / `Result` / `FormSubmissionService` / `ReportService` / `JobCreateDTO` / `JobUpdateDTO` / `RuntimeMetricsCollector` / `NotificationDigestService` / `AuthService` / `ProfileDeviceService` / `WebConfig` / `ProfileController` 共 16 处字面值改委托 | 分页上限、时间格式、头像前缀、成功码逐字不变；公共常量字段保留 |
| 3 | 组织方式 | 新增 5 份 `package-info.java`（`common`、`common.constant`、`common.api`、`jooq`、`config`） | 仅 Javadoc；约定脚本剥离注释后不产生类型/依赖，H1–H5 不受影响 |
| 4 | 组织方式 | 新增 `docs/DIRECTORY.md`；重写 `docs/MODULES.md`；修 `docs/README.md` 索引漂移 | 纯文档，修复死链与过时口径 |
| 5 | 配置管理 | 父 POM 增 `zstd-jni.version` **并在 `dependencyManagement` 注册该依赖**；`scaffold-kafka/pom.xml` 去掉硬编码 `<version>` | 解析到的版本仍是 `1.5.5-11`；定向构建曾报 `version is missing`，补齐注册后复验通过 |
| 6 | 配置管理 | `application-prod.yml` 旧 `jobrunr:` 段加注释 | 不增删改任何配置键值 |
| 7 | 组织方式 | `src/styles.css` → `src/styles/base.css`，`main.js` import 与 8 处注释同步 | CSS 内容与加载顺序不变，仅路径归位 |
| 8 | 公共能力 | 新增 `CommonConstantsTest` | 纯测试，锁定字面值与委托一致性 |

## 3. 明确保留的若依差异（尚未对齐）

| 差异 | 为何不改 | 影响 |
|---|---|---|
| `IXxxService` + `XxxServiceImpl` 命名 | 本仓用单实现类 + `*Port` 防腐层；重命名会改 Bean 名与全部注入点 | 仅命名风格 |
| `AjaxResult` / `TableDataInfo` / `BaseController` | `Result<T>` 由 `api-contract.json` 固定，前后端一致性测试共同断言；替换会改响应 JSON 字段 | **契约级**，不可改 |
| Controller 集中到 `scaffold-admin` | 本仓 Controller 随业务模块分布；搬迁会改组件扫描与 17 模块依赖方向 | 部署结构差异 |
| `com.acme.scaffold.framework` 包根 | 历史类分布在 4 个顶层包；重命名牵动数百处 import | 形式差异 |
| `common.jooq` 第二包根 | 同上 | 形式差异 |
| 43 个 `@ConfigurationProperties` 分散在特性包 | 集中会改配置类包路径与 `@EnableConfigurationProperties` 引用 | 组织差异，功能等价 |
| `src/api` 69 文件平铺（若依按模块分目录） | 需同步改 69 个文件与全部 import | 大规模搬迁 |
| `src/api/http.js` 与 `apiDoc.js` 两个 axios 实例 | 后者 baseURL 为 `/`、独立超时与鉴权逻辑，仅用于拉 OpenAPI JSON；合并会改变文档请求行为 | 语义不同，刻意保留 |
| `common/util` / `text` / `tree` 三个单文件工具包未合并为 `utils` | 合并会改 FQN | 形式差异 |
| 生产 `application-prod.yml` 中 `jobrunr:` 旧前缀 | JobRunr 6.x 实际读 `org.jobrunr.*`；该段本就不生效，直接删除会改变配置文件内容，需先在预发验证 | **待验证的真实配置项**，见 §5 |
| `@Log` / `@DataScope` 等注解命名 | 本仓以 `AuditAspect` + `DataScopeConditions` + Port 实现等价能力 | 命名风格 |

## 4. 回归验证要点

1. **契约**：`scripts/check-api-contract.py` + 后端 `ApiContractConsistencyTest` + 前端 `result.test.js`，
   确认 `Result<T>` 包络、成功码 `"0"`、错误码未变。
2. **常量委托**：`CommonConstantsTest` 断言 `MAX_PAGE_SIZE=100`、两种时间格式、头像前缀、
   成功码，并验证 `PagingRules.MAX_PAGE_SIZE` / `PageQuery.MAX_SIZE` /
   `KeysetPageQuery.MAX_SIZE` / `Result.success().code()` 与常量一致。
3. **分页与时间格式**：既有 `PagingRulesTest` / `PageResultTest` / `JsonTruncatorTest` 与
   报表、任务、运行指标、登录设备、通知摘要相关 Service 测试全绿。
4. **前端样式**：`npx vite build` 成功（验证 `styles/base.css` 路径可解析），
   人工核对暗色主题变量与全局基础样式仍生效；`npm test` 133 例全绿。
5. **约定门禁**：`check-conventions.py` EXIT=0（新增 `package-info.java` 不得引入 H1–H5 违规）。
6. **全量回归**：`mvn -o -f pom.xml clean test` 从根聚合 POM 跑完 18 个 reactor 项。

## 5. 兼容性风险与未解决项

| 风险 | 等级 | 说明 / 建议 |
|---|---|---|
| `application-prod.yml:52` 旧 `jobrunr:` 前缀 | 中 | 该段在 JobRunr 6.x 下不生效，生产实际取框架默认值；本轮只加注释未改键值。建议在预发环境确认 dashboard/后台线程数符合预期后，单独提交配置修正 |
| 前端样式路径变更 | 低 | 已用 `git mv` 保留文件历史，全仓核实仅 1 处 import + 8 处注释引用；构建门禁验证路径解析 |
| `package-info.java` 与跨模块同包 | 低 | 5 份说明放在无同包冲突的位置；`com.acme.scaffold.monitor.*` 在 framework 与 monitor 两模块共存，刻意未加包级说明，避免 split-package 的 `package-info` 冲突 |
| 常量集中后的编译期耦合 | 低 | 6 个目标模块原本均已依赖 `scaffold-common`，未新增 Maven 依赖边，H5 DAG 不变 |
| 文档与 POM 再次漂移 | 低 | `docs/MODULES.md` 已写明「以根 POM 为准并修表」；后续新增模块须同步更新 `DIRECTORY.md` |

## 6. 结论

本轮完成的是**目录结构与公共能力的低风险对齐**：把散落的通用字面值收敛到若依约定位置、
用包级说明与目录规范固化职责边界、修正文档漂移、统一版本管理、归位前端样式文件。
后续追加 `scripts/check-directory.py`（D1–D7）把这些约定从「文档要求」升级为可执行守护：
新增包根 / 模块、配置散落到非启动模块、根级 CSS 复活、规范文档死链、子模块写死版本都会阻断，
且脚本只读、不参与编译与运行时。
若依的响应体命名、Controller 集中、包根重命名、接口前缀命名等属于**契约级或大规模搬迁**差异，
在不改变接口行为与避免无关重写的约束下明确保留，并集中于本文件说明，供后续版本按独立变更评估。
