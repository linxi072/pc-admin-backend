# 文档索引（java-admin-framework）

本目录是重构后（**12 个 Maven 模块**）的统一文档入口。

## 新生成的核心文档（推荐先读）
- [架构说明](ARCHITECTURE.md) — 模块化单体总览、分层、关键技术决策、依赖 DAG、本次重构说明
- [模块职责](MODULES.md) — 12 个模块的单一职责、依赖方向与关键包
- [关键流程](KEY-FLOWS.md) — 认证授权 / OAuth2 / 数据权限 / 工作流 / 多租户 / 幂等 / 可观测性 / 代码生成
- [使用方式](USAGE.md) — 构建 / 运行 / 测试 / 双门禁 / 开发约定 / 新增模块 / 前端对应
- [演进路线图](ROADMAP.md) — V1.0.7 之后的新需求与扩展方向（四大方向 / Phase 0–4 / 候选模块 / 铁律衔接）

> 历史归档、重构计划、需求差距、里程碑笔记与各专题手册等属于历史口径，已统一清理，不再作为权威来源；当前事实以本目录四份核心文档（`ARCHITECTURE` / `MODULES` / `KEY-FLOWS` / `USAGE`）与 `scaffold-admin/src/main/resources/db/migration/` 迁移链为准；**演进规划**以 [`ROADMAP.md`](ROADMAP.md) 为权威总纲（其描述的候选模块均处于规划态，尚未创建）。
