# 模块职责

工程由 **14 个 Maven 模块**组成，包根统一为 `com.acme.scaffold`。下表为单一事实来源；
模块是否真实存在的最终判据是根 `pom.xml` 的 `<modules>`，以及
`scripts/check-conventions.py` 的 H5 模块 DAG 校验（表与 POM 不一致时以 POM 为准并修表）。

| 模块 | 层 | 职责（一句话） | 依赖 | 关键包 |
|---|---|---|---|---|
| `scaffold-common` | 基础 | 跨模块基础设施：统一响应/异常/分页、**通用常量**、i18n、审计/幂等切面、导出/导入、**jOOQ DSL**、插件 SPI 运行时 | 无 | `common.*`（含 `common.constant`）、`jooq.*`、`plugin.*` |
| `scaffold-realtime` | 基础 | WebSocket 握手鉴权端口与会话管理 | common | `realtime.port`/`realtime.websocket` |
| `scaffold-kafka` | 基础 | Kafka 保序规则与消费者薄封装 | common | `kafka.*` |
| `scaffold-codegen` | 工具 | 表元数据读取与 CRUD 骨架生成（M50）+ AI 脚手架（R3） | common | `codegen.*`、`ai.*` |
| `scaffold-cms` | 业务域 | 栏目/文章/内容管理 | common | `cms.*` |
| `scaffold-im` | 业务域 | 站内即时消息与会话管理 | common | `im.*` |
| `scaffold-framework` | 框架装配 | 全局 Spring 配置、指标链路、N+1/慢查询监听 | common, realtime | `config.*`、`observability.*`、`monitor.detection.*` |
| `scaffold-security` | 业务域 | 认证授权与令牌域：JWT/刷新令牌/Token 版本、**OAuth2 授权+资源服务器**、密码策略、数据权限、限流、在线会话 | common, realtime | `security.auth`/`oauth2`/`jwt`/`permission`/`token`/`ratelimit`/`online` |
| `scaffold-system` | 业务域 | 系统管理与 RBAC：用户/角色/菜单/机构/字典/参数/文件/站内信/公告/租户/导入导出 | common, realtime, security | `system.*`（含 `bootstrap.ApiResourceAutoSyncRunner`） |
| `scaffold-monitor` | 业务域 | 告警收件箱、健康探针、缓存与监控指标查询 | common, framework, security, system | `monitor.alert`/`monitor.health`/`monitor.service` |
| `scaffold-job` | 业务域 | 任务定义管理与周期任务注册（JobRunr） | common, monitor, system | `job.*` |
| `scaffold-workbench` | 业务域 | 工作台看板与报表聚合 | common, system | `workbench.*` |
| `scaffold-workflow` | 业务域 | Flowable 对接：流程定义/实例/任务，认领/转办/完成 + 管理员治理 + 审批中心门户 | common, monitor, realtime, system | `workflow.*`、`approval.*` |
| `scaffold-admin` | 启动装配 | 应用入口、运行期配置与 Flyway 迁移（**唯一可部署模块**） | 全部 | `FrameworkApplication` + `db/migration` |

> **模块 ↔ 包映射说明**：多数值模块与顶层包同名，但有意存在两处例外——
> **2026-11-08 整合**：`plugin.*` / `approval.*` / `ai.*` 三个包根分别并入 common / workflow / codegen，
> 包路径与类名均未改写，故「模块 ↔ 包」不再一一对应，以 `scripts/check-directory.py` 的 `EXPECTED_ROOTS` 为准。
> `scaffold-framework` 的类位于 `com.acme.scaffold.config` / `observability` / `monitor.detection` /
> `chaos`（无 `...framework` 包）；`scaffold-common` 另含 `com.acme.scaffold.jooq.*`。
> 根 `pom.xml` 已书面声明「不做包名重排，模块边界由 Maven 依赖强制」，故保留现状。
> 与若依约定的完整差异对照见 [RUOYI-ALIGNMENT.md](RUOYI-ALIGNMENT.md)。

## 依赖方向铁律
1. 模块间不得形成环（H5 校验守护）。
2. 特性模块之间不互相直连 Service；需协作时经 `common` 的 **Port（防腐层）** 或上层编排（`admin`）。
3. 纯函数规则类（`*Rules`/`*Writer`/`*Builder`）随其操作的数据域走，不放「名字听起来相关」的模块。
4. 实体（`*DO`）归属其领域模块；其它模块经该模块的 Service / Port 访问，不直接 import 其 entity。
5. 跨模块通用字面值归 `common.constant.CommonConstants`；已有单一事实来源的（错误码、
   `PagingRules`）不重复搬运，避免产生第二事实来源。

## 新增模块步骤
1. 在根 `pom.xml` 的 `<modules>` 注册；
2. 新建 `scaffold-xxx/pom.xml`，`parent` 指向 `java-admin-framework`，`artifactId` 为 `scaffold-xxx`；
3. 仅声明**真正需要**的依赖（保持 DAG 无环）；非 Spring Boot BOM 管理的第三方依赖版本写入父 POM
   `<dependencyManagement>`，子模块省略 `<version>`；
4. 包根 `com.acme.scaffold.xxx`，遵循 `controller → service → domain` 分层（H3 规则）；
5. 若暴露 HTTP 端点，同步补：前端路由 + i18n 键 + 菜单种子（`db/migration`）+ API 客户端；
6. 跑双门禁（`mvn -o ... clean test` + `check-conventions.py`）确认无环、无违规，并同步更新本表。

---

## 7. 规划态与已落地模块对照

> 规划动机 / 优先级 / 验收见 **[ROADMAP.md](ROADMAP.md)**。本节只记录状态对照，
> **不**作为模块是否存在的判据；判据始终是根 `pom.xml` 的 `<modules>`。

### 已从规划态落地（现已在上表中）

| 原候选 | 对应需求 | 现状 | 真实依赖 |
|---|---|---|---|
| `scaffold-plugin` | R1 插件化 | 已落地，见上表 | common |
| `common.event` | R2 领域事件 | 已落地为 `common` 子包（非独立 Maven 模块） | — |
| `scaffold-ai` | R3 AI 辅助 | 已落地，见上表 | common, codegen |
| `scaffold-cms` | R5 内容管理 | 已落地，见上表 | common |
| `scaffold-im` | R6 站内 IM | 已落地，见上表 | common |
| `scaffold-approval` | R7 审批门户 | 已落地，见上表 | common, workflow |

> 注意：ROADMAP.md 中的「规划依赖」是设计期设想，与上表「真实依赖」可能不同；
> 实施以各模块 `pom.xml` 为准。例如 cms/im 实际只依赖 common，approval 依赖 common + workflow。

### 仍处于规划态（未创建）

| 候选模块 | 层 | 规划依赖 | 对应需求 |
|---|---|---|---|
| `scaffold-lowcode` | 业务域 | common, system, plugin | R10 低代码深化 |
| `packages/ui` | 前端（可选） | 无 | R11 组件库 |

**与「新增模块步骤」的衔接**：当上述任一候选进入实施时，严格复用 §「新增模块步骤」六步，
并在第 1 步注册后、第 5 步补前端资产时同步更新本表状态（规划态 → 实施中 → 已落地）；
任何新增依赖边不得与现有 14 模块形成环。
