# 文档索引（java-admin-framework）

本目录是重构后（**14 个 Maven 模块**）的统一文档入口。

## 核心文档（推荐先读）
- [架构说明](ARCHITECTURE.md) — 模块化单体总览、分层、关键技术决策、依赖 DAG、本次重构说明
- [模块职责](MODULES.md) — 14 个模块的单一职责、依赖方向与关键包
- [目录结构规范](DIRECTORY.md) — 后端 14 模块 / 前端 src 目录树、common 子包职责、system 子域分层骨架、落位决策树、禁止事项与 D1–D11 守护清单
- [命名规范](NAMING.md) — 标识符大小写约定（PascalCase 类型 / UPPER_SNAKE 常量 / camelCase 方法·字段），由 `scripts/check-naming.py` 静态守护，并接入 `check-all.py` 的 `naming` 步骤
- [若依约定对齐说明](RUOYI-ALIGNMENT.md) — 五维差异、已对齐项、保留差异、回归要点与兼容性风险
- [关键流程](KEY-FLOWS.md) — 认证授权 / OAuth2 / 数据权限 / 工作流 / 多租户 / 幂等 / 可观测性 / 代码生成
- [使用方式](USAGE.md) — 构建 / 运行 / 测试 / 门禁清单与 `check-all.py` 统一入口 / 开发约定 / 新增模块 / 前端对应 / 数据库初始化与参考件
- [演进路线图](ROADMAP.md) — V1.0.7 之后的新需求与扩展方向（四大方向 / Phase 0–4 / 候选模块 / 铁律衔接）

## 核对资料（历史口径）
- [上线就绪评估整改核对说明（V1.0.7）](LAUNCH-READINESS-REMEDIATION-V1.0.7.md) — V1.0.7 上线就绪评估的整改核对台账

> 当前权威事实以本目录**七份核心文档**（`ARCHITECTURE` / `MODULES` / `KEY-FLOWS` / `USAGE` /
> `DIRECTORY` / `RUOYI-ALIGNMENT` / `ROADMAP`）与 `scaffold-admin/src/main/resources/db/migration/`
> 迁移链为准；上方核对资料属历史口径，不作为架构与接口契约的权威来源。
> 2026-11-08 起测试文件与测试资料已按「精简工程结构」要求移除，`test/` 子目录不再存在；
> 门禁清单相应调整为「约定 / 目录 / 契约静态核对」，详见 [`USAGE.md`](USAGE.md)。
> **演进规划**以 [`ROADMAP.md`](ROADMAP.md) 为总纲，其中部分候选模块
> （plugin / ai / cms / im / approval）已落地，模块真实状态对照以 [`MODULES.md`](MODULES.md) 为准。
>
> 索引完整性（每份文档都已挂接、链接目标存在）由 `scripts/check-directory.py` 的 **D10** 守护；
> 新增文档后须同步补一行链接，否则目录门禁阻断。
