# 编码规范与目录结构约定

> 本文档是项目「编码风格与目录结构」的**单一事实来源**。目标：在不引入额外构建依赖（兼容离线 `mvn -o` 与 CI）的前提下，用一份约定 + 一个零依赖校验脚本 `scripts/check-conventions.py`，把目录分层、命名、依赖方向、无环规则固化下来，防止架构随迭代漂移。
>
> 适用代码：`java-admin-framework` 后端 `src/main/java`（`com.acme.scaffold`）。前端工程 `pc-admin-vue3-vite` 的格式基线见文末「前端附则」。

---

## 1. 设计原则

| 原则 | 含义 |
|---|---|
| 按特性分包（package-by-feature） | 顶层包 = 业务能力域（如 `security` / `system` / `workflow`），同特性的代码聚在一起，而非按层横向切片。 |
| 层子包可选但推荐 | 特性模块内，类多时按职责拆 `controller` / `service` / `config` / `domain` 等子包；类少时可暂平铺，增长后再拆。 |
| 单一职责 | 一个类只做一件事；一个包只承载一类职责。 |
| 依赖单向、无环 | 依赖只能从上层指向底层，禁止反向与循环，包依赖图必须为 DAG。 |
| 约定可校验 | 所有硬规则由 `check-conventions.py` 自动检查，CI 门禁拦截。 |

---

## 2. 目录与包结构约定

### 2.1 顶层业务域（一级包）

`com.acme.scaffold` 下的每个一级包是一个**业务能力域**，全小写、单数业务名：

```
com.acme.scaffold
├── security        # 认证、授权、令牌、加解密、防重放、验证码、在线会话、数据权限
├── system          # 系统管理：用户/角色/菜单/字典/文件/登录日志等
├── workflow        # 工作流引擎对接、任务认领、设计器
├── job             # 调度任务管理（JobRunr）与具体 Job
├── monitor         # 可观测性、运营监控、探针
├── workbench       # 工作台、报表
├── codegen         # 代码生成器
├── observability   # 指标/链路追踪（通用，非业务）
├── realtime        # WebSocket 实时推送
├── kafka           # Kafka 顺序消费与分区键约定
├── common          # 跨域公共件：统一响应/异常/分页/幂等/审计/导出/导入/i18n/脱敏/树构建
├── config          # 全局 Spring 配置装配（数据源/安全/Web/Redis/JobRunr…）
└── jooq            # jOOQ 动态 DSL 与 Record 映射
```

> 新增业务能力时，先开一级业务域包；除非是「全项目通用」才放进 `common` / `config` / `jooq`。

### 2.2 层子包（二级，按职责）

特性模块内推荐按下表拆子包。**已按层子包组织**的模块（2026-10-17 由 `check-conventions.py` W2 概览核实）：`codegen`、`common`、`job`、`kafka`、`monitor`、`realtime`、`security`、`system`、`workbench`、`workflow`；仍为**平铺**的是三个通用包 `config`（23 类）/ `jooq`（7 类）/ `observability`（12 类）。

> 平铺的通用包允许**按关注点**拆子包而不带层名——如 `config.web` / `config.jooq` / `config.redis` / `config.cache` / `config.datasource` / `config.jobrunr` / `config.observability`、`jooq.dsl` / `jooq.mapping` / `jooq.paging`。这类子包**不是层**，因此：
> - H3 依赖方向判定只认包路径**最后一段**是否落在 `TIER` 表内（domain/entity/model 为 0 层，config 为 1，service 为 2，controller/web 为 3），最后一段非层 token 的按 neutral 跳过；
> - W1 错层检测对 `*Config` 类做了豁免：只要包路径任意一段含 `config` 即不告警，故 `config.web.SecurityConfig` 属正常放置；
> - W2 概览中这类模块仍计为「平铺」，属预期，不需强行补 `controller`/`service` 等层名。

| 子包 | 承载内容 | 典型后缀 | 禁止做的事 |
|---|---|---|---|
| `controller` | HTTP 入口：参数校验、权限注解、调 service、包装 `Result`/`PageResult` | `*Controller` | 写业务逻辑、直接操作 DB |
| `service` | 业务逻辑、事务边界、编排；接口 + `Impl` | `*Service` / `*ServiceImpl` / `*Manager` / `*Executor` | 依赖 `controller`、直接拼 HTTP |
| `config` | Spring Bean 装配、第三方客户端、安全/Web/数据源配置 | `*Config` / `*Configuration` | 写业务规则；依赖 `controller`/`web` |
| `domain` / `entity` | 实体 `DO`、枚举、值对象、领域对象 | `*DO` / `*Entity` / `*Model` | 反向依赖 `service`/`controller`/`config` |
| `dto` / `vo` | 请求/响应/查询对象 | `*DTO` / `*VO` / `*Command` / `*Query` / `*Request` / `*Response` | 放业务规则（用 `Rules`/`Service` 承载） |
| `port` / `adapter` | 防腐层、外部系统对接、SPI | `*Port` / `*Provider` / `*Adapter` / `*Registry` / `*Resolver` | — |
| `assembler` | 实体 ↔ DTO 互转 | `*Assembler` | 含业务判断 |
| `rules` / `policy` | 纯函数式业务规则（便于离线单测） | `*Rules` / `*Policy` / `*Validator` | 依赖 Spring 容器 |
| `bootstrap` | 启动期 Runner / 数据播种 | `*Runner` / `*Bootstrap` | 含长耗时业务 |

