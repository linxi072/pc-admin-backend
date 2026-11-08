# 目录结构规范

> 适用范围：`java-admin-framework/`（17 个 Maven 模块）与 `pc-admin-vue3-vite/src/`。
> 权威判据：根 `pom.xml` 的 `<modules>`、`scripts/check-conventions.py`（H1–H5 / W1–W4）、
> `scripts/api-contract.json`（交互契约）、`scripts/check-api-contract.py`。
> 本文档只描述**目录与包的归属约定**，模块职责见 [MODULES.md](MODULES.md)，
> 与若依约定的差异见 [RUOYI-ALIGNMENT.md](RUOYI-ALIGNMENT.md)。

## 1. 后端目录骨架

```
java-admin-framework/
├── pom.xml                     父 POM：仅聚合 + 版本管理（子模块不写第三方版本号）
├── scripts/                    约定校验与交互契约（不参与编译）
│   ├── check-conventions.py    包级环 / 重复 FQN / 依赖方向 / 数据类放置 / 模块 DAG
│   ├── api-contract.json       统一响应 Result<T> 的唯一事实来源
│   ├── check-api-contract.py
│   └── test_check_conventions.py
├── docs/                       架构与规范文档（本目录）
└── scaffold-<name>/            17 个模块，目录结构统一
    ├── pom.xml
    ├── src/main/java/com/acme/scaffold/<包根>/
    ├── src/main/resources/     仅 admin 放运行期配置与 Flyway 迁移
    └── src/test/java/
```

`scaffold-admin` 是**唯一可部署 / 启动模块**，持有 `FrameworkApplication`、全部
`application*.yml`、`logback-spring.xml` 与集成/E2E 测试；其它模块不得放启动类或
运行期配置，也不得反向依赖 admin（W3 守护）。

## 2. 模块 ↔ 包根对照

| 模块 | 顶层包根 | 说明 |
|---|---|---|
| common | `common.*`（27 个一级子包）+ `jooq.*` | `jooq` 为历史第二包根，见 §3 例外 |
| realtime / kafka / codegen / plugin / ai / cms / im / approval | 同名包根 | 与模块名一致 |
| security / system / monitor / job / workbench / workflow | 同名包根 | 与模块名一致 |
| **framework** | `config.*` / `observability.*` / `monitor.*` / `chaos.*` | **无 `framework` 包**，历史例外；`monitor` 下分 detection / observability |
| **admin** | `com.acme.scaffold`（启动类） | 无业务包，测试按 `test`/`integration`/`e2e`/`consistency` 分 |

> 包根与模块名不一致的两处例外均有 `package-info.java` 写明原因。模块边界由 Maven 依赖
> 与 H5 DAG 强制，不靠包名推断；**不得**为「看起来更整齐」而重命名包根。

## 3. `scaffold-common` 一级子包职责表

采用「扁平特性子包」组织（非若依八层分包）。下表按职责分组，括号内为含子目录的 Java 文件数，
用于判断新类应落在何处；数量随迭代变化，不用于校验。

| 组 | 子包 | 职责 |
|---|---|---|
| 契约 | `api`(10) / `api.dto` | Result / PageResult / KeysetPageResult / CursorCodec / PagingRules |
| 契约 | `constant`(1) | 跨模块稳定字面值，见 `CommonConstants` 收录标准 |
| 契约 | `error`(2) / `exception`(2) / `i18n`(4) / `validation`(4) | 错误码、异常、国际化、校验 |
| 运行时 | `context`(2) / `web`(1) / `controller`(1) / `security`(1) / `sensitive`(2) / `tenant`(12) | 上下文、MVC、脱敏、多租户 |
| 运行时 | `observability`(2) / `resilience`(2) | 链路与容错 |
| 能力 | `audit`(14) / `event`(9) / `idempotency`(13) / `id`(1) | 审计、领域事件、幂等、ID |
| 能力 | `export`(5) / `importer`(15) / `messaging`(11) / `notification`(5) | 导入导出、消息总线、通知渠道 |
| 能力 | `plugin`(12) / `oauth2`(1) / `text`(1) / `tree`(1) / `util`(1) | 插件 SPI、OAuth2、文本/树/通用工具 |

**单一事实来源铁律**（落位决策树）：

1. 跨模块稳定字面值且尚无归属 → `common.constant.CommonConstants`；
2. 已有归属的直接引用，**禁止复制**：错误码 → `error.CommonErrorCode`；
   分页护栏 → `api.PagingRules`；文本截断 → `text.TextUtils`；成功码 → `constant.CommonConstants.SUCCESS_CODE`；
