# 架构手册

面向架构师与技术负责人。描述系统的总体架构、分层与命名、RBAC 权限模型、核心表结构、工作流状态机、统一响应/异常契约、关键配置、可观测性指标体系、操作审计、API 最小集合，以及关键架构决策（ADR）、微服务拆分触发条件、风险与验收清单。

---

> **阅读指引**：设计文档《Java 后端基础管理框架设计方案》描述目标架构（包名 com.acme.scaffold、XXL-JOB、Boot 4 基线），实际实现以 java-admin-framework（Spring Boot 3.3.4 / Java 17·21 / MyBatis-Plus + jOOQ 3.19.11 / JobRunr 6.3.4 / Flowable 7.1.0）为准，二者在调度引擎、持久层选型上存在有意偏差，文中已标注。

---

## 本手册整合来源

- **docs/archive/java-admin-framework-README.md**
  - ## 1. 技术栈
  - ## 2. 目录结构（核心包）
  - ## 4. 认证与权限
  - ## 11. 关键设计点
  - ## 15. 后续建议
- **docs/archive/design-spec.md**
  - ## 1. 结论与推荐基线
  - ## 2. 总体架构与依赖规则
  - ## 3. 完整目录结构
  - ## 4. 分层与命名约定
  - ## 5. RBAC 权限模型
  - ## 6. 核心表结构
  - ## 7. 工作流审批设计
  - ## 8. 统一响应、异常、校验与分页
  - ## 9. 关键配置示例
  - ## 10. 性能监控与可观测性
  - ## 11. 操作审计设计
  - ## 12. API 最小集合
  - ## 15. 分阶段实施建议
  - ## 16. 关键架构决策
  - ## 17. 拆分为微服务的触发条件
  - ## 18. 风险与注意事项
  - ## 19. 官方资料依据
  - ## 20. 最小可交付验收清单

---

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 1. 技术栈

| 领域       | 选型                                                                  |
| -------- | ------------------------------------------------------------------- |
| JDK      | 17                                                                  |
| Web / 安全 | Spring Boot 3.3.4 / Spring Security 6（OAuth2 Resource Server + JWT） |
| ORM      | **jOOQ 3.19**（Spring Boot 托管版本，动态 DSL）                              |
| 数据库      | MySQL 8.4（Flyway 初始化；JobRunr / Flowable 各自自动建表）                     |
| 缓存       | Redis 7（Spring Data Redis，可选）                                       |
| 工作流      | Flowable 7.1.0                                                      |
| 调度       | **JobRunr 6.3.4**（SQL 存储 + 内嵌 BackgroundJobServer）                  |
| 可观测      | Actuator + Micrometer + Prometheus                                  |
| 文档       | springdoc-openapi（仅 local/dev）                                      |

### 迁移对照

| 维度    | 迁移前                                                                              | 迁移后                                                                            | 影响                                               |
| ----- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------ |
| 持久层   | MyBatis-Plus 3.5.7（BaseMapper / Wrapper / 分页插件 / 逻辑删除 / 乐观锁 / MetaObjectHandler） | jOOQ 动态 DSL（`DSLContext` + `JooqTables` + `JooqWriters` + `SnakeRecordMapper`） | 表结构与 SQL 语义不变；不再依赖 XML/注解映射与代码生成                 |
| 主键    | `IdType.ASSIGN_ID`                                                               | `IdGenerator`（雪花算法，insert 后回填 POJO）                                            | 行为一致，仍为 `BIGINT` 单调 ID                           |
| 逻辑删除  | `@TableLogic`                                                                    | `JooqWriters.notDeleted(table)` 谓词 + `delete(..., true)`                       | `deleted=0/1` 语义不变                               |
| 审计字段  | `MetaObjectHandler` 自动填充                                                         | `JooqWriters` 统一填充 `tenant_id/version/created_at/updated_at`                   | 填充规则不变；null 字段不写入（沿用 MP 的 NOT_NULL 策略，交由数据库默认值）  |
| 分页    | `PaginationInnerInterceptor` + MP `Page`                                         | `JooqWriters.page(...)`（count + limit/offset）                                  | `PageQuery` / `PageResult` 契约不变                  |
| 慢 SQL | `SlowQueryInnerInterceptor`（MyBatis 拦截器）                                         | `SlowQueryListener`（jOOQ `ExecuteListener`）                                    | 阈值改为 `app.jooq.slow-query-threshold-ms`，默认 500ms |
| 调度    | XXL-JOB 执行器（需独立调度中心）                                                             | JobRunr（任务存业务库，应用内嵌执行）                                                         | 去掉调度中心与 9999 端口；提供 `demo-job` 每 5 分钟执行           |

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 2. 目录结构（核心包）

```
com.acme.scaffold
├── FrameworkApplication.java
├── common
│   ├── api/        Result / PageQuery / PageResult
│   ├── error/      ErrorCode / CommonErrorCode
│   ├── exception/  BusinessException / GlobalExceptionHandler
│   ├── validation/ CreateGroup / UpdateGroup
│   ├── audit/      AuditOperation / AuditAspect / AuditLogService / AuditRecord
│   └── IdGenerator  替代 MyBatis-Plus 的 ASSIGN_ID
├── jooq/           jOOQ 支撑层：JooqTables / JooqWriters / SnakeRecordMapper(Provider)
├── security
│   ├── config/     SecurityConfig / JwtProperties / JwtDecoderConfig
│   ├── jwt/        JwtProvider / JwtAuthConverter
│   ├── token/      RefreshTokenService（opaque + 轮换 + 重放检测）
│   ├── permission/ PermissionService / DataScope(扩展点)
│   ├── context/    CurrentPrincipal / SecurityContextFacade
│   ├── application/ AuthApplicationService
│   └── controller/ AuthController
├── system
│   ├── entity|service|controller|dto   用户/角色/菜单/机构(部门)/接口资源/字典/系统变量
│                                          公告(SysAnnouncement)/站内信(SysMessage + SysMessageReceipt)
├── workflow
│   ├── port/       WorkflowEnginePort（屏蔽 Flowable）
│   ├── adapter/    FlowableWorkflowAdapter
│   ├── service/    WorkflowService（不可变审批记录 + 幂等）
│   └── controller|dto|entity
├── monitor/        BusinessMetrics / BusinessHealthIndicator / SlowQueryListener
│                   RuntimeMetricsCollector(JDK 采集 CPU/内存/磁盘/JVM)
│                   MonitorService / MonitorController(运行指标+在线会话+异常日志)
├── job/            DemoJob（JobRunr 示例，替代原 XXL-JOB 的 demoJobHandler）
│                   AnnouncementExpiryJob(公告过期下线) / MonitorSampleJob(指标采样)
└── config/         Jooq / JobRunr / Redis / Web(CORS) / TraceId / Observability / SpringDoc
```

> 说明：迁移后**不再有 `mapper` 包**。原 `*Mapper` 接口全部删除，数据访问统一走 `DSLContext`。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 4. 认证与权限

- **登录**：`POST /api/auth/login` → 返回 `accessToken`（JWT，15 分钟）与 `refreshToken`（不透明，7 天）。
- **刷新**：`POST /api/auth/refresh` → 校验并**轮换**刷新令牌（旧令牌失效，检测到重用则吊销整个家族）。
- **登出**：`POST /api/auth/logout` → 吊销当前用户全部刷新令牌。
- **接口鉴权**：所有 `/api/**` 需 Bearer Token；方法级权限用 `@PreAuthorize("hasAuthority('system:user:read')")`。
- **权限码约定**：`模块:资源:动作`，如 `system:user:read`、`workflow:task:approve`，不与 URL 强耦合。

示例：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"ChangeMe123!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')

curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/system/users/page?page=1&size=10"
```

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 11. 关键设计点

- **模块化单体**：单进程部署，认证/系统/工作流/调度/审计/监控按包边界隔离；达到独立扩容/发布条件后再拆微服务。
- **安全**：JWT 仅承载非敏感声明；刷新令牌不透明且哈希存储；账号锁定、密码加密、登录失败计数、审计脱敏。
- **数据权限扩展点**：`DataScopeProvider.resolveOrgIds(userId, resourceCode)` 预留，业务查询据此拼接机构过滤（见 `UserService#list`）。
- **工作流端口**：业务层只依赖 `WorkflowEnginePort`，不直接调用 Flowable `RuntimeService`/`TaskService`。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 15. 后续建议

> 2026-10-08 更新：设计方案 §20 中「审计日志可按 traceId 查询」「写接口幂等」「Token 版本失效」三项
> 已分别落地为第 17、18、19 节；下方为剩余建议。

1. 补充集成测试覆盖登录、刷新轮换、会签、驳回、转办（使用本地 MySQL 实例，不使用容器）。
1.1. 幂等与 Token 版本的**运行期行为**（并发同 key 去重、改角色后旧 token 401）同样依赖 MySQL/Redis，需本机验证。
2. 接入 Prometheus + Grafana + Alertmanager（均以原生进程部署），沉淀仪表盘与告警规则。
3. 如需更强类型安全，引入 jOOQ 代码生成替换当前动态 DSL（见第 8 节）。
4. （已完成）数据权限切面自动注入已落地：`DataScope` 注解 + `DataScopeAspect` + `DataScopeContext` /  
   `DataScopeConditions` 替代逐 Service 手工接线，已接入 `system:user` 与 `system:org`。  
   后续新增受控资源只须在查询方法加 `@DataScope(resourceCode=...)` 并调用 `DataScopeConditions.of(...)`，  
   不再需要手工调用 `DataScopeProvider#resolve`。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 1. 结论与推荐基线

### 1.1 推荐架构

采用 **模块化单体（Modular Monolith）+ 六边形边界（Port/Adapter）**：

- 部署形态：初期一个 Spring Boot 进程，降低开发、部署与事务复杂度。
- 代码形态：认证授权、系统管理、工作流、调度、审计、监控均为独立 Maven 模块。
- 模块协作：只通过公开 API、Port 接口或领域事件；禁止跨模块直接调用 Mapper。
- 数据层：初期共用 MySQL 实例，但每个模块拥有自己的表；禁止直接修改 Flowable 的 `ACT_*` 表。
- 演进方式：达到独立扩容、独立发布或团队边界条件后，再将模块拆成微服务。

不建议一开始就使用微服务。基础管理框架最常见的失败原因不是吞吐不足，而是分布式事务、权限一致性、配置和运维复杂度过早进入项目。

### 1.2 技术栈建议

