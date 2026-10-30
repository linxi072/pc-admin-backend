# 新手开发部署教程（Getting Started）

> 面向**第一次接触本仓库的新手开发者**：从一台干净电脑开始，按本教程顺序执行，最终在本地跑通「MySQL + Redis + 后端（8080）+ 前端（5173）」，并用管理员账号登录后台。
>
> 全程**不需要 Docker**（项目约定原生进程构建与部署）。所有命令均经过真实环境验证；标注「⚠️ 坑」的条目是本项目实战中真实踩过并已定位根因的问题，请仔细阅读对应排查章节。

## 0. 导览：你将要做什么

| 阶段 | 做什么 | 对应章节 | 预计结果 |
|---|---|---|---|
| ① 环境准备 | 安装 JDK 21 / Maven / MySQL 8 / Redis / Node 22 | §1 | `java -version` 等核对命令全部通过 |
| ② 依赖安装 | 启动 MySQL 与 Redis，创建 `scaffold` 库 | §2 | `mysql -e "select 1"`、`redis-cli ping` 通过 |
| ③ 项目配置 | 获取代码、看懂目录、配置 `.env` 环境变量 | §3 | `.env` 就绪，`JWT_SECRET` 已生成 |
| ④ 部署步骤 | 编译 → 启动后端 → 启动前端 → 冒烟验收 → 登录 | §4 | 浏览器打开 5173 能登录进后台 |
| ⑤ 排查 | 出问题按症状查表 | §5 | —— |

**与四本手册的关系**：本教程是「最短上手路径」，只讲新手必需内容；深入内容请按需阅读《架构手册》《开发手册》《运维手册》《部署手册》（入口见 [`docs/index.md`](../index.md)）。手册中重复的命令以本教程为准（本教程命令最新）。

**代码仓库结构提醒**：后端 `java-admin-framework/` 与前端 `pc-admin-vue3-vite/` **在同一个 git 仓库内**（仓库根目录同时包含这两个目录）。本教程中 `java-admin-framework/` 简称为「后端目录」，`pc-admin-vue3-vite/` 简称为「前端目录」。

---

## 1. 环境准备

### 1.1 软件清单与版本要求

| 软件 | 版本要求 | 用途 | 核对命令 |
|---|---|---|---|
| JDK | **21**（编译基线声明 17，运行时用 21） | 后端编译与运行 | `java -version` |
| Maven | 3.9+（仓库内**无** `mvnw` 包装器，需本机安装） | 后端构建 | `mvn -v` |
| MySQL | **8.x**（8.0 / 8.4 均可） | 业务数据库 | `mysql --version` |
| Redis | 6.x / 7.x | 缓存 / Token 黑名单 / 在线会话 | `redis-cli ping` |
| Node.js | **22 LTS**（≥18 亦可，推荐 22） | 前端构建与开发服务器 | `node -v` |

> 前端依赖较重（element-plus / echarts），内存 ≥ 8GB 的开发机会更顺畅。

### 1.2 安装命令（按平台）

#### macOS（Apple Silicon / Intel 通用，推荐 Homebrew）

```bash
# JDK 21（Temurin）
brew install --cask temurin@21

# Maven
brew install maven

# MySQL 8 与 Redis
brew install mysql redis

# Node 22
brew install node@22
```

#### Linux（Debian / Ubuntu）

```bash
# JDK 21
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk

# Maven
sudo apt-get install -y maven

# MySQL 8（Ubuntu 22.04+ 源内即为 8.0）
sudo apt-get install -y mysql-server redis-server

# Node 22（NodeSource）
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs
```

#### Windows

