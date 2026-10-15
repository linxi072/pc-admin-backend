# 文档总目录（java-admin-framework）

> 项目文档已统一梳理为**四本独立手册**，覆盖架构、开发、运维、部署四类读者视角；分散的历史工作记录已归档至 [`docs/archive/`](archive/)。
> 本目录是所有文档的统一入口，手册之间按主题分工、互不重复，交叉内容以「参见对应手册」指引。

## 一、四本手册

| 手册 | 路径 | 面向读者 | 核心内容 |
|---|---|---|---|
| 架构手册 | [`docs/manual/architecture.md`](manual/architecture.md) | 架构师 / 技术负责人 | 总体架构与依赖规则、分层与命名、RBAC 权限模型、核心表结构（DDL）、工作流状态机、统一响应/异常/分页契约、关键配置示例、可观测性指标、操作审计设计、API 最小集合、关键架构决策（ADR）、微服务拆分触发条件、风险与验收清单、实现状态速览 |
| 开发手册 | [`docs/manual/development.md`](manual/development.md) | 后端 / 前端开发者 | 本地开发环境、持久层 jOOQ 用法、系统管理模块开发、工作流审批与自定义设计器、调度任务、接口资源扫描登记、角色数据权限、服务端排序、菜单模块与拖拽排序、代码生成器与验证码、前端接口规范与工程说明、集成测试与缺陷修复记录 |
| 运维手册 | [`docs/manual/operations.md`](manual/operations.md) | 运维 / 一线支撑 | 可观测性（Actuator/Prometheus 与指标解读）、监控/日志/在线用户/定时任务等运营菜单、报表与工作台、审计日志查询与脱敏、健康探针、Token 缓存失效、常见故障排查（含工作流设计「不存在」案例） |
| 部署手册 | [`docs/manual/deployment.md`](manual/deployment.md) | 部署 / 上线负责人 | 部署架构与依赖（MySQL/Redis/Flyway/JobRunr/Flowable）、本地原生部署（无 Docker）、数据库迁移与 V32 既有环境升级、profile 配置、可观测性运营侧部署、生产加固清单、CI/CD、冒烟验收与回滚 |

## 二、部署与运维配套产物（非文档，随代码交付）

- `monitoring/`：Prometheus / Alertmanager / Grafana 配置与看板（运营侧部署用，详见《部署手册》）。
- `db/migration/V32__consolidated_schema.sql`：合并迁移（V1~V31 顺序拼接，可执行为单一来源）。
- `db/reference/schema_ddl.sql`、`db/reference/data_dml.sql`：DDL / DML 分类参考件（**不可直接当迁移执行**，详见《部署手册》§SQL 资产整理）。
- `scripts/`：`build_v32.py`、`build_reference.py` 等构建/校验脚本。

## 三、历史归档（已被手册整合，保留以供追溯）

位于 [`docs/archive/`](archive/)：

- `design-spec.md` — 《Java 后端基础管理框架设计方案》（目标架构，含包名 `com.acme.scaffold`、XXL-JOB、Boot 4 基线；与实际实现存在有意偏差）
- `java-admin-framework-README.md` — 框架原 README（已拆分并入四本手册）
- `menu-api-and-schema.md` — 菜单接口与表结构说明
- `requirement-gap-analysis.md` — 需求文档 vs 实现差距分析 & 分阶段方案
- `frontend-api-standard.md` — 前端接口调用规范
- `pc-admin-vue3-vite-README.md` — 前端工程说明
- `monitoring-README.md` — 可观测性运营侧部署说明
- `QA-business-test-report-V28.md` — V28 QA 业务测试报告与修复总结
- `defect-fix-report-java-admin-framework-2026-10-08.md` — 缺陷修复清单（分级报告）
- `defect-investigation-workflow-design-not-found-2026-10-12.md` — 工作流设计「不存在」问题排查报告
- `module-plan-java-admin-framework-2026-10-08.md` — 后续模块开发规划
- `review-java-admin-framework-2026-10-08.md` — 代码审查报告

## 四、文档约定与基线

- **实际技术栈**：Spring Boot 3.3.4 / Java 17·21 / MyBatis-Plus + jOOQ 3.19.11 / JobRunr 6.3.4 / Flowable 7.1.0 / MySQL 8.x / Redis；**不使用 Docker**，全部以原生进程方式构建与部署。
- **设计文档 vs 实现**：设计方案描述目标架构（包名 `com.acme.scaffold`、XXL-JOB、Spring Boot 4 基线），而实际实现采用 jOOQ 替代 MyBatis-Plus、JobRunr 替代 XXL-JOB、Flowable 7.1.0，并以 `java-admin-framework` 为工程名；二者在调度引擎与持久层选型上存在**有意偏差**，以各手册正文为准。
- **单一事实源**：表结构、接口契约、权限码、流程语义以 `db/migration/V32__consolidated_schema.sql` 与四本手册正文为权威；归档文档仅作历史追溯。
