# 目录结构规范

> 适用范围：`java-admin-framework/`（14 个 Maven 模块）与 `pc-admin-vue3-vite/src/`。
> 权威判据：根 `pom.xml` 的 `<modules>`、`scripts/check-conventions.py`（H1–H5 / W1–W4）、
> `scripts/api-contract.json`（交互契约）、`scripts/check-api-contract.py`（前后端契约）、
> `scripts/check-directory.py`（D1–D11 目录约定）、
> `scripts/check-cluster-layout.py`（D12 子域簇结构与本文档 §4 表一致性）；
> 统一执行入口 `scripts/check-all.py`。
> 本文档只描述**目录与包的归属约定**，模块职责见 [MODULES.md](MODULES.md)，
> 与若依约定的差异见 [RUOYI-ALIGNMENT.md](RUOYI-ALIGNMENT.md)。

## 1. 后端目录骨架

```
java-admin-framework/
├── pom.xml                     父 POM：仅聚合 + 版本管理（子模块不写第三方版本号）
├── scripts/                    约定校验与交互契约（不参与编译）
│   ├── check-conventions.py    H1–H5 / W1–W4：包级环 / 重复 FQN / 依赖方向 / 数据类 / 模块 DAG
│   ├── check-directory.py      D1–D11 目录约定守护
│   ├── check-cluster-layout.py D12：§4 子域簇表 ≡ 磁盘现状（文件数 / 层集合 / DO 落位）
│   ├── check-permission.py     P1：菜单权限码 ⊆ 接口权限码（注解 / sys_api_resource 种子）
│   ├── check-api-contract.py   前后端接口契约静态对称（多模块源码根扫描）
│   ├── check-all.py            统一门禁入口：编排上述各项 + scripts 单测
│   ├── api-contract.json       统一响应 Result<T> 字段口径的唯一事实来源
│   └── test_*.py               校验器行为回归 92 例（naming / cluster-layout / permission / directory / api-contract）
├── docs/                       架构与规范文档（本目录，全部须挂 docs/README.md 索引）
└── scaffold-<name>/            14 个模块，目录结构统一
    ├── pom.xml
    ├── src/main/java/com/acme/scaffold/<包根>/   一级目录只允许 com（admin 例外允许 db）
    ├── src/main/resources/     仅 admin 放运行期配置与 db/init 手工初始化脚本
    └── src/test/               （2026-11-08 起测试文件已移除；D9/D11 守护规则保留）
```

`scaffold-admin` 是**唯一可部署 / 启动模块**，持有 `FrameworkApplication`、全部
`application*.yml` 与 `logback-spring.xml`（测试源码已于 `087acdb` 全量移除，
当前无集成/E2E 测试）；其它模块不得放启动类或运行期配置，也不得反向依赖 admin（W3 守护）。

## 2. 模块 ↔ 包根对照

| 模块 | 顶层包根 | 说明 |
|---|---|---|
| common | `common.*`（27 个一级子包）+ `mybatis.*` | `mybatis.dsl` 为数据访问层单一包，见 §3 |
| realtime / kafka / cms / im | 同名包根 | 与模块名一致 |
| **common** | `common.*` + `mybatis.*` + `plugin.*` | `plugin.*` 由并入的 plugin 能力带入 |
| **codegen** | `codegen.*` + `ai.*` | `ai.*` 由并入的 AI 脚手架带入 |
| **workflow** | `workflow.*` + `approval.*` | `approval.*` 由并入的审批门户带入 |
| security / system / monitor / job / workbench / workflow | 同名包根 | 与模块名一致 |
| **framework** | `config.*` / `observability.*` / `monitor.*` / `chaos.*` | **无 `framework` 包**，历史例外；`monitor` 下分 detection / observability |
| **admin** | `com.acme.scaffold`（启动类） | 无业务包；`src/main/java/db/` 保留为数据库引导组件例外位（Flyway Java 迁移已移除） |

> 上述包根与 admin 的 `db` / 测试包根例外，均在 `scripts/check-directory.py` 的
> `EXPECTED_ROOTS`、`MAIN_ROOT_EXCEPTIONS`、`TEST_ROOT_EXCEPTIONS` 中登记并由 D1/D8/D9 守护；
> `package-info.java` 仅覆盖 common 的 3 个包、`mybatis` 与 framework/config 共 5 处（不是每个例外包都有）。
> 模块边界由 Maven 依赖与 H5 DAG 强制，不靠包名推断；**不得**为「看起来更整齐」而重命名包根。

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

`common.mybatis` 例外：位于 common 模块但包根为 `com.acme.scaffold.mybatis`，只放无状态 DSL 工具，
不放业务实体、不定义表结构。重排包根需改动全仓数百处 import，属高风险零收益搬迁，保留。