- **JDK 21**：下载 Temurin 21 安装包（`.msi`），安装后设置系统环境变量 `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x`，并把 `%JAVA_HOME%\bin` 加入 `Path`。
- **Maven**：下载二进制包解压到 `C:\maven`，把 `C:\maven\bin` 加入 `Path`。
- **MySQL 8**：下载 MySQL Installer，按向导安装并记住 root 密码；或使用 WSL2 中的 Linux 方式安装。
- **Redis**：Windows 可用 [Memurai](https://www.memurai.com/) 或 WSL2 中的 Redis。
- **Node 22**：下载 LTS 安装包（`.msi`）。

> Windows 下后续命令请在 **PowerShell** 或 **Git Bash** 中执行；涉及 `export VAR=value` 的命令在 PowerShell 中改为 `$env:VAR="value"`。

### 1.3 JDK 21 核对与多版本切换（macOS 重点）

macOS 上常装多个 JDK，务必确认当前默认是 21：

```bash
# 查看已安装的 JDK 列表
/usr/libexec/java_home -V

# 核对当前 java 版本（应输出 21.x）
java -version

# 每个新终端会话建议先执行（本教程所有命令均假设已设置）：
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

> **⚠️ 坑（JDK 版本）**：本项目 Lombok + jOOQ + Flowable 组合在 JDK 17 上可编译，但项目约定**运行时统一用 21**。若 `java -version` 显示 1.8 / 11 / 17，先按上面命令切换，再继续后续步骤。

### 1.4 Maven 与离线仓库（重要）

本仓库**不提供 `mvnw`**，请使用本机 Maven。若你的网络可以访问 Maven Central，直接使用即可；若在与本项目相同的**离线/内网环境**，则需要：

- 一个已预热（prefetch）的本地仓库目录；
- Maven 的 `settings.xml` 中把 `localRepository` 指向该目录；
- 所有命令统一加 `-o`（offline）参数。

本项目开发机的实际配置（供参考，请按你的环境替换路径）：

```bash
# 开发机 Maven 位于自定义目录（非 brew 默认路径）
MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn

# settings.xml 中：localRepository 指向 /Users/mezo/Documents/localRepository/mvnRepository
# （注意：真实仓库不是默认的 ~/.m2/repository）

# 离线构建示例
$MVN -o clean test
```

> **⚠️ 坑（离线仓库路径）**：`mvn` 找不到依赖报 `Cannot access ... in offline mode` 时，先确认 `settings.xml` 的 `localRepository` 指向的是**真实仓库目录**（预热过依赖的那个），而不是空的默认目录；再确认命令带了 `-o`。详见 §5.2。

---

## 2. 依赖安装（MySQL / Redis / Node 前端依赖）

### 2.1 MySQL 8：启动、建库、核对

```bash
# macOS：以服务方式启动
brew services start mysql
# Linux：sudo systemctl start mysql（并设开机自启：sudo systemctl enable mysql）

# 连接核对（按提示输入 root 密码）
mysql -u root -p -e "SELECT VERSION();"

# 创建业务数据库（字符集必须 utf8mb4；Flyway 会自动建表，无需手工执行 DDL）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS scaffold
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 核对库已创建
mysql -u root -p -e "SHOW DATABASES LIKE 'scaffold';"
```

期望输出：`scaffold` 一行。

> **说明**：本项目的表结构（`sys_*` / `wf_*` 共 20 张）由 Flyway 在应用**首次启动时**自动创建并灌入种子数据（迁移链 `V32`~`V59`，其中 `V57` 为 Java 类迁移 `V57__backfill_tenant_id.java`）；`ACT_*`（Flowable）与 `jobrunr_*`（JobRunr）表由对应组件自动创建。**你不需要手工执行任何 DDL。**

### 2.2 Redis：启动与核对

```bash
# macOS
brew services start redis
# Linux：sudo systemctl start redis-server

# 核对（应返回 PONG）
redis-cli ping
```

> Redis 无密码时保持默认即可；若你的 Redis 设有密码，在 §3.3 的 `.env` 中配置 `REDIS_PASSWORD`。

### 2.3 Node 与前端依赖

```bash
cd <仓库根>/pc-admin-vue3-vite   # 前端目录（与后端目录同级）

node -v                          # 应 ≥ 18（推荐 22）
npm install                      # 安装依赖（首次约 1~3 分钟）
```

> 前端依赖装在本地 `node_modules/`，不会写入后端。若 `npm install` 报网络错误，切换 npm 镜像后重试（如 `npm config set registry https://registry.npmmirror.com`）。

---

## 3. 项目配置

### 3.1 获取代码

```bash
git clone <你的仓库地址>          # 仓库内同时包含 java-admin-framework 与 pc-admin-vue3-vite
cd <仓库根>
git checkout feat/V1.0.3         # 当前活跃开发分支（多 Maven 模块结构）
```

> 分支策略：`main` 为受保护主干，**不允许直接提交**；功能开发一律切 `feat/*` 分支，按关注点拆分提交（`feat:` / `test:` / `docs:` / `fix:` / `ci:`），由负责人合并。新手请基于 `feat/V1.0.3` 或从 `main` 切自己的 `feat/<名字>` 分支。

### 3.2 目录结构速览（5 分钟版）

```
<仓库根>/
├── java-admin-framework/            # 后端（多 Maven 模块，V1.0.3）
│   ├── pom.xml                      # 父 POM（packaging=pom，11 个模块）
│   ├── scaffold-admin/              # ★ 唯一可部署模块：启动类 + 配置 + Flyway 迁移 + 集成测试
│   │   └── src/main/resources/
│   │       ├── application.yml      # 主配置（端口/数据源/Flyway/JobRunr/Flowable…）
│   │       ├── application-local.yml    # 本地 profile（默认激活）
│   │       ├── application-prod.yml     # 生产 profile（凭据全部走环境变量）
│   │       └── db/migration/        # Flyway 迁移链 V32~V59（schema 版本 59；V57 为 Java 类迁移）
│   ├── scaffold-common/             # 基础件（上下文/工具/脱敏/导出导入…）
│   ├── scaffold-system/             # 安全 + 系统管理（RBAC/菜单/字典…）
│   ├── scaffold-workflow/           # 工作流审批（Flowable 7.1.0）
│   ├── scaffold-monitor/ scaffold-job/ scaffold-framework/ …  # 其余模块
│   ├── scripts/                     # 构建/校验/探针脚本（check-conventions.py 等）
│   └── docs/                        # 四本手册 + 专项文档（本文档所在处）
└── pc-admin-vue3-vite/              # 前端（Vue 3 + Element Plus + Vite）
    ├── vite.config.js               # 代理 /api、/ws → 127.0.0.1:8080
    └── src/                         # 页面/API 封装/路由
```

新手只需关心三个文件：`application-local.yml`（改配置）、`db/migration/`（看表结构演进，**不要改已应用的迁移**）、`vite.config.js`（改代理端口时）。

### 3.3 环境变量配置（.env）

后端所有敏感配置（数据库口令、JWT 密钥等）都从**环境变量**注入，yml 中不硬编码。本仓库提供模板：

```bash
cd java-admin-framework
cp .env.example .env        # 复制模板
```

按你的本机实际修改 `.env`（本地开发最小集）：

```bash
# ===== 必改项 =====
SPRING_PROFILES_ACTIVE=local
DB_PRIMARY_URL=jdbc:mysql://localhost:3306/scaffold?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_PRIMARY_USERNAME=root
DB_PRIMARY_PASSWORD=你的MySQLroot密码
JWT_SECRET=<32字节Base64密钥，生成方法见下>

# ===== 可选项（本地默认值已可用，按需修改）=====
REDIS_PASSWORD=                # Redis 无密码则留空
JOBRUNR_DASHBOARD_PORT=8000    # JobRunr 面板端口（仅 local）
```

**生成 JWT_SECRET**（必须 ≥256 位 / 32 字节，Base64）：

```bash
# macOS / Linux
openssl rand -base64 32
# 或 Python
python3 -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

> **⚠️ 坑（JWT_SECRET）**：不配置 `JWT_SECRET` 时应用也能启动——后端会生成**仅当前进程有效的随机临时密钥**（重启后所有已签发 Token 失效，仅用于开发/测试）。生产环境必须显式配置，否则重启即全员掉登录。也不要使用仓库里任何文档中出现过的示例密钥。

**加载 .env 并启动**（任选一种方式，之后命令都生效）：

```bash
set -a && . ./.env && set +a     # bash/zsh：导出 .env 全部变量
```

> Windows PowerShell 没有 `.` 加载语法，请手动 `$env:DB_PRIMARY_PASSWORD="..."` 逐条设置，或使用 `dotenv` 类工具。

完整的可用环境变量清单（含多数据源、通知渠道、密钥源等）见《部署手册》§15.5 与 [`scripts/init/env.template`](../scripts/init/env.template)（初始化脚本套件的模板，注释更全）。

### 3.4 Profile 说明

| Profile | 激活方式 | 特点 |
|---|---|---|
| `local`（默认） | `SPRING_PROFILES_ACTIVE=local` | 数据库/Redis 指向本机；开启 Swagger 匿名访问；开启 JobRunr Dashboard（8000）；登录验证码开启 |
| `prod` | 环境变量注入 | 数据库凭据无默认值（缺失即 fail-fast，防止误连）；Swagger 需鉴权；验证码开启 |

---

## 4. 部署步骤

### 4.0 全流程总览

按顺序执行以下 6 步，每步都有「期望结果」，对不上即进入 §5 排查：

1. **中间件就绪**（§2）：MySQL 在跑、Redis 在跑、`scaffold` 库已建；
2. **编译 + 单测**：`mvn -o clean test`（857 例全绿为正常基线）；
3. **启动后端**：三选一（`spring-boot:run` / `java -cp` / `jar`）；
4. **冒烟验收**：`/livez`、`/readyz`、登录接口；
5. **启动前端**：`npm run dev` → 浏览器登录；
6. **（可选）初始化脚本**：`python3 scripts/init/install.py all` 一键完成建库/核对/健康检查。

### 4.1 第一步：中间件就绪校验

```bash
mysql -u root -p -e "SELECT 1;"     # 期望：1
redis-cli ping                      # 期望：PONG
```

### 4.2 第二步：编译与单元测试

```bash
cd java-admin-framework
export JAVA_HOME=$(/usr/libexec/java_home -v 21)     # macOS；其它平台设为自己的 JDK21
export PATH="$JAVA_HOME/bin:$PATH"
set -a && . ./.env && set +a                         # 加载 .env（§3.3）

MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn   # 按你的环境替换
$MVN -o clean test
```

期望：12 个模块 `BUILD SUCCESS`，`Tests run: 857, Failures: 0, Errors: 0`（数量随版本增长，以仓库最新记录为准）。

> **⚠️ 坑（增量编译假阳性）**：修改代码后只跑 `mvn -o test`（不带 `clean`）时，未变化的测试源可能不重编译，出现「旧代码通过、新代码漏测」。**验证编译必须用 `mvn -o clean test-compile` 或 `mvn -o clean test`**。

> 集成测试（`*IT`，需真实 MySQL）不随单测跑：`$MVN -o test -Dtest="*IT"`。新手首次可跳过，跑通单测即可继续。

### 4.3 第三步：启动后端（三种方式，按优先级选一种）

#### 方式 A：Maven 插件直接启动（最简单，推荐新手）

```bash
cd java-admin-framework
export SPRING_PROFILES_ACTIVE=local
$MVN -o spring-boot:run -pl scaffold-admin -am
```

期望日志关键行：

```
Tomcat started on port 8080 (http)        ← 应用对外端口
Tomcat started on port 9090 (http)        ← Actuator 管理端口（独立）
Started FrameworkApplication in ~15 seconds
```

> 多模块工程必须带 `-pl scaffold-admin -am`（只构建可部署模块及其依赖）。启动约 15~40 秒（Flyway 首次要跑 39 个迁移建表 + 灌种子）。

#### 方式 B：`java -cp` 直接启动（离线环境无打包插件时用）

**适用场景**：离线仓库缺少 `maven-jar-plugin` / `spring-boot-maven-plugin` 的传递依赖时，`clean package` 与 `spring-boot:run` 均不可用（本项目在沙箱/离线环境即如此）。此时用 surefire 的 booter classpath 直接启动：

```bash
cd java-admin-framework

# 1) 提取 classpath（必须 -X 模式，booter 目录才会留存）
$MVN -o -X -pl scaffold-admin -am test -Dtest='ObservabilityPropertiesTest' \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false

# 2) 解析 booter jar 的 Class-Path，生成持久化文件（205 项绝对路径）
python3 - <<'PY'
import glob, zipfile, os
booter = sorted(glob.glob('scaffold-admin/target/surefire/surefirebooter*.jar'))[-1]
with zipfile.ZipFile(booter) as z:
    manifest = z.read('META-INF/MANIFEST.MF').decode()
cp = ''
for line in manifest.splitlines():
    if line.startswith(' '):
        cp += line.strip()
    elif cp:
        break
    elif line.startswith('Class-Path:'):
        cp = line.split(':', 1)[1].strip()
base = os.path.dirname(booter)
paths = [p if os.path.isabs(p) else os.path.normpath(os.path.join(base, p)) for p in cp.split()]
open('scaffold-admin/target/dev-classpath.txt', 'w').write('\n'.join(paths))
print('entries:', len(paths))
PY

# 3) 启动（注意三个坑，缺一不可，详见 §5.3）
cd scaffold-admin
env -u SERVER__PORT -u SERVER__HOST JWT_SECRET="<你的32字节base64>" \
  $JAVA_HOME/bin/java -cp "$(cat target/dev-classpath.txt)" \
  com.acme.scaffold.FrameworkApplication --spring.profiles.active=local --server.port=8080
```

#### 方式 C：打 jar 启动（联网环境 / CI 常用）

```bash
cd java-admin-framework
$MVN -o clean package -DskipTests
java -jar scaffold-admin/target/scaffold-admin-1.0.0.jar --spring.profiles.active=local
```

### 4.4 第四步：冒烟验收（后端）

新开一个终端（保持后端运行），先确认端口与健康状态：

```bash
# 存活探针：仅依赖 JVM，恒 200
curl -fsS http://localhost:8080/livez

# 就绪探针：检查数据库等必需依赖，不可用返回 503
curl -fsS http://localhost:8080/readyz

# Actuator（独立管理端口 9090，不是 8080！）
curl -fsS http://localhost:9090/actuator/health
```

期望：`/livez`、`/readyz` 返回 `{"status":"UP",...}`；`/actuator/health` 返回 `{"status":"UP"}`。

**登录接口冒烟**（local profile 默认开启验证码，需先取题）：

```bash
# 1) 取验证码（返回 token 与运算题，如 "3 × 4 = ?"）
curl -fsS http://localhost:8080/api/auth/captcha

# 2) 登录（默认管理员 admin / admin123；验证码答案注意全角符号换算）
curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123",
       "captchaToken":"<上一步的token>","captchaAnswer":"12"}'
