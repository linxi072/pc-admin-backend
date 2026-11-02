# 模块职责

工程由 **12 个 Maven 模块**组成，包根统一为 `com.acme.scaffold`。下表为单一事实来源。

| 模块 | 层 | 职责（一句话） | 依赖 | 关键包 |
|---|---|---|---|---|
| `scaffold-common` | 基础 | 跨模块基础设施：统一响应/异常/分页、i18n、审计/幂等切面、导出/导入、**jOOQ DSL** | 无 | `common.*`、`jooq.dsl`/`jooq.paging` |
| `scaffold-realtime` | 基础 | WebSocket 握手鉴权端口与会话管理 | common | `realtime.port`/`realtime.websocket` |
| `scaffold-kafka` | 基础 | Kafka 保序规则与消费者薄封装 | common | `kafka.*` |
| `scaffold-codegen` | 工具 | 表元数据读取与 CRUD 骨架生成（M50） | common | `codegen.controller`/`codegen.service` |
| `scaffold-framework` | 框架装配 | 全局 Spring 配置、指标链路、N+1/慢查询监听 | common, realtime | `config.*`、`observability.*`、`monitor.detection.*` |
| `scaffold-security` | 业务域 | 认证授权与令牌域：JWT/刷新令牌/Token 版本、**OAuth2 授权+资源服务器**、密码策略、数据权限、限流、在线会话 | common, realtime | `security.auth`/`oauth2`/`jwt`/`permission`/`token`/`ratelimit`/`online` |
| `scaffold-system` | 业务域 | 系统管理与 RBAC：用户/角色/菜单/机构/字典/参数/文件/站内信/公告/租户/导入导出 | common, realtime, security | `system.*`（含 `bootstrap.ApiResourceAutoSyncRunner`） |
| `scaffold-monitor` | 业务域 | 告警收件箱、健康探针、缓存与监控指标查询 | common, framework, security, system | `monitor.alert`/`monitor.health`/`monitor.service` |
| `scaffold-job` | 业务域 | 任务定义管理与周期任务注册（JobRunr） | common, monitor, system | `job.*` |
| `scaffold-workbench` | 业务域 | 工作台看板与报表聚合 | common, system | `workbench.*` |
| `scaffold-workflow` | 业务域 | Flowable 对接：流程定义/实例/任务，认领/转办/完成 + 管理员治理 | common, monitor, realtime, system | `workflow.*` |
| `scaffold-admin` | 启动装配 | 应用入口、运行期配置与 Flyway 迁移、全部集成/端到端测试（**唯一可部署模块**） | 全部 | `FrameworkApplication` + `test`/`integration`/`e2e`/`consistency` |

## 依赖方向铁律
1. 模块间不得形成环（H5 校验守护）。
2. 特性模块之间不互相直连 Service；需协作时经 `common` 的 **Port（防腐层）** 或上层编排（`admin`）。
3. 纯函数规则类（`*Rules`/`*Writer`/`*Builder`）随其操作的数据域走，不放「名字听起来相关」的模块。
4. 实体（`*DO`）归属其领域模块；其它模块经该模块的 Service / Port 访问，不直接 import 其 entity。

## 新增模块步骤
1. 在根 `pom.xml` 的 `<modules>` 注册；
2. 新建 `scaffold-xxx/pom.xml`，`parent` 指向 `java-admin-framework`，`artifactId` 为 `scaffold-xxx`；
3. 仅声明**真正需要**的依赖（保持 DAG 无环）；
4. 包根 `com.acme.scaffold.xxx`，遵循 `controller → service → domain` 分层（H3 规则）；
5. 若暴露 HTTP 端点，同步补：前端路由 + i18n 键 + 菜单种子（`db/migration`）+ API 客户端；
6. 跑双门禁（`mvn -o ... clean test` + `check-conventions.py`）确认无环、无违规。
