# 系统初始化安装脚本套件（scripts/init/）

面向**新环境搭建 / 重复部署 / 交接校验**的一键式初始化脚本。把「环境变量 → 建库授权 → 基础数据核对补种 → 安装校验」四个环节收敛为可重复执行的确定流程。

- 零第三方依赖，Python 3.9+（已在 3.9.6 与 3.13.12 双版本验证）；
- **幂等可重复**：任一步骤可反复执行，结果一致（`CREATE ... IF NOT EXISTS` / `INSERT IGNORE` / 只补缺失键）；
- **结果可预期**：退出码 `0` = 无 FAIL（`SKIP`/`PENDING` 不算失败），`1` = 存在 FAIL；
- **先预演再实跑**：`--dry-run` 与实跑共用同一计划，只打印不落盘。

## 快速开始

```bash
cd java-admin-framework

# 0) 预演（不执行任何写操作）
python3 scripts/init/install.py all --dry-run

# 1) 一键实跑（env + db + seed + verify）
python3 scripts/init/install.py all

# 2) 分步执行（按需）
python3 scripts/init/install.py env      # 生成/补全 .env（自动生成 32 字节 Base64 JWT_SECRET）
python3 scripts/init/install.py db       # 建库（可选 --app-user/--app-password 建业务账号并授权）
python3 scripts/init/install.py seed     # 基础数据核对，缺失则幂等补种
python3 scripts/init/install.py verify   # 前置条件/迁移版本/种子摘要/健康检查
```

连接信息解析顺序：**命令行参数 > 环境变量（`DB_PRIMARY_*`）> local 默认值**（`root@localhost:3306/scaffold`）。示例：

```bash
python3 scripts/init/install.py db --db-host 127.0.0.1 --db-user root --db-password root
python3 scripts/init/install.py verify --base-url http://localhost:8080 --require-health
```

## 四个步骤分别做什么

| 步骤 | 动作 | 幂等机制 |
|---|---|---|
| `env` | 由 `env.template` 生成仓库根 `.env`；已存在则只补缺失键 | 不覆盖既有值；`--force` 才重生成 `JWT_SECRET`（会当场失效旧 Token） |
| `db` | `CREATE DATABASE IF NOT EXISTS`（utf8mb4）；可选建业务账号 + `GRANT` | 全部 `IF NOT EXISTS`；`information_schema` 复核库存在 |
| `seed` | 核对 9 项基线（admin/SUPER_ADMIN/绑定/字典/配置/接口资源/菜单/两条修复菜单）；缺失则补种 | 补种语句与 Flyway 迁移 V32/V39 种子逐字等价，`INSERT IGNORE`；口令经 `MYSQL_PWD` 传递不走命令行 |
| `verify` | JDK/Maven/mysql/Redis/Node 前置检查；迁移版本（本地链 vs `flyway_schema_history`）；种子摘要；`/livez`、`/readyz`、`/api/connectivity/ping` 健康三连 | 只读；HTTP 一律走 `ProxyHandler({})` opener，不受 `http_proxy` 干扰 |

## 状态与退出码

| 标记 | 含义 | 是否影响退出码 |
|---|---|---|
| `[ OK ]` | 通过 | 否 |
| `[FAIL]` | 失败（缺工具/连不上/校验不过） | **是（退出码 1）** |
| `[SKIP]` | 跳过（如 `--skip-health`、无 redis-cli） | 否 |
| `[WAIT]` | 等待后续动作（如库为空待应用首次启动跑 Flyway） | 否 |

## 与 Flyway 的关系（重要）

标准路径下**全部基础数据由 Flyway 迁移链（V32~V39）在应用首次启动时自动导入**，本套件不替代 Flyway。`seed` 步骤面向两类场景兜底：

1. DBA 手工管控（关闭 Flyway 自动执行）时的核对与补种；
2. 历史缺陷修复（V35/V36 菜单 id=1091 碰撞被 `INSERT IGNORE` 静默丢行 → V39 以 1093/1094 重播种）。

若库中表尚不存在，`seed` 返回 `[WAIT]` 并提示先启动后端（教程 §4.3），不会误报失败。

## 自测

```bash
python3 -m unittest discover -s scripts -p "test_init_install.py"     # 20 例，离线可跑
```

覆盖：JWT_SECRET 生成与强度校验、.env 解析/补全/保留/force、JDBC 解析与连接优先级、幂等 SQL 生成、补种语句与迁移种子一致性、迁移版本扫描、proxy-free opener 功能验证（假代理下直达本地服务）、ping 包络双形态判定、CLI 帮助与 dry-run 编排。

## 文件清单

| 文件 | 职责 |
|---|---|
| `install.py` | 总入口：argparse CLI、四步编排、报告渲染、退出码 |
| `env_setup.py` | `.env` 生成/补全计划与落盘、JWT_SECRET 生成与强度校验 |
| `db_init.py` | JDBC 解析、连接信息优先级、mysql 客户端封装、建库 SQL |
| `seed_check.py` | 9 项基线核对、与迁移等价的幂等补种语句 |
| `verify_install.py` | 前置条件、迁移版本、种子摘要、健康检查（proxy-free） |
| `env.template` | 环境变量模板（27 项，含逐项注释） |
| `../test_init_install.py` | 离线自测（20 例） |

> 安全约定：`.env` 已被仓库根 `.gitignore` 忽略（含口令与密钥，禁止入库）；模板文件 `env.template` 与 `.env.example` 不含真实凭据。