```

期望：返回 `{"code":0,"data":{"accessToken":"eyJ...","refreshToken":"..."}}` 形式的统一包络。

> **⚠️ 坑（验证码）**：① 题目中乘法是**全角 `×`**、除法是**全角 `÷`**（如 `3 × 4 = ?`），手算时别认错；② 登录字段名是 `captchaToken` 与 `captchaAnswer`（**不是** `captcha`，误发报 400 `AUTH_008`）；③ 验证码 120 秒过期，超时重新 `GET /api/auth/captcha`。
>
> **⚠️ 坑（默认口令）**：内置超级管理员为 `admin` / `admin123`（角色 `SUPER_ADMIN`，由 Flyway 迁移 `V32__consolidated_schema.sql` 播种，bcrypt 存储）。**首次登录后请立即在「个人中心 → 修改密码」更换**，生产环境必须更换。

端口速查：

| 端口 | 用途 | 备注 |
|---|---|---|
| **8080** | 应用对外（API / Swagger / WebSocket） | `server.port` |
| **9090** | Actuator 管理端点（`/actuator/health`、`/actuator/prometheus`） | 独立端口，与 8080 不冲突 |
| **8000** | JobRunr Dashboard（仅 local） | `JOBRUNR_DASHBOARD_PORT` 可覆盖 |
| 5173 | Vite 开发服务器（前端） | `npm run dev` |
| 4173 | Vite 预览（`npm run preview`） | |

Swagger（仅 local，匿名放行）：`http://localhost:8080/swagger-ui.html`；JobRunr 面板：`http://localhost:8000/dashboard`。

