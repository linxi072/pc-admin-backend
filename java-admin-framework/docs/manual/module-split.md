# 模块拆分指南（V1.0.3）

> 本文是工程**多模块拆分**的单一事实来源：模块职责边界、依赖关系、命名规范、目录结构、核心配置与迁移步骤均以本文为准；包级目录/命名约定见《编码规范与目录结构约定》（`coding-standard.md`），总体架构见《架构手册》。
>
> 拆分思路参照 **RuoYi-Vue 的模块化单体**实践：公共件与框架装配下沉为基础模块，业务能力按功能域各自成模块，启动装配收敛到唯一的可部署模块；模块间依赖强制成 **DAG**，由 `scripts/check-conventions.py` 的 **H5 模块环检测**在 CI 门禁守护。

---

## 1. 背景与目标

V1.0.2 及以前，后端为**单 Maven 模块**（`com.acme:java-admin-framework`），全部 504 个主源码类平铺在一个 `src/` 下。随着模块数增长，出现三个结构性问题：

| 问题 | 表现 | 拆分后的解法 |
|---|---|---|
| 构建无边界 | 任何改动都触发全量编译；依赖方向仅靠包级约定软约束 | Maven 模块依赖硬约束 + H5 环检测 |
| 测试无归属 | 单测/集成测试混在一个 `src/test`，无法按模块独立回归 | 测试随模块归属，集成测试收敛到 admin |
| 部署无收敛 | 可执行 jar 与库代码同仓同模块 | 仅 `scaffold-admin` 产出可执行 jar |

**拆分目标**（验收口径）：

1. 各模块可**独立开发**（改一个模块不触碰其他模块源码）；
2. 各模块可**独立测试**（`mvn -o -pl <模块> -am test` 只跑本模块及依赖）；
3. 各模块可**独立部署**（库模块不产出部署物，部署物只有 `scaffold-admin` 的 fat jar）；
4. 行为**零回归**：拆分前后单测 852 例全绿、接口契约与错误码不变。

---

## 2. 模块清单与职责边界

| # | 模块（artifactId） | 层 | 职责边界（做什么 / 不做什么） | 主源码类数 | 单测数 |
|---|---|---|---|---|---|
| 1 | `scaffold-common` | 基础层 | **做**：统一响应/分页、错误码与全局异常、i18n 与校验消息、安全上下文端口、客户端 IP 解析、审计/幂等切面、导出/导入中心、脱敏、树构建、ID 生成、通知抽象、jOOQ 手写 DSL。**不做**：任何业务规则、任何 Spring Security 装配、任何具体表 CRUD | 76 | 175 |
| 2 | `scaffold-realtime` | 通信层 | **做**：原生 WebSocket 握手鉴权**端口**、会话管理、定向推送。**不做**：JWT 解析（经端口由 system 注入）、业务推送内容生产 | 13 | 12 |
| 3 | `scaffold-kafka` | 通信层 | **做**：Kafka 保序规则、原生消费者薄封装、监控查询。**不做**：spring-kafka 接入、业务消息语义 | 14 | 32 |
| 4 | `scaffold-codegen` | 工具层 | **做**：表元数据读取、CRUD 骨架代码生成。**不做**：代码落盘持久化、模板市场 | 11 | 24 |
| 5 | `scaffold-framework` | 框架装配层 | **做**：全局 Spring 配置（多数据源/jOOQ/Web/缓存/Redis/可观测）、指标与链路追踪、N+1 与慢查询监听。**不做**：业务逻辑、周期任务注册（已移入 job） | 43 | 76 |
| 6 | `scaffold-system` | 业务层 | **做**：RBAC（用户/角色/菜单/字典/参数/机构/岗位）、认证授权（JWT/刷新令牌/Token 版本/密码策略/防重放/限流/验证码/数据权限/登录服务/在线会话/登录日志）、系统业务（公告/站内信/文件/短信记录/导入导出编排）、WS 握手 JWT 适配器。**不做**：工作流、调度、报表 | 229 | 316 |
| 7 | `scaffold-monitor` | 业务层 | **做**：告警接收与收件箱、健康探针、监控指标查询。**不做**：jOOQ 监听探针（在 framework） | 32 | 34 |
| 8 | `scaffold-job` | 业务层 | **做**：任务定义管理、周期任务注册（`job.config.JobRunrConfig`）、具体 Job。**不做**：调度引擎装配（JobRunr starter 自动配置） | 28 | 30 |
| 9 | `scaffold-workbench` | 业务层 | **做**：工作台看板、报表查询与多 Sheet 导出。**不做**：报表数据采集作业（复用 job） | 23 | 47 |
| 10 | `scaffold-workflow` | 业务层 | **做**：Flowable 对接、任务认领/转办/完成、流程设计与部署。**不做**：自定义引擎、表单引擎 | 34 | 26 |
| 11 | `scaffold-admin` | 启动装配层 | **做**：应用入口、运行期配置（application*.yml / logback）、Flyway 迁移、全部集成/端到端测试、跨模块一致性守护测试。**不做**：业务逻辑、可被其他模块依赖 | 1 | 80 |