## 4. 子域分层骨架（多模块）

各模块先按**业务子域**切分为一级子包（簇），簇内再按分层骨架组织。
legacy 的全局 `controller/` `service/` `dto/` `entity/` `rules/` 五个扁平包已于 2026-11-09
在 `system/` 全量打散并删除；此后禁止在簇根再出现这五类包（D1 只守护模块级顶层包根，
子簇包名不受 D1 约束，但须遵循本节落位口径）。

> **骨架范围（2026-11-10 P2-3 批次更新）**：本节按「**一模块一张子域簇一览表**」组织，
> `scripts/check-cluster-layout.py`（D12）依表标题中的 `` `scaffold-xxx` `` 推导模块包根并逐表校验；
> 未标注模块名的旧式标题按 `scaffold-system` 解析。当前已收敛：
> **`scaffold-system` 36 簇** + **`scaffold-cms` 3 簇** + **`scaffold-im` 3 簇** +
> **`scaffold-workbench` 3 簇**（合计 45 簇 / 4 模块）。
> `kafka` / `workflow` 仍按特性子包组织，按《若依规范对齐差异清单与重构方案_20261109.md》
> P2-3 逐模块推进，每完成一模块再向本节追加一张簇表。
> 本节不代表全仓已按簇骨架归一。

簇内分层：

| 层 | 目录 | 放什么 |
|---|---|---|
| 接口层 | `api/` | `*Controller`，只做参数绑定与结果包装 |
| 应用层 | `application/` | `*Service`，用例编排与事务边界 |
| 领域层 | `domain/model/` | `*DO`（H4 强制：包路径须含 `domain`/`entity`/`model`/`po`） |
| 领域层 | `domain/rule/` | `*Rules` 纯函数规则，不触 DB、不抛业务异常 |
| 领域层 | `domain/repository/` | `*Port` 端口接口（出站集成与统计端口，如 `dashboard/domain/repository/SystemStatsPort`） |
| 契约层 | `dto/` | `*DTO` / `*VO` / `*Request` / `*Response` / `*Query`；**一律簇根 `dto/`**，不设 `api/dto/` |
| 适配层 | `adapter/` | 持久化 / 远程 / MQ 适配器与 `*Port` 实现 |
| 配置层 | `config/` | `@Configuration` 与配置属性 |

落位口径：

1. 关联表 DO 归**拥有外键的聚合根**所在簇 —— `sys_user_role` / `sys_user_org` / `sys_user_post`
   → `user`；`sys_role_menu` / `sys_role_api` / `sys_role_data_scope*` / `sys_role_column_permission` → `role`；
2. 跨簇共享的包私有支撑类提升为 `public` 并放入中性簇 `support/`（`CurrentUserSupport` /
   `FileResponseSupport` / `RemindLimitRules`），不复制第二份；
3. 通用渠道规则下沉 `scaffold-common`（`common/notification/ChannelNormalizeRules`，与 `Channel` 枚举同域）；
4. 表由哪个模块独占写入，DO 就归该模块 —— `sys_monitor_sample` → `scaffold-monitor` 的 `monitor/entity`；
5. 簇间依赖只允许单向，`domain/model` 不得反向依赖 `application`（H3 方向约束 + H1 包级无环）；
6. 端口接口（`*Port`）一律落 `domain/repository/`，实现落 `adapter/`（先例：
   `dashboard/domain/repository/SystemStatsPort` ↔ `dashboard/adapter/`、`notification/domain/repository/MailClient` ↔ `notification/adapter/`）；
7. 子特性（如 `notification/unsubscribe/`）沿用同一分层骨架，自带 `api/` + `application/` + `config/` + `domain/rule/`；
8. `system/` 下已无任何簇根 `controller/` / `service/` / `entity/` / `port/` / `rules/` 目录，
   也不设 `api/dto/` —— 全部子域簇已按本节骨架归一（2026-11-09 复检为空）。

