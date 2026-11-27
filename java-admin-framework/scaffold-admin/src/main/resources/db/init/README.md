# 数据库手工初始化（db/init/）

> 运行时已移除 Flyway：应用启动不连接数据库执行任何 DDL / 版本迁移，也不维护
> `flyway_schema_history`。全新环境由 DBA / 部署脚本按顺序显式执行本目录的两个脚本。

## 1. 文件与职责

| 文件 | 性质 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| `ddl.sql` | 手工执行 | 是 | 建库 `scaffold`（utf8mb4 / utf8mb4_0900_ai_ci）+ 全部表、索引、结构变更与表重命名，末尾建 `schema_baseline` 基线表 |
| `dml.sql` | 手工执行 | 是 | 菜单 / 权限码 / 角色 / 用户 / 字典 / 参数 / 用户-角色-组织绑定等种子数据，末尾写 `schema_baseline` 基线标记；文件末附可选的存量库 `tenant_id` 回填例程 |
| `README.md` | 说明 | — | 本文件 |
| `upgrade_idempotency_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 幂等记录去重后补 `(tenant_id, api_scope, idempotency_key)` 唯一索引；新库无需执行 |
| `upgrade_form_code_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 表单定义补生成列 `form_code_active` 与 `(tenant_id, form_code_active)` 唯一索引；新库无需执行 |
| `upgrade_user_username_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 用户表补生成列 `username_active` 与 `(tenant_id, username_active)` 唯一索引；新库无需执行 |
| `upgrade_user_contact_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 用户表补生成列 `mobile_active` / `email_active` 与手机号、邮箱唯一索引；新库无需执行 |
| `upgrade_config_key_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 系统参数表补生成列 `config_key_active` 与配置键唯一索引；新库无需执行 |
| `upgrade_tenant_code_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 租户表补生成列 `tenant_code_active` 与租户编码唯一索引；新库无需执行 |
| `upgrade_role_code_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 角色表补生成列 `role_code_active` 与角色编码全局唯一索引；新库无需执行 |
| `upgrade_openapi_client_id_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 开放平台客户端表补生成列 `client_id_active` 与客户端 ID 全局唯一索引；新库无需执行 |
| `upgrade_org_code_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 机构表补生成列 `org_code_active` 与 `(tenant_id, org_code_active)` 唯一索引；新库无需执行 |
| `upgrade_dict_code_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 字典类型表补生成列 `dict_code_active` 与字典编码全局唯一索引；新库无需执行 |
| `upgrade_mask_strategy_code_unique_index.sql` | 存量库升级（可选） | 仅升级时 | 脱敏策略表补生成列 `strategy_code_active` 与 `(tenant_id, strategy_code_active)` 唯一索引；新库无需执行 |

原 `00_create_database.sql`、`01_full_schema.sql`、`02_backfill_tenant_id.sql`，以及
`db/reference/{schema_ddl,data_dml}.sql`、`db/migration/V*.sql` 已归并进上述两个文件，
**不再作为初始化入口**；若仍留存于仓库，仅为历史归档，请勿用于建库。历史可从 git 记录取回。

## 2. 执行顺序

```bash
export MYSQL_PWD='<password>'
mysql -h127.0.0.1 -P3306 -uroot --default-character-set=utf8mb4 \
  < scaffold-admin/src/main/resources/db/init/ddl.sql
mysql -h127.0.0.1 -P3306 -uroot --default-character-set=utf8mb4 \
  < scaffold-admin/src/main/resources/db/init/dml.sql
```

两个脚本各自建立独立连接，因此 `dml.sql` 头部也显式 `USE scaffold;`。
`ddl.sql` 已包含建库语句，无需再单独执行任何前置脚本。

## 3. 整理口径（重要）