> 类数/单测数为 V1.0.3 拆分后实测值（`mvn -o clean test`，852 例全绿）。

### 2.1 与 RuoYi-Vue 划分的对应关系

| RuoYi-Vue-Plus | 本工程 | 说明 |
|---|---|---|
| `ruoyi-admin`（启动 + 配置 + IT） | `scaffold-admin` | 同为唯一可部署模块 |
| `ruoyi-common-core` / `-web` / `-mybatis` 等 | `scaffold-common` | 本工程 common 规模适中，暂不拆子模块（见 §8 演进指引） |
| `ruoyi-common-security`（LoginHelper/SecurityUtils） | `scaffold-system.security.*` + `scaffold-common.context` | 端口在 common、实现在 system（依赖倒置） |
| `ruoyi-modules-system` | `scaffold-system` | RBAC + 系统管理 + 登录（RuoYi 亦将登录置于 system） |
| `ruoyi-modules-workflow` | `scaffold-workflow` | 同 |
| `ruoyi-quartz` / `ruoyi-common-job` | `scaffold-job` | 调度引擎为 JobRunr 而非 Quartz |
| `ruoyi-monitor` | `scaffold-monitor` + `scaffold-framework` | 监控业务在 monitor，监听探针属框架装配 |
| `ruoyi-generator` | `scaffold-codegen` | 同 |
| （无对应） | `scaffold-kafka` / `scaffold-realtime` | 本工程特有的消息与实时通道 |

---

## 3. 依赖关系（模块 DAG）

```
scaffold-admin ──▶ 全部模块（唯一可部署模块，不被任何模块依赖）
        │
        ├─▶ scaffold-workflow ──▶ scaffold-monitor ──▶ scaffold-framework ──▶ scaffold-realtime ──▶ scaffold-common
        │                       ──▶ scaffold-system ──┘          │
        ├─▶ scaffold-job ───────▶ scaffold-monitor               │（framework 的 WebSocketConfig 注册 realtime 端点）
        ├─▶ scaffold-workbench ─▶ scaffold-system                │
        ├─▶ scaffold-codegen ───▶ scaffold-common ◀──────────────┘
        ├─▶ scaffold-kafka ─────▶ scaffold-common
        └─▶ scaffold-framework / scaffold-realtime / scaffold-common
```

**依赖矩阵**（行依赖列，✅ = 编译期依赖）：

| 模块 | common | realtime | kafka | codegen | framework | system | monitor | job | workbench | workflow | admin |
|---|---|---|---|---|---|---|---|---|---|---|---|
| common | - | | | | | | | | | | |
| realtime | ✅ | - | | | | | | | | | |
| kafka | ✅ | | - | | | | | | | | |
| codegen | ✅ | | | - | | | | | | | |
| framework | ✅ | ✅ | | | - | | | | | | |
| system | ✅ | ✅ | | | | - | | | | | |
| monitor | ✅ | | | | ✅ | ✅ | - | | | | |
| job | ✅ | | | | | ✅ | ✅ | - | | | |
| workbench | ✅ | | | | | ✅ | | | - | | |
| workflow | ✅ | ✅ | | | | ✅ | ✅ | | | - | |
| admin | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - |

**硬规则**（H5，CI 门禁）：

