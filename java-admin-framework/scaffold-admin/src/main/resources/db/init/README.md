# 数据库手工初始化（db/init/）

> 2026-11-08 起运行时已**移除 Flyway**：应用启动不再连接数据库执行任何 DDL / 版本迁移，
> 也不再维护 `flyway_schema_history`。全新环境由 DBA / 部署脚本显式执行本目录 SQL。

## 1. 文件与职责

| 文件 | 性质 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| `00_create_database.sql` | 手工执行 | 是（新实例） | 建空库 `scaffold`，字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`；库名与 `DB_PRIMARY_URL` 对齐 |
| `01_full_schema.sql` | **生成产物** | 是 | 唯一可执行的新库初始化脚本：把 `db/migration/` 全部版本 SQL 按版本号顺序逐字拼接，含建表、索引、菜单/字典/管理员种子，末尾写 `schema_baseline` |
| `02_backfill_tenant_id.sql` | 手工执行 | 否 | 等价替代原 V57 Java 迁移，仅在「旧库升级 / 导入历史数据」时执行；全新空库为 no-op |
| `README.md` | 说明 | — | 本文件 |

`db/reference/schema_ddl.sql` 与 `db/reference/data_dml.sql` 是**分类阅读参考件**（DDL / DML 分卷），
**不可单独执行建库**（语句顺序已被重排，ALTER 可能早于 CREATE）。它们与 `01_full_schema.sql`
由同一脚本 `scripts/build_reference_modular.py` 从 `db/migration/` 派生。

## 2. 执行顺序

```bash
export MYSQL_PWD='<password>'
mysql -h127.0.0.1 -P3306 -uroot --default-character-set=utf8mb4 \
  < scaffold-admin/src/main/resources/db/init/00_create_database.sql
mysql -h127.0.0.1 -P3306 -uroot --default-character-set=utf8mb4 scaffold \
  < scaffold-admin/src/main/resources/db/init/01_full_schema.sql
# 仅存量库升级时需要：
mysql -h127.0.0.1 -P3306 -uroot --default-character-set=utf8mb4 scaffold \
  < scaffold-admin/src/main/resources/db/init/02_backfill_tenant_id.sql
```

建议先用 `scripts/init/install.py db` / `verify` 做前置与结果校验（见 `scripts/init/README.md`）。
`verify` 读取 `schema_baseline.baseline_version` 与本地版本链最大值比对，不再查询
`flyway_schema_history`。

## 3. 约束与风险

- **仅空库一次执行**：`01_full_schema.sql` 内含非幂等的 `CREATE TABLE` / `ALTER TABLE` /
  `CREATE INDEX`，重复执行会报「表/列已存在」。存量环境升级须由 DBA 逐版本评估差异。
- **字符集**：`utf8mb4_0900_ai_ci`（MySQL 8）。若服务端不支持 0900 排序规则，
  把 `00_create_database.sql` 改为 `utf8mb4_general_ci`，并同步检查结构链中的显式 COLLATE。
- **多区域部署（R4）**：`app.multiregion.enabled=true` 时每个部署单元是独立物理库，
  需**对每个单元各执行一遍** `00` + `01`。应用不再提供按单元自动迁移的 Runner。
- **调度**：Quartz 使用 RAMJobStore（内存存储），不产生 `QRTZ_*` 表；若切换到 JDBC
  JobStore，需另行导入 Quartz 官方表结构脚本。

## 4. 版本链与再生成

`db/migration/` 保留为**版本化结构源 / 历史记录**（不自动执行、不参与 classpath 扫描），
新增版本文件后重跑：

```bash
python3 scripts/build_reference_modular.py
```

该脚本同时重生成 `db/init/01_full_schema.sql` 与两份分类参考件；版本范围由运行时探测，
不写死。`01_full_schema.sql` 中保留的 Flyway 历史注释仅为追溯用途，不代表运行时依赖。
