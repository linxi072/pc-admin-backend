# 历史归档索引（docs/archive/）

本目录存放已被四本手册（`docs/manual/architecture.md` / `development.md` / `operations.md` / `deployment.md`）与专项指南整合的**历史工作记录**，仅作追溯用途，**不再是当前实现的权威来源**。当前权威事实请以四本手册正文与 `scaffold-admin/src/main/resources/db/migration/` 迁移链（V32~V59，其中 V57 为 Java 类迁移 `V57__backfill_tenant_id.java`）为准。

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| `design-spec.md` | 《Java 后端基础管理框架设计方案》目标架构（包名 `com.acme.scaffold`、XXL-JOB、Boot 4 基线） | 与实际实现存在**有意偏差**（持久层用 jOOQ 替代 MyBatis-Plus、调度用 JobRunr 替代 XXL-JOB、Flowable 7.1.0，工程名 `java-admin-framework`） |
| `java-admin-framework-README.md` | 框架原 README | 已拆分并入四本手册 |
| `menu-api-and-schema.md` | 菜单接口与表结构说明 | 历史口径，菜单种子以迁移链为准 |
| `requirement-gap-analysis.md` | 需求文档 vs 实现差距分析 & 分阶段方案 | 历史口径，最新差距见《功能差距分析与优化建议》 |
| `frontend-api-standard.md` | 前端接口调用规范 | 历史口径，最新规范见《编码规范与目录结构约定》与前端工程说明 |
| `pc-admin-vue3-vite-README.md` | 前端工程说明 | 历史口径 |
| `monitoring-README.md` | 可观测性运营侧部署说明 | 历史口径，最新见《部署手册》与 `monitoring/` |
| `QA-business-test-report-V28.md` | V28 QA 业务测试报告与修复总结 | 历史报告 |
| `defect-fix-report-java-admin-framework-2026-10-08.md` | 缺陷修复清单（分级报告） | 历史报告 |
| `defect-investigation-workflow-design-not-found-2026-10-12.md` | 工作流设计「不存在」问题排查报告 | 历史排查记录，结论已纳入《运维手册》常见故障排查 |
| `module-plan-java-admin-framework-2026-10-08.md` | 后续模块开发规划 | 历史规划，落地状态见《重构计划与进度》 |
| `review-java-admin-framework-2026-10-08.md` | 代码审查报告 | 历史审查记录 |

> 归档文档**不应被新代码或新文档引用**为权威；若发现正文仍指向本目录作为实现依据，应改为指向四本手册或迁移链。