1. 模块依赖图必须成 DAG，禁止任何形式模块环（Maven 反应堆本身也无法成环，H5 提前在静态层拦截）；
2. 仅 `scaffold-admin` 可依赖全部模块；**任何模块不得依赖 `scaffold-admin`**（W3 告警）；
3. 底层模块（common/realtime/kafka/codegen）不得依赖业务模块。

---

## 4. 命名规范

### 4.1 Maven 坐标

| 项 | 规范 | 示例 |
|---|---|---|
| groupId | 与包根一致 `com.acme.scaffold` | `com.acme.scaffold` |
| artifactId | 统一 `scaffold-` 前缀 + 小写功能名（H5 附带 W4 校验） | `scaffold-system` |
| version | 与父 POM 对齐（`1.0.0`），子模块声明依赖时用 `${project.version}` | `${project.version}` |
| parent | 每个子模块声明父 POM `com.acme.scaffold:java-admin-framework:1.0.0` | 见各 `pom.xml` |

### 4.2 Java 包名（**保持不变**）

拆分**不做包名重排**：所有模块的包名仍为 `com.acme.scaffold.<特性>.*`（如 `scaffold-system` 模块内是 `com.acme.scaffold.security.*` 与 `com.acme.scaffold.system.*`）。理由：

- 模块边界由 **Maven 依赖**强制，包名无需跟随模块名，避免 500+ 文件的 package/import  churn 与回归风险；
- Spring 组件扫描以 `com.acme.scaffold` 根包为界，`scaffold-admin` 的 `@SpringBootApplication` 经 classpath 自动汇聚全部模块 Bean，**零显式配置**；
- `check-conventions.py` 的 H1–H4 包级校验跨模块全局生效，包结构约定不变。

### 4.3 类与文件命名

沿用《编码规范》§3（层承载后缀、`DO`/`DTO`/`VO` 语义、`Rules`/`Port` 等）。新增两处端口/适配器命名：

- 端口（被依赖方定义）：`realtime.port.HandshakeTokenAuthenticator`；
- 适配器（依赖方实现）：`security.jwt.JwtHandshakeAuthenticator`。

---

## 5. 目录结构（拆分后）

```
java-admin-framework/                       # 工程根 = 父 POM 所在目录
├── pom.xml                                 # 父 POM（packaging=pom）：modules + dependencyManagement + profiles
├── scaffold-common/                        # 基础层（零内部依赖）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/acme/scaffold/
│       │   ├── common/                     # api/error/exception/i18n/validation/audit/idempotency/
│       │   │                               #   export/importer/sensitive/tree/id/notification/controller
│       │   ├── common/context/             # 【新】安全上下文端口（原 security.context，依赖倒置）
│       │   ├── common/web/                 # 【新】ClientIpResolver（原 config.web）
│       │   └── jooq/                       # dsl/mapping/paging（手写动态 DSL）
│       └── resources/
│           ├── i18n/                       # messages*/validation* 消息束（原根 resources）
│           └── （原 `db/codegen/` + `jooq-codegen.xml`：方案C 已移除 jOOQ 代码生成层，见《开发手册》§8.1）
├── scaffold-realtime/                      # 通信层：WebSocket
│   └── src/main/java/com/acme/scaffold/realtime/
│       ├── websocket/ session/ service/ controller/ payload/ dto/
│       └── port/                           # 【新】HandshakeTokenAuthenticator 端口
├── scaffold-kafka/                         # 通信层：Kafka（rules/domain/port/adapter/service/config/controller）
├── scaffold-codegen/                       # 工具层：代码生成
├── scaffold-framework/                     # 框架装配层
│   └── src/main/java/com/acme/scaffold/
│       ├── config/                         # datasource/jooq/web/cache/redis/observability
│       ├── observability/                  # metrics/trace/export/recorder/service/controller/dto
│       └── monitor/detection/              # N+1 与慢查询监听（jOOQ ExecuteListener）
├── scaffold-system/                        # 业务层：系统管理 + 认证授权
│   └── src/main/java/com/acme/scaffold/
│       ├── system/                         # entity/service/controller/dto/file/notification/smslog/…
│       └── security/                       # jwt/token/password/antireplay/ratelimit/captcha/
│                                           #   permission/online/service/controller/config/log/error/…
│                                           #   context/（仅剩 SecurityContextFacadeImpl 适配器）
├── scaffold-monitor/                       # 业务层：运营监控（alert/health/service/controller/domain/dto）
├── scaffold-job/                           # 业务层：调度任务
│   └── src/main/java/com/acme/scaffold/job/
│       ├── domain/entity/dto/service/controller/port/handler/
│       └── config/                         # 【新】JobRunrConfig（原 config.jobrunr）
├── scaffold-workbench/                     # 业务层：工作台报表
├── scaffold-workflow/                      # 业务层：工作流（Flowable）
└── scaffold-admin/                         # 启动装配层（唯一可部署）
    └── src/main/
        ├── java/com/acme/scaffold/FrameworkApplication.java
        └── resources/
            ├── application.yml / application-local.yml / application-prod.yml
            ├── logback-spring.xml
            └── db/migration/ (V32~V59，V57 为 Java 类迁移) + db/reference/（DDL/DML 分类参考件 + by-module 模块化参考）
    └── src/test/java/com/acme/scaffold/
        ├── BaseIntegrationTest.java        # 集成测试基类（@SpringBootTest）
        ├── e2e/                            # 7 个端到端测试（*E2eTest）
        ├── integration/                    # 21 个集成测试（*IT）+ AbstractHttpIntegrationTest
        ├── consistency/                    # 【新】跨模块一致性守护（错误码消息键化 / 可观测配置 ↔ yml）
        ├── perf/ tools/                    # 性能基准、DB 连通性检查
        ├── security/AuthFlowIT.java        # 原 system 包内的 IT（随上下文迁移）
        └── workflow/LeaveApprovalWorkflowIT.java
```