3. 纯函数规则 → 对应能力包的 `rules` 子包（`*Rules` 不抛业务异常、不触 DB）；
4. 数据结构 → `dto` / `vo` / `domain` / `entity` / `model`（H4 硬规则，`dto` 不得放特性包根）；
5. 跨模块协作 → `common` 定义 `*Port`，由拥有数据的模块提供 `@Component` 适配器；
6. 业务代码 → 归属其领域模块，不因「名字听起来相关」放进别的模块。

`common.jooq` 例外：位于 common 模块但包根为 `com.acme.scaffold.jooq`，只放无状态 DSL 工具，
不放业务实体、不定义表结构。重排包根需改动全仓数百处 import，属高风险零收益搬迁，保留。

## 4. 前端目录骨架

```
pc-admin-vue3-vite/src/
├── main.js / App.vue            应用入口
├── api/                        约 69 个平铺 JS 文件：API 客户端 + http.js（统一请求封装） + __tests__/
├── router/  store/  layout/    路由 / 状态 / 外壳
├── components/                 跨页面共享组件
├── composables/  utils/  i18n/ 组合式函数 / 工具 / 国际化（各带 __tests__/）
├── styles/                     base.css（全局基础）+ theme-tokens.css + element-theme.css
├── views/                      13 个特性目录（system / workflow / monitor / oauth2 / ...）
└── micro-apps/                 微前端示例（hello / notes）
```

**样式加载顺序**（`main.js` 固定，不可调换）：`element-plus` 基础样式 → dark css-vars →
`./styles/base.css` → `./styles/theme-tokens.css` → `./styles/element-theme.css`。
本轮把原先与 `styles/` 并列的根级 `styles.css` 归位为 `styles/base.css`，文件内容与
加载顺序均未改变，仅路径变化。所有样式表集中在 `styles/`，新增全局样式不再放到 `src/` 根。

前端落位约定：HTTP 请求一律放 `src/api`，经 `src/api/http.js` 的 axios 实例统一注入
鉴权头与拆包（`apiDoc.js` 另有独立实例，仅用于拉取 OpenAPI JSON，不参与业务请求）；
页面放 `views/<特性>/`；可复用逻辑放 `composables/` 或 `utils/`；纯函数需带
`src/**/__tests__/*.test.js` 的 `node:test` 用例。

> `src/api` 采用平铺而非若依的 `api/system/*.js` 分子目录。分目录需同步改 69 个文件与
> 全部 import，属大规模搬迁，保留并在差异文档说明。

## 5. 禁止事项

1. 不在特性包根放 `*DTO` / `*VO` / `*Request` / `*Response`（H4 阻断）；
2. 不新增顶层包根；两处历史例外不再扩大；
3. 不在多个模块定义同一 FQN（H2 阻断），不制造跨模块包级环（H1）与模块环（H5）；
4. 不在非 admin 模块放启动类、`application*.yml` 或 `logback*.xml`；
5. 不在子模块 POM 写第三方依赖 `<version>`，版本统一在父 POM `dependencyManagement`；
6. 不为对齐若依而改 `Result<T>` 包络、HTTP 路径、菜单种子或前端路由；
7. 不把已有单一事实来源的常量 / 工具复制第二份。

## 6. 守护机制

| 约定 | 守护方式 |
|---|---|
| 包级环 / 重复 FQN / 分层 / 数据类 / 模块 DAG | `python3 scripts/check-conventions.py`（EXIT=0） |
| 统一响应与错误码 | `scripts/api-contract.json` + 后端 `ApiContractConsistencyTest` + 前端 `result.test.js` |
| 常量字面值不漂移 | `CommonConstantsTest` |
| 前端目录与构建可解析 | `env -u NODE_OPTIONS npm test` + `npx vite build` |
| 目录结构约定 D1–D7（包根白名单 / 启动类位置 / 配置归属 / 全局样式 / 常量与文档齐全 / 版本管理） | `python3 scripts/check-directory.py`（EXIT=0），行为回归 `scripts/test_check_directory.py` 13 例；脚本自身单测与既有脚本测试一起跑 `python3 -m unittest discover -s scripts -p "test_*.py" -v` |
| 全量编译与回归 | `mvn -o -f pom.xml clean test`（从根聚合 POM，禁止 `-f scaffold-admin/pom.xml`） |

新增目录 / 包后必须同步更新本文档、`docs/MODULES.md` **与 `scripts/check-directory.py` 的
`EXPECTED_ROOTS` 白名单**，否则目录校验会阻断（D1）。只增文件不增目录时无需改文档。