| 领域 | 推荐选型 | 建议版本基线 | 说明 |
|---|---|---:|---|
| JDK | Eclipse Temurin / OpenJDK | Java 21 LTS | 比 Java 17 有更长使用周期，可使用虚拟线程但不应默认滥用 |
| Web 框架 | Spring Boot | 4.1.x | 当前稳定主线；Spring Boot 3.5.x 已结束社区维护，不适合作为新长期脚手架主线 |
| 安全 | Spring Security | 由 Boot BOM 管理 | 使用 Resource Server JWT 能力，不手写 JWT Filter 核心验证逻辑 |
| ORM | MyBatis-Plus | 3.5.17 | 官方已提供 Boot 4 Starter；复杂 SQL 继续使用 MyBatis XML |
| 数据库 | MySQL | 8.4 LTS | LTS 分支更适合长期基础平台 |
| 迁移 | Flyway | 由 Boot BOM 管理 | 所有结构变更版本化，禁止手工改生产表 |
| 连接池 | HikariCP | 由 Boot BOM 管理 | Spring Boot 默认，指标可直接接入 Micrometer |
| 缓存 | Redis | 8.x，固定补丁版本 | 只用于缓存、限流、短期状态；数据库仍是权限和 Token 的最终事实源 |
| 调度 | XXL-JOB | 3.4.2 | 中文生态和运维控制台成熟；通过 `JobSchedulerPort` 隔离，并跟踪安全公告 |
| 工作流 | Flowable OSS | 8.0.x | 原生 BPMN、人任务、历史、多实例；通过 `WorkflowEnginePort` 隔离引擎 API |
| 指标 | Actuator + Micrometer | 由 Boot BOM 管理 | 暴露健康、JVM、HTTP、连接池等指标 |
| 指标存储 | Prometheus + Alertmanager | 当前维护版 | 拉取 `/actuator/prometheus`，统一告警 |
| 可视化 | Grafana | 当前维护版 | 指标、日志、链路统一视图 |
| 链路 | Micrometer Tracing + OpenTelemetry | 由 Boot BOM 管理 | OTLP 输出，避免绑定单一厂商 |
| API 文档 | springdoc-openapi | 与 Boot 4 兼容版 | 仅在 local/dev 默认开启 |
| 测试 | JUnit 5 + Testcontainers | 由 BOM 管理 | MySQL/Redis 集成测试不要用 H2 替代 |

### 1.3 可替代选型

1. **Spring Boot 3.5.16**：仅用于必须兼容现有 Boot 3 组件的过渡项目。它是 3.5.x 最后一个 OSS 版本，新脚手架不建议以此作为长期主线。
2. **jOOQ 替代 MyBatis-Plus**：若系统以复杂报表、动态 SQL、强类型查询为主，jOOQ 的编译期校验更好；若团队更熟悉 MyBatis 且 CRUD 较多，MyBatis-Plus 更低成本。
3. **Spring Authorization Server / Keycloak**：当多个系统共享 SSO、OIDC、第三方客户端或统一身份中心时启用；单一后台系统先采用内置 Auth 模块即可。
4. **JobRunr 替代 XXL-JOB**：若只需应用内持久化任务、希望少部署一个控制台，JobRunr 更轻；若需要跨应用执行器、集中运维和人工触发，保留 XXL-JOB。
5. **Valkey 替代 Redis**：若组织对 Redis 许可证或再分发有严格要求，可在 Spring Data Redis 抽象下切换 Valkey。
6. **简单状态机替代 Flowable**：流程永远只有少量固定节点时，自建状态机更轻；一旦要求会签、或签、动态节点、历史追踪和流程版本，Flowable 更合适。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 2. 总体架构与依赖规则

```text
Admin UI / Open API
        |
        v
Web & Security Layer
  - RequestId / TraceId
  - Validation / Response / Exception
  - Authentication / Authorization
  - Rate Limit / Audit
        |
        v
Business Modules
  +----------------+  +----------------+
  | module-auth    |  | module-system  |
  +----------------+  +----------------+
  | module-workflow|  | module-job     |
  +----------------+  +----------------+
  | module-audit   |  | module-monitor |
  +----------------+  +----------------+
        |
        v
Ports / Adapters
  MyBatis-Plus | Redis | Flowable | XXL-JOB | Micrometer | OTLP
        |
        v
MySQL 8.4 | Redis | Prometheus | Grafana | Tempo/Loki(optional)
```

依赖规则：

```text
bootstrap -> modules -> framework-starters -> common
modules.api <- other modules
modules.infrastructure -> external libraries
modules.domain -X-> Spring MVC / MyBatis / Redis / Flowable / XXL-JOB
```

关键约束：

- Controller 不直接调用 Mapper。
- Application Service 负责事务、用例编排和 DTO 转换。
- Domain 层不依赖 Web、ORM、Redis、Flowable、XXL-JOB。
- 基础模块不得反向依赖业务模块。
- 模块间不共享实体类；只共享稳定 DTO、事件或 Port。
- 任何外部组件均通过配置开关和 Adapter 接入，便于测试与替换。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 3. 完整目录结构

```text
java-admin-scaffold/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/
├── .env.example
├── docker-compose.yml
├── checkstyle.xml
├── deploy/
│   ├── mysql/init/
│   │   ├── 00-create-databases.sql
│   │   └── 01-xxl-job.sql
│   ├── prometheus/prometheus.yml
│   └── grafana/provisioning/
├── scaffold-bom/
│   └── pom.xml
├── scaffold-common/
│   ├── common-core/
│   │   └── src/main/java/com/acme/scaffold/common/core/
│   │       ├── api/ApiResponse.java
│   │       ├── api/PageQuery.java
│   │       ├── api/PageResult.java
│   │       ├── error/ErrorCode.java
│   │       ├── error/CommonErrorCode.java
│   │       ├── exception/BusinessException.java
│   │       └── model/BaseId.java
│   ├── common-web/
│   │   └── src/main/java/com/acme/scaffold/common/web/
│   │       ├── advice/GlobalExceptionHandler.java
│   │       ├── filter/RequestContextFilter.java
│   │       ├── config/JacksonConfig.java
│   │       └── validation/
│   ├── common-security/
│   │   └── src/main/java/com/acme/scaffold/common/security/
│   │       ├── context/CurrentPrincipal.java
│   │       ├── context/SecurityContextFacade.java
│   │       ├── permission/PermissionService.java
│   │       ├── datascope/DataScopeProvider.java
│   │       └── annotation/DataScope.java
│   └── common-test/
│       └── src/main/java/com/acme/scaffold/common/test/
├── scaffold-starters/
│   ├── starter-web/
│   ├── starter-security/
│   ├── starter-mybatis/
│   ├── starter-redis/
│   ├── starter-observability/
│   ├── starter-audit/
│   ├── starter-workflow-flowable/
│   └── starter-job-xxl/
├── scaffold-modules/
│   ├── module-auth/
│   │   └── src/main/java/com/acme/scaffold/auth/
│   │       ├── api/controller/AuthController.java
│   │       ├── api/dto/LoginCommand.java
│   │       ├── api/vo/TokenView.java
│   │       ├── application/AuthApplicationService.java
│   │       ├── domain/model/RefreshToken.java
│   │       ├── domain/service/TokenService.java
│   │       ├── domain/port/TokenStore.java
│   │       └── infrastructure/
│   │           ├── jwt/JwtTokenService.java
│   │           └── persistence/RefreshTokenRepositoryImpl.java
│   ├── module-system/
│   │   └── src/main/java/com/acme/scaffold/system/
│   │       ├── api/controller/{User,Org,Role,Menu,ApiResource}Controller.java
│   │       ├── application/
│   │       ├── domain/
│   │       │   ├── model/{User,Org,Role,Menu,ApiResource}.java
│   │       │   ├── repository/
│   │       │   └── service/
│   │       └── infrastructure/persistence/
│   │           ├── entity/
│   │           ├── mapper/
│   │           ├── repository/
│   │           └── convert/
│   ├── module-workflow/
│   │   └── src/main/java/com/acme/scaffold/workflow/
│   │       ├── api/controller/{Definition,Instance,Task}Controller.java
│   │       ├── application/WorkflowApplicationService.java
│   │       ├── domain/model/
│   │       ├── domain/port/WorkflowEnginePort.java
│   │       └── infrastructure/flowable/FlowableWorkflowAdapter.java
│   ├── module-job/
│   │   └── src/main/java/com/acme/scaffold/job/
│   │       ├── domain/port/JobSchedulerPort.java
│   │       ├── infrastructure/xxl/XxlJobSchedulerAdapter.java
│   │       └── handler/
│   ├── module-audit/
│   │   └── src/main/java/com/acme/scaffold/audit/
│   │       ├── annotation/AuditOperation.java
│   │       ├── aspect/AuditOperationAspect.java
│   │       ├── application/AuditLogService.java
│   │       └── infrastructure/persistence/
│   └── module-monitor/
│       └── src/main/java/com/acme/scaffold/monitor/
│           ├── metrics/BusinessMetrics.java
│           ├── health/BusinessHealthIndicator.java
│           └── slowquery/SlowQueryInterceptor.java
├── scaffold-bootstrap/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/acme/scaffold/ScaffoldApplication.java
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   ├── application-test.yml
│       │   ├── application-prod.yml
│       │   ├── logback-spring.xml
│       │   ├── db/migration/
│       │   │   ├── V1__baseline.sql
│       │   │   ├── V2__security.sql
│       │   │   └── V3__workflow_extension.sql
│       │   └── processes/
│       │       └── leave-approval.bpmn20.xml
│       └── test/java/com/acme/scaffold/
│           ├── ArchitectureTest.java
│           ├── SecurityIntegrationTest.java
│           └── WorkflowIntegrationTest.java
└── scripts/
    ├── dev-up.sh
    ├── dev-down.sh
    └── smoke-test.sh
```

### 3.1 模块职责

| 模块 | 负责 | 不负责 |
|---|---|---|
| `common-core` | 响应、错误码、分页、基础抽象 | Spring Bean、数据库访问 |
| `common-web` | Web 异常映射、校验、序列化、请求上下文 | 业务异常定义以外的业务规则 |
| `common-security` | 当前用户、权限与数据权限 SPI | 用户表持久化、Token 签发实现 |
| `starter-*` | 自动配置、条件装配、统一依赖入口 | 业务实体 |
| `module-auth` | 登录、刷新、注销、会话、账号风控 | 用户与角色的 CRUD |
| `module-system` | 用户、机构、角色、菜单、接口资源 | Token 生命周期 |
| `module-workflow` | 流程定义扩展、实例、任务、审批记录 | 直接修改 `ACT_*` 表 |
| `module-job` | XXL-JOB 执行器适配、统一任务处理规范 | 调度中心源码二次开发 |
| `module-audit` | 操作审计、安全审计 | 普通调试日志 |
| `module-monitor` | 业务指标、慢 SQL、健康扩展 | 监控数据持久化平台 |
| `bootstrap` | 聚合模块、启动、环境配置、Flyway | 具体领域逻辑 |

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 4. 分层与命名约定

