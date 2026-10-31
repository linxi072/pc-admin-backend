# 文档总目录（java-admin-framework）

> 项目文档已统一梳理为**四本独立手册**，覆盖架构、开发、运维、部署四类读者视角；分散的历史工作记录已归档至 [`docs/archive/`](archive/)。
> 本目录是所有文档的统一入口，手册之间按主题分工、互不重复，交叉内容以「参见对应手册」指引。

## 一、四本手册

| 手册 | 路径 | 面向读者 | 核心内容 |
|---|---|---|---|
| 架构手册 | [`docs/manual/architecture.md`](manual/architecture.md) | 架构师 / 技术负责人 | 总体架构与依赖规则、分层与命名、RBAC 权限模型、核心表结构（DDL）、工作流状态机、统一响应/异常/分页契约、关键配置示例、可观测性指标、操作审计设计、API 最小集合、关键架构决策（ADR）、微服务拆分触发条件、风险与验收清单、实现状态速览 |
| 开发手册 | [`docs/manual/development.md`](manual/development.md) | 后端 / 前端开发者 | 本地开发环境、持久层 jOOQ 用法、系统管理模块开发、工作流审批与自定义设计器、调度任务、接口资源扫描登记、角色数据权限、服务端排序、菜单模块与拖拽排序、代码生成器与验证码、前端接口规范与工程说明、缓存管理（M5）、Bean Validation 校验消息键化与通知渠道（M6 邮件 + 短信）及短信发送记录中心使用指南、集成测试与缺陷修复记录 |
| 运维手册 | [`docs/manual/operations.md`](manual/operations.md) | 运维 / 一线支撑 | 可观测性（Actuator/Prometheus 与指标解读）、监控/日志/在线用户/定时任务等运营菜单、报表与工作台、审计日志查询与脱敏、健康探针、Token 缓存失效、缓存管理增强（M5）与通知渠道（M6 邮件 + 短信，含短信发送记录中心）运维要点、常见故障排查（含工作流设计「不存在」案例） |
| 部署手册 | [`docs/manual/deployment.md`](manual/deployment.md) | 部署 / 上线负责人 | 部署架构与依赖（MySQL/Redis/Flyway/JobRunr/Flowable）、本地原生部署（无 Docker）、数据库迁移与 V32 既有环境升级、profile 配置、可观测性运营侧部署、生产加固清单、CI/CD、冒烟验收与回滚 |

> **新手第一次接触本仓库？先读** [《新手开发部署教程》](manual/getting-started.md)：从零环境到本地跑通后端 + 前端并按序执行的完整路径（环境准备 → 依赖安装 → 项目配置 → 部署步骤 → 常见问题排查），含实战踩坑排查表；配合 [初始化脚本套件](../scripts/init/README.md) 可一键完成环境就绪校验。

## 五、专项指南（专题文档）

针对跨手册的进阶主题，单独成文以避免在四本手册中重复展开：