子域簇一览（`scaffold-system`，括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `announcement`(14) | api/application/domain{model,rule}/dto | 公告发布与已读、催办 |
| `apiresource`(10) | api/application/domain{model}/dto | API 资源登记与启动自扫描 |
| `audit`(2) | api/application | 审计日志查询（数据在 `common.audit`） |
| `bootstrap`(1) | application | 启动期自检与 API 资源自动登记 |
| `config`(8) | api/application/domain{rule,model}/dto | 参数配置 |
| `dashboard`(12) | adapter/api/application/domain{model,repository,rule} | 运营看板与统计聚合 |
| `dict`(12) | api/application/domain{model}/dto | 字典类型与字典项 |
| `export`(14) | api/application/config/dto | 导出任务中心与导出接口 |
| `file`(14) | api/application/domain{model,rule}/enums | 文件上传下载与去重 |
| `form`(17) | api/application/domain{model,rule}/dto | 低代码表单定义与提交 |
| `importer`(8) | api/application/config | 导入中心与导入任务 |
| `log`(5) | api/application/config | 操作日志与登录日志查询 |
| `maskstrategy`(9) | api/application/domain{model,rule}/dto | 脱敏策略 |
| `menu`(8) | api/application/domain{model,rule}/dto | 菜单树与菜单授权；`domain/rule` 为 P0-2 菜单类型取值域 |
| `message`(12) | api/application/domain{model,rule}/dto | 站内信与已读回执 |
| `notification`(45) | adapter/api/application/config/domain{repository,rule}/dto + `unsubscribe/` 子特性 | 通知**投递**（渠道适配与发送）；端口按 `domain/repository`（与 `dashboard/domain/repository/SystemStatsPort` 同先例），退订子特性自带 api/application/config/domain/rule |
| `notifydigest`(4) | application/domain{model,rule}/job | 通知摘要聚合 |
| `notifylog`(6) | api/application/domain{model,rule} | 通知记录与查询 |
| `notifymgmt`(13) | api/application/domain{model,rule}/dto | 通知中心与通知模板管理 |
| `notifypref`(6) | api/application/domain{model,rule}/dto | 通知偏好 |
| `notifyquiet`(6) | api/application/domain{model,rule}/dto | 免打扰时段 |
| `notifysupport`(1) | application | 通知共享支撑 |
| `oauth2`(2) | consent/{adapter,domain/model} | 授权同意页 |
| `online`(14) | adapter/api/application/domain{model,rule}/dto | 在线会话与登录限制 |
| `openapi`(8) | adapter/api/application/domain{model}/dto | 开放平台客户端与凭据 |
| `org`(6) | api/application/domain{model}/dto | 组织机构树 |
| `password`(2) | adapter/domain{model} | 密码历史 |
| `permission`(5) | adapter/api | 数据/字段受控资源与权限适配器 |
| `post`(10) | api/application/domain{model,rule}/dto | 职务 |
| `profile`(11) | api/application/dto | 个人中心（资料、设备、改密、展示偏好） |
| `role`(16) | api/application/domain{model}/dto | 角色与数据范围、字段权限 |
| `smslog`(6) | api/application/domain{model,rule} | 短信发送记录 |
| `support`(3) | （平铺） | 跨簇共享支撑类 |
| `tenant`(22) | api/application/domain{model,rule}/dto | 多租户管理与隔离 |
| `token`(1) | adapter | 令牌版本适配器 |
| `user`(12) | adapter/api/application/domain{model}/dto | 用户与用户关联表 |

`scaffold-cms` 于 P2-3 首个批次完成收敛，按「文章 / 栏目」两个聚合根加一个共享支撑簇划分：

子域簇一览（`scaffold-cms`，括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `article`(7) | api/application/domain{model,event}/dto | 文章 CRUD、发布态流转；`domain/event` 为文章发布领域事件 |
| `category`(7) | api/application/domain{model}/dto | 树形栏目 CRUD 与栏目树构建 |
| `support`(1) | （平铺） | 跨簇共享的内容发布态 / 栏目启用态枚举 `CmsStatus` |

> `event` 属业务分层名，不是 legacy 扁平包，故不列入 C5 `FORBIDDEN_FLAT`；领域事件按
> `domain/event/` 落位，与 `domain/model/` 并列。`article/application` 对
> `category/domain/model/CmsCategoryDO` 的跨簇引用为单向，符合落位口径第 5 条
> （`domain/model` 不反向依赖 `application`、H1 包级无环）。
> 跨簇共享支撑类只保留 `support/` 一份，禁止复制（§4 落位口径第 2 条）。

`scaffold-im` 按「会话 / 消息」两个聚合根加一个共享支撑簇划分：

子域簇一览（`scaffold-im`，括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `conversation`(6) | api/application/domain{model}/dto | 会话创建（P2P / 群组）、成员管理、会话列表与未读计数 |
| `message`(5) | api/application/domain{model}/dto | 消息发送、已读回执、消息分页查询 |
| `support`(1) | （平铺） | 跨簇共享的会话 / 消息状态常量 `ImStatus` |

