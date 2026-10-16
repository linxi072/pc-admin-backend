# 部署手册

面向部署与上线负责人。覆盖部署架构与依赖（MySQL/Redis/Flyway/JobRunr/Flowable）、本地原生部署（无 Docker）、数据库迁移与 V32 既有环境升级、profile 配置、可观测性运营侧部署、生产加固清单、CI/CD、冒烟验收与回滚。

---

> **阅读指引**：本地启动与可观测性运营侧部署的章节原始出处为框架 README 与 monitoring/README；原生环境的完整链路验证项见《架构手册》验收清单。

---

## 本手册整合来源

- **docs/archive/java-admin-framework-README.md**
  - ## 3. 本地启动（不使用 Docker）
  - ## 34. SQL 资产整理：V32 合并迁移 + DDL / DML 分类参考件
- **docs/archive/design-spec.md**
  - ## 13. 本地启动方案
  - ## 14. 测试与质量门禁
- **docs/archive/monitoring-README.md**
  - ## 端口规划（避免冲突）
  - ## 目录结构
  - ## 快速启动（原生）
  - ## 验收对照（《方案》§15 阶段 4 / 验收清单）
  - ## 启用 JobRunr 指标
  - ## 与 G1 W3C traceparent 的关系

---

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 3. 本地启动（不使用 Docker）

```bash
# 1) 原生安装并启动依赖（以 macOS 为例）
brew install mysql redis
brew services start mysql
brew services start redis

# 创建数据库（Flyway 与 JobRunr 会自动建表，无需手工执行 DDL）
mysql -u root -e "CREATE DATABASE IF NOT EXISTS scaffold
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 其它平台：使用系统包管理器安装 MySQL / Redis 并以服务方式启动即可，
# 均不需要容器。

# 2) 超级管理员账号由 Flyway 的 V32__consolidated_schema.sql 内置（admin / SUPER_ADMIN）

# 3) 编译运行（JDK 21 + 指定本地 Maven，离线仓库）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_PROFILES_ACTIVE=local
export SERVER_PORT=8080                       # 应用对外端口（见下方「端口说明」）
/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -o spring-boot:run

# 4) 访问
#    API:            http://localhost:8080/api/...
#    Swagger:        http://localhost:8080/swagger-ui.html      （仅 local）
#    Actuator:       http://localhost:9090/actuator/health      （独立管理端口，见下方「端口说明」）
#    Prometheus:     http://localhost:9090/actuator/prometheus
#    JobRunr 面板:   http://localhost:8000/dashboard            （仅 local，独立端口，见第 16 节说明）
```

### 端口说明（务必先核对）

| 端口 | 用途 | 来源 |
|---|---|---|
| **8080** | 应用对外服务端口（API / Swagger / WebSocket） | `application.yml` `server.port: 8080` |
| **9090** | Actuator 管理端口（`/actuator/health`、`/actuator/prometheus`） | `application.yml` `management.server.port: 9090`（独立管理端口，与应用端口分离） |
| 8000 | JobRunr Dashboard（仅 local，`JOBRUNR_DASHBOARD_PORT` 可覆盖） | `application-local.yml` |
| 9091 / 3000 / 9093 | Prometheus / Grafana / Alertmanager（运营侧） | 见「端口规划」 |

> 应用对外端口（**8080**）与管理端口（**9090**）为**两个独立端口**，不存在冲突：业务流量走 8080，运维指标与探针走 9090。启动日志会分别打印两行 `Tomcat started on port 8080` 与 `Tomcat started on port 9090`。JobRunr Dashboard 为独立端口 8000，**不在上述两个端口上**。

### 构建与测试命令（本机基线）

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn

$MVN -o clean test          # 全量单元测试（当前 559 例全绿）
$MVN -o test -Dtest="*IT"   # 集成测试（需本地 MySQL）
$MVN -o clean package -DskipTests
```

> 本机 Maven 位于 `/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn`（非 `mvnw`）；离线环境统一带 `-o`。

> **默认账号**：`V32__consolidated_schema.sql` 会内置超级管理员 `admin`（角色 `SUPER_ADMIN`），  
> 其原始 `password_hash` 为占位死值（无对应明文）。已通过 `V32__consolidated_schema.sql`（原 V4 段）  
> 将其重置为可用密码 **`admin123`**（bcrypt，`{bcrypt}` 前缀）。首次登录请使用 `admin / admin123`。

### 建表来源说明

| 表                                                     | 创建方式                                                   |
| ----------------------------------------------------- | ------------------------------------------------------ |
| `sys_*` / `wf_*`（20 张，V1~V4）                          | Flyway：`src/main/resources/db/migration/V32__consolidated_schema.sql`         |
| `sys_dict_type` / `sys_dict_data` / `sys_config`（3 张） | Flyway：`V32__consolidated_schema.sql`（字典分类/字典数据/系统变量，含种子数据；原 V5 段）  |
| `sys_user_role` / `sys_user_org`（2 张，纯 N:N 中间表）     | Flyway：`V32__consolidated_schema.sql`（原 V15 段，回退 V6 单列收敛） |
| `ACT_*`（Flowable）                                     | Flowable 自动建表（`flowable.database-schema-update: true`） |
| `jobrunr_*`（调度）                                       | JobRunr 自动建表（`jobrunr.database.skip-create: false`）    |

### 用户-角色 / 用户-部门：纯多对多模型（V15，回退 V6 收敛）

用户与角色、用户与部门均为**纯 N:N**（中间表 `sys_user_role`、`sys_user_org`），不再保留 `sys_user` 上的 `role_id` / `org_id` / `primary_org_id` 单列。

- `V32__consolidated_schema.sql` 中的 V6 段曾将多对多收敛为单列（理由：避免权限并集越权），其后的 V15 段回退该收敛，全程用 `information_schema` 守卫、可重复执行：
  - 幂等建 `sys_user_role`（`uk_user_role(user_id,role_id)` + `idx`）、`sys_user_org`（`uk_user_org(user_id,org_id)` + `idx`）；
  - 把 `sys_user` 的 `role_id` / `org_id` / `primary_org_id` 数据回填进关联表（`is_primary=1`），再 `DROP` 这三列及 `idx_tenant_role` / `idx_tenant_org` 索引。
- **权限聚合取并集**：`PermissionService.roleIdsOf(userId)` 改为从 `sys_user_role` 读取该用户全部角色 ID 取并集；JWT 层 `JwtAuthConverter` 本就按逗号 `split` 成 `Set`，多角色天然就绪。
- **主维度**：`primaryRoleId` / `primaryDeptId` 标记「主」角色 / 部门（传空取集合首位），用于权限聚合与数据范围（如 `DEPT` 类数据权限依赖主部门）。
- **数据权限联动**：`DefaultDataScopeProvider`、`DataScopeConditions` 的部门过滤与 `orgOfUser` 改走 `sys_user_org`（`is_primary=1`）子查询；`AssigneeResolver`、站内信收件解析（`MessageService.resolveReceivers`）同步基于关联表。
- **改动面**：`JooqTables`（注册 `SYS_USER_ROLE` / `SYS_USER_ORG`、移除 `SYS_USER` 三列）、`SysUserDO`、`PermissionService`、`DefaultDataScopeProvider`、`AssigneeResolver`、`MessageService`、`DataScopeConditions`、`ProfileView` / `ProfileService`、`UserView` / `UserService` 及 `CreateUserRequest` / `UpdateUserRequest` DTO。

> 部门树 + 用户多部门归属 + `SUPER_ADMIN` 角色/菜单关联的种子原见仓库根 `permission-schema.sql`（第 9–10 节）；该脚本已并入分类参考件 `db/reference/schema_ddl.sql`（逐语句带来源注释），其种子内容仍与 V32 中的 V15 段互补。

基于 Spring Boot 的**模块化单体**企业后台基础框架，作为后续业务系统的脚手架。覆盖：RBAC 权限、认证授权与刷新令牌、性能监控、工作流审批、统一响应/异常/分页、操作审计与脱敏追溯、写接口幂等、Token 版本失效、数据权限扩展点。

> 本脚手架已完成**技术栈迁移**：持久层由 **MyBatis-Plus → jOOQ**，任务调度由 **XXL-JOB → JobRunr**。  
> 迁移后功能与原实现保持一致（表结构、接口契约、权限码、流程语义均未变更），且**全流程不使用 Docker**（构建与部署均以原生进程方式运行）。

## 34. SQL 资产整理：V32 合并迁移 + DDL / DML 分类参考件

### 34.1 变更概览

把分散的 SQL 资产按类型重整：可执行的迁移链收敛为单个版本化迁移，结构与数据另出两份分类参考件。

| 类别 | 文件 | 说明 |
| --- | --- | --- |
| 新增（可执行） | `db/migration/V32__consolidated_schema.sql`（1882 行 / 125206 字节） | V1~V31 逐版本**原样拼接**（语句级保持原始交错顺序），供 Flyway 在全新环境一次性建库 |
| 新增（参考件） | `db/reference/schema_ddl.sql`（1311 行）、`db/reference/data_dml.sql`（1205 行） | 全部 DDL / DML 按类型分类汇总，逐语句带 `[源文件 L行号]` 来源注释 |
| 新增（工具） | `scripts/build_v32.py`、`scripts/build_reference.py` | 生成与校验脚本，可复现（原件删除后自动从 V32 还原重跑） |
| 删除 | 34 个：V1~V31 共 31 个迁移 + `db/scaffold_full_init.sql` + `sql/schema.sql` + 仓库根 `permission-schema.sql` | 内容已完整并入 V32 与分类参考件 |
| **保留** | `db/codegen/`（16 个 SQL + README） | **不是重复副本**：是 jOOQ 代码生成并行层的 H2 归一化 DDL 输入，被 `src/main/resources/jooq-codegen.xml` 的 `<scripts>` 显式引用（见 §15），删除会破坏 `mvn -Pjooq-codegen generate-sources` |

### 34.2 关键约束：分类文件不能直接当迁移执行

DDL / DML 分类本身成立，但**这两份文件不可按「先 DDL 后 DML」的顺序直接执行**，原因是跨版本依赖被破坏：

- `V6`：`DROP TABLE sys_user_role / sys_user_org`（删表前，其回填 UPDATE 先读这两张表）；
- `V15`：重建两表 → `INSERT IGNORE ... SELECT ... FROM sys_user WHERE role_id IS NOT NULL` → 随后 `DROP COLUMN role_id / org_id / primary_org_id`。

若拆成两份先后执行，DDL 文件跑完时 `sys_user.role_id` 列已被 V15 删除，DML 文件中的回填语句会直接抛 `ERROR 1054 Unknown column 'role_id'`，**任何新库都建不起来**。因此：

- **可执行迁移链** = `V32__consolidated_schema.sql`（保持原始交错顺序）；
- **分类文件** = 结构 / 数据的分类参考与评审件（文件头已写入同样警示）。

### 34.3 分类规则

| 项 | 规则 |
| --- | --- |
| 类型判定 | 语句首个关键字：`CREATE / ALTER / DROP / TRUNCATE / RENAME` → DDL；`INSERT / UPDATE / DELETE / REPLACE` → DML |
| PREPARE 动态块 | `SET @ddl = (...)` + `PREPARE` + `EXECUTE` + `DEALLOCATE` 视为**一个执行单元**，按内部动态 SQL 的首个关键字整体归类（共 23 组，PREPARE / DEALLOCATE 数量 23:23 配平） |
| 会话语句 | `SET NAMES` 等会话指令归一为各文件头单条（原始共 1 条） |
| 顺序 | 各文件内按「源文件版本序 + 文件内原始顺序」排列 |
| 来源追溯 | 每文件一段来源横幅，每条语句前带 `[源文件 L行号]` 注释，原始注释就地保留 |

分类结果：DDL 62 个单元 / 119 条语句，DML 141 个单元 / 153 条语句（含 `permission-schema.sql` 的 7 DDL + 7 DML）。

### 34.4 既有环境升级步骤（Flyway 历史替换）

删除 V1~V31 后，已建库的 `flyway_schema_history` 仍留有 31 条记录，`validate-on-migrate: true` 会报 “applied migration not resolved locally”，故需先替换历史记录（**业务数据不受影响**）：

```sql
-- 1) 备份历史表（可回滚）
--    mysqldump -h 127.0.0.1 -uroot -proot <db> flyway_schema_history > backup.sql