---

## 6. 核心配置文件

### 6.1 父 POM（`pom.xml`）

| 段 | 内容 |
|---|---|
| `<modules>` | 11 个模块，按依赖序排列（Maven 会自动重排，声明序仅供阅读） |
| `<properties>` | `java.version=17`（编译基线，运行时 JDK 21）、flowable/jooq/jjwt/springdoc/jobrunr/kafka 版本、`skipITs=true`、otel 版本 |
| `<dependencies>`（继承） | lombok（optional）、**springdoc-openapi-starter-webmvc-ui**（各模块控制器统一用 OpenAPI 注解）、starter-test / security-test（test） |
| `<dependencyManagement>` | 非 Boot BOM 管理的第三方：jooq、jjwt×3、flowable、jobrunr、kafka-clients、spring-context-support、jakarta.mail、angus-mail、springdoc |
| `<build>` | failsafe（`*IT`，skipITs 可覆盖）；pluginManagement 托管 spring-boot-maven-plugin |
| `<profiles>` | `coverage`（JaCoCo，**按模块独立门禁**）、`otel`（默认关闭）；`jooq-codegen` profile 已随方案C 移除（数据访问统一为纯手写动态 DSL） |

### 6.2 模块 POM 依赖矩阵（第三方 + 内部）

| 模块 | 关键第三方依赖 | 内部依赖 |
|---|---|---|
| common | starter-web / starter-aop / starter-validation / starter-jooq、**spring-security-core**（异常映射） | - |
| realtime | starter-web / starter-websocket | common |
| kafka | starter-web、kafka-clients | common |
| codegen | starter-web | common |
| framework | starter-web / starter-aop / starter-data-redis / starter-actuator、springdoc | common、realtime |
| system | starter-web / security / oauth2-resource-server / validation / aop / actuator、jjwt×3、spring-context-support + jakarta.mail + angus-mail | common、realtime |
| monitor | starter-web | common、framework、system |
| job | starter-web、jobrunr-spring-boot-3-starter | common、monitor、system |
| workbench | starter-web | common、system |
| workflow | starter-web / starter-aop、flowable-spring-boot-starter-process | common、monitor、realtime、system |
| admin | mysql-connector-j（runtime）、flyway-mysql、starter-actuator、spring-boot-maven-plugin（repackage） | 全部模块 |

> jOOQ 运行库经 `scaffold-common` 传递获得（版本由父 POM 收敛）；`springdoc` 注解经父 POM 继承获得。

### 6.3 资源归属

