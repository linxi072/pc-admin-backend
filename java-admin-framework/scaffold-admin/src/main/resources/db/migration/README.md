# Flyway 迁移索引（db/migration/）

本目录是**数据库结构唯一可执行事实源**，由 Flyway 在应用启动时按版本顺序执行（`spring.flyway.locations=classpath:db/migration`，`validate-on-migrate=true`）。

> ⚠️ **严禁修改已发布迁移**：任何对已应用迁移文件（含注释、空白）的改动都会改变文件字节，导致启动校验 `Validate failed: ... checksum mismatch` 而失败。修复只能以**新的更高版本迁移前向追加**（参见《部署手册》附录 O.1）。本目录只新增、不修改、不重排、不重命名。

## 版本 → 模块映射

| 版本 | 文件 | 功能模块 | 说明 |
| --- | --- | --- | --- |
| V32 | `V32__consolidated_schema.sql` | 基础框架（V1~V31 合并） | 用户/角色/菜单/部门/岗位/字典/参数/审计/登录日志/权限/接口资源/工作流基线等核心表与种子；全新环境一次性建库的单一来源 |
| V33 | `V33__file_management.sql` | 文件中心 | 文件管理表 |
| V34 | `V34__csv_export_center.sql` | CSV 导出中心 | 导出任务与模板 |
| V35 | `V35__post_management.sql` | 岗位管理 | 岗位表（并修复菜单 id 碰撞） |
| V36 | `V36__import_center.sql` | 导入中心 | 导入任务与错误明细 |
| V37 | `V37__sms_record.sql` | 短信记录 | 短信发送记录 |
| V38 | `V38__system_parameters.sql` | 系统参数 | 参数配置表 |
| V39 | `V39__observability_center.sql` | 可观测中心 | 链路/N+1/幂等/告警收件箱；修复 V35/V36 菜单 id=1091 碰撞 |
| V40 | `V40__announcement_read_receipt.sql` | 通知中心 | 公告已读回执 |
| V41 | `V41__notification_template.sql` | 通知中心 | 通知模板 |
| V42 | `V42__notification_record.sql` | 通知中心 | 通知发送记录 |
| V43 | `V43__notification_preference.sql` | 通知中心 | 通知偏好 |
| V44 | `V44__notification_quiet_hours.sql` | 通知中心 | 通知免打扰时段 |
| V45 | `V45__notification_digest.sql` | 通知中心 | 通知摘要 |
| V46 | `V46__mask_strategy_center.sql` | 脱敏策略中心 | 脱敏策略与规则 |
| V47 | `V47__form_pilot.sql` | 表单中心 | 表单定义试点 |
| V48 | `V48__openapi_client.sql` | 开放平台 | OpenAPI 客户端 |
| V49 | `V49__workflow_monitor.sql` | 工作流监控 | 工作流监控视图/表 |
| V50 | `V50__file_dedup.sql` | 文件中心 | 文件去重 |
| V51 | `V51__tenant_management.sql` | 租户管理（SaaS） | 租户与隔离基础 |
| V52 | `V52__dashboard_menu.sql` | 报表大屏 | 大屏菜单播种 |
| V53 | `V53__form_submission.sql` | 表单中心 | 表单提交存储 |
| V54 | `V54__tenant_isolation_menu.sql` | 租户管理（SaaS） | 租户隔离态势菜单 |
| V55 | `V55__realtime_menu.sql` | 实时推送 | 实时推送菜单 |
| V56 | `V56__data_scope_menu.sql` | 数据权限 | 数据权限配置菜单 |
| V57 | `V57__backfill_tenant_id.java` | 租户管理（SaaS） | **Java 类迁移**：回填存量 `tenant_id`（非 SQL 文件） |
| V58 | `V58__form_definition_menu.sql` | 表单中心 | 表单定义菜单 |
| V59 | `V59__job_dependency.sql` | 任务依赖编排 | DAG 任务依赖 |

schema 版本当前为 **59**（V57 为 Java 类迁移，无对应 `.sql`）。

## 配套参考（非迁移，不被 Flyway 扫描）

- `../reference/schema_ddl.sql` / `data_dml.sql`：V32~V59 的 DDL / DML 分类参考件（由 `scripts/build_reference_modular.py` 从本目录派生，仅供阅读与追溯，**不可当迁移执行**）。
- `../reference/by-module/*.sql`：按功能模块归类的参考 SQL（同上来源，按模块分组）。
- （原 `../../common/src/main/resources/db/codegen/`：jOOQ codegen 并行层 —— **已于方案C（2026-10-31）移除**：该目录与 `jooq-codegen.xml` 已删除，全仓数据访问统一为纯手写动态 DSL，本迁移链不受影响且始终为唯一事实来源。）