### 4.5 第五步：启动前端并登录

```bash
cd <仓库根>/pc-admin-vue3-vite
npm run dev
```

期望：`Local: http://localhost:5173/`。浏览器打开：

1. 访问 `http://localhost:5173`，应看到登录页（标题「运营管理后台」）；
2. 用 `admin` / `admin123` 登录（页面会自动拉取验证码并渲染运算题）；
3. 登录成功进入工作台，左侧菜单应包含「工作台 / 系统管理 / 运维管理 / 报表管理」等入口（由 `sys_menu` 种子 + `SUPER_ADMIN` 绑定决定）。

> 前端通过 `vite.config.js` 把 `/api`、`/ws` 代理到 `127.0.0.1:8080`，**无需改前端任何配置**；只有你的后端不用 8080 时才需要改代理目标。
>
> **⚠️ 坑（前端页面 404 / 菜单空白）**：先确认后端 8080 活着（`curl http://localhost:8080/livez`），再看浏览器 Network 里 `/api/...` 是否 401/502；502 通常是后端没起来或端口不一致。

### 4.6 第六步（可选）：用初始化脚本一键完成环境就绪校验

本仓库提供可复用的初始化脚本套件（详见 [`scripts/init/README.md`](../scripts/init/README.md)），把「建库授权 → 环境变量 → 种子核对 → 健康检查」串成一键流程：