| 指南 | 路径 | 面向读者 | 核心内容 |
| --- | --- | --- | --- |
| 多数据源使用指南 | [`docs/manual/datasource-guide.md`](manual/datasource-guide.md) | 后端开发者 / 部署负责人 | 多数据源配置组织结构、环境变量外部化、加载方式（fail-fast）、路由切换逻辑（@WithDataSource / DataSourceRouter）、新增数据源五步、故障排查与 prod 变量更名注意事项 |
| 编码规范与目录结构约定 | [`docs/manual/coding-standard.md`](manual/coding-standard.md) | 全体开发者 /  reviewer | 按特性分包 + 层子包的目录约定、各层职责与单一职责、命名规范（类/方法/包/常量）、代码格式基线（.editorconfig）、依赖方向与无环规则；配套零依赖校验脚本 `scripts/check-conventions.py` 的 CI 集成方式 |
| 模块拆分指南（V1.0.3） | [`docs/manual/module-split.md`](manual/module-split.md) | 架构师 / 技术负责人 / 后端开发者 | 多 Maven 模块（scaffold-*）的职责边界、依赖 DAG 与命名规范、目录结构与资源配置、父/子 POM 核心配置、迁移与解环重构步骤、独立开发/测试/部署指南 |
| 功能差距分析与优化建议 | [`docs/manual/feature-gap-analysis.md`](manual/feature-gap-analysis.md) | 技术负责人 / 架构师 | 对照设计方案 §12/§20、gap-analysis、模块计划逐项核对（直接核查真实代码）；严格需求已闭环的结论、真实未实现项（形态偏差）、已具备但未明示的能力（OpenAPI/慢 SQL 监听）、增强型基础功能（导出/导入中心、缓存管理、i18n、脱敏、Bean Validation 校验消息键化、通知渠道邮件+短信、限流注解化）与待优化项（N+1/深翻页/覆盖率门禁）的优先级与建议实现方式 |
| 重构计划与进度 | [`docs/manual/refactoring-plan.md`](manual/refactoring-plan.md) | 技术负责人 / 架构师 | 代码结构重构的分批计划与落地状态（R/B/F/C 各批次条目、门禁依据、已实施步骤与剩余项） |
| Kafka 顺序消费指南 | [`docs/manual/kafka-ordered-consumer.md`](manual/kafka-ordered-consumer.md) | 后端开发者 | 分区内顺序消费的启用方式、`kafka.ordered-consumer.*` 配置项、顺序性保证边界与并发度取舍、分区键设计建议与故障排查 |
| 可观测中心使用指南 | [`docs/manual/observability-center.md`](manual/observability-center.md) | 运维人员 / 前端开发者 | 链路追踪 / Kafka 监控 / N+1 疑似 / 幂等记录 / 告警收件箱五合一运维入口的页面结构、接口与权限模型（一页多权的 403 降级设计）、V39 菜单播种与 V35/V36 菜单 ID 碰撞修复说明、前端契约测试（node:test 25 例）运行方式 |
| 全模块测试与修复报告（2026-10-19） | [`docs/manual/test-report-2026-10-19.md`](manual/test-report-2026-10-19.md) | 测试 / 技术负责人 | 全模块功能测试（正常/异常/边界三视角）与可量化性能测试的范围与方法、三门禁结果（630 单测/约定校验/前端构建）、31 项缺陷分级清单（P1×5 + P2×26，含复现步骤）、修复与回归验证、微基准吞吐指标、真实环境性能测试方案与沙箱边界说明 |
| 系统功能使用说明 | [`docs/manual/system-usage.md`](manual/system-usage.md) | 运维 / 实施 / 开发者 | 按「定位与层级 → 业务价值 → 配置样例 → 操作步骤」四段式逐模块说明（认证授权、系统管理、文件、通知、表单、租户/SaaS、工作流、实时推送、调度、可观测等），面向实施与一线支撑的实操手册 |

> 四本手册中仅保留要点与交叉索引：架构手册 §9.6 / 开发手册 §34 / 运维手册 §31 / 部署手册 §15 / **编码规范见《编码规范与目录结构约定》**。

## 二、部署与运维配套产物（非文档，随代码交付）

- `monitoring/`：Prometheus / Alertmanager / Grafana 配置与看板（运营侧部署用，详见《部署手册》）。
- `scaffold-admin/src/main/resources/db/migration/V32__consolidated_schema.sql`：合并迁移（V1~V31 顺序拼接，可执行为单一来源；V1.0.3 模块拆分后随 scaffold-admin 交付）。
- `scaffold-admin/src/main/resources/db/reference/schema_ddl.sql`、`data_dml.sql`：DDL / DML 分类参考件（**不可直接当迁移执行**，详见《部署手册》§SQL 资产整理）。
- `scripts/init/`：系统初始化安装脚本套件（`.env` 生成 / 建库授权 / 基础数据核对补种 / 安装校验，幂等可重复，另见该目录 README 与《新手开发部署教程》§4.6）。
- `scripts/`：`build_v32.py`、`build_reference.py` 为 V32 合并迁移的历史归档工具（输入原件已删除，不可重跑，仅存追溯口径）；`check-conventions.py` 为编码风格与目录结构约定校验器（零依赖，详见《编码规范与目录结构约定》）；`connectivity-probe.py` / `realtime-ws-probe.py` / `fe-connectivity.mjs` 为前后端联通探针（详见《开发手册》「真实运行环境联调」章节）。

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