> 完整的「按特性分包 + 层子包」目录约定、各层职责、命名规范（类/方法/包/常量）、代码格式基线与可校验的依赖方向/无环规则，已独立成文：**《编码规范与目录结构约定》`docs/manual/coding-standard.md`**（配套零依赖校验脚本 `scripts/check-conventions.py`，CI 门禁）。下文仅保留与架构强相关的要点与数据库字段约定，详细分层命名以该手册为准。

### 4.1 模块内分层（要点）

- 按特性分包：一级包 = 业务能力域（`security` / `system` / `workflow` / `job` / `monitor` / `workbench` …）；同特性代码聚于一处。
- 特性模块内按职责拆子包：`controller`（HTTP 入口）、`service`（业务/事务）、`config`（装配）、`domain`/`entity`（实体）、`dto`/`vo`（传输）、`port`/`adapter`（防腐）、`rules`（纯函数规则）。
- 依赖方向单向、无环：`controller → service → domain`；`domain` 不反向依赖 `service`/`config`/`controller`；`config` 不得依赖 `controller`/`web`。

### 4.2 命名规则（要点）

| 类型 | 后缀示例 | 规则 |
|---|---|---|
| 入参 | `CreateUserRequest` / `LoginCommand` | 不直接接收 Entity |
| 出参 | `UserView` / `TokenView` | 不直接返回 Entity |
| 应用服务 | `UserApplicationService` | 一个服务对应一组业务用例 |
| 领域服务 | `PermissionDomainService` | 只承载领域规则 |
| 持久化实体 | `UserDO` | 统一 `DO` 后缀 |
| 外部端口 | `WorkflowEnginePort` | 屏蔽具体厂商 API |
| 转换器 | `UserConverter` | 推荐显式转换或 MapStruct |
| 错误码 | `AUTH_001` | 模块前缀 + 三位编号，HTTP 状态另行映射 |
| 权限码 | `system:user:read` | `模块:资源:动作`，不与 URL 强耦合 |

> 完整后缀清单见《编码规范与目录结构约定》§3。

### 4.3 数据字段约定

- 主键：`BIGINT UNSIGNED`，雪花 ID（`ASSIGN_ID` 策略）；JSON 输出为字符串，避免 JavaScript 精度丢失。
- 时间：数据库使用 `DATETIME(3)`，应用使用 UTC `Instant`；展示层按用户时区转换。
- 布尔：`TINYINT UNSIGNED`，0/1。
- 枚举：数据库存稳定字符串，不存 Java ordinal。
- 逻辑删除：`deleted`；账号名等安全标识默认不允许删除后复用。
- 乐观锁：重要聚合包含 `version`。
- 多租户：预留 `tenant_id`，单租户默认值为 0；启用前必须补充租户上下文与唯一索引。
- 不默认创建数据库外键：使用唯一索引、应用校验和定期一致性检查，降低未来拆库成本。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 5. RBAC 权限模型

### 5.1 核心关系

```text
User N---M Role
User N---M Org
Role N---M Menu
Role N---M ApiResource
Role 1---N DataScopeRule N---M Org
```

菜单权限与接口权限必须分离：

- `sys_menu` 决定前端路由、按钮和展示。
- `sys_api_resource` 决定后端 HTTP 方法与路径所需权限。
- 二者可共享同一 `permission_code`，但不能把“是否显示菜单”当成后端安全控制。
- 方法级使用 `@PreAuthorize("hasAuthority('system:user:read')")` 作为最终边界。
- 接口资源表用于动态资源治理、权限扫描和后台配置；启动时可扫描 Controller 映射并生成差异报告，不建议生产环境自动删除资源。

### 5.2 认证与 Token 策略

#### Access Token

- JWT，建议 RSA/ECDSA 非对称签名。
- 有效期建议 10～15 分钟。
- Claims：`iss`、`aud`、`sub`、`jti`、`iat`、`exp`、`sid`、`tenant_id`、`token_version`。
- JWT 仅放身份和稳定声明，不放完整菜单或大量权限；权限从 Redis 缓存读取，缓存失效时回源 MySQL。
- 使用 `kid` 支持签名密钥轮换；私钥不得进入 Git。

#### Refresh Token

- 使用 256 bit 以上密码学随机数生成的 opaque token，不使用长期 JWT。
- 客户端只得到原文一次；服务端只存 SHA-256 哈希。
- 浏览器管理端默认将 Refresh Token 放入 `Secure + HttpOnly + SameSite=Strict` Cookie，Path 限定为 `/api/auth`；Access Token 仅保存在内存并通过 Authorization Header 发送。
- Cookie 模式下 `/login`、`/refresh`、`/logout` 必须校验 CSRF Token 与 Origin；移动端或服务端客户端可改为响应体返回 Refresh Token，并存入平台安全存储。
- 默认有效期 7 天，支持“记住我”扩展到 30 天。
- 每次刷新必须轮换，并记录 `family_id` 与 `replaced_by_id`。
- 旧 Refresh Token 再次出现视为重放：撤销整个 token family，要求重新登录。
- 注销时撤销 Refresh Token；如要求 Access Token 立即失效，将 `jti` 写入 Redis 黑名单，TTL 等于剩余有效期。

#### 权限缓存

```text
security:user:{userId}:authorities:v{tokenVersion} -> Set<String>   TTL 10m
security:revoked:jti:{jti}                         -> 1             TTL <= 15m
security:login:fail:{account}:{ip}                 -> counter       TTL 15m
security:nonce:{purpose}:{id}                      -> value         one-time
```

权限、角色、账号状态变更后：

1. 数据库事务提交；
2. 增加用户 `token_version`；
3. 删除用户权限缓存；
4. 发布 `UserPermissionChangedEvent`；
5. 所有旧 Token 在下一次校验时失效。

### 5.3 密码与账号安全基线

- 使用 `DelegatingPasswordEncoder`，新系统优先 Argon2id；若部署资源受限，使用 BCrypt。
- work factor 需在目标机器压测，目标为一次密码验证约 0.5～1 秒。
- 禁止明文、MD5、SHA-1、裸 SHA-256。
- 登录失败 5 次锁定 15 分钟；继续失败采用渐进式锁定。
- 用户名和密码错误统一返回“账号或凭证错误”，防止账号枚举。
- 登录、刷新、改密、重置密码按账号与 IP 双维度限流。
- 修改密码后撤销所有 Refresh Token，并增加 `token_version`。
- 保留最近 5 次密码哈希，禁止重复使用；首次登录或管理员重置后强制改密。
- 管理员账号建议支持 TOTP/WebAuthn 二次认证扩展。
- 密码、Token、Cookie、Authorization、身份证号等字段禁止进入审计明文。

### 5.4 数据权限扩展点

第一阶段不要全局改写所有 SQL。采用显式、可测试的策略：

```java
public interface DataScopeProvider {
    DataScope resolve(CurrentPrincipal principal, String resource);
}

public sealed interface DataScope
        permits AllScope, OrgScope, OrgTreeScope, SelfScope, CustomOrgScope {
}
```

查询服务示例：

```java
@DataScope(resource = "system:user")
public PageResult<UserView> pageUsers(UserQuery query) {
    DataScope scope = dataScopeProvider.resolve(currentPrincipal.get(), "system:user");
    return userRepository.page(query, scope);
}
```

落地规则：

- `ALL`：不附加组织条件。
- `ORG`：`org_id = currentOrgId`。
- `ORG_TREE`：查询机构闭包表或物化路径得到子机构集合。
- `SELF`：`created_by = currentUserId` 或业务定义的 owner 字段。
- `CUSTOM`：来自 `sys_role_data_scope_org`。
- 多角色默认取并集；高安全场景可针对特定资源配置交集策略。
- 所有可应用数据权限的查询必须有 `resource` 和可过滤字段定义。

> **缓存与数据权限的耦合（易踩坑）**：带 `@DataScope` 的热点读（如 `OrgService.tree()` 机构树）缓存时，**绝不能**用单一全局键（`menu-tree` 那种全局菜单树可以，因为菜单树对所有人都相同）。否则首调（多为 ALL 权限管理员）会把全量树写入缓存，受限用户随后命中缓存读到越权子树。正确做法是以「数据权限范围签名」为缓存键隔离：`ALL` / `SELF:<userId>` / `ORG:<id 升序>`（见 `OrgService.orgScopeKey()`）。同时须保证缓存键在 `DataScopeContext` 写入之后计算——本项目将 `DataScopeAspect` 置为 `@Order(HIGHEST_PRECEDENCE)`（最外层），使缓存拦截器计算键时上下文已就绪；若切面晚于缓存拦截器执行，键会恒为 `ALL` 而把受限范围误并入管理员缓存。机构写路径（create/update/delete）统一 `allEntries` 整缓存驱逐。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 6. 核心表结构

### 6.1 表清单

| 表 | 作用 | 关键索引/约束 |
|---|---|---|
| `sys_user` | 用户与账号安全状态 | `uk_tenant_username`、`uk_tenant_mobile` |
| `sys_org` | 树形机构 | `uk_tenant_org_code`、`idx_parent` |
| `sys_user_org` | 用户与机构关系 | `(tenant_id,user_id,org_id)` |
| `sys_role` | 角色 | `uk_tenant_role_code` |
| `sys_user_role` | 用户角色授权，可限定机构上下文 | `(tenant_id,user_id,role_id,scope_org_id)` |
| `sys_menu` | 目录、菜单、按钮 | `uk_tenant_menu_code`、`idx_parent` |
| `sys_api_resource` | 接口资源 | `(tenant_id,http_method,path_pattern)` |
| `sys_role_menu` | 角色菜单 | `(tenant_id,role_id,menu_id)` |
| `sys_role_api` | 角色接口 | `(tenant_id,role_id,api_id)` |
| `sys_role_data_scope` | 角色数据范围规则 | `(tenant_id,role_id,resource_code)` |
| `sys_role_data_scope_org` | CUSTOM 范围机构 | `(tenant_id,rule_id,org_id)` |
| `sys_refresh_token` | Refresh Token 轮换与重放检测 | `uk_token_hash`、`idx_family` |
| `sys_password_history` | 密码历史 | `idx_user_time` |
| `sys_login_log` | 登录安全日志 | `idx_user_time`、`idx_ip_time` |
| `sys_operation_log` | 操作审计 | `idx_module_time`、`idx_operator_time` |
| `wf_definition_ext` | 流程定义业务扩展 | `(tenant_id,process_key,version)` |
| `wf_node_config` | 节点审批配置 | `(definition_ext_id,activity_id)` |
| `wf_instance_ext` | 业务与流程实例关联 | `(tenant_id,business_type,business_id)` |
| `wf_task_ext` | 任务扩展与并发控制 | `uk_task_id` |
| `wf_approval_record` | 不可变审批记录 | `uk_operation_id` |