```bash
cd java-admin-framework
python3 scripts/init/install.py --help                # 查看全部子命令
python3 scripts/init/install.py all --dry-run         # 预演：只打印将执行的动作
python3 scripts/init/install.py all                   # 实跑（幂等，可重复执行）
```

脚本会在结束后打印一份「就绪报告」：前置条件、数据库连接、迁移版本（期望 39）、种子行数、健康探针结果。任一项 FAIL 都会给出对应的排查指引（指向本文档 §5 对应小节）。

---

## 5. 常见问题排查（FAQ）

按「症状 → 原因 → 处理」组织。带 🔗 的条目可在对应手册找到更深入的说明。

### 5.1 环境与工具类

| 症状 | 原因 | 处理 |
|---|---|---|
| `java -version` 是 1.8/11/17 | 默认 JDK 不对 | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`（macOS）；Windows 改 `JAVA_HOME` 环境变量 |
| `mvn: command not found` | Maven 未装 / 未入 Path | 安装后把 `bin` 加入 `Path`；本仓库无 `mvnw`，不能用 `./mvnw` |
| `Cannot access ... in offline mode` | 离线仓库缺依赖或 `-o` 与仓库不匹配 | 确认 `settings.xml` 的 `localRepository` 指向真实仓库；确认命令带 `-o`；缺插件时改用 §4.3 方式 B |
| `mysql: command not found` | 客户端未入 Path | macOS：`brew install mysql-client` 后 `echo 'export PATH=...' >> ~/.zshrc`；或直接用绝对路径 |
| 连接 MySQL 报 `Access denied for user 'root'@'localhost'` | 口令错 / 认证方式不匹配 | 核对 `.env` 的 `DB_PRIMARY_PASSWORD`；MySQL 8 默认 `caching_sha2_password`，老客户端驱动不兼容时改 `mysql_native_password` |
| 连接 Redis 超时 | Redis 未启动 / 端口不对 | `redis-cli ping`；`brew services list` 看状态；`REDIS_HOST`/`REDIS_PORT` 环境变量核对 |

### 5.2 构建类

| 症状 | 原因 | 处理 |
|---|---|---|
| 单测「假通过」（改了代码但测试没重跑） | 增量编译不重编未变测试源 | 一律 `mvn -o clean test` / `clean test-compile` |
| `Tests run: N` 与文档基线不一致 | 正常现象：测试数随版本增长 | 以「0 failures / 0 errors」为准，不以具体数字为准 |
| `package` 卡住或报缺插件 | 离线仓库无 `maven-jar-plugin` / `spring-boot-maven-plugin` 传递依赖 | 走 §4.3 方式 B（`java -cp`）；或联网环境构建 |
| 前端 `npm install` 慢/失败 | 网络问题 | 换镜像：`npm config set registry https://registry.npmmirror.com` 后重试 |