### 2.3 各层职责与依赖方向（硬性）

```
controller  ──▶  service  ──▶  domain / port
    │              │
    └──────────────┘
         config 装配上述各层（可引用 service/domain 以声明 Bean，但不得引用 controller/web）
```

- `controller` → `service` → `domain`：允许且推荐。
- `domain` / `entity` / `model`：**不得**依赖 `controller` / `service` / `config`（最底层，零向上依赖）。
- `service` / `application`：**不得**依赖 `controller` / `web`。
- `config`：**不得**依赖 `controller` / `web`（可依赖 `service`/`domain` 以装配 Bean）。
- 全局 **禁止包级循环依赖**（见 §5 校验）。

---

## 3. 命名规范

### 3.1 类命名（层承载后缀）

| 类别 | 后缀 | 示例 |
|---|---|---|
| 接口层 | `Controller` / `RestController` | `AuthController` |
| 业务层 | `Service`（接口） / `ServiceImpl`（实现） / `Manager` / `Executor` | `TokenVersionService` / `RefreshTokenServiceImpl` |
| 配置层 | `Config` / `Configuration` | `JooqConfig` / `SecurityConfig` |
| 领域/数据 | `DO` / `Entity` / `Model` | `SysFileDO` / `JobNextFire` |
| 传输对象 | `DTO` / `VO` / `Command` / `Query` / `Request` / `Response` | `JobCreateDTO` / `FileView` |
| 规则/策略 | `Rules` / `Policy` / `Validator` | `TokenVersionRules` / `ClaimRules` |
| 端口/适配 | `Port` / `Provider` / `Adapter` / `Registry` / `Resolver` | `JobSchedulerPort` / `SecretProvider` |
| 横切 | `Aspect` / `Filter` / `Interceptor` / `Listener` / `Handler` | `DataScopeAspect` / `TraceIdFilter` |
| 任务 | `Job` / `Runner` / `Scheduler` | `SessionCleanupJob` / `ApiResourceAutoSyncRunner` |
| 异常/错误 | `Exception` / `ErrorCode` | `BusinessException` / `FileErrorCode` |
| 配置属性 | `Properties` | `MultiDataSourceProperties` |

- 实体表映射对象统一用 `DO` 后缀（`SysXxxDO`），与 DTO/VO 区分。
- 纯函数式规则类用 `Rules`/`Policy`，保持无 Spring 依赖、可离线单测。
- 常量/枚举：`*Type` / `*Status` / `*Code`，枚举值用 `UPPER_SNAKE_CASE`。

### 3.2 方法命名

- 动词开头、小驼峰；查询用 `get` / `find` / `query` / `list`，命令用 `create` / `update` / `delete` / `save`，校验用 `verify` / `validate` / `check`，存在性用 `exists`。
- 布尔返回以 `is` / `has` / `can` 开头。
- 统一异常处理：业务异常抛 `BusinessException(ErrorCode, message)`，由 `GlobalExceptionHandler` 统一映射，不在业务代码里直接拼 HTTP 状态码。

### 3.3 包与文件

- 包名全小写、单数业务名，避免无含义缩写（`security` 而非 `sec`）。
- 一个 `.java` 文件一个顶级公共类型，文件名 = 类名。
- 接口与实现同包；`Impl` 后缀实现接口。

---

## 4. 代码格式基线

统一由根目录 **`.editorconfig`** 定义（IDE 安装 EditorConfig 插件即自动套用）：

- 字符集 UTF-8；换行 LF；文件末换行；去除行尾空白。
- Java：4 空格缩进；行宽 120；import 顺序由 IDE/Checkstyle 模板保证（先 `java.`/`javax.`，再 `com.acme.scaffold`，再第三方，静态 import 置底）。
- 前端（`.vue`/`.js`/`.ts`/`.json`/`.yml`）：2 空格缩进。
- 大括号同 K&R 风格（左括号不换行）；`switch`/`if` 单语句也建议加括号以免误改。

---

## 5. 自动化校验（零依赖）

校验脚本 `scripts/check-conventions.py` 仅用 Python 标准库，**无需联网、无第三方依赖**，可在本地与 CI 运行。

### 5.1 校验项

