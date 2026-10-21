# Java 后端基础管理框架（可运行脚手架）

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖 RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 MyBatis-Plus → jOOQ，任务调度由 XXL-JOB → JobRunr；迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 文档

项目文档已统一整理为四本独立手册，详见 **[`docs/index.md`](docs/index.md)**：

- 架构手册：[`docs/manual/architecture.md`](docs/manual/architecture.md)
- 开发手册：[`docs/manual/development.md`](docs/manual/development.md)
- 运维手册：[`docs/manual/operations.md`](docs/manual/operations.md)
- 部署手册：[`docs/manual/deployment.md`](docs/manual/deployment.md)

## 快速开始

```bash
# 1) 原生安装并启动依赖（MySQL / Redis，无需 Docker）
# 2) 超级管理员账号由 V32__consolidated_schema.sql 内置（admin / SUPER_ADMIN）
# 3) 编译运行
./mvnw -o clean package -DskipTests
java -jar target/*.jar
# 4) 访问
#    API:            http://localhost:8080/api/...
#    Actuator:       http://localhost:9090/actuator/health
#    Prometheus:     http://localhost:9090/actuator/prometheus
#    Swagger:        http://localhost:8080/swagger-ui.html   （仅 local）
#    JobRunr 面板:   http://localhost:8080/dashboard         （仅 local）
```

> 完整启动、数据库迁移升级（V32）、可观测性部署与生产加固步骤见《部署手册》。