-- 2) 删除旧的 31 条记录
DELETE FROM flyway_schema_history WHERE CAST(version AS UNSIGNED) BETWEEN 1 AND 31;

-- 3) 插入 V32 记录（checksum 必须取真实值，见下）
INSERT INTO flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (32, '32', 'consolidated schema', 'SQL', 'V32__consolidated_schema.sql', 726365994, 'root', NOW(), 1660, 1);
```

> **checksum 取真实值**：不能手算（Java CRC32 与文件裸字节 CRC32 并不一致）。正确做法是在一个临时库上让 Flyway 真正执行一次 V32，再从该库的 `flyway_schema_history` 读出 checksum：
> ```bash
> mkdir -p /tmp/v32only && cp src/main/resources/db/migration/V32__consolidated_schema.sql /tmp/v32only/
> jshell --class-path "<flyway-core>:<flyway-mysql>:<mysql-connector-j>:<gson>:<jackson-*>" /tmp/v32_flyway.jsh
> mysql -e "SELECT checksum FROM <tmp_db>.flyway_schema_history WHERE version='32';"
> ```
> 全新环境无需此步骤：空库直接执行 V32 即可。

### 34.5 验证记录

| 范围 | 方法 | 结果 |
| --- | --- | --- |
| V32 内容保真 | 剥离 31 个版本横幅后与原件拼接逐字节比对 | **True**（125206 字节，横幅 31 个） |
| 分类完整性 | 源语句 ↔ DDL∪DML 多重集比对 | 缺失 0 / 多余 0 |
| 分类保真 | 字符流守恒（剥离空白后与源文件逐字符比对）+ 终止符检查 | OK / 缺分号 0 |
| 动态块配平 | PREPARE / DEALLOCATE 计数 | 23 : 23 |
| 全新库回放 | Flyway core 在 `java_admin_verify` 独立执行 V32 | 1 migration applied，成功到 v32 |
| 结构 Diff | 与开发库对比表 / 列 / 索引（共同 33 张表） | 列定义差异 0、索引差异 0、scratch 缺失表 0 |
| 数据 Diff | `CHECKSUM TABLE` 逐表比对（33 张共同表） | **全部一致**（0 张不一致） |
| 单元测试 | `mvn -o test` | 317/317 BUILD SUCCESS |
| 集成测试 | `mvn -o test -Dtest="*IT"` | 103/103 BUILD SUCCESS（历史替换后应用正常启动、业务全通） |
| 文档一致性 | 全仓扫描已删文件的引用 | README 7 处 + `docs/menu-api-and-schema.md` 3 处已同步，残留 0 |

> 开发库 85 张表 vs scratch 34 张的差额（51 张）为 `ACT_* / FLW_*`（Flowable 预建，45 张）与 `jobrunr_*`（JobRunr 运行时自建，6 张），均非迁移产物，不影响等价性判定。

### 34.6 影响与注意事项

- **手工重建路径变化**：原 `db/scaffold_full_init.sql`（幂等手工全量初始化）已并入 V32，手工重建改为「全新库 + 执行 V32」；V32 非幂等，仅在空库或已按 §34.4 处理历史的库上执行。
- **代码生成层不受影响**：`db/codegen/` 保留，`jooq-codegen.xml` 引用路径不变。
- **生产环境（prod）** 若为既有库，升级时同样需按 §34.4 替换历史记录；全新库直接执行 V32。
- **后续新增迁移** 从 `V33` 起编号，继续走 `db/migration/`；当前已至 `V36`（`V33` 文件管理 / `V34` 导出中心 / `V35` 岗位管理 / `V36` 导入中心）。
- **已应用脚本不得再改**（包括注释）：checksum 按整个文件字节计算，改动会导致启动校验失败；处理办法见附录 O.1。

### 34.7 复现方式

```bash
python3 scripts/build_v32.py        # 重建 V32 并做字节级等价校验
python3 scripts/build_reference.py  # 重建分类参考件（原件已删时自动从 V32 还原）
```

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 13. 本地启动方案

### 13.1 Docker Compose 最小依赖（设计方案原文，实现已不采用）

> ⚠️ **本节为《设计方案》§13 原文口径，与实现不符，仅供对照，不要照此部署**：实际实现**全流程不使用 Docker**，且调度已由 XXL-JOB 迁移为 **JobRunr**（嵌入式 `BackgroundJobServer`，随应用进程启动，无需独立 Admin 服务与 `xxl_job` 库）。实际依赖只有 MySQL 8.x 与 Redis，启动方式见 §3。

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: scaffold
      MYSQL_USER: scaffold
      MYSQL_PASSWORD: scaffold
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    command:
      - "--character-set-server=utf8mb4"
      - "--collation-server=utf8mb4_0900_ai_ci"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./deploy/mysql/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 5s
      timeout: 3s
      retries: 30

  redis:
    image: redis:8.6
    command: ["redis-server", "--appendonly", "yes"]
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  xxl-job-admin:
    image: xuxueli/xxl-job-admin:3.4.2
    environment:
      PARAMS: >-
        --spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
        --spring.datasource.username=root
        --spring.datasource.password=root
        --xxl.job.accessToken=local-dev-token
    ports:
      - "8088:8080"
    depends_on:
      mysql:
        condition: service_healthy

volumes:
  mysql-data:
  redis-data:
```