### 5.3 启动类（本项目实战坑，重点）

| 症状 | 根因 | 处理 |
|---|---|---|
| **启动后端口是随机 5 位数**（如 50280），curl 8080 拒绝 | 环境中注入了 `SERVER__PORT` 变量；Spring relaxed binding 把**双下划线**映射为 `server.port`，覆盖 yml 的 8080 | 启动时 `env -u SERVER__PORT -u SERVER__HOST` 清掉，并显式 `--server.port=8080`（§4.3 方式 B 已内置） |
| **`BindException: Address already in use: 8000`**（JobRunr Dashboard） | 上一次实例没退干净，8000 被占 | 重启前先 `lsof -ti :8080 \| xargs kill; lsof -ti :9090 \| xargs kill; lsof -ti :8000 \| xargs kill`，或 `JOBRUNR_DASHBOARD_PORT` 换端口 |
| **`Migration checksum mismatch for migration version xx`** | 改了**已应用过**的 Flyway 迁移（哪怕只加一行注释：checksum 按整个文件字节计算） | `git checkout` 还原该文件；确需保留修改时在临时库真跑一次读出真实 checksum 再 `repair`；schema 变更一律新增 `V40`，不回改旧脚本 🔗《部署手册》附录 O.1 |
| **`BeanCurrentlyInCreationException` 指向某个 `*Config`** | 配置类构造注入了自己声明的 Bean（Spring Boot 3 默认禁止循环引用） | 把该 `@Bean` 方法改为 `static` 🔗《部署手册》附录 O.2 |
| **`required a single bean, but 2 were found: spanRecorder, otlpSpanExporter`** | 可观测性双 SpanExporter 注册导致单点注入歧义 | 已修复（`otlpSpanExporter` 加 `@Primary`，由 `ObservabilityWiringTest` 3 例守护）；若你引入了新的同类型 Bean，用 `@Primary` 明确首选 |
| 启动 15 秒后退出，日志有 `Failed to configure a DataSource` | `.env` 未加载 / 数据库口令错 | `set -a && . ./.env && set +a` 后重启；核对 `DB_PRIMARY_*` |
| `Flyway` 报 `applied migration not resolved locally` | 既有库的 `flyway_schema_history` 里有本地已删除的版本记录 | 按《部署手册》§34.4 替换历史记录（V32 合并迁移的既有环境升级路径） |