两个文件由原版本链 `V32~V85`（53 个 SQL 迁移 + Java 迁移 V57 的等价内容）归并而来，
DDL / DML 各自的语句**保持原版本链的相对执行顺序**，绝大多数语句逐字保留。
由于拆分后变为「先执行全部结构、再执行全部数据」，以下历史中间态语句做了等价压缩：

1. 4 个只服务于**存量库数据回填**的动态 SQL 守卫块（`UPDATE`×2 / `INSERT`×2）在空库上恒为
   no-op，已从 `ddl.sql` 剔除；
2. V1 旧版 `sys_user_role` 种子（含已下线的 `scope_org_id` 列）改写为最终表结构下的等价
   `INSERT`：`admin ↔ SUPER_ADMIN, is_primary=1`；
3. V6 的 `UPDATE sys_user SET primary_org_id = org_id ...` 依赖后续已下线的单列，
   对空库无净效果，已移除。

其余结构演进（V6 关联表 DROP、后续重建、列下线、V84/V85 表重命名、information_schema
幂等守卫）全部保留。历史上的 `tenant_id` 回填存储过程（`scaffold_backfill_tenant_id`）
已在初始化 SQL 规范化时移除；当前 `ddl.sql`/`dml.sql` 只含 CREATE TABLE / INDEX / INSERT，
不含任何存储过程、函数、触发器或 `DELIMITER` 段。若旧库存在 tenant_id 为 NULL 的孤儿行，
需在应用侧按租户治理流程逐表修复，不再依赖数据库端例程。

## 4. 约束与风险

- **仅空库一次执行**：内含非幂等的 `CREATE TABLE` / `ALTER TABLE` / `CREATE INDEX`，
  重复执行会报「表/列已存在」。存量库升级须由 DBA 逐项评估差异。
- **字符集**：`utf8mb4_0900_ai_ci`（MySQL 8）。若服务端不支持 0900 排序规则，
  把两个文件中的建库 / 显式 `COLLATE` 改为 `utf8mb4_general_ci`。
- **库名**：`scaffold` 须与 `application-*.yml` 的 `DB_PRIMARY_URL` 一致，
  两个文件的 `USE` / `CREATE DATABASE` 需同步修改。
- **多区域部署**：`app.multiregion.enabled=true` 时每个部署单元是独立物理库，
  需对每个单元各执行一遍 `ddl.sql` + `dml.sql`。
- **调度**：Quartz 使用 RAMJobStore，不产生 `QRTZ_*` 表；若切换到 JDBC JobStore，
  需另行导入 Quartz 官方表结构脚本。
- **客户端**：`ddl.sql`/`dml.sql` 均为标准 SQL（无 `DELIMITER $$`、无存储过程体），
  普通 MySQL 客户端即可执行，无需特殊指令支持。
- **幂等唯一索引**：新库由 `ddl.sql` 内联创建 `uk_idem_tenant_scope_key`；存量库需在停写窗口
  先按 `upgrade_idempotency_unique_index.sql` 去重再补索引，否则并发同键请求仍可能重复执行业务。
  该升级脚本不做索引存在性守卫，重复执行前请先按文件头注释检查。
- **表单编码唯一索引**：新库由 `ddl.sql` 内联生成列 `form_code_active`（逻辑删除行为 NULL）与
  `uk_form_tenant_code_active`；存量库按 `upgrade_form_code_unique_index.sql` 先去重再建索引。
  唯一性只覆盖未删除行，逻辑删除后可用同编码重建。
- **用户名唯一索引**：新库由 `ddl.sql` 内联生成列 `username_active`（逻辑删除行为 NULL）与
  `uk_user_tenant_username_active`；存量库按 `upgrade_user_username_unique_index.sql` 先去重再建索引。
  唯一性只覆盖未删除行，逻辑删除后可用同用户名重建。
- **手机号 / 邮箱唯一索引**：新库由 `ddl.sql` 内联生成列 `mobile_active` / `email_active`
  （逻辑删除行或未绑定（NULL）退化为 NULL）与 `uk_user_tenant_mobile_active` /
  `uk_user_tenant_email_active`；存量库按 `upgrade_user_contact_unique_index.sql` 先去重再建索引。
  未绑定值可多行共存，解绑后原值可复用。