> 两个 Service 分别引用对方的 DO（`ImConversationService` 用 `ImMessageDO` 生成会话最后一条
> 消息，`ImMessageService` 用 `ImConversationDO` / `ImConversationMemberDO` 校验成员），但方向
> 恒为 `application → 对方 domain/model`，`domain/model` 不反向依赖 `application`，包级依赖图
> 仍是 DAG（H1 无环）。`ImStatus` 只在 `support/` 保留一份，禁止复制。

`scaffold-workbench` 按「工作台概览 / 报表 / 看板模板」三个聚合根划分。原 `report/` 下的
`csv/` `export/` `stats/` `rules/` 是纯函数与导出适配，统一收敛到 `domain/rule/` 与 `adapter/`：

子域簇一览（`scaffold-workbench`，括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `overview`(4) | api/application/dto | 工作台首页统计卡片、周期型 / 快照型指标对比 |
| `report`(23) | adapter/api/application/domain{rule}/dto | 报表查询、下钻、CSV / XLSX 导出；`domain/rule` 收纳 4 个零依赖纯函数 |
| `dashboard`(6) | api/application/domain{model}/dto | 数据看板模板 CRUD 与布局配置 JSON 校验 |

> `ReportCsvWriter` / `ReportSheets` / `ReportStats` / `ReportDrillRules` 四个零 Spring、零 DB 依赖的
> 纯函数统一落 `report/domain/rule/`；`ReportExportTask`（`@Component`，实现 `ExportTask` 模板）
> 落 `report/adapter/`，其对 `report/application/ReportService` 的依赖为 `adapter → application`
> 单向。H3 按包路径最后一段判层，`adapter` / `rule` / `dto` / `api` 均为 neutral，不参与方向
> 校验；三簇之间零跨簇引用，包级依赖图仍是 DAG（H1 无环）。`DashboardTemplateDO` 由
> `dashboard/domain/` 迁入 `dashboard/domain/model/`，满足 C4。`report/dto/` 与 `dashboard/dto/`
> 因簇名与原特性包名一致，路径与 package 声明保持不变。

## 5. 前端目录骨架