注意：`deploy/mysql/init/01-xxl-job.sql` 必须使用 XXL-JOB 3.4.2 官方发行包中的初始化 SQL，不复制旧版本脚本。

### 13.2 启动步骤（按实现修正）

```bash
# 1) 原生启动依赖（无 Docker）
brew services start mysql
brew services start redis

# 2) 构建并运行（JDK 21 + 本地 Maven，离线）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export SPRING_PROFILES_ACTIVE=local
/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -o clean verify
/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -o spring-boot:run
```

> 工程为**单 Maven 模块**（非 `scaffold-bootstrap` 多模块），包名为 `com.acme.scaffold`；仓库内**无 `mvnw`**，请使用上文绝对路径的 Maven。

### 13.3 冒烟检查

```bash
curl -fsS http://localhost:8080/livez
curl -fsS http://localhost:8080/readyz
curl -fsS http://localhost:9090/actuator/health
curl -fsS http://localhost:9090/actuator/prometheus
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

> 应用对外端口为 **8080**（`server.port`），`/actuator/*` 走独立管理端口 **9090**（`management.server.port`）。
>
> **与设计方案的差异（实现为准）**：实际实现为 JWT 无状态认证，**不存在 `/api/auth/csrf` 端点**（设计方案 §12 该条为 N/A，见《功能差距分析与优化建议》§3），登录直接 POST `/api/auth/login` 即可，无需 CSRF token。演示库默认管理员口令见 §3「默认账号」。

首次启动流程：

1. 手工创建 `scaffold` 数据库（`CREATE DATABASE IF NOT EXISTS scaffold CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`）；无 `xxl_job` 库（调度已为 JobRunr，随应用建 `jobrunr_*` 表）。
2. Flyway 依次执行 `db/migration/` 迁移链（当前 `V32`~`V36`，schema 版本 **36**）创建框架表与种子数据。
3. 超级管理员由 `V32__consolidated_schema.sql` 内置：`admin` / `SUPER_ADMIN`，口令见 §3「默认账号」（bcrypt，`{bcrypt}` 前缀）；非环境变量生成的一次性账号。
4. 首次登录建议立即改密（登录验证码默认开启，见 `auth.captcha.enabled`）。
5. Flowable 由应用自动建表：`flowable.database-schema-update: true`（非 Flyway 导入、非 `false`），`async-executor-activate: true`，`history-level: audit`。
6. Spring Boot 启动后 `/livez`、`/readyz` 可访问（免鉴权 `PUBLIC_API`）；local profile 下 JobRunr Dashboard 监听 **8000**。

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 14. 测试与质量门禁

### 14.1 测试层级

| 层级 | 重点 |
|---|---|
| 单元测试 | Token 轮换、账号锁定、数据权限求并集、会签完成条件、错误码 |
| Repository 集成测试 | MySQL 8.4 Testcontainers，索引、唯一约束、乐观锁、分页 |
| Redis 集成测试 | 缓存失效、黑名单 TTL、分布式锁只在必要场景使用 |
| 安全集成测试 | 未认证 401、越权 403、Token 过期、重放检测、接口权限 |
| 工作流集成测试 | 会签、或签、驳回、转办、并发重复审批、历史记录 |
| 架构测试 | ArchUnit 校验层依赖和禁止跨模块 Mapper |
| 冒烟测试 | 本地 Compose 启动、登录、CRUD、审批、指标端点 |

### 14.2 CI 门禁

- `mvn clean verify` 全量通过。
- 单元测试与集成测试分阶段执行。
- Flyway migration checksum 校验。
- Checkstyle/Spotless、SpotBugs、ArchUnit。
- 依赖漏洞扫描与许可证扫描。
- OpenAPI breaking change 检查。
- 容器镜像非 root、SBOM、基础镜像漏洞检查。

本目录提供「可采集 → 可定位 → 可告警」的运营侧产物。**采用原生进程部署（无 Docker）**，与项目整体约定一致。

## 端口规划（避免冲突）

| 组件 | 端口 | 说明 |
|---|---|---|
| 应用（对外服务） | **8080** | `application.yml` `server.port: 8080`（API / Swagger / WebSocket） |
| 应用管理端点 | **9090** | `application.yml` `management.server.port: 9090`，暴露 `/actuator/prometheus`；与应用端口 8080 为独立端口，不冲突 |
| JobRunr Dashboard | **8000** | 仅 local，`JOBRUNR_DASHBOARD_PORT` 可覆盖；独立于应用端口 |
| Prometheus | **9091** | 启动加 `--web.listen-address=:9091`，避免与 9090 冲突 |
| Grafana | **3000** | 默认 |
| Alertmanager | **9093** | 默认 |

## 目录结构

```
monitoring/
├── prometheus/
│   ├── prometheus.yml      # 抓取配置：scrape 应用 9090/actuator/prometheus
│   └── alert-rules.yml     # 5 条告警规则（错误率/P99/老年代/连接池/JobRunr）
├── alertmanager/
│   └── alertmanager.yml    # 路由 + 接收端（webhook/email 占位，需替换）
└── grafana/
    ├── provisioning/
    │   ├── datasources/prometheus.yml   # 数据源指向 Prometheus 9091
    │   └── dashboards/dashboards.yml     # 自动加载下方 5 个看板
    └── dashboards/
        ├── jvm.json         # JVM（堆/老年代/CPU/线程/GC/类）
        ├── http.json         # HTTP（QPS/错误率/P99/状态码）
        ├── hikaricp.json     # HikariCP（使用率/活跃空闲/获取耗时/创建耗时）
        ├── workflow.json     # 工作流/认证（审批耗时/登录成败）
        └── jobrunr.json      # 调度（失败/成功/队列）
```

## 快速启动（原生）

```bash
# 1) 应用：已暴露 /actuator/prometheus（management.server.port=9090）

# 2) Prometheus
prometheus --config.file=prometheus/prometheus.yml --web.listen-address=:9091

# 3) Alertmanager
alertmanager --config.file=alertmanager/alertmanager.yml

# 4) Grafana（指向本目录做 provisioning）
grafana-server \
  --cfg:default.paths.provisioning=/abs/path/monitoring/grafana/provisioning \
  --cfg:default.paths.plugins=/abs/path/monitoring/grafana
# 或把 grafana/dashboards 软链到 /etc/grafana/provisioning/dashboards
```

启动后访问 Grafana `http://localhost:3000`，左侧「Dashboards → Java Admin Framework」即可看到 5 个看板；
Prometheus `http://localhost:9091` 的 Alerts 页可看到告警状态，触发后由 Alertmanager 推送到配置的 webhook/email。

## 验收对照（《方案》§15 阶段 4 / 验收清单）

| 验收项 | 实现 |
|---|---|
| grafana dashboard：JVM/HTTP/HikariCP/工作流/调度 | `grafana/dashboards/*.json` 五块看板 |
| alertmanager rules：错误率>1% / P99>800ms / 老年代>80% / 连接池>85% / JobRunr 失败>0 | `prometheus/alert-rules.yml` 五条 |
| 原生环境触发一条告警可被 Alertmanager 接收 | 调整阈值或人为制造高错误率即可验证路由与接收端 |

## 启用 JobRunr 指标

`jobrunr.json` 依赖 `jobrunr_jobs_*` 指标。需在 `application.yml` 增加：

```yaml
org:
  jobrunr:
    metrics:
      enabled: true
```

未开启时该看板为 No Data（不影响其余面板）。

## 与 G1 W3C traceparent 的关系

`monitor/observability` 已实现 W3C `traceparent` 传播（见 `TraceContext` / `TraceIdFilter`），
MDC `traceId` 与审计日志、Result 共用同一链路 ID。待具备 OTel Collector 时，以 Maven profile
`-Potel` 引入 OTel SDK 即可将 span 导出至 Jaeger/Tempo，无需改造业务代码。

---

## 15. 多数据源部署：环境变量与外部化

> 完整配置项、新增数据源五步、故障排查见《多数据源使用指南》`docs/manual/datasource-guide.md`；配置结构见《架构手册》§9.6。

框架支持连接并切换多个数据库。所有连接串与凭据**统一外部化到环境变量**，yml 中不硬编码任何凭据（`local` 可 `:默认值` 回退，`prod` 无默认值强制注入）。

### 15.1 环境变量清单

| 变量 | 作用 | 必填 | 备注 |
| --- | --- | --- | --- |
| `DB_PRIMARY_URL` | 主库 JDBC 连接串 | 是 | Flyway / JobRunr / Flowable 均在主库 |
| `DB_PRIMARY_USERNAME` | 主库账号 | 是 | |
| `DB_PRIMARY_PASSWORD` | 主库密码 | 是 | 建议走 Vault / 密钥管理，勿落明文 |
| `DB_<NAME>_URL` | 第二个及后续库连接串 | 按 entries 而定 | `<NAME>` 大写、连字符转下划线（如 `report` → `DB_REPORT_URL`） |
| `DB_<NAME>_USERNAME` / `DB_<NAME>_PASSWORD` | 对应库账号 / 密码 | 同上 | |

### 15.2 部署步骤

1. **声明数据源**：在 `application.yml` 设 `app.datasources.primary: primary`；在 `application-prod.yml` 的 `app.datasources.entries` 列出所有库名（仅列名，凭据走变量）。
2. **注入环境变量**：为每个库注入 `DB_<NAME>_URL/USERNAME/PASSWORD`；`prod` 不为凭据提供默认值（缺失即启动 fail-fast，避免误连）。
3. **启动验证**：启动日志应出现各池 `hikari-<name> Started`；未切换时主库业务正常；`/actuator/health` 应 `UP`。

### 15.3 变量更名迁移（重要）

原单库变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` **已更名为** `DB_PRIMARY_URL` / `DB_PRIMARY_USERNAME` / `DB_PRIMARY_PASSWORD`。既有部署脚本 / CI / `.env` 必须同步更名，否则启动即 fail-fast。

### 15.4 连接池参数

Hikari 连接池参数（`minimumIdle` / `maximumPoolSize` / `connectionTimeout` / `validationTimeout` / `idleTimeout` / `maxLifetime` / `connectionTestQuery`）可在 `app.datasources.entries.<name>.pool.*` 覆盖；缺省沿用 `MultiDataSourceProperties.Pool` 默认值（最小空闲 5 / 最大 20 / 连接超时 3000ms 等）。生产高并发场景按需调大 `maximumPoolSize`。

### 15.5 其它凭据类环境变量

| 变量 | 作用 | 必填 | 备注 |
| --- | --- | --- | --- |
| `JWT_SECRET` | JWT 签名密钥（Base64，≥256 位） | 是（prod） | 未配置时生成仅当前进程有效的随机临时密钥（重启失效，仅开发/测试） |
| `ALERT_WEBHOOK_TOKEN` | Alertmanager webhook 接收端校验令牌 | 否 | 非空时要求请求携带匹配的 `X-Alert-Token` 头 |
| `OTLP_ENDPOINT` | OTLP/HTTP span 导出端点 | 否 | `observability.otlp.enabled=true` 时生效 |
| `NOTIFICATION_EMAIL_USERNAME` | 邮件渠道 SMTP 账号 | 否 | `app.notifications.email.enabled=true` 时生效；不落仓库 |
| `NOTIFICATION_EMAIL_PASSWORD` | 邮件渠道 SMTP 密码 | 否 | 同上；建议走 Vault / 密钥管理 |
| `AVATAR_DIR` / `FILE_STORAGE_DIR` | 头像 / 文件存储目录 | 否 | 缺省 `./uploads/avatar` / `./uploads/file`，生产建议指定绝对路径 |

---

## 附录 O. 启动失败排查：两条高频踩坑（2026-10-17）

### O.1 修改了已应用的 Flyway 迁移脚本 → 启动校验失败

**现象**：启动时报 `Migration checksum mismatch for migration version xx`（`validate-on-migrate: true` 时应用直接起不来）。

**根因**：Flyway 的 checksum 是对**迁移脚本整个文件的字节**计算的，不是语句级、也不是去掉注释后的语义级。因此：

- **已应用过的迁移脚本一律不得再修改**——哪怕只加一行注释、改一处缩进或补一个空行，checksum 都会变，校验即失败；
- 常见的「改注释说明」「补一句说明性 SQL 注释」都会踩中。

**处理**：

1. **首选**：把文件改回原样（git 还原），保持与 `flyway_schema_history` 记录一致。
2. **确需保留修改**时：执行等价 `repair`——更新 `flyway_schema_history` 中该版本的 `checksum` 为当前文件的真实值。**真实值不能手算**（Java CRC32 与文件裸字节 CRC32 不一致），取法见 §34.4「checksum 取真实值」：在临时库上让 Flyway 真跑一次该脚本，再从 `flyway_schema_history` 读出。
3. **任何 schema 变更一律新增版本号**（当前最新为 `V36`，下一个用 `V37`），不回头改旧脚本。

### O.2 配置类自引用循环依赖 → 启动失败

**现象**：启动时报 `BeanCurrentlyInCreationException` / `Requested bean is currently in creation: ...`，指向某个 `*Config`。

**根因**：配置类在**构造器**中注入了**它自己声明的** `@Bean`，形成配置类实例 → 依赖该 Bean → 又需要配置类实例的自引用环。Spring Boot 3 默认 `spring.main.allow-circular-references: false`，**默认禁止**，直接启动失败。

**修复**：把该 `@Bean` 方法声明为 **`static`**。静态 `@Bean` 工厂方法不依赖配置类实例，可在配置类实例化之前被调用，环即解开。

```java
@Configuration
public class XxxConfig implements WebMvcConfigurer {
    // ❌ 非 static：本类构造注入该 Bean → 自引用环
    // ✅ static：不依赖配置类实例
    @Bean
    public static SomeBean someBean(XxxProperties properties) { ... }
}
```

> 实例：`common/i18n/I18nConfig#localeChangeInterceptor`（构造注入 `LocaleChangeInterceptor`，故该 `@Bean` 必须为 `static`，见源码 Javadoc）；该约束由 `I18nConfigAssemblerTest` 锁定。新增配置类时若需构造注入自身声明的 Bean，照此处理。

---

## 附录 P. 生产加固与质量门禁落地清单（综述）

以下为《需求差距分析》§7–§9 及《设计方案》§15 阶段 5 的落地结论（完整证据见 `docs/archive/requirement-gap-analysis.md`）。

**登录安全增强（2026-10-10）**：登录验证码接入登录流程；Access Token TTL 15m→**30m**、Refresh Token TTL 7d→**30d**；多端登录限制（`SessionLimitRules` + `max-concurrent-sessions` 默认 3，策略 reject/evict-oldest，越限返回 `AUTH_011` 409）。

**密钥与凭证**：密钥轮换 `JwtKeyRotationService` + `RotatingJwtDecoder`（kid 双密钥，`previousJwtSecret` 宽限期）；外部密钥源 `VaultSecretProvider`（零依赖，JDK HttpClient 调 Vault KV v2）+ `LocalSecretProvider` 回退，`secret.vault.*` 配置。

**限流与防重放**：`RateLimitFilter` 对 `/api/auth/login`、`/api/auth/refresh` 固定窗口限流（默认 60s/10 次，超 429 `AUTH_010`）；`AntiReplayFilter`（请求头 `X-Request-Timestamp` + `X-Request-Nonce`，opt-in 由 `auth.anti-replay.enabled` 控制）。

**密码策略**：`PasswordPolicy`（长度 8–64、大小写/数字/特殊字符可配置、弱口令字典、复用最近 5 次校验）。

**依赖安全（SCA）**：CI `sca` 作业接入 OWASP dependency-check（非阻塞 advisory）；`.github/workflows/security-scan-scheduled.yml` 定期自动扫描（每周一 03:00 + 手动）。

**CI/CD**：`.github/workflows/ci.yml` 于 push/PR 触发——`build-and-unit-test` 跑 `mvn -B test`（surefire）；`integration-test` 用 MySQL 8.4 服务容器 + env 覆盖数据源跑 failsafe `*IT`（Flyway 自动建表）；`maven-failsafe-plugin` + `skipITs`（默认 true）。

**健康探针**：`/livez`（仅 JVM/主循环，恒 200）、`/readyz`（检查必需依赖 DB，不可用时 503），`monitor/health` 包，已加入免鉴权 `PUBLIC_API`。

**范围外 / N/A**：多租户、压测/容量基线/故障注入/回滚演练（需原生环境）、Docker Compose（采用原生进程启动）。

> 说明：**文件上传已实现**（`system/file` + `V33__file_management.sql`，见《开发手册》文件管理章节），原「无上传接口」的表述已失效；上传安全（类型/大小校验、存储目录隔离）随该模块落地，不在「范围外」。

---

> 更新日期：2026-10-17（本轮按代码现状核对并修订：§3 启动命令改为 JDK 21 + 本机绝对路径 Maven（`-o`），修正端口取值——应用对外 **8080**（`server.port`）、Actuator **9090**（`management.server.port`，独立管理端口、无冲突），删除此前「9090/8080 端口冲突待确认」的误记；JobRunr Dashboard 端口修正为 8000；§13 标记 Docker/XXL-JOB 为设计方案原文并给出按实现的启动步骤与冒烟命令（无 `/api/auth/csrf`）；端口规划表补齐应用端口与 JobRunr 面板；新增附录 O「两条高频踩坑」（Flyway checksum 不可改已应用脚本 / 配置类自引用须 `static @Bean`）；迁移链更新至 V32~V36、schema 版本 36；修正「无上传接口」的失效表述）
