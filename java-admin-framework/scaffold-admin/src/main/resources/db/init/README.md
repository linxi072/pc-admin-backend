# 数据库手工初始化（db/init/）

> 运行时已移除 Flyway：应用启动不连接数据库执行任何 DDL / 版本迁移，也不维护
> `flyway_schema_history`。全新环境由 DBA / 部署脚本按顺序显式执行本目录的两个脚本。

## 1. 文件与职责

| 文件 | 性质 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| `ddl.sql` | 手工执行 | 是 | 建库 `scaffold`（utf8mb4 / utf8mb4_0900_ai_ci）+ 全部表、索引、结构变更与表重命名，末尾建 `schema_baseline` 基线表 |
| `dml.sql` | 手工执行 | 是 | 菜单 / 权限码 / 角色 / 用户 / 字典 / 参数 / 用户-角色-组织绑定等种子数据，末尾写 `schema_baseline` 基线标记；文件末附可选的存量库 `tenant_id` 回填例程 |
| `README.md` | 说明 | — | 本文件 |

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
幂等守卫）全部保留。`dml.sql` 末尾的 `tenant_id` 回填例程为原 `02` 脚本原文，
仅在「旧库升级 / 导入历史数据」时需要，全新空库为 no-op。

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
- **客户端**：`dml.sql` 末尾的回填例程含 `DELIMITER $$`，请使用支持该指令的
  MySQL 客户端（如 `mysql` CLI）；纯初始化可跳过该段。

## 5. 校验

`schema_baseline.baseline_version = 'V85'`、`source_count = 53` 为归并时的版本快照标记，
供部署校验读取；该值不随文件整理而改变。

> 注意：仓库中 `docs/`、`db/migration/README.md` 及部分 Java 注释仍引用旧的
> `01_full_schema.sql` / `db/reference/*` 路径，属历史文档，不影响应用编译与运行；
> 如需按新方案统一口径，应单独更新这些文档。