| 资源 | 归属模块 | 说明 |
|---|---|---|
| `i18n/messages*`、`i18n/validation*` | scaffold-common | `I18nConfig` 以 `classpath:i18n/...` 声明，消息束须与配置同模块；运行期经 jar 合入最终 classpath |
| `db/migration/V32~V59` | scaffold-admin | Flyway 在启动模块执行，`spring.flyway.locations=classpath:db/migration`（其中 `V57` 为 Java 类迁移 `V57__backfill_tenant_id.java`） |
| `db/reference/*` | scaffold-admin | 参考 DDL/DML（非迁移；V1.0.3 拆分后位于 `scaffold-admin/src/main/resources/db/reference/`） |
| `jooq/*`（手写 DSL） | scaffold-common | `JooqTables` / `JooqWriters` / `JooqSorts` / `JooqKeysetPager` 等手写动态 DSL 基建（方案C 移除 codegen 后唯一数据访问方式） |
| `application*.yml`、`logback-spring.xml` | scaffold-admin | 运行期配置与日志 |
| `.env.example` | 工程根 | 环境变量样例（非 Maven 资源） |

---

## 7. 迁移与改造步骤（V1.0.3 已执行清单）

本次拆分由 `scripts/migrate-modules.py` 一次性完成机械迁移（幂等脚本，保留供追溯/复盘），随后人工完成解环重构与配置改造：

### 7.1 机械迁移（脚本）

1. **主源码**：`common/*`、`jooq/*` → scaffold-common；`config/{datasource,jooq,web,cache,redis,observability}`、`observability/*`、`monitor/detection` → scaffold-framework；`system/*`+`security/*` → scaffold-system；`workflow/job/workbench/codegen/kafka/realtime` 各自成模块；`FrameworkApplication` → scaffold-admin。
2. **测试**：按顶层包镜像迁移；`monitor/detection` 测试随框架层；跨模块测试（e2e/integration/perf/tools/基类/两个散落 IT）→ scaffold-admin。
3. **资源**：按 §6.3 归属迁移。
4. **import 重写**：`security.context.*` → `common.context.*`（26 个文件）；`config.web.ClientIpResolver` → `common.web.ClientIpResolver`。

### 7.2 解环重构（3 处依赖倒置 + 1 处职责归位）

拆分前顶层包存在 5 组环（common↔security、monitor↔config、observability↔config、realtime↔security、system↔security）。按「**能合并的合并、能倒置的倒置、能归位的归位**」处置：

| 环 | 手段 | 具体改动 |
|---|---|---|
| system ↔ security（RBAC 实体 + 登录链路双向引用 30+ 处） | **合并** | 两包同属「身份与系统管理」能力域，合入 `scaffold-system`（与 RuoYi 将登录置于 system 模块一致） |
| common ↔ security（`security.context` 被审计/幂等切面引用） | **倒置** | `SecurityContextFacade`/`CurrentPrincipal` 端口移入 `common.context`；`SecurityContextFacadeImpl` 适配器留在 `security.context`（common ← system，不成环） |
| common ↔ config.web（`ClientIpResolver` 被审计/限流/认证引用） | **归位** | 纯逻辑解析器移入 `common.web`；Bean 注册（`ClientIpConfig`）留 framework |
| realtime ↔ security（握手拦截器直接调 `JwtProvider`） | **倒置** | realtime 定义 `port.HandshakeTokenAuthenticator`；`security.jwt.JwtHandshakeAuthenticator` 实现之 |
| monitor/observability ↔ config（jOOQ 监听装配、OTel 配置互引） | **归位** | `config.jobrunr.JobRunrConfig` 移入 `job.config`（周期任务注册归调度模块）；`config.*`+`observability.*`+`monitor.detection` 同归 framework |
| security.config → realtime（**未使用的 import**） | **清理** | 删除 `SecurityConfig` 中无用 import（仅为注释提及） |

### 7.3 配置与工具改造

1. 新增 11 个模块 POM（见 §6.2 依赖矩阵），父 POM 收敛版本与通用依赖；
2. `check-conventions.py`：支持**多源码根自动发现**（`scaffold-*/src/main/java`），新增 **H5 模块依赖环检测**（解析父/模块 POM，Tarjan SCC）+ W3（禁依赖 admin）+ W4（命名前缀）；`--src` 显式模式保持单目录兼容；
3. 两个跨模块守护测试改为「**反应堆根发现**」（从 CWD 上溯找 `scaffold-common/pom.xml`），不再依赖 surefire 工作目录恰为项目根；
4. 跨模块守护测试（错误码消息键化、可观测配置 ↔ application.yml 一致性）迁入 `scaffold-admin` 的 `consistency` 包并补 import；
5. CI：convention-check 失败注解补 H5 说明；SCA 报告上传路径改 glob（多模块各自产出）。