- **配置键唯一索引**：新库由 `ddl.sql` 内联生成列 `config_key_active`（逻辑删除行为 NULL）与
  `uk_config_key_active`（全局唯一，sys_config 为平台维表）；存量库按
  `upgrade_config_key_unique_index.sql` 先去重再建索引。逻辑删除后可用同键重建。
- **租户编码唯一索引**：新库由 `ddl.sql` 内联生成列 `tenant_code_active`（逻辑删除行为 NULL）与
  `uk_tenant_code_active`（全局唯一，sys_tenant 为平台维表）；存量库按
  `upgrade_tenant_code_unique_index.sql` 先去重再建索引。逻辑删除后可用同编码重建。
- **角色编码唯一索引**：新库由 `ddl.sql` 内联生成列 `role_code_active`（逻辑删除行为 NULL）与
  `uk_role_code_active`（全局唯一：租户管理员编码前缀规则依赖该约束）；存量库按
  `upgrade_role_code_unique_index.sql` 先去重再建索引。逻辑删除后可用同编码重建。
  `RoleService.existsByCode` 走 `SqlWriters.countGlobal` 显式全局查重，与索引口径一致。
- **客户端 ID 唯一索引**：新库由 `ddl.sql` 内联生成列 `client_id_active`（逻辑删除行为 NULL）与
  `uk_openapi_client_id_active`（全局唯一：OAuth2 授权服务器按 client_id 全局认证，无租户维度）；
  存量库按 `upgrade_openapi_client_id_unique_index.sql` 先去重再建索引（被去重丢弃的客户端
  须重新注册并换发密钥）。逻辑删除后可用同 ID 重建。
  `OpenApiClientService.existsByClientId` 走 `SqlWriters.countGlobal` 显式全局查重，与索引口径一致。
- **机构编码唯一索引**：新库由 `ddl.sql` 内联生成列 `org_code_active`（逻辑删除行为 NULL）与
  `uk_org_tenant_code_active`（租户内唯一：sys_org 为业务表，各租户独立机构树，唯一键含
  tenant 维度）；存量库按 `upgrade_org_code_unique_index.sql` 先去重再建索引。
  逻辑删除后可用同编码重建。`OrgService.existsByCode` 的租户隔离查重与索引口径一致。
- **字典编码唯一索引**：新库由 `ddl.sql` 内联生成列 `dict_code_active`（逻辑删除行为 NULL）与
  `uk_dict_code_active`（全局唯一，sys_dict_type 为平台维表）；存量库按
  `upgrade_dict_code_unique_index.sql` 先去重再建索引（被去重类型的字典数据须先归并）。
  逻辑删除后可用同编码重建。`DictTypeService.existsByCode` 的全局查重与索引口径一致。
- **策略编码唯一索引**：新库由 `ddl.sql` 内联生成列 `strategy_code_active`（逻辑删除行为 NULL）
  与 `uk_mask_strategy_code_active`（租户内唯一：解析面按 tenant_id 加载启用策略，唯一键含
  tenant 维度）；存量库按 `upgrade_mask_strategy_code_unique_index.sql` 先去重再建索引。
  逻辑删除后可用同编码重建。`MaskStrategyService.existsByCode` 的租户隔离查重与索引口径一致。

## 5. 校验

`schema_baseline.baseline_version = 'V85'`、`source_count = 53` 为归并时的版本快照标记，
供部署校验读取；该值不随文件整理而改变。

> 注意：仓库中 `docs/`、`db/migration/README.md` 及部分 Java 注释仍引用旧的
> `01_full_schema.sql` / `db/reference/*` 路径，属历史文档，不影响应用编译与运行；
> 如需按新方案统一口径，应单独更新这些文档。