Flowable 自身的 `ACT_RE_*`、`ACT_RU_*`、`ACT_HI_*`、`ACT_GE_*` 表由官方脚本或引擎管理，不复制其运行时数据。

### 6.2 基础 RBAC DDL

```sql
CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    mobile VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    primary_org_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    password_changed_at DATETIME(3) NULL,
    password_expired TINYINT UNSIGNED NOT NULL DEFAULT 0,
    token_version INT UNSIGNED NOT NULL DEFAULT 1,
    mfa_enabled TINYINT UNSIGNED NOT NULL DEFAULT 0,
    last_login_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    UNIQUE KEY uk_tenant_mobile (tenant_id, mobile),
    KEY idx_org_status (tenant_id, primary_org_id, status),
    KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_org (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ancestors VARCHAR(1000) NOT NULL DEFAULT '0',
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    org_type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT',
    sort_no INT NOT NULL DEFAULT 0,
    leader_user_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_org_code (tenant_id, org_code),
    KEY idx_parent (tenant_id, parent_id, sort_no),
    KEY idx_ancestors (tenant_id, ancestors(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_org (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    org_id BIGINT UNSIGNED NOT NULL,
    is_primary TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_org (tenant_id, user_id, org_id),
    KEY idx_org_user (tenant_id, org_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_no INT NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_code (tenant_id, role_code),
    KEY idx_status_sort (tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    scope_org_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    valid_from DATETIME(3) NULL,
    valid_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_scope (tenant_id, user_id, role_id, scope_org_id),
    KEY idx_role_user (tenant_id, role_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_menu (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL,
    route_path VARCHAR(255) NULL,
    component_path VARCHAR(255) NULL,
    permission_code VARCHAR(128) NULL,
    icon VARCHAR(64) NULL,
    visible TINYINT UNSIGNED NOT NULL DEFAULT 1,
    keep_alive TINYINT UNSIGNED NOT NULL DEFAULT 0,
    external_url VARCHAR(500) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_menu_code (tenant_id, menu_code),
    KEY idx_parent_sort (tenant_id, parent_id, sort_no),
    KEY idx_permission (tenant_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_api_resource (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    resource_name VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    controller_method VARCHAR(255) NULL,
    auth_mode VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_method_path (tenant_id, http_method, path_pattern),
    KEY idx_permission (tenant_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_menu (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    menu_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, menu_id),
    KEY idx_menu_role (tenant_id, menu_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_api (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    api_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, api_id),
    KEY idx_api_role (tenant_id, api_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_data_scope (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    resource_code VARCHAR(128) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    combine_mode VARCHAR(16) NOT NULL DEFAULT 'UNION',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_resource (tenant_id, role_id, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_data_scope_org (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rule_id BIGINT UNSIGNED NOT NULL,
    org_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (tenant_id, rule_id, org_id),
    KEY idx_org_rule (tenant_id, org_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 6.3 Token 与安全审计 DDL

```sql
CREATE TABLE sys_refresh_token (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    family_id VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    device_id VARCHAR(128) NULL,
    user_agent VARCHAR(500) NULL,
    ip_address VARCHAR(64) NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(64) NULL,
    replaced_by_id BIGINT UNSIGNED NULL,
    reuse_detected TINYINT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_hash (token_hash),
    KEY idx_user_expiry (tenant_id, user_id, expires_at),
    KEY idx_family (tenant_id, family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_password_history (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_login_log (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NULL,
    username VARCHAR(64) NULL,
    login_type VARCHAR(32) NOT NULL,
    result VARCHAR(20) NOT NULL,
    failure_code VARCHAR(64) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    trace_id VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (tenant_id, user_id, occurred_at),
    KEY idx_ip_time (ip_address, occurred_at),
    KEY idx_result_time (result, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_operation_log (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    module_code VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(100) NULL,
    request_method VARCHAR(16) NULL,
    request_path VARCHAR(500) NULL,
    request_summary JSON NULL,
    result_summary JSON NULL,
    result_code VARCHAR(64) NULL,
    duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ip_address VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    success TINYINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_module_time (tenant_id, module_code, occurred_at),
    KEY idx_operator_time (tenant_id, operator_id, occurred_at),
    KEY idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

审计日志要求：

- `request_summary` 与 `result_summary` 只存白名单字段，统一脱敏与长度限制。
- 禁止记录密码、Token、Cookie、Authorization、文件正文和大对象。
- 普通审计可在事务提交后异步写入；合规强审计使用事务内 outbox，异步投递到独立审计存储。
- 审计记录原则上不可更新；归档和删除需独立权限与审批。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 7. 工作流审批设计

### 7.1 设计原则

- Flowable 负责 BPMN 定义、运行时状态、任务和历史，是流程引擎事实源。
- 业务扩展表负责业务单据绑定、节点审批规则、操作幂等和统一查询。
- 所有 Flowable 调用封装在 `FlowableWorkflowAdapter` 内，业务代码不得直接依赖 `RuntimeService`、`TaskService`。
- 流程发布后不可原地修改，生成新版本；运行中实例继续绑定旧版本。

### 7.2 状态机

流程实例状态：

```text
DRAFT -> RUNNING -> APPROVED
                 -> REJECTED
                 -> CANCELLED
                 -> TERMINATED
RUNNING <-> SUSPENDED
```

任务状态：

```text
CREATED -> PENDING -> CLAIMED -> APPROVED
                             -> REJECTED
                             -> TRANSFERRED -> PENDING(new assignee)
PENDING/CLAIMED -> CANCELLED
```

### 7.3 会签、或签、驳回与转办

| 能力 | BPMN/实现策略 | 关键规则 |
|---|---|---|
| 会签 | Parallel/Sequential multi-instance user task | `ALL`：全部同意；`RATIO`：达到比例；人员列表在节点进入时快照 |
| 或签 | Parallel multi-instance + completion condition | 任一人同意即完成，其余任务取消并记录原因 |
| 驳回 | 节点配置 `reject_target` + 引擎状态迁移 | 只允许回到已配置节点、上一审批节点或发起人；禁止前端任意传目标节点 |
| 转办 | 更换 assignee 并写不可变记录 | 校验目标用户有效、禁止转给自己、可配置是否允许二次转办 |
| 委派 | Flowable delegate/resolve 语义 | 与转办区分：原审批人仍保留最终处理责任 |
| 撤回 | 仅在后续任务未处理且定义允许时 | 使用乐观锁，避免与审批并发冲突 |
| 加签 | 动态创建或扩展多实例任务 | 明确前加签/后加签，并记录操作者与原因 |

### 7.4 工作流扩展表

```sql
CREATE TABLE wf_definition_ext (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    process_key VARCHAR(128) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    version INT UNSIGNED NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(128) NOT NULL,
    form_schema JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_version (tenant_id, process_key, version),
    UNIQUE KEY uk_engine_definition (process_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wf_node_config (
    id BIGINT UNSIGNED NOT NULL,
    definition_ext_id BIGINT UNSIGNED NOT NULL,
    activity_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    approval_mode VARCHAR(20) NOT NULL DEFAULT 'ANY',
    approval_ratio DECIMAL(5,2) NULL,
    assignee_type VARCHAR(32) NOT NULL,
    assignee_expression VARCHAR(1000) NOT NULL,
    reject_policy VARCHAR(32) NOT NULL DEFAULT 'PREVIOUS',
    reject_target_activity_id VARCHAR(128) NULL,
    allow_transfer TINYINT UNSIGNED NOT NULL DEFAULT 1,
    allow_delegate TINYINT UNSIGNED NOT NULL DEFAULT 0,
    timeout_minutes INT UNSIGNED NULL,
    config_json JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_definition_activity (definition_ext_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wf_instance_ext (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    process_instance_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(128) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id VARCHAR(128) NOT NULL,
    title VARCHAR(300) NOT NULL,
    starter_user_id BIGINT UNSIGNED NOT NULL,
    starter_org_id BIGINT UNSIGNED NULL,
    current_activity_id VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_instance (process_instance_id),
    UNIQUE KEY uk_business_instance (tenant_id, business_type, business_id),
    KEY idx_starter_status (tenant_id, starter_user_id, status),
    KEY idx_status_started (tenant_id, status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wf_task_ext (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    task_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    activity_id VARCHAR(128) NOT NULL,
    assignee_user_id BIGINT UNSIGNED NULL,
    original_assignee_user_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL,
    approval_mode VARCHAR(20) NOT NULL,
    due_at DATETIME(3) NULL,
    claimed_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_assignee_status (tenant_id, assignee_user_id, status),
    KEY idx_instance_activity (process_instance_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wf_approval_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    operation_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NULL,
    activity_id VARCHAR(128) NULL,
    action VARCHAR(32) NOT NULL,
    operator_user_id BIGINT UNSIGNED NOT NULL,
    from_user_id BIGINT UNSIGNED NULL,
    to_user_id BIGINT UNSIGNED NULL,
    opinion VARCHAR(2000) NULL,
    attachment_refs JSON NULL,
    snapshot JSON NULL,
    trace_id VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_id (operation_id),
    KEY idx_instance_time (tenant_id, process_instance_id, occurred_at),
    KEY idx_task_time (task_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

`operation_id` 是客户端或服务端生成的幂等键。审批、驳回、转办等写操作必须先校验任务状态和版本，再写审批记录，防止重复提交。

### 7.5 工作流端口

```java
public interface WorkflowEnginePort {
    DeploymentResult deploy(WorkflowDefinition definition);
    ProcessInstanceRef start(StartProcessCommand command);
    TaskPage findTasks(TaskQuery query);
    void claim(String taskId, String userId);
    void complete(CompleteTaskCommand command);
    void reject(RejectTaskCommand command);
    void transfer(TransferTaskCommand command);
    void suspend(String processInstanceId);
    void activate(String processInstanceId);
    WorkflowHistory history(String processInstanceId);
}
```

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 8. 统一响应、异常、校验与分页

### 8.1 统一响应体

```java
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("0", "OK", data, traceId, Instant.now());
    }
}
```

约定：

- HTTP 状态表达协议结果：400、401、403、404、409、422、429、500。
- `code` 表达稳定业务错误，例如 `AUTH_001`、`SYSTEM_USER_003`。
- 不能为了“统一响应”把所有 HTTP 状态都返回 200。
- 下载、流式响应、Actuator、OpenAPI 不包装。

### 8.2 全局异常映射

| 异常 | HTTP | 处理 |
|---|---:|---|
| `MethodArgumentNotValidException` | 400 | 返回字段级错误列表 |
| `BusinessException` | 422/409 | 使用异常内错误码和可公开消息 |
| `AuthenticationException` | 401 | 不暴露认证细节 |
| `AccessDeniedException` | 403 | 记录拒绝原因与权限码 |
| `DataIntegrityViolationException` | 409 | 映射唯一键冲突，不回传 SQL |
| 未知异常 | 500 | 返回通用消息，详细堆栈只进日志 |

### 8.3 分页

```java
public record PageQuery(int page, int size, List<SortItem> sorts) {
    public PageQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
    }
}

public record PageResult<T>(
        long page,
        long size,
        long total,
        List<T> records) {
}
```

排序字段必须服务端白名单映射，禁止把客户端字段名直接拼进 SQL。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

### 8.4 国际化（i18n）

后端消息国际化基于 Spring `MessageSource` + `LocaleResolver` + `LocaleChangeInterceptor`，零额外依赖（均在 `spring-context` / `spring-webmvc`）。

- **资源束**：`src/main/resources/i18n/messages.properties`（默认/中文兜底）、`messages_zh_CN.properties`、`messages_en_US.properties`，按 `Locale` 三级回退；新增语言只需补 `messages_<locale>.properties`。
- **装配**（`common/i18n/I18nConfig`，实现 `WebMvcConfigurer`）：
  - `MessageSource`：`ReloadableResourceBundleMessageSource`（基名 `classpath:i18n/messages`，编码 UTF-8）——不使用 `ResourceBundleMessageSource`，因其走 JDK `ResourceBundle` 默认 ISO-8859-1 会导致中文乱码；
  - `LocaleResolver`：`SessionLocaleResolver`，默认 `zh_CN`，按会话记忆语言；
  - `LocaleChangeInterceptor`：参数名 `lang`（如 `?lang=en_US`），注册到拦截器链（`order(0)`）。
- **装配约束（防自引用循环依赖）**：配置类构造注入的 Bean，其 `@Bean` 工厂方法**必须声明为 `static`**——`I18nConfig` 构造注入 `LocaleChangeInterceptor`，而该 Bean 声明在配置类自身，实例级工厂需先完成配置类实例化，会形成「i18nConfig → 自身」循环（Spring Boot 3 默认禁止，启动即 `UnsatisfiedDependencyException`）；static 工厂不依赖配置类实例，环即解开。该装配由 `I18nConfigAssemblerTest`（`ApplicationContextRunner` 轻量上下文）锁定。**教训：已在库中应用过的迁移脚本不可再改（哪怕纯注释）——Flyway checksum 按整文件字节计算，注释变更同样导致 `validate-on-migrate` 校验失败、启动被拒；如确需改动，须对库执行等价 `flyway repair`（更新 `flyway_schema_history.checksum`）。**
- **解析服务**（`common/i18n/I18nService`）：按 `LocaleContextHolder` 当前语言解析消息键；键缺失时返回键本身（便于定位未翻译项）；支持 `{0}`/`{1}` 占位符。
- **接入点**：`GlobalExceptionHandler` 框架级错误已改用消息键（`error.*`），默认 zh_CN 行为与旧中文文案一致，携带 `?lang=en_US` 即返回英文。
- **配置项**（`app.i18n.*`，缺省即生效，无需在 `application*.yml` 声明）：
  - `app.i18n.default-locale`：默认语言，默认 `zh_CN`；
  - `app.i18n.param-name`：语言切换参数名，默认 `lang`。

```java
// 控制器 / 异常处理器中按当前语言取文案
String msg = i18nService.getMessage("error.not_found");            // 当前请求语言
String en  = i18nService.getMessage("error.not_found", Locale.US); // 指定语言
```

> 后续增强（非阻断）：前端配合做前端 i18n；Bean Validation 约束 `message` 键化（如 `@NotBlank(message = "{error.field.required}")`）使字段级错误也走资源束；`BusinessException` 消息亦可改为传递消息键。

### 8.5 导出中心（模板注册 + CSV / 零依赖 XLSX 双格式）

导出中心基于模板注册模式：业务模块声明一个实现 `common/export/ExportTask` 的 Spring Bean（`code()`/`displayName()`/`fileName()`/`headers()`/`rows(Map)`，可选覆写 `csv(Map)`），`system/controller/ExportController` 注入 `List<ExportTask>` 自动收集并调度，新增导出只需新增一个 Bean，无需改接口。

- **接口**：`GET /api/system/export/templates` 列举模板及其支持格式（`formats`）；`GET /api/system/export/download?code=<模板编码>&format=CSV|XLSX` 流式下载（其余查询参数原样透传给 `rows(Map)` 作筛选；**缺省 `format` 视为 CSV**，保持既有前端零改动向后兼容）。下载响应不包装 `Result`（§约定），异常走统一 JSON 错误体。
- **两种格式，共享同一份数据模型**：
  - **CSV**：复用 `common/export/CsvExporter`（UTF-8 BOM + RFC4180 转义），零依赖；
  - **XLSX**：`common/export/XlsxDataExporter` 仅用 JDK `java.util.zip` 手写最小合规 OOXML（XLSX 本质是 ZIP+XML），采用 inlineStr 内联字符串省去 `sharedStrings.xml`，文本经 XML 转义防注入。**不引入 POI**，离线构建友好。多 Sheet 能力经 `exportWorkbook(List<XlsxSheet>)` 提供：`xl/styles.xml` 定义表头样式（加粗 + 灰底，cellXfs 下标 1），首行单元格套 `s="1"`，并按内容自适应列宽（CJK 按 2 计、钳制 [6,50]）；工作表名统一规范化（非法字符 `: \ / ? * [ ]` 清洗、按代码点截断 31 字符、重名追加序号），产物首表仍为 `sheet1.xml`，导入端 `XlsxDataReader` 可直接读回（导出→导入闭环不因多 Sheet 而中断）。
- **单表 vs 多段**：单表结构模板（`headers()` 非空）默认走「headers + rows 单 Sheet」路径；多段结构（如报表导出覆写 `csv(Map)` 自产 CSV 字节、`headers()` 为空）可通过覆写 `xlsxSheets(Map)`（返回 `common/export/XlsxSheet(name, headers, rows)` 列表）解锁 XLSX 多 Sheet 工作簿——各段落各占一页（如报表的汇总/分布/明细/趋势），未提供该能力时请求 XLSX 仍被拒绝并提示改用 CSV。`/templates` 的 `formats` 由 `supportsXlsx()`（默认「单表结构即支持」）声明，前端按其渲染双格式按钮。
- **报表接入（口径同源）**：`workbench/report/export/ReportSheets` 为纯函数类，将 `ReportVO` 映射为汇总/分布（或类别分布+状态分布）/明细/趋势多 Sheet，指标中文名直接复用 `ReportCsvWriter` 的 `mainLabel`/`secondaryLabel`（提升为 public），保证报表 CSV 与 XLSX 两种导出格式的字段口径**永不漂移**；明细页缺 key 单元格归一空串，与 CSV 明细段行为一致。`ReportExportTask.supportsXlsx()` 返回 true，`requireReportRead()` 权限兜底对两种格式同样生效。
- **配置**：`system/export/config/ExportProperties`（`app.export.max-rows`，默认 10 万行，XLSX 导出超限按 400 拒绝并提示缩小筛选范围；下限钳制为 1）。放置方式与 `system/notification/config` 一致（`@EnableConfigurationProperties`）。
- **权限与审计**：`@PreAuthorize("hasAuthority('system:export:read')")`（由 `ApiResourceAutoSyncRunner` 启动时按注解自动登记并授予 SUPER_ADMIN）+ `@AuditOperation`。注意端点权限 ≠ 被导出数据自身权限（报表导出另需 `report:read`，由 `ReportExportTask` 内兜底校验）。
- **前端**：`pc-admin-vue3-vite/src/views/system/ExportCenterView.vue` 按模板返回的 `formats` 渲染「导出 CSV / 导出 XLSX」按钮；`api/export.js` 走 `requestRaw` 取 Blob，并防御式区分业务错误 JSON。
- **测试**：`ExportFormatTest`（枚举大小写/非法值解析）、`XlsxDataExporterTest`（ZIP 结构/转义/UTF-8/多 Sheet 产物与表名规范化/经 `XlsxDataReader` 读回首表）、`ReportSheetsTest`（使用/统计视角 Sheet 结构与口径、缺明细省页、写出闭环、防御性拷贝）、`ExportControllerTest`（MockMvc standalone + 最小 advice 复刻异常映射：CSV 向后兼容、单表 XLSX 分支、多 Sheet 模板双格式与工作簿产物、未知格式/未知模板/未提供 xlsxSheets/超行数上限等校验）、`ReportExportTaskTest`（权限兜底/参数解析/多段 CSV/XLSX Sheet 产出与权限兜底）。

### 8.6 导入中心（模板注册 + CSV / 零依赖 XLSX 双格式解析）

导入中心与导出中心**严格对称**：业务模块声明一个实现 `common/importer/ImportTask` 的 Spring Bean（`code()`/`displayName()`/`headers()`/`validate(ImportRow)`/`importRow(ImportRow)`），`system/service/ImportCenterService` 注入 `List<ImportTask>` 自动收集并统一调度，新增导入只需新增一个 Bean，无需新增上传接口。

- **接口**（`system/controller/ImportController`，`/api/system/import`）：
  - `GET /templates` 列举模板及其表头（供前端渲染与「下载模板」）；
  - `GET /template?code=<模板编码>&format=CSV|XLSX` 下载**仅表头**的空白模板（默认 XLSX，便于直接在 Excel 填写）；
  - `POST /{code}?dryRun=true|false`（multipart `file`）上传导入，返回统计与失败明细。
- **格式无关的统一中间模型**：CSV / XLSX 均解析为 `common/importer/TabularData`（表头 + 行，所有行补齐至统一列宽），因此同一模板天然接受两种格式，业务实现完全不必感知文件格式。
  - **CSV**：`common/importer/CsvDataReader` 状态机实现 RFC4180（引号内逗号/换行/双引号转义），自动剥离 UTF-8 BOM（与 `CsvExporter` 往返一致），兼容 `CRLF`/`LF`/单独 `CR`；
  - **XLSX**：`common/importer/XlsxDataReader` 仅用 JDK `ZipInputStream` + StAX 解析 OOXML，**不引入 POI**，与 `XlsxDataExporter` 构成「导出→编辑→导入」闭环。支持共享字符串表（`t="s"`，含富文本多 `<t>` 拼接）与内联字符串（`t="inlineStr"`）；**按单元格 `r` 属性定位列**（Excel 会跳过空单元格不写 `<c>`，按出现顺序排列会导致列错位）；禁用 DTD 与外部实体防 XXE。
- **校验与落库分离**（`ImportTask` 契约刻意如此设计）：
  - `validate(ImportRow)` 为**纯函数**校验，不产生副作用，用于组合 `common/importer/ImportValidators` 的校验构件（`required` / `maxLength` / `integerRange` / `oneOf` / `matches` / `collect`）——`collect` 一次收拢**全部**字段错误，使对话框一屏展示所有问题，用户一轮即可改完；
  - `importRow(ImportRow)` 仅在 `validate` 无错误时被调用，实现应委托既有业务服务（而非直接写库），从而复用其事务、业务规则与缓存驱逐；抛异常即视为该行失败；
  - **预检模式（`dryRun=true`）** 只调 `validate` 不落库，让用户先确认数据再正式导入。注意预检**不**校验数据库层面的唯一性冲突（如字典值重复），此类冲突在正式导入时由业务服务返回并计入该行失败明细。
- **导入语义（逐行独立）**：单行失败不影响其余行（每行由业务服务自身事务保证），失败明细随结果返回供修正后重传；全空行（Excel 编辑残留）自动跳过且不计入统计，但**行号仍按文件物理位置编号**，便于用户对照定位。
- **前置校验**：表头必须与模板逐列一致（顺序敏感，列数或名称不符即 400 并提示下载模板）；行数超 `app.import.max-rows` 拒绝并提示拆分文件；解析失败（非 OOXML / 非法 ZIP）统一转 400，避免以 500 暴露内部堆栈。
- **结果模型**：`ImportResult(dryRun, totalRows, successCount, failureCount, errors)`。`failureCount` 与 `errors` **刻意分离**——失败行数始终是真实值，而 `errors` 明细受 `app.import.max-errors` 上限约束（防大文件产生超大响应体），因此 `errors.size()` 可能小于 `failureCount`。
- **失败行报告（CSV）**：上传接口支持 `errorReport=true`——同一请求内若存在失败行，响应改为 CSV 附件（UTF-8 BOM，列：数据行号 / 失败原因 / 模板各列**原始值**），用户可离线修复后重传；无失败行仍返回 JSON 统计。**与本次导入同请求完成，不产生二次执行**。报告单元格对 `= + - @`、Tab、CR 开头的值加单引号前缀（OWASP 防 CSV 公式注入）；明细范围与 `errors` 一致（同受 `max-errors` 约束）。前端在导入对话框以「失败时下载错误报告」开关启用，走 `requestRaw` blob 通道按 content-type 区分 JSON/CSV。
- **配置**（`system/importer/config/ImportProperties`，`app.import.*`，缺省即生效）：`max-rows` 默认 5000（下限钳 1）、`max-errors` 默认 200（下限钳 1）。
- **权限与审计**：`system:import:read`（模板列表 / 下载模板）、`system:import:write`（上传导入），由 `ApiResourceAutoSyncRunner` 自动登记并授予 SUPER_ADMIN；上传接口另加 `@RateLimit(max = 10, windowSeconds = 60, scope = IP)`。`@AuditOperation(recordParams = false)`——**刻意不记录入参**，因 `MultipartFile` 无法安全序列化进审计摘要。
- **试点实现**：`system/importer/DictDataImportTask`（字典数据），列宽上限与 `sys_dict_data` 表结构对齐（类型码 64 / 标签 100 / 值 100 / 备注 255），落库复用 `DictDataService.create` 从而自动继承「同类型下字典值唯一」规则与 `dict-data` 缓存整驱。
- **前端**：`pc-admin-vue3-vite/src/views/system/ImportCenterView.vue` + `api/import.js`，页内提供「下载模板（XLSX/CSV）→ 选择文件 → 预检 → 确认导入 → 失败明细表」闭环；路由 `/system/import`（菜单与权限由后端驱动，见 `V36__import_center.sql`）。
- **测试**：`CsvDataReaderTest`（BOM/引号语义/三种行分隔/列宽补齐/空行保真）、`XlsxDataReaderTest`（与写出器往返一致、按 `r` 属性对齐稀疏列、共享字符串含富文本、非 OOXML 拒绝）、`ImportValidatorsTest`、`ImportCenterServiceTest`（格式识别/表头校验/行号定位/空行跳过/业务冲突转行错误/预检不落库/行数上限/明细上限分离/CSV 与 XLSX 等价/失败行原始值捕获）、`ImportControllerTest`（模板列表与下载、上传预检、四类错误分支、失败报告 CSV 附件与防公式注入、全成功回落 JSON）、`DictDataImportTaskTest`（纯校验不触库、trim 与空值归一、冲突透传）共 52 例。

### 8.7 敏感数据脱敏（common/sensitive）

审计摘要、登录日志、导出文件等**对外可见**的数据中可能夹带登录密码、Token、手机号、邮箱、身份证、银行卡号等。存储层允许保留原文（排障需要），但对外返回或落盘导出时必须脱敏——设计方案 §20 明确要求「审计日志可按 traceId 查询且无敏感字段」。

- **模块位置**：`common/sensitive`（框架级通用能力）。该类原位于 `common/audit` 仅服务审计日志，随登录日志、导出中心等复用需求出现而提升为通用模块；**既有静态方法签名与行为保持不变**（迁移零破坏），仅新增更细粒度策略与统一分派入口。
- **类型驱动**：`SensitiveType` 枚举（`MOBILE` / `ID_CARD` / `BANK_CARD` / `EMAIL` / `NAME` / `ADDRESS` / `IP` / `CREDENTIAL_TEXT` / `NONE`）把「字段属于哪类敏感数据」与「脱敏算法」解耦；调用方只声明类型，规则集中在 `SensitiveMasker`，避免各处硬编码正则导致口径漂移。统一入口 `SensitiveMasker.mask(value, type)`（null 类型或 `NONE` 原样返回，且对已脱敏文本幂等）。
- **策略**（`SensitiveMasker`，纯函数、零依赖、便于单测）：
  - `maskPhone` 保留前 3 后 4（`138****1234`）；`maskEmail` 保留首字符与域名（`z***@example.com`）；`maskIdCard` 保留后 4 位；`maskBankCard` 保留后 4 位并**等长掩码**（便于核对位数）；`maskName` 保留首字符；`maskAddress` 保留前 6 字符；
  - `maskIp` 对 IPv4 保留前两段（`192.168.*.*`，保留网段以便判断来源区域），IPv6 与代理链等非 IPv4 文本整体掩码；
  - `maskSummary` 处理**不定结构的自由文本**（审计摘要）：先按「键名」抹除整段凭证值（`password`/`token`/`secret`/`captcha` 等，兼容 snake_case/kebab-case/camelCase，长短键名排序避免漏匹配），再按「值形态」抹 PII（手机号/邮箱/身份证）。用正则而非 Jackson 反序列化，因为摘要可能是参数拼接串而非严格 JSON。
- **安全默认 + 权限逃生**：对外视图默认脱敏；仅当主体显式持有对应「明文可见」权限时才回传原文——审计用 `system:audit:sensitive`，登录日志用 `system:login-log:sensitive`（判定统一走 `SecurityContextFacade`）。这些权限**不在种子数据中预置**，需按需授予，因此默认行为即安全。
- **落地应用**：
  - **审计日志**（`common/audit/AuditLogView`）：摘要与 IP 一律脱敏，高权限仅放行摘要原文（IP 恒掩码，避免个人轨迹外泄）；
  - **登录日志**（`system/log/LoginLogView`）：经 `LoginLogView.of(DO, sensitiveVisible)` 统一构造，默认 IP 掩码，补齐该类既有的「展示层掩码」待办；
  - **导出**（`system/export/LoginLogExportTask`）：导出文件会离开系统边界，故**一律掩码**——即便导出者持有敏感权限也不放行原文。
- **测试**：`SensitiveMaskerTest`（18 例：既有 12 例断言原样保留以证明迁移零行为变更，另加银行卡/姓名/地址/统一分派/幂等/边界）、`LoginLogViewTest`（4 例：默认掩码/敏感可见/非 IPv4 整体掩码/null 视图）、`LoginLogExportTaskTest`（4 例：元数据/行宽一致/参数透传/ISO 时间解析与非法值容错）。

> **扩展方式**：新增一类敏感数据的脱敏，只需在 `SensitiveType` 加常量并在 `SensitiveMasker.mask` 的 switch 补一个分支（switch 穷尽性会强制补全）；新增一处应用，只需在视图工厂或导出任务里按类型调用 `SensitiveMasker.mask(...)`，无需改动机器。

## 9. 关键配置示例

### 9.1 根 Maven 配置

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
</parent>

<properties>
    <java.version>21</java.version>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <flowable.version>8.0.0</flowable.version>
    <xxl-job.version>3.4.2</xxl-job.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-bom</artifactId>
            <version>${mybatis-plus.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

核心依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter-process</artifactId>
    <version>${flowable.version}</version>
</dependency>
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>${xxl-job.version}</version>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

版本治理要求：

- Spring 生态依赖优先由 Boot BOM 管理。
- 禁止业务模块各自覆盖 Jackson、Netty、Spring Security 版本。
- CI 每周运行 OWASP Dependency-Check/Trivy 或组织统一 SCA。
- XXL-JOB 3.4.2 修复了 RollingLog 越权查看问题；升级时仍需逐条核对官方安全公告。

### 9.2 主配置

```yaml
spring:
  application:
    name: java-admin-scaffold
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  lifecycle:
    timeout-per-shutdown-phase: 30s
  jackson:
    default-property-inclusion: non_null
  # 数据源连接属性已迁移至 app.datasources（见 §9.6），并按环境外部化到环境变量，避免硬编码。
  # 关闭 Spring Boot 单数据源自动装配，改由 app.datasources 多数据源配置接管。
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
  flyway:
    enabled: true
    baseline-on-migrate: false
    validate-on-migrate: true
  data:
    redis:
      timeout: 2s
      connect-timeout: 2s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
          max-wait: 500ms

server:
  port: 8080
  shutdown: graceful
  forward-headers-strategy: framework

management:
  server:
    port: 9090
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
    access:
      default: read-only
  endpoint:
    health:
      probes:
        enabled: true
        add-additional-paths: true
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
  tracing:
    sampling:
      probability: ${TRACING_SAMPLE_RATE:0.1}

# 持久层为 jOOQ（不使用 MyBatis-Plus）；多数据源配置见 §9.6。
# 慢 SQL 阈值（jOOQ ExecuteListener 采集）：
app:
  jooq:
    slow-query-threshold-ms: 500

flowable:
  database-schema-update: true
  async-executor-activate: true
  history-level: audit
  process-definition-location-prefix: classpath*:/processes/

app:
  security:
    issuer: java-admin-scaffold
    audience: admin-api
    access-token-ttl: 15m
    refresh-token-ttl: 7d
    max-login-failures: 5
    lock-duration: 15m
  slow-sql:
    threshold: 500ms
```

### 9.3 本地配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/scaffold?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: ${MYSQL_USER:scaffold}
    password: ${MYSQL_PASSWORD:scaffold}
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}

xxl:
  job:
    enabled: true
    admin:
      addresses: http://localhost:8088
    executor:
      appname: java-admin-scaffold
      port: 9999
      logpath: ./logs/xxl-job
      logretentiondays: 30
    access-token: ${XXL_JOB_ACCESS_TOKEN:local-dev-token}

logging:
  level:
    root: INFO
    com.acme.scaffold: DEBUG
```

### 9.4 Spring Security 配置骨架

```java
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health", "prometheus")).permitAll()
                .anyRequest().hasAuthority("platform:actuator:read"))
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers(EndpointRequest.toAnyEndpoint()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").access(localOnly())
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(restAuthenticationEntryPoint())
                .accessDeniedHandler(restAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

生产环境如果 Prometheus 不经过认证，应限制在管理网络、防火墙或 ServiceMonitor 内，不可把 9090 暴露到公网。

上述安全链按浏览器管理端设计：前端先访问 `/api/auth/csrf` 获取 CSRF Token，后续变更请求携带 `X-XSRF-TOKEN`。若提供不使用 Cookie 的移动端/服务端 Token API，应使用独立 `SecurityFilterChain`，仅对纯 Bearer Token 请求关闭 CSRF，不能全局混用两种策略。

### 9.5 jOOQ 配置（替代 MyBatis-Plus）

```java
@Configuration(proxyBeanMethods = false)
class JooqConfig {
    // 产出 @Primary DSLContext：底层指向多数据源路由（见 §9.6），
    // 未切换时回落主库；MySQL 方言 + Snake 命名映射 + 慢 SQL 监听。
    @Bean
    @Primary
    public DSLContext dslContext(DataSource routingDataSource,
            @Value("${app.jooq.slow-query-threshold-ms:500}") long slowQueryThresholdMs) {
        DataSourceConnectionProvider provider =
                new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(routingDataSource));
        org.jooq.Configuration cfg = new DefaultConfiguration()
                .set(provider)
                .set(new DefaultTransactionProvider(provider))
                .set(SQLDialect.MYSQL)
                .set(new SnakeRecordMapperProvider())
                .set(new DefaultExecuteListenerProvider(new SlowQueryListener(slowQueryThresholdMs)))
                .set(new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER));
        return DSL.using(cfg);
    }
}
```

> 持久层已从 MyBatis-Plus 迁移到 jOOQ（详见《开发手册》§9 持久层用法）。复杂列表一次性联表/批量查询，禁止循环调用形成 N+1。

### 9.6 多数据源配置（app.datasources）

框架支持**连接并切换多个数据库**，连接串与凭据统一外部化到环境变量，避免硬编码。完整使用说明见《多数据源使用指南》`docs/manual/datasource-guide.md`。

**主配置（application.yml）**：声明默认主库名，并关闭 Spring Boot 单库自动装配。

```yaml
spring:
  # 关闭单数据源自动装配，改由 app.datasources 多数据源配置接管
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
app:
  datasources:
    primary: primary          # 默认（主）数据源；不切换时所有查询落主库
```

**Profile 配置（application-local.yml / application-prod.yml）**：填充各具名数据源，凭据走 `${ENV}`。

```yaml
app:
  datasources:
    entries:
      primary:                # 主库：业务表 / Flyway / JobRunr / Flowable
        url: ${DB_PRIMARY_URL:jdbc:mysql://localhost:3306/scaffold?...}
        username: ${DB_PRIMARY_USERNAME:root}
        password: ${DB_PRIMARY_PASSWORD:root}
      report:                 # 第二个库（按需启用，凭据同样走环境变量）
        url: ${DB_REPORT_URL}
        username: ${DB_REPORT_USERNAME}
        password: ${DB_REPORT_PASSWORD}
```

**加载与路由**：`MultiDataSourceProperties`（`@ConfigurationProperties("app.datasources")`）聚合配置；`DataSourceRegistry` 启动期逐库建 Hikari 池（缺失主库或缺 `url` 即 fail-fast）；`RoutingDataSource`（`AbstractRoutingDataSource`）按 `DataSourceContextHolder`（ThreadLocal）路由，未切换回落主库。切换方式有两种：

- **声明式** `@WithDataSource("report")`：标注方法，切面置 `HIGHEST_PRECEDENCE` 早于事务切面，结束后自动还原；
- **编程式** `DataSourceRouter.with("report", task)` / `router.dsl("report")`：显式按名切换或取具名 `DSLContext`。

连接池默认参数（`MultiDataSourceProperties.Pool`）：`minimumIdle=5 / maximumPoolSize=20 / connectionTimeout=3000 / validationTimeout=1000 / idleTimeout=600000 / maxLifetime=1800000 / connectionTestQuery=SELECT 1`。

> **部署注意**：prod 环境变量由旧的单库 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 更名为 `DB_PRIMARY_URL` / `DB_PRIMARY_USERNAME` / `DB_PRIMARY_PASSWORD`，部署侧必须同步。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 10. 性能监控与可观测性

### 10.1 指标体系

| 类别 | 指标 | 来源 |
|---|---|---|
| HTTP | QPS、P50/P95/P99、4xx/5xx、并发请求 | `http.server.requests` |
| JVM | heap/non-heap、GC 暂停、线程、类加载 | Micrometer JVM binders |
| 系统 | CPU、load、文件句柄、磁盘 | Process/System metrics + Node Exporter |
| 连接池 | active、idle、pending、max、timeout | Hikari Micrometer |
| MySQL | 慢查询、锁等待、连接、buffer pool | MySQL Exporter + slow query log/performance_schema |
| Redis | 延迟、连接、内存、命中率、eviction | Redis Exporter + Lettuce metrics |
| 工作流 | 待办数、超时数、平均审批时长、失败数 | 自定义 `workflow.*` |
| 调度 | 成功率、失败率、耗时、重试、积压 | XXL-JOB + 自定义 `job.*` |
| 安全 | 登录失败、锁定、Token 重放、403 | 自定义 `security.*` |

### 10.2 慢 SQL

采用两层监控：

1. **数据库侧为准**：启用 MySQL slow query log 或 `performance_schema`，生产阈值建议先设 500ms。
2. **应用侧定位上下文**：MyBatis Interceptor 记录 statementId、耗时、影响行数、traceId；禁止输出敏感参数和完整大 SQL。
3. P6Spy 只允许 local/dev 使用，不建议生产常开。
4. 对慢 SQL 进行归一化聚合，避免把 SQL 参数作为 metrics tag 导致高基数。

### 10.3 健康检查

- `/livez`：只检查 JVM 与应用主循环，不依赖 MySQL、Redis、第三方服务。
- `/readyz`：检查提供服务所必需的依赖；若 Redis 只是可降级缓存，不应因 Redis 故障摘除实例。
- `/actuator/health`：详细信息仅允许监控角色访问。
- `/actuator/prometheus`：仅内网抓取。
- `shutdown`、`heapdump`、`env`、`beans` 默认不暴露。

### 10.4 告警阈值初始建议

以下阈值是初始值，运行两到四周后必须根据基线与 SLO 调整：

| 级别 | 条件 | 持续时间 | 处理建议 |
|---|---|---:|---|
| P1 | 可用性低于 99% 或 5xx > 5% | 2 分钟 | 立即响应 |
| P2 | 5xx > 1% | 5 分钟 | 排查发布、依赖和线程池 |
| P2 | HTTP P95 > 500ms 或 P99 > 1s | 10 分钟 | 按 URI 模板和 trace 定位 |
| P2 | JVM heap > 90% | 5 分钟 | 检查 GC、缓存与泄漏 |
| P3 | JVM heap > 80% | 15 分钟 | 容量预警 |
| P2 | GC pause P99 > 500ms | 10 分钟 | 分析堆、分配率与 GC 日志 |
| P2 | 进程 CPU > 85% | 10 分钟 | 查热点、流量与线程 |
| P2 | Hikari active/max > 85% 或 pending > 0 | 5 分钟 | 查慢 SQL、事务和池容量 |
| P2 | MySQL 慢 SQL > 2s | 任一高频 SQL | 立即优化或限流 |
| P3 | MySQL 慢 SQL > 500ms | 5 分钟增长 | 建立优化清单 |
| P2 | Redis memory > 85% 或 eviction > 0 | 5 分钟 | 扩容或调整淘汰策略 |
| P2 | Redis P99 > 10ms（同机房） | 10 分钟 | 检查网络、热 key、大 key |
| P2 | XXL-JOB 连续失败 >= 3 | 单任务 | 暂停/告警/人工处理 |
| P2 | 审批任务超过 SLA | 按流程配置 | 催办或升级 |

不要对 QPS 设置静态“过高”阈值，应使用历史基线、容量模型和同比突增比例。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 11. 操作审计设计

注解示例：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {
    String module();
    String action();
    String resourceId() default "";
}
```

处理流程：

1. AOP 只收集元数据、操作者、请求摘要和开始时间。
2. 方法完成后生成 `AuditEvent`。
3. 事务提交后写日志，失败事务也单独记录失败审计。
4. 通过 `SensitiveFieldSanitizer` 对 Request/Response 白名单化。
5. 审计写入失败不得无限阻塞主请求；强合规操作改用事务 outbox。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 12. API 最小集合

```text
GET    /api/auth/csrf
POST   /api/auth/login
POST   /api/auth/refresh
POST   /api/auth/logout
GET    /api/auth/me
PUT    /api/auth/password

GET    /api/system/users
POST   /api/system/users
PUT    /api/system/users/{id}
PUT    /api/system/users/{id}/status
PUT    /api/system/users/{id}/roles

GET    /api/system/orgs/tree
POST   /api/system/orgs
PUT    /api/system/orgs/{id}

GET    /api/system/roles
POST   /api/system/roles
PUT    /api/system/roles/{id}/menus
PUT    /api/system/roles/{id}/apis
PUT    /api/system/roles/{id}/data-scope

GET    /api/system/menus/tree
GET    /api/system/api-resources
POST   /api/system/api-resources/sync-preview
POST   /api/system/api-resources/sync-confirm

POST   /api/workflow/definitions/{key}/publish
POST   /api/workflow/instances
GET    /api/workflow/instances/{id}
GET    /api/workflow/tasks/todo
POST   /api/workflow/tasks/{id}/claim
POST   /api/workflow/tasks/{id}/approve
POST   /api/workflow/tasks/{id}/reject
POST   /api/workflow/tasks/{id}/transfer
GET    /api/workflow/instances/{id}/history
```

写接口建议支持 `Idempotency-Key`，审批类接口必须强制使用。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 15. 分阶段实施建议

### 阶段 0：工程骨架

交付：

- Maven 多模块、Boot 4.1、Java 21。
- Docker Compose：MySQL、Redis、XXL-JOB。
- Flyway、统一响应、异常、校验、分页、多环境配置。
- `/livez`、`/readyz`、`/actuator/prometheus`。

验收：一条命令启动依赖，Maven 可构建，健康检查通过。

### 阶段 1：认证与 RBAC

交付：

- 用户、机构、角色、菜单、接口资源 CRUD。
- 登录、JWT 签发、Refresh Token 轮换、注销、强制下线。
- 方法级鉴权与动态接口资源扫描预览。
- 密码策略、登录锁定、登录日志。

验收：覆盖 401、403、权限变更即时失效、Refresh Token 重放检测。

### 阶段 2：数据权限与审计

交付：

- `DataScopeProvider`、五类数据范围、查询显式接入。
- 操作审计、字段脱敏、审计检索。
- 幂等键、乐观锁和关键业务防重。

验收：多角色范围合并正确，越权数据不可查询，审计不泄露敏感字段。

### 阶段 3：工作流

交付：

- Flowable 8 适配层、流程定义发布、实例和任务 API。
- 会签、或签、驳回、转办、撤回。
- 审批记录、任务 SLA、幂等和并发控制。

验收：至少用“请假审批”覆盖全部流转路径和并发重复提交。

### 阶段 4：调度与可观测性

交付：

- XXL-JOB 执行器、标准 Handler、失败指标。
- Prometheus/Grafana dashboard、Alertmanager 规则。
- OpenTelemetry trace、结构化日志、慢 SQL 定位。

验收：一次请求可从 trace 找到日志、SQL 和外部调用；故障可触发告警。

### 阶段 5：生产加固

交付：

- 密钥轮换、配置中心/Secrets、备份恢复演练。
- 限流、防重放、安全头、上传安全、依赖 SCA。
- 压测、容量基线、故障注入、回滚预案。

验收：发布、回滚、密钥轮换、MySQL/Redis 短暂故障均有演练记录。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 16. 关键架构决策

1. **主线用 Boot 4.1，而非 Boot 3.5**：3.5.16 已是该代最后一个 OSS 版本；新脚手架应减少短期二次升级。
2. **使用 Spring Security Resource Server 处理 JWT**：避免自写核心 token 校验过滤器。
3. **Access JWT + opaque Refresh Token**：兼顾无状态性能、可撤销与重放检测。
4. **菜单与 API 权限分离**：前端显示从来不是后端安全边界。
5. **数据权限显式进入 Repository**：比全局 SQL 文本改写更可测、更可控。
6. **Flowable 是流程事实源，扩展表不复制运行时真相**：避免双写状态漂移。
7. **工作流和 XXL-JOB 必须经 Port 隔离**：第三方版本变化不污染业务模块。
8. **数据库慢查询和应用 trace 双层定位**：一个负责准确统计，一个负责业务上下文。
9. **初期模块化单体**：保留本地事务和低运维成本，达到明确条件再拆服务。
10. **所有生产配置外置**：密钥、数据库密码、Token 私钥、XXL-JOB Token 不进入代码库。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 17. 拆分为微服务的触发条件

满足以下任一条件再考虑拆分：

- 模块需要独立扩缩容，且资源曲线明显不同。
- 模块有独立发布节奏或独立团队。
- 单体发布窗口已成为业务瓶颈。
- 数据隔离、合规或故障域要求独立部署。
- 模块边界已经通过内部 API/事件稳定运行至少两个迭代。

拆分顺序建议：`auth`（若成为统一身份中心）→ `workflow` → `job` → 其他业务模块。`system` 基础资料不要过早拆分，否则权限与组织查询会变成高频远程调用。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 18. 风险与注意事项

- Boot 4 使用 Spring Framework 7、Jakarta Servlet 6.1 和 Jackson 3，旧组件必须先做兼容性验证。
- Flowable 8 与 Boot 4 同代，但仍应通过 Adapter 隔离并锁定补丁版本。
- XXL-JOB 应使用至少 3.4.2，并限制控制台网络边界、修改默认账号、设置强 Token，持续跟踪安全公告。
- Redis 不应成为用户、权限、流程状态的唯一事实源。
- JWT 无法天然即时撤销；必须结合短有效期、token version、Refresh Token 撤销和必要的 jti 黑名单。
- `tenant_id` 只是字段预留，不等于完成多租户安全；真正启用前要补充租户解析、SQL 隔离、缓存隔离和测试矩阵。
- MySQL 与 Flowable 表升级必须由版本化脚本控制，生产禁止 `database-schema-update=true`。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 19. 官方资料依据

- Spring Boot 4.1.1 系统要求：<https://docs.spring.io/spring-boot/system-requirements.html>
- Spring Boot 3.5.16 为 3.5.x 最后一个 OSS 版本：<https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now>
- Spring Boot Actuator 端点与健康探针：<https://docs.spring.io/spring-boot/reference/actuator/endpoints.html>
- Spring Boot Micrometer 指标：<https://docs.spring.io/spring-boot/reference/actuator/metrics.html>
- Spring Boot Tracing：<https://docs.spring.io/spring-boot/reference/actuator/tracing.html>
- Spring Security OAuth2 Resource Server：<https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html>
- Spring Security 密码存储：<https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html>
- MyBatis-Plus Boot 4 Starter：<https://baomidou.com/en/getting-started/>
- MySQL 8.4 LTS 文档：<https://dev.mysql.com/doc/refman/8.4/en/>
- Flowable Spring Boot 文档：<https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot/>
- Flowable Releases：<https://github.com/flowable/flowable-engine/releases>
- XXL-JOB 3.4.2 Release：<https://github.com/xuxueli/xxl-job/releases>
- XXL-JOB Core Maven Central：<https://central.sonatype.com/artifact/com.xuxueli/xxl-job-core>

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 20. 最小可交付验收清单

- [ ] `docker compose up -d` 后 MySQL、Redis、XXL-JOB 健康。
- [ ] `./mvnw clean verify` 通过。
- [ ] Flyway 可在空库初始化，可对已初始化库重复校验。
- [ ] 登录返回 Access Token 和一次性 Refresh Token。
- [ ] Refresh Token 轮换与重放检测通过。
- [ ] 用户、机构、角色、菜单、接口资源 CRUD 可用。
- [ ] 未授权接口返回 401，越权返回 403。
- [ ] 权限变更后旧权限缓存与 Token 版本正确失效。
- [ ] 会签、或签、驳回、转办与重复提交测试通过。
- [ ] 审计日志可按 traceId 查询且无敏感字段。
- [ ] `/livez`、`/readyz`、Prometheus 指标可用。
- [ ] Hikari、HTTP、JVM、慢 SQL、工作流、调度指标可观察。
- [ ] local/test/prod 配置隔离，生产 Secrets 不在 Git。

---

## 附录 A. 实现状态与需求差距（速览）

以下为《需求文档 vs 实现差距分析》的核心结论（完整逐项证据见 `docs/archive/requirement-gap-analysis.md`）。

**核心功能已落地**：RBAC 权限、认证/刷新/登出、数据权限（五类范围 + AOP 注入）、操作审计（脱敏 + traceId 查询）、幂等（Idempotency-Key，审批强制）、Token 版本失效、工作流（内置 + 自定义设计器 / 发布 / 会签或签 / 驳回 / 转办 / 认领）、监控、公告/站内信、实时 WebSocket、服务端排序白名单、工作台聚合统计。

**增强型基座能力**：Kafka 海量数据有序消费（同 key 分区 + 单线程串行 + 版本表 + 手动提交，保序四层机制）、Spring Cache 缓存管理（Redis 实现 + 进程内统计 + 监控联动，`app.cache.*` 配置化）、通知渠道抽象（站内信 / Webhook / WebSocket / 邮件 / 短信五渠道，`NotificationPort` 路由，邮件与短信默认关闭、短信网关 LOG/HTTP 可配）、CSV 导出中心、keyset 游标深翻页、N+1 查询告警（jOOQ ExecuteListener）、`@RateLimit` 注解化限流、JaCoCo 覆盖率 profile。

**可观测与运维**：Actuator `/prometheus` + Micrometer 基座；OTel W3C `traceparent` 传播 + 零依赖 OTLP/HTTP 导出器（opt-in）；Grafana 五块看板 + Alertmanager 五条规则 + 应用内接收端（`monitoring/` 交付）。

**安全与质量**：登录验证码、Token TTL 30m/30d、多端登录限制、密钥轮换（kid 双密钥）、外部密钥源 Vault/KMS（零依赖）、速率限制/防重放（opt-in）、密码强度策略、CI 质量门禁（`.github/workflows/ci.yml`）、编码约定校验（`scripts/check-conventions.py`，H1 循环 / H2 重复 FQN / H3 依赖方向 / H4 数据类放置 硬门禁 + W* 警告）。

**范围外 / N/A**：多租户 G8（明确不启用，`tenant_id` 仅预留）；文件上传安全（无上传接口）；压测/容量基线/故障注入/回滚演练（需原生环境）；Docker Compose 启动（有意改为原生进程启动，无 Docker）。