### 7.4 验证门禁（全部通过）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 多模块编译 | `mvn -o clean test-compile` | ✅ 11 模块全过 |
| 单元测试 | `mvn -o clean test` | ✅ **852 例全绿**（与拆分前基线一致，零回归） |
| 编码约定 | `python3 scripts/check-conventions.py` | ✅ EXIT=0（H1–H5 零硬违规，507 文件 / 11 源码根） |
| 校验器自测 | `python3 -m unittest discover -s scripts` | ✅ 17 例全过（新增 5 例 H5） |

---

## 8. 独立开发 / 测试 / 部署指南

### 8.1 独立开发

- 改 `scaffold-system` 的认证逻辑：只需关注 `scaffold-system` 目录；其依赖（common/realtime）接口稳定，不受影响。
- 新增业务模块：仿照既有模块建目录 + POM（artifactId 遵循 `scaffold-` 前缀），在父 POM `<modules>` 登记，按需声明内部依赖；**提交前跑 check-conventions.py**，H5 会阻止你引入模块环。
- 跨模块调用只能经**编译期依赖**（禁止反射/字符串类名绕过）；确需解耦时用**端口倒置**（参考 `HandshakeTokenAuthenticator`）。

### 8.2 独立测试

```bash
MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

$MVN -o -pl scaffold-system -am test      # 只测 system 及其依赖（common/realtime）
$MVN -o -pl scaffold-common test          # 单模块（依赖已安装时可用，省去 -am）
$MVN -o clean test                        # 全反应堆（提交前必跑）
$MVN -o verify -DskipITs=false            # 含 MySQL 集成测试（*IT 均在 scaffold-admin）
```

### 8.3 独立部署

- **库模块**（common/realtime/kafka/codegen/framework/system/monitor/job/workbench/workflow）不产出部署物，仅发布 jar 供他模块依赖；
- **可执行产物**仅 `scaffold-admin`：`mvn -o clean package` → `scaffold-admin/target/scaffold-admin-1.0.0.jar`（spring-boot repackage，lombok 已排除）；
- 运行方式不变：`java -jar scaffold-admin/target/scaffold-admin-1.0.0.jar`（配置、迁移、日志资源均在 admin）。

### 8.4 测试归属规则（新增测试放哪里）

| 测试类型 | 归属 | 判据 |
|---|---|---|
| 纯单元测试（无 Spring 上下文） | 被测类所在模块 | 只 import 本模块 + common 的类 |
| 跨模块一致性守护 | `scaffold-admin` 的 `consistency` 包 | 同时校验多个模块的类/配置/资源 |
| 集成 / 端到端（`@SpringBootTest`、真实 MySQL） | `scaffold-admin` 的 `integration` / `e2e` | 需完整应用上下文，只有 admin 汇聚全部 Bean |
| 性能基准 | `scaffold-admin` 的 `perf` | 常跨多模块调用 |

---

## 9. 演进指引

1. **common 再细分时机**：当 `scaffold-common` 类数超过 ~150 或出现「Web 相关 / 持久化相关 / 业务无关横切」三类关注点混杂时，可按 RuoYi-Vue-Plus 方式拆出 `scaffold-common-web` / `scaffold-common-persistence`；拆分时保持端口在底层（参考本次 `common.context` 倒置）。
2. **业务模块独立成服务**：模块边界即未来微服务边界——若某业务模块需独立部署，将其 controller 迁出 admin 并新建对应启动模块即可，DAG 已保证无循环依赖。
3. **新增通信方式**（如 MQTT/RabbitMQ）：仿 `scaffold-kafka` 建模块，只依赖 common。
4. **门禁同步**：任何模块/POM 变动后必须重跑 `check-conventions.py`（H5 守护 DAG）；修改本指南或《编码规范》须同步更新另一份。

---

> 更新日期：2026-10-21（V1.0.3 拆分实施完成：11 模块、852 单测全绿、H1–H5 零硬违规）