### 5.4 运行类

| 症状 | 原因 | 处理 |
|---|---|---|
| **访问 localhost 超时 / 连接被拒**，但服务明明在跑 | 沙箱/办公网注入了 `http_proxy` 环境变量，curl 走了代理 | `curl --noproxy '*' http://localhost:8080/...`，或 `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY no_proxy="*"` 后再访问 |
| **`/api/connectivity/ping` 返回 401** | 匿名白名单遗漏（历史缺陷，已修复） | 当前代码白名单已含 ping/slow GET；若你改过 `SecurityConfig.BASE_PUBLIC_API`，确认保留这两条 matcher |
| **登录报 400 `AUTH_008`** | 验证码字段名错误 | 用 `captchaToken` + `captchaAnswer`，且答案按全角 `×`/`÷` 换算 |
| **登录报 401 但口令正确** | 验证码过期（120s） / 账户锁定（连续失败 5 次锁 15m） | 重新取验证码；等待锁定到期或 `UPDATE sys_user SET failed_login_count=0, locked_until=NULL WHERE username='admin';` |
| **登录成功但左侧菜单为空 / 页面 404 跳工作台** | 菜单种子缺失（历史缺陷：V35/V36 菜单 id=1091 碰撞，INSERT IGNORE 静默丢行） | 已由 V39 以 id=1093/1094 重播种修复；手工核对：`SELECT id,menu_code FROM sys_menu WHERE deleted=0 ORDER BY id;` 应含 `system:import`、`system:observability-center` |
| **`/actuator/health` 连不上** | 走错端口：Actuator 在 **9090**，不在 8080 | `curl http://localhost:9090/actuator/health` |
| **前端页面能开但接口 502** | 后端未启动 / 端口不一致 / 代理目标错 | 核对后端 `Tomcat started on port 8080`；必要时改 `vite.config.js` 的 proxy target |
| **修改菜单/权限后不生效** | 权限缓存（M5）按 key 缓存 + Token 版本失效机制 | 重新登录（Token 版本号递增使旧 Token 失效）或调 `POST /api/system/monitor/caches/evict` 🔗《运维手册》§19 |

