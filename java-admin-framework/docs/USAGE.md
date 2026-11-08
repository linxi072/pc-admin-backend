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
```

## 3. 双门禁（提交前必过）
| 门禁 | 命令 | 通过标准 |
|---|---|---|
| 测试 | `mvn -o -f pom.xml -pl <module> -am clean test` | BUILD SUCCESS，全部用例绿 |
| 约定 | `python3 scripts/check-conventions.py` | 退出码 0（H1–H5 规则：分层、依赖无环、命名等） |
| 目录约定（补充校验） | `python3 scripts/check-directory.py` | 退出码 0（D1–D7：包根白名单、启动类位置、配置归属、全局样式、规范文档与索引、子模块版本管理；行为回归 `python3 -m unittest discover -s scripts -p "test_*.py" -v`） |

> 注意：从 root 聚合 pom 用 `-pl <module> -am`，**禁止**直接 `-f scaffold-admin/pom.xml` 单独构建；`check-conventions.py` 默认 120s 会被 SIGTERM，须 `dangerouslyDisableSandbox` 且 `timeout>=600000`。

## 4. 开发约定
- 分层：`controller → service → domain`（`scripts/check-conventions.py` H3 守护）。
- 跨模块协作经 **Port（防腐层）**，特性模块不互相直连 Service。
- 数据写入统一走 `JooqWriters`，自动填充/逻辑删除/租户隔离不变；DDL 标识符须显式加引号保留 snake_case 小写。
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
- 新增后端端点时，前端须同步补：路由 + i18n 键 + 菜单种子（Flyway）+ API 客户端函数。