```
pc-admin-vue3-vite/src/
├── main.js / App.vue            应用入口
├── api/                        约 69 个平铺 JS 文件：API 客户端 + http.js（统一请求封装）
├── router/  store/  layout/    路由 / 状态 / 外壳
├── components/                 跨页面共享组件
├── composables/  utils/  i18n/ 组合式函数 / 工具 / 国际化（__tests__/ 目录当前为空）
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
页面放 `views/<特性>/`；可复用逻辑放 `composables/` 或 `utils/`。
当前 4 个 `src/**/__tests__/` 目录为空、前端无 `node:test` 用例，`package.json` 已移除
失效的 `test` 脚本；前端门禁改用 `check-sfc.mjs` / `i18n-leak-scan.mjs` / `vite build`。

> `src/api` 采用平铺而非若依的 `api/system/*.js` 分子目录。分目录需同步改 69 个文件与
> 全部 import，属大规模搬迁，保留并在差异文档说明。

## 6. 禁止事项

1. 不在特性包根放 `*DTO` / `*VO` / `*Request` / `*Response`（H4 阻断）；
2. 不新增顶层包根，历史例外不再扩大；`src/main/java` 一级目录只允许 `com`（admin 例外 `db`），`src/test/java` 若存在则包根须与 main 白名单对齐（D8/D9）；
3. 不在多个模块定义同一 FQN（H2 阻断），不制造跨模块包级环（H1）与模块环（H5）；
4. 不在非 admin 模块放启动类、`application*.yml` 或 `logback*.xml`；
5. 不在子模块 POM 写第三方依赖 `<version>`，版本统一在父 POM `dependencyManagement`；
6. 不为对齐若依而改 `Result<T>` 包络、HTTP 路径、菜单种子或前端路由；
7. 不把已有单一事实来源的常量 / 工具复制第二份；
8. 不在 `scaffold-system` 根级重建 `controller/` / `service/` / `dto/` / `entity/` / `rules/`
   扁平包（2026-11-09 已全量打散并删除）；新类一律按 §4 归入对应子簇的分层目录。

## 7. 守护机制

| 约定 | 守护方式 |
|---|---|
| 包级环 / 重复 FQN / 分层 / 数据类 / 模块 DAG（H1–H5 / W1–W4） | `python3 scripts/check-conventions.py`（EXIT=0） |
| 标识符命名约定 N1–N4 | `python3 scripts/check-naming.py`（EXIT=0），行为回归 `scripts/test_check_naming.py` 4 例 |
| 目录结构约定 D1–D11 | `python3 scripts/check-directory.py`（EXIT=0），行为回归 `scripts/test_check_directory.py` 29 例 |
| 子域簇结构与 §4 表一致 D12 | `python3 scripts/check-cluster-layout.py`（EXIT=0，多模块逐表校验），行为回归 `scripts/test_check_cluster_layout.py` 14 例 |
| 菜单权限码 ↔ 接口权限码一致 P1 | `python3 scripts/check-permission.py`（EXIT=0），行为回归 `scripts/test_check_permission.py` 12 例；规则详见 `docs/NAMING.md` §6 |
| 前后端接口契约静态对称 | `python3 scripts/check-api-contract.py`（信息性；`--strict` 才阻断），行为回归 `scripts/test_check_api_contract.py` 33 例 |
| 统一响应与错误码字段口径 | `scripts/api-contract.json`（唯一事实来源）+ `scripts/check-api-contract.py`（路由静态对称）；`ApiContractConsistencyTest` / `result.test.js` / `CommonConstantsTest` 均为已移除的历史测试资产 |
| 前端目录与构建可解析 | `node scripts/check-sfc.mjs <file.vue>` + `node scripts/i18n-leak-scan.mjs src` + `npx vite build`（`npm test` 当前不可用） |
| 全量编译与校验器回归 | `mvn -o -f pom.xml clean compile`（从根聚合 POM，禁止 `-f scaffold-admin/pom.xml`）+ `python3 -m unittest discover -s scripts -p "test_*.py"`（92 例） |

**统一门禁入口**：`python3 scripts/check-all.py` 把 `check-conventions.py`（硬性）、
`check-naming.py`（硬性）、`check-directory.py`（硬性）、`check-cluster-layout.py`（硬性）、
`check-permission.py`（硬性）、`check-api-contract.py`（信息性，不阻断）与
`scripts/test_*.py` 行为回归（硬性，当前 **92 例**）编排成一次运行，共 **7 步**，
逐项打印 EXIT 与耗时；硬性项任一失败返回 `EXIT=1`，仅信息性项失败返回
`EXIT=0` 但列出风险提示。常用参数：`--only conventions,directory`、`--skip-tests`、
`--quiet`；受限环境（沙箱阻止 `tempfile` 默认目录探测）加 `--test-tmpdir <dir>`。
新增 `scripts/test_*.py` 由 `unittest discover` 自动发现，无需改编排。
各步骤仍可单独执行，本脚本只做编排、不改文件、不参与编译与运行时。

D1–D12 一览（详见 `scripts/check-directory.py` 与 `scripts/check-cluster-layout.py` 文件头 docstring）：

| 编号 | 守护内容 |
|---|---|
| D1 | 按模块登记顶层包根白名单 `EXPECTED_ROOTS` |
| D2 | 仅 `scaffold-admin` 可在 `com.acme.scaffold` 根级放启动类 |
| D3 | 仅 admin 可持 `application*.yml` / `logback*.xml`（main 资源） |
| D4 | 前端 `src/` 根不得有 CSS，`src/styles/` 三份全局样式齐全 |
| D5 | `common/constant/CommonConstants.java` 必须存在 |
| D6 | 三份规范文档齐全，`docs/README.md` 索引引用齐全 |
| D7 | 子模块 POM `<dependencies>` 不得写具体第三方版本 |
| D8 | `src/main/java` 一级目录只允许 `com`；admin 例外允许 `db`（数据库引导组件保留位） |
| D9 | `src/test/java` 若存在，包根与 main 白名单对齐；admin 9 个历史包根 + `BaseIntegrationTest.java` 例外 |
| D10 | `docs/` 下所有文档须挂进 `docs/README.md` 索引，且索引相对链接不得是死链 |
| D11 | `src/test/resources` 仅 admin 可持 `application*.yml` / `logback*.xml`（当前测试资源已移除） |
| D12 | §4「子域簇一览」表 ≡ 磁盘现状：每簇文件数、二级层集合、各层子目录集合双向一致；DO 不得直挂 `domain/`；簇根不得重建 `controller/` `service/` `entity/` `rules/` `port/` 扁平包 |

新增目录 / 包后必须同步更新本文档、`docs/MODULES.md` **与 `scripts/check-directory.py` 的
`EXPECTED_ROOTS` 白名单**，否则目录校验会阻断（D1）；新增 main/test 一级目录还须同步
`MAIN_ROOT_EXCEPTIONS` / `TEST_ROOT_EXCEPTIONS`（D8/D9）。改动 `scaffold-system` 子域簇的
目录结构（增删层 / 移文件）还须同步 §4「子域簇一览」表，否则子域簇校验会阻断（D12）。
`docs/` 下新增文档须同步补 `docs/README.md` 索引，否则 D10 阻断。只增文件不增目录时无需改文档。
