# 文档索引（java-admin-framework）

本目录是重构后（**12 个 Maven 模块**）的统一文档入口。

## 新生成的核心文档（推荐先读）
- [架构说明](ARCHITECTURE.md) — 模块化单体总览、分层、关键技术决策、依赖 DAG、本次重构说明
- [模块职责](MODULES.md) — 12 个模块的单一职责、依赖方向与关键包
- [关键流程](KEY-FLOWS.md) — 认证授权 / OAuth2 / 数据权限 / 工作流 / 多租户 / 幂等 / 可观测性 / 代码生成
- [使用方式](USAGE.md) — 构建 / 运行 / 测试 / 双门禁 / 开发约定 / 新增模块 / 前端对应

## 权威手册（深度参考，保留于 `manual/`）
- 架构手册 `manual/architecture.md`
- 开发手册 `manual/development.md`
- 运维手册 `manual/operations.md`
- 部署手册 `manual/deployment.md`

## 专题技术参考（保留于 `manual/`，按需查阅）
`coding-standard` · `getting-started` · `oauth2-server` · `nplus1-*` · `observability-*` ·
`system-usage` · `system-parameters` · `idempotency-records` · `kafka-ordered-consumer` ·
`alert-inbox` · `datasource-guide`

> 历史归档、重构计划、需求差距与里程碑笔记等属于历史口径，已从 `docs/archive` 清理，不再作为权威来源；当前事实以四本手册与 `scaffold-admin/src/main/resources/db/migration/` 迁移链为准。