| 编号 | 级别 | 规则 |
|---|---|---|
| H1 | 硬（失败） | 包级循环依赖（强连通分量检测） |
| H2 | 硬（失败） | 重复的全限定类名 |
| H3 | 硬（失败） | 依赖方向不变量（见 §2.3 禁止项） |
| H4 | 硬（失败） | 数据类放置（对应脚本 `DATA_CLASS_RULES`）：`*DO` / `*PO` / `*Entity` 应在 `domain`/`entity`/`model`/`po` 子包，`*Model` 应在 `domain`/`entity`/`model` 子包；`*DTO` / `*VO` / `*Request` / `*Response` / `*Command` / `*Query` 应在 `dto`/`vo`/`domain`/`entity`/`model` 子包；**包路径任意一段命中即合规**（如 `workflow.dto.design` 不误报）。后缀匹配取最长优先，且类名须长于后缀本身（避免 `DO`、`Entity` 这类同名误伤） |
| W1 | 警告 | 疑似错层放置（类名层承载后缀 `LAYER_BEARING` 与包末段层 `TIER` **均为显式层且冲突**时才报，如 `*Controller` 落在 `*service` 包；`*Config` 类在包路径含 `config` 段时豁免；`*Filter`/`*Aspect`/`*Properties` 等无 tier 后缀不误报） |
| W2 | 警告 | 模块分层概览（已分层 vs 平铺，供人工参考，不强制）。当前实际输出：已分层 `codegen(13) / common(58) / job(28) / kafka(13) / monitor(30) / realtime(6) / security(85) / system(154) / workbench(26) / workflow(37)`；平铺 `config(23) / jooq(7) / observability(12)` |

退出码：`0` 仅含 W* 或无问题；`1` 存在任何 H* 硬违规。当前基线：扫描 447 个 Java 文件，H1–H4 全部通过，仅 W2 概览输出。

### 5.2 使用

```bash
# 项目根目录
python3 scripts/check-conventions.py                 # 默认扫描 src/main/java
python3 scripts/check-conventions.py --src src/main/java
python3 -m unittest discover -s scripts -p "test_*.py"  # 校验器行为测试（12 例）
```

### 5.3 CI 集成

已在 `.github/workflows/ci.yml` 接入 `convention-check` 作业（独立 job，先于构建执行）：

```yaml
convention-check:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: 校验器行为测试
      run: python3 -m unittest discover -s scripts -p "test_*.py"
    - name: 编码约定校验
      run: |
        if ! python3 scripts/check-conventions.py; then
          echo "::error::编码约定校验未通过（硬违规即阻断合并）。H1=包级循环依赖；H2=重复 FQN；H3=依赖方向；H4=数据类放置。请按上方输出逐项修复。"
          exit 1
        fi
```

任一 H* 硬违规即 job 失败并输出 `::error::` 失败原因注解，阻断合并；W* 警告不阻断。本地 `pre-commit` 亦可挂接，提交前自动拦截硬违规。

---

## 6. 前端附则（pc-admin-vue3-vite）

- 目录：`src/api`（按资源拆 `*.js`，如 `file.js`）、`src/views`（页面，按业务域子目录）、`src/router`（静态路由，菜单由后端驱动）、`src/components`（复用组件）。
- 格式：`src/**` 下统一 2 空格缩进（见 `.editorconfig`）。
- 接口约定：列表/分页走 `request`，下载走 `requestRaw`（blob 不拆包），上传走 `FormData`。
- 命名：视图 `XxxView.vue`、API 模块 `xxx.js`、组件 `XxxCard.vue` / `XxxDialog.vue`。

---

## 7. 例外与演进

- 个别历史类命名未套标准后缀（如 `Alert`、`Vault`、`Captcha`、`ClaimRules` 等语义化名称），属有意保留，不强制改名；新增代码须遵循 §3。
- 平铺模块当前仅剩通用包 `config` / `jooq` / `observability`，属允许状态：它们按**关注点**而非按层拆子包（见 §2.2）。特性模块（`monitor` / `job` / `workbench` / `codegen` / `security` / `system` / `workflow` / `common` / `kafka` / `realtime`）均已完成层子包拆分；当某平铺模块类数超 ~15 且出现多职责混杂时，应依 §2.2 拆层子包。
- 数据类放置已升级为硬规则 **H4**（原 W3 警告级）：`common.api` / `common.audit` / `common.idempotency` / `security.token` / `security.log` / `system.file` / `system.notification` 等处的 9 个历史放置类已于 V1.0.2 第二轮拆分全部归位至 `dto` / `domain` 子包，具备硬规则条件；新增数据类未按 §2.2 放置时将直接阻断合并。
- 本约定随评审演进；任何修改须同步更新本文档与 `check-conventions.py` 的 `TIER` / `FORBIDDEN` / `LAYER_BEARING` / `DATA_CLASS_RULES` 四张表。

---

> 更新日期：2026-10-17（本轮按 `scripts/check-conventions.py` 实际规则与运行输出核对：修正「平铺模块」清单为 `config`/`jooq`/`observability` 三通用包，并补充「通用包可按关注点拆子包、不带层名」及其在 H3/W1/W2 下的判定口径；H4 描述补齐 `*PO` 与后缀最长匹配/长度门槛；补入顶层包 `kafka` 及 `common` 的新增子职责；标注当前基线 447 文件、H* 全通过、校验器自测 12 例）
