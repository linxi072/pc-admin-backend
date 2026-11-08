# 使用方式

## 1. 环境
- JDK **21**（编译用 Java 17，`java.version=17`；运行用 JDK 21）
- 离线 Maven：`/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -o`
- 依赖：MySQL、Redis（均原生进程，无 Docker）

## 2. 构建 / 运行 / 测试
```bash
# 设 JDK21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 编译（离线）
mvn -o clean package -DskipTests

# 运行（唯一可部署模块）
java -jar scaffold-admin/target/scaffold-admin-1.0.0.jar

# 全量测试（双门禁之一）
mvn -o -f pom.xml -pl scaffold-security,scaffold-system,scaffold-monitor,scaffold-admin,scaffold-workflow,scaffold-job,scaffold-workbench -am clean test

# 约定校验（双门禁之二，必须 dangerouslyDisableSandbox + timeout>=600000）
python3 scripts/check-conventions.py

# 统一门禁入口（一次跑完 约定 / 目录 / 契约 / 校验器单测）
python3 scripts/check-all.py                    # 硬性项全绿返回 EXIT=0
python3 scripts/check-all.py --only conventions,directory   # 只跑指定步骤
python3 scripts/check-all.py --skip-tests       # 跳过脚本单测
python3 scripts/check-all.py --test-tmpdir /tmp # 受限环境钉定临时目录
```

## 3. 门禁（提交前必过）
| 门禁 | 命令 | 通过标准 |
|---|---|---|
| 编译 | `mvn -o -f pom.xml compile` | BUILD SUCCESS（2026-11-08 起测试文件已移除，以编译 + 静态门禁为准） |
| 约定 | `python3 scripts/check-conventions.py` | 退出码 0（H1–H5 规则：分层、依赖无环、命名等） |
| 目录约定 | `python3 scripts/check-directory.py` | 退出码 0（D1–D11：包根白名单 / 启动类位置 / 配置归属 / 全局样式 / 常量与文档齐全 / 版本管理 / main 与 test 源码一级目录 / 文档索引与死链 / 测试期配置归属） |
| 前后端契约（信息性） | `python3 scripts/check-api-contract.py` | 默认不阻断；`--strict` 且 `frontend_only` 非空时返回 1。扫描 14 个 `scaffold-*/src/main/java` 源码根 |
| 校验器行为回归 | `python3 -m unittest discover -s scripts -p "test_*.py" -v` | 用例已按精简要求移除；保留编排步骤以便日后补回 |
| **统一入口** | `python3 scripts/check-all.py` | 一次编排约定 / 目录 / 契约 / 行为回归四步，硬性项全部 `EXIT=0`；契约步仅信息性、不阻断 |

> 注意：从 root 聚合 pom 用 `-pl <module> -am`，**禁止**直接 `-f scaffold-admin/pom.xml` 单独构建；`check-conventions.py` 默认 120s 会被 SIGTERM，须 `dangerouslyDisableSandbox` 且 `timeout>=600000`。
> 受限环境若报 `No usable temporary directory`（`tempfile` 探测文件删除被策略拦截），用 `check-all.py --test-tmpdir <dir>` 钉定已存在且可写的临时目录；该参数只影响脚本单测子进程。

## 4. 开发约定
- 分层：`controller → service → domain`（`scripts/check-conventions.py` H3 守护）。
- 跨模块协作经 **Port（防腐层）**，特性模块不互相直连 Service。
- 数据写入统一走 `SqlWriters`，自动填充/逻辑删除/租户隔离不变；DDL 标识符须显式加引号保留 snake_case 小写。
- i18n：消息目录 `src/i18n/messages.js` 嵌套点分键，新子组须塞进父命名空间内部。
- 权限码：新 `@PreAuthorize` 端点由 `ApiResourceAutoSyncRunner` 自动登记，无需手工维护。

## 5. 新增模块
见 [模块职责](MODULES.md#新增模块步骤)。核心四步：注册 root `<module>` → 建 `pom.xml`（正确 `parent` 与依赖）→ 包根 `com.acme.scaffold.xxx` 遵循分层 → 跑双门禁确认无环。

## 6. 前端对应
前端工程 `pc-admin-vue3-vite`（同仓库 `outputs/`）使用 Vue3 + Vite + Element Plus + Pinia：
- 视图 `src/views/{system,workflow,monitor,oauth2,profile,report}/`
- API 客户端 `src/api/*.js`（经 `src/api/http.js` 统一拦截，自动携带租户/JWT、统一报错）
- 路由 `src/router/index.js`（菜单驱动侧边栏，未授权路由重定向 `/dashboard`）
- 前端门禁：`node scripts/check-sfc.mjs <files>` + `node scripts/i18n-leak-scan.mjs src` + `vite build`
- 新增后端端点时，前端须同步补：路由 + i18n 键 + 菜单种子（版本化结构链 `db/migration/` 新增版本文件后重生成 `db/init/01_full_schema.sql`）+ API 客户端函数。

## 7. 数据库初始化与参考件
- **唯一可执行初始化入口**：`scaffold-admin/src/main/resources/db/init/`（`00_create_database.sql` → `01_full_schema.sql`，可选 `02_backfill_tenant_id.sql`）。运行时已移除 Flyway，**应用启动不执行任何 DDL**，全新环境须由 DBA 手工执行；`db/migration/` 降级为版本化结构源（当前 `V32~V81`，49 个 SQL 文件），`db/reference/` 为分类阅读参考件。
- **严禁修改已发布迁移**：连注释与空白都不能改，否则启动报 `Validate failed: ... checksum mismatch`；修复只能以更高版本迁移前向追加。
- **参考件（非可执行）**：`db/reference/schema_ddl.sql`（DDL）与 `data_dml.sql`（DML）由 `python3 scripts/build_reference_modular.py` 从迁移链整链派生，仅供阅读与追溯，**不可当迁移执行**。header 的版本范围由脚本运行时探测，不写死。
- **新增迁移后的三件套**：① 追加 `V<N>__*.sql`；② 重跑 `python3 scripts/build_reference_modular.py` 重生成两份参考件；③ 同步更新 `db/migration/README.md` 的「版本 → 模块映射」与「V60–V81 新增表结构与字段变更」章节。缺任一步都会让文档/参考件落后于实现（2026-11-08 曾发现 V60~V81 共 22 个迁移漏派生）。
- 完整迁移索引、10 张新增表的列清单与 5 个新增列见 [`db/migration/README.md`](../scaffold-admin/src/main/resources/db/migration/README.md)。
- 代码侧表/列登记集中在 `scaffold-common` 的 `SqlTables.java`，与迁移链 DDL 一一对应（2026-11-08 字段级扫描：58 个 `TableRef` 常量与 DDL 零差异）。新增表/列须同步登记，否则手写动态 DSL 取不到列。
