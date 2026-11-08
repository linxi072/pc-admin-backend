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
| V57 | `V57__backfill_tenant_id.java` | 租户管理（SaaS） | **Java 类迁移**：回填存量 `tenant_id`；位于 `scaffold-admin/src/main/java/db/migration/`，非 SQL 文件 |
| V58 | `V58__form_definition_menu.sql` | 表单中心 | 表单定义菜单（menu_id=1128） |
| V59 | `V59__job_dependency.sql` | 任务依赖编排 | DAG 任务依赖 |
| V60 | `V60__config_center_wecom_dingtalk_params.sql` | 参数中心 / 通知渠道 | 企业微信、钉钉机器人渠道参数种子（M35 渠道 + M36 热更新） |
| V61 | `V61__role_column_permission.sql` | 字段级数据权限（M41） | 新建 `sys_role_column_permission`；权限策略菜单种子（menu_id=1128，见 V62 修复） |
| V62 | `V62__fix_permission_policy_menu.sql` | 字段级数据权限（M41） | 修复 V61 与 V58 菜单主键 1128 碰撞，以独立主键 1130 补种权限策略菜单并绑定 SUPER_ADMIN |
| V63 | `V63__health_menu.sql` | 健康探针（M42） | 健康探针可视化页菜单，承载 `/livez`、`/readyz` |
| V64 | `V64__oauth2_token_registry.sql` | OAuth2 令牌治理（M43） | 新建 `sys_oauth2_token`：jti + 元数据落库，支持查看与吊销 |
| V65 | `V65__oauth2_token_menu.sql` | OAuth2 令牌治理（M43） | OAuth2 令牌管理菜单（查看 / 吊销） |
| V66 | `V66__form_submission_menu.sql` | 表单中心（M47） | 跨表单统一提交管理菜单 |
| V67 | `V67__menu_reorganize.sql` | 菜单体系 | 40+ 功能菜单按业务域重组为 13 个一级分类 |
| V68 | `V68__oauth2_pkce_consent.sql` | OAuth2 授权码（#900） | 新建 `sys_oauth2_consent`；`sys_openapi_client` 增 `redirect_uris`、`pkce_required`（redirect_uri 白名单 + 强制 PKCE + 用户同意持久化） |
| V69 | `V69__workflow_delegate.sql` | 工作流委派（#901） | `wf_task_ext` 增 `delegator_user_id`、`delegation_state` |
| V70 | `V70__alert_rule.sql` | 告警规则闭环（#922） | 新建 `alert_rule`：可配置阈值规则的持久化与周期评估 |
| V71 | `V71__idempotency_menu.sql` | 幂等运维（M2） | 幂等记录分页 / 统计 / 过期清理菜单 |
| V72 | `V72__api_docs_menu.sql` | API 文档中心（#972） | 复用 springdoc `/v3/api-docs` 的纯前端浏览器菜单 |
| V73 | `V73__workflow_instance_admin_menu.sql` | 流程实例治理 | 挂起 / 恢复 / 改派 / 迁移四端点前端入口 |
| V74 | `V74__cms.sql` | 内容管理 CMS（R5） | 新建 `cms_category`、`cms_article`：栏目树 + 文章 CRUD + 发布态 |
| V75 | `V75__im.sql` | 站内消息 IM（R6） | 新建 `im_conversation`、`im_conversation_member`、`im_message`：会话 + 成员 + 消息 |
| V76 | `V76__approval_portal.sql` | 审批中心门户（R7） | `wf_task_ext` 增 `activity_name` 节点快照列 + 审批中心菜单种子 |
| V77 | `V77__ai_scaffold.sql` | AI 辅助开发（R3） | AI 脚手架只读生成入口菜单种子（权限码 `ai:scaffold:generate`） |
| V78 | `V78__dashboard_template.sql` | 数据看板模板库（R8） | 新建 `dashboard_template`：CRUD + `layout_json` 布局 + 发布态流转 |
| V79 | `V79__micro_frontend.sql` | 微前端宿主（R12） | 子应用挂载菜单种子（离线自研主壳，无外部依赖） |
| V80 | `V80__chaos_config_center_menu.sql` | 混沌工程 / 配置中心（R15/R16） | 两个可视化页菜单种子（控制器受 `@ConditionalOnProperty` 守卫） |
| V81 | `V81__operation_log_query_index.sql` | 审计日志性能（E2E F1/F2） | `sys_operation_log` 补 `idx_operator_occurred`、`idx_occurred_id` 两个查询索引 |

schema 版本当前为 **81**：共 49 个 SQL 迁移（V32~V81）+ 1 个 Java 类迁移（V57）。

## V60–V81 新增表结构与字段变更

> 本节为**索引性说明**；权威定义始终是 `db/migration/` 迁移文件，派生视图见 `../reference/schema_ddl.sql`。
> 基表列以 `V32__consolidated_schema.sql` 为准，后续 `ALTER TABLE` 追加的列**不得回填进基表定义**。

### 新增表（10 张）

