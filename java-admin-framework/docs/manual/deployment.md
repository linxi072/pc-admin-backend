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

# 3) 编译运行
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run

# 4) 访问
#    API:            http://localhost:8080/api/...
#    Actuator:       http://localhost:9090/actuator/health
#    Prometheus:     http://localhost:9090/actuator/prometheus
#    Swagger:        http://localhost:8080/swagger-ui.html   （仅 local）
#    JobRunr 面板:   http://localhost:8080/dashboard         （仅 local，见第 16 节说明）
```

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
- **后续新增迁移** 从 `V33` 起编号，继续走 `db/migration/`。

### 34.7 复现方式

```bash
python3 scripts/build_v32.py        # 重建 V32 并做字节级等价校验
python3 scripts/build_reference.py  # 重建分类参考件（原件已删时自动从 V32 还原）
```

> 目标：形成一个可直接创建、可本地启动、可作为后续业务系统脚手架的企业级 Java 后端基础框架。设计优先级依次为：安全正确、模块边界清晰、运维可见、低成本起步、可平滑拆分。

## 13. 本地启动方案

### 13.1 Docker Compose 最小依赖

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

### 13.2 启动步骤

```bash
cp .env.example .env
docker compose up -d mysql redis xxl-job-admin
./mvnw -pl scaffold-bootstrap -am clean verify
./mvnw -pl scaffold-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local
```

### 13.3 冒烟检查

```bash
curl -fsS http://localhost:8080/livez
curl -fsS http://localhost:8080/readyz
curl -fsS http://localhost:9090/actuator/health
curl -fsS http://localhost:9090/actuator/prometheus
CSRF_TOKEN=$(curl -fsS -c /tmp/scaffold-cookies http://localhost:8080/api/auth/csrf)
curl -i -b /tmp/scaffold-cookies -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  -d '{"username":"admin","password":"ChangeMe123!"}'
```

其中 `/api/auth/csrf` 返回纯文本 token，并由 Spring 同步写入 `XSRF-TOKEN` Cookie；生产前端读取该非 HttpOnly 的 CSRF Cookie，但 Refresh Token Cookie 必须保持 HttpOnly。

首次启动流程：

1. Docker 初始化 `scaffold` 与 `xxl_job` 数据库。
2. Flyway 创建框架表和种子数据。
3. 本地 profile 检测不存在管理员时，通过环境变量生成一次性管理员账号。
4. 控制台只输出一次性密码或要求从环境变量读取；首次登录强制修改。
5. Flowable 通过 Flyway 导入匹配版本的官方结构，`database-schema-update=false`。
6. Spring Boot 启动后 `/livez`、`/readyz` 可访问。

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
| 应用管理端点 | **9090** | `application.yml` `management.server.port=9090`，暴露 `/actuator/prometheus` |
| Prometheus | **9091** | 启动加 `--web.listen-address=:9091`，避免与应用 9090 冲突 |
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

## 附录 P. 生产加固与质量门禁落地清单（综述）

以下为《需求差距分析》§7–§9 及《设计方案》§15 阶段 5 的落地结论（完整证据见 `docs/archive/requirement-gap-analysis.md`）。

**登录安全增强（2026-10-10）**：登录验证码接入登录流程；Access Token TTL 15m→**30m**、Refresh Token TTL 7d→**30d**；多端登录限制（`SessionLimitRules` + `max-concurrent-sessions` 默认 3，策略 reject/evict-oldest，越限返回 `AUTH_011` 409）。

**密钥与凭证**：密钥轮换 `JwtKeyRotationService` + `RotatingJwtDecoder`（kid 双密钥，`previousJwtSecret` 宽限期）；外部密钥源 `VaultSecretProvider`（零依赖，JDK HttpClient 调 Vault KV v2）+ `LocalSecretProvider` 回退，`secret.vault.*` 配置。

**限流与防重放**：`RateLimitFilter` 对 `/api/auth/login`、`/api/auth/refresh` 固定窗口限流（默认 60s/10 次，超 429 `AUTH_010`）；`AntiReplayFilter`（请求头 `X-Request-Timestamp` + `X-Request-Nonce`，opt-in 由 `auth.anti-replay.enabled` 控制）。

**密码策略**：`PasswordPolicy`（长度 8–64、大小写/数字/特殊字符可配置、弱口令字典、复用最近 5 次校验）。

**依赖安全（SCA）**：CI `sca` 作业接入 OWASP dependency-check（非阻塞 advisory）；`.github/workflows/security-scan-scheduled.yml` 定期自动扫描（每周一 03:00 + 手动）。

**CI/CD**：`.github/workflows/ci.yml` 于 push/PR 触发——`build-and-unit-test` 跑 `mvn -B test`（surefire）；`integration-test` 用 MySQL 8.4 服务容器 + env 覆盖数据源跑 failsafe `*IT`（Flyway 自动建表）；`maven-failsafe-plugin` + `skipITs`（默认 true）。

**健康探针**：`/livez`（仅 JVM/主循环，恒 200）、`/readyz`（检查必需依赖 DB，不可用时 503），`monitor/health` 包，已加入免鉴权 `PUBLIC_API`。

**范围外 / N/A**：多租户、上传安全（无上传接口）、压测/容量基线/故障注入/回滚演练（需原生环境）、Docker Compose（采用原生进程启动）。