- **实际技术栈**：Spring Boot 3.3.4 / Java 17（编译基线，`pom.xml` 声明；运行时可用 21）/ jOOQ 3.19.11（**纯手写动态 DSL**：`JooqTables` / `JooqWriters` / `JooqSorts` / `TableRef` / `JooqKeysetPager` 为数据访问单一事实来源，无代码生成层）/ JobRunr 6.3.4 / Flowable 7.1.0 / MySQL 8.x / Redis / Flyway；**不使用 Docker**，全部以原生进程方式构建与部署。
- **设计文档 vs 实现**：设计方案描述目标架构（包名 `com.acme.scaffold`、XXL-JOB、Spring Boot 4 基线），而实际实现采用 jOOQ 替代 MyBatis-Plus、JobRunr 替代 XXL-JOB、Flowable 7.1.0，并以 `java-admin-framework` 为工程名；二者在调度引擎与持久层选型上存在**有意偏差**，以各手册正文为准。
- **单一事实源**：表结构、接口契约、权限码、流程语义以 `scaffold-admin/src/main/resources/db/migration/` 迁移链（当前 `V32`~`V59`，其中 `V57` 为 Java 类迁移 `V57__backfill_tenant_id.java`，schema 版本 59）与四本手册正文为权威；归档文档仅作历史追溯。
- **端口约定**：应用对外服务端口与 Actuator 管理端口分离，详见《部署手册》§端口与冒烟；JobRunr Dashboard 独立端口（local profile 默认 8000），不与应用端口复用。

---

> 更新日期：2026-10-21（本轮新增《新手开发部署教程》（docs/manual/getting-started.md，五段式 + 实战踩坑排查表）与系统初始化安装脚本套件 scripts/init/（env/db/seed/verify 四步，幂等可重复，20 例离线自测）；登记新手入口；修正 V1.0.3 模块拆分后的陈旧路径口径：迁移链与 DDL/DML 分类参考件位于 scaffold-admin/src/main/resources/db/，build_v32/build_reference 脚本转为历史归档）
>
> 更新日期：2026-10-28（文档与 SQL 一致性整理）：技术栈补 jOOQ codegen 并行层说明；迁移链口径更新至 `V32`~`V59`（其中 `V57` 为 Java 类迁移 `V57__backfill_tenant_id.java`，schema 版本 59）；文档统一命名为英文 kebab-case（`系统功能使用说明.md` → `system-usage.md` 并登记于本目录）；新增 `docs/archive/README.md` 归档索引、`db/migration/README.md` 迁移→模块索引、`db/reference/by-module/` 按功能模块分类参考 SQL（由 `scripts/build_reference_modular.py` 从 `db/migration/` 派生，与迁移链保持单一事实源一致）。
>
> 更新日期：2026-10-31（**方案 C 后文档同步**）：技术栈移除 jOOQ codegen 并行层描述，统一为**纯手写动态 DSL 单一事实来源**；删除 `db/codegen/` 与 `jooq-codegen.xml` 相关表述（该目录与文件已于 2026-10-31 随 #857/#858 物理移除）；《开发手册》§8.1、《部署手册》§34、《模块拆分指南》、《重构计划》同步更正 codegen 残留与批次状态（B1/B2/B5/C1/C2 经磁盘复核均已完成；详见《重构计划》与 `docs/manual/health-report-2026-10-31.md`）。