| 表 | 迁移 | 列（按 DDL 顺序） | 用途 |
| --- | --- | --- | --- |
| `sys_role_column_permission` | V61 | `id, tenant_id, role_id, resource_code, field, created_at, updated_at` | 角色 × 资源 × 字段的明文可见权（M41） |
| `sys_oauth2_token` | V64 | `id, tenant_id, jti, client_id, user_id, username, scope, token_type, issued_at, expires_at, status, version, deleted, created_at, updated_at` | 签发令牌注册表，吊销后请求期实时拒绝 |
| `sys_oauth2_consent` | V68 | `id, tenant_id, user_id, client_id, scopes, status, expires_at, created_at, updated_at` | 授权码流程用户同意（consent）持久化 |
| `alert_rule` | V70 | `id, tenant_id, rule_name, metric_key, comparator, threshold, severity, for_duration_sec, consecutive_count, channel_type, channel_config, enabled, remark, version, deleted, created_by, created_at, updated_by, updated_at` | 可配置阈值告警规则 |
| `cms_category` | V74 | `id, tenant_id, parent_id, category_code, category_name, description, sort_no, status, version, deleted, created_by, created_at, updated_by, updated_at` | CMS 栏目树 |
| `cms_article` | V74 | `id, tenant_id, category_id, title, summary, content, author, cover_url, status, view_count, published_at, offline_at, version, deleted, created_by, created_at, updated_by, updated_at` | CMS 文章与发布态 |
| `im_conversation` | V75 | `id, tenant_id, conversation_type, title, owner_id, status, version, deleted, created_by, created_at, updated_by, updated_at` | IM 会话 |
| `im_conversation_member` | V75 | `id, tenant_id, conversation_id, user_id, member_role, last_read_message_id, status, version, deleted, created_at, updated_at` | IM 会话成员与已读回执 |
| `im_message` | V75 | `id, tenant_id, conversation_id, sender_id, content, msg_type, status, reply_to_id, version, deleted, created_by, created_at, updated_by, updated_at` | IM 消息 |
| `dashboard_template` | V78 | `id, tenant_id, template_code, template_name, description, layout_json, status, view_count, version, deleted, created_by, created_at, updated_by, updated_at` | 看板模板与布局配置 |

### 既有表新增列（5 列 / 3 张表）

| 表 | 新增列 | 迁移 | 说明 |
| --- | --- | --- | --- |
| `sys_openapi_client` | `redirect_uris VARCHAR(1024)` | V68 | 授权码回调地址白名单（逗号分隔，空白=不约束） |
| `sys_openapi_client` | `pkce_required TINYINT(1)` | V68 | 是否强制 PKCE，默认 0 |
| `wf_task_ext` | `delegator_user_id BIGINT UNSIGNED` | V69 | 委派发起人（原审批人），非委派任务为 NULL |
| `wf_task_ext` | `delegation_state VARCHAR(16)` | V69 | `PENDING`=已委派待办、`RESOLVED`=已办结交还、NULL=非委派 |
| `wf_task_ext` | `activity_name VARCHAR(255)` | V76 | 活动（节点）名称快照，待办/已办列表无需回查引擎 |

### 新增索引（V60+）

- V74：`cms_category` 3 个（租户+父级 / 编码 / 状态）、`cms_article` 3 个（租户+栏目 / 状态 / 标题）。
- V75：`im_conversation` 2 个、`im_conversation_member` 2 个、`im_message` 2 个。
- V78：`dashboard_template` 3 个。
- V81：`sys_operation_log` 2 个（`idx_operator_occurred`、`idx_occurred_id`），修复审计日志与时间翻页的全表扫描。

> DDL 约定：标识符显式加引号保留 snake_case 小写；索引一律用独立 `CREATE INDEX` 语句，禁止内联 `KEY "x"(...)`（H2 会误解析为 domain）。

## 配套参考（非迁移，不被 Flyway 扫描）

- `../reference/schema_ddl.sql` / `data_dml.sql`：由 `scripts/build_reference_modular.py` 从本目录**全部** `V*.sql` 自动派生的 DDL / DML 分类参考件，仅供阅读与追溯，**不可当迁移执行**；header 中的版本范围由脚本运行时探测，当前为 `V32~V81`（49 个 SQL 迁移）。
- **新增迁移后必须重生成**：`python3 scripts/build_reference_modular.py`。2026-11-08 曾发现参考件停在 V59、且被手工注入 V69 委派列与 V70 `alert_rule` 片段却无来源标签的失真；已由整链重生成纠正，V32 基表恢复原始定义，各变更按真实来源标签归位。
- `../reference/by-module/*.sql`：已于 2026-10-28 审计整改删除（数据脚本统一整合为上述两个文件），本目录不再生成；如需逐模块视图，从 git 历史取回即可。
- （原 `../../common/src/main/resources/db/codegen/`：jOOQ codegen 并行层 —— **已于方案C（2026-10-31）移除**：该目录与 `jooq-codegen.xml` 已删除，全仓数据访问统一为纯手写动态 DSL，本迁移链不受影响且始终为唯一事实来源。）

## 历史口径说明

- `V77__ai_scaffold.sql` 头注释仍写「新增 scaffold-ai 模块」；方案 C 后该模块已并入 `scaffold-codegen`。因 Flyway checksum 铁律，历史迁移注释不作修改，以本节说明为准。
- V1~V31 的历史迁移已合并进 V32，不再单独存在；`schema_ddl.sql` 中的 `-- [from V32__...]` 段落即其等价汇总。
