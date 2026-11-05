# Java 后端基础管理框架（可运行脚手架）

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖 RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 MyBatis-Plus → jOOQ，任务调度由 XXL-JOB → JobRunr；迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。
>
> V1.0.3 起工程按功能维度拆分为 **17 个 Maven 模块**（参照 RuoYi-Vue 模块化单体思路；权威模块清单以根 `pom.xml` 的 `<modules>` 为准），模块职责、依赖 DAG 与命名规范见 **[`docs/MODULES.md`](docs/MODULES.md)**；架构 / 关键流程 / 使用方式见 `docs/` 下新生成的核心文档。

## 模块结构

```
java-admin-framework（17 个 Maven 模块）
├── scaffold-admin        # 启动装配层：应用入口、运行期配置、Flyway 迁移、集成测试（唯一可部署模块）
├── scaffold-common       # 基础层：统一响应/异常/分页、i18n、审计/幂等切面、导出/导入、jOOQ DSL
├── scaffold-realtime     # 通信层：WebSocket 握手鉴权端口与会话管理
├── scaffold-kafka        # 通信层：Kafka 保序规则与消费者薄封装
├── scaffold-codegen      # 工具层：表元数据读取与 CRUD 骨架生成
├── scaffold-security     # 认证授权与令牌域：JWT/刷新令牌/Token 版本、OAuth2、密码策略、数据权限、限流、在线会话
├── scaffold-framework    # 框架装配层：全局 Spring 配置、指标链路、N+1/慢查询监听
├── scaffold-system       # 业务层：RBAC + 系统管理
├── scaffold-monitor      # 业务层：告警收件箱、健康探针、监控查询
├── scaffold-job          # 业务层：任务定义管理与周期任务注册（JobRunr）
├── scaffold-workbench    # 业务层：工作台看板与报表
├── scaffold-workflow     # 业务层：Flowable 对接、任务认领/转办/完成
├── scaffold-approval     # 业务层：审批中心门户（编排 workflow 能力）
├── scaffold-cms          # 业务层：内容管理（启发式/占位实现）
├── scaffold-im           # 业务层：即时通讯（启发式/占位实现）
├── scaffold-plugin       # 工具层：插件扩展点（启发式/占位实现）
└── scaffold-ai           # 业务层：AI 接入（默认关闭，占位实现）
```

## 文档

重构后的统一文档入口见 **[`docs/README.md`](docs/README.md)**。新生成的核心文档：

- 架构说明：[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- 模块职责：[`docs/MODULES.md`](docs/MODULES.md)
- 关键流程：[`docs/KEY-FLOWS.md`](docs/KEY-FLOWS.md)
- 使用方式：[`docs/USAGE.md`](docs/USAGE.md)

## 快速开始

```bash
# 1) 原生安装并启动依赖（MySQL / Redis，无需 Docker）
# 2) 超级管理员账号由 V32__consolidated_schema.sql 内置（admin / SUPER_ADMIN）
# 3) 编译运行（离线仓库）
mvn -o clean package -DskipTests
java -jar scaffold-admin/target/scaffold-admin-1.0.0.jar
# 4) 访问
#    API:            http://localhost:8080/api/...
#    Actuator:       http://localhost:9090/actuator/health
#    Prometheus:     http://localhost:9090/actuator/prometheus
#    Swagger:        http://localhost:8080/swagger-ui.html   （仅 local）
#    JobRunr 面板:   http://localhost:8080/dashboard         （仅 local）
```

> 完整启动、数据库迁移升级（V32~V39，位于 `scaffold-admin/src/main/resources/db/migration/`）、可观测性部署与生产加固步骤见《部署手册》。