### 5.5 排查工具箱

```bash
# 1) 应用日志（前台启动就在当前终端；后台启动看重定向文件）
#    日志格式见 logback-spring.xml；traceId 贯穿审计日志与统一响应

# 2) 端口占用核对（macOS/Linux）
lsof -i :8080 -i :9090 -i :8000 -i :5173

# 3) 健康三连
curl -fsS http://localhost:8080/livez
curl -fsS http://localhost:8080/readyz
curl -fsS http://localhost:9090/actuator/health

# 4) 数据库状态核对（应见 V32~V59 全部 success=1，其中 V57 为 Java 类迁移）
mysql -u root -p scaffold -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

# 5) 种子核对（默认管理员/角色/菜单/字典应在）
mysql -u root -p scaffold -e "
  SELECT (SELECT COUNT(*) FROM sys_user WHERE username='admin') AS admin_exists,
         (SELECT COUNT(*) FROM sys_role WHERE role_code='SUPER_ADMIN') AS super_role,
         (SELECT COUNT(*) FROM sys_menu WHERE deleted=0) AS menus,
         (SELECT COUNT(*) FROM sys_dict_type) AS dict_types;"

# 6) 前后端联通探针（后端起来后跑，2/2 PASS 为正常）
python3 scripts/connectivity-probe.py --base-url http://localhost:8080

# 7) 前端联通测试（真实后端模式）
API_BASE=http://localhost:8080 node --test scripts/fe-connectivity.mjs
```

> 更完整的运维排查（慢 SQL、N+1、缓存命中率、告警收件箱）见《运维手册》；联调证据链与探针脚本说明见《开发手册》「真实运行环境联调」章节。

---

## 6. 附录

### 6.1 命令速查（按使用频率排序）

```bash
# 环境（每个新终端）
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_PROFILES_ACTIVE=local
set -a && . ./.env && set +a

# 后端
$MVN -o clean test                          # 单测门禁
$MVN -o test -Dtest="*IT"                   # 集成测试（需 MySQL）
$MVN -o spring-boot:run -pl scaffold-admin -am   # 启动（方式 A）
$MVN -o -pl scaffold-system -am test        # 单模块快速迭代

# 前端
cd ../pc-admin-vue3-vite && npm install && npm run dev
npm run build                               # 产物校验

# 校验与初始化
python3 scripts/check-conventions.py        # 编码/目录约定校验（必须 EXIT=0）
python3 scripts/init/install.py all         # 初始化一键流程（幂等）
```

### 6.2 延伸阅读

| 想了解 | 读什么 |
|---|---|
| 架构全景、模块边界、表结构 | 《架构手册》`docs/manual/architecture.md` |
| 日常开发（jOOQ 写法、模块开发模式） | 《开发手册》`docs/manual/development.md` |
| 部署上线、多数据源、生产加固 | 《部署手册》`docs/manual/deployment.md` |
| 运维监控、故障排查 | 《运维手册》`docs/manual/operations.md` |
| 多 Maven 模块拆分 | 《模块拆分指南》`docs/manual/module-split.md` |
| 编码规范与目录约定 | 《编码规范与目录结构约定》`docs/manual/coding-standard.md` |
| 初始化脚本用法 | `scripts/init/README.md` |

---

> 更新日期：2026-10-21（新增《新手开发部署教程》：五段式结构，整合四本手册/专项文档/scripts 资产；命令均经真实运行环境验证；排查章节收录本轮实证的 8 类坑：JDK 版本、离线仓库路径、增量编译假阳性、`SERVER__PORT` 劫持、8000 端口冲突、Flyway checksum、循环依赖 static @Bean、代理 `--noproxy`、验证码全角符号与字段名、菜单 ID 碰撞）
