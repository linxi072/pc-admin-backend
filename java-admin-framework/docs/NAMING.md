# 命名规范（Naming Conventions）

> 适用仓库：`java-admin-framework`
> 配套脚本：`scripts/check-naming.py`（零依赖，仅标准库）
> 门禁整合：`scripts/check-all.py` 的 `naming` 步骤（硬性）
> 与 `scripts/check-conventions.py` 的关系：后者负责**架构 / 层 / 目录结构**约定
> （包级环、依赖方向、数据类放置、模块 DAG），本规范只负责**标识符大小写风格**，
> 二者职责互补、互不重叠。

---

## 1. 规则总览

| 规则 | 级别 | 适用对象 | 强制风格 | 违规示例 | 合规示例 |
|---|---|---|---|---|---|
| **N1** | 硬（失败即阻断） | `class` / `interface` / `enum` / `record` | PascalCase（首字母大写、仅字母数字、无下划线） | `badName` / `user_dao` | `UserDao` / `NotificationPreferenceService` |
| **N2** | 硬（失败即阻断） | `static final` 常量 | UPPER_SNAKE（全大写 + 下划线） | `maxValue` / `defaultTimeout` | `MAX_VALUE` / `DEFAULT_TIMEOUT` |
| **N3** | 警告 | 方法名 | camelCase（首字母小写） | `Get_User` / `SaveUser`(首字母大写) | `getUser` / `saveUser` |
| **N4** | 警告 | 字段 / 局部变量 / 参数名 | camelCase（首字母小写） | `user_name` / `UserID` | `userName` / `userId` |

级别说明：
- **硬（N1 / N2）**：任何违反都会让 `check-naming.py` 返回 `EXIT=1`，进而阻断统一门禁。
- **警告（N3 / N4）**：仅报告，不阻断构建（命名风格属于可读性问题，逐步收敛）。

> 说明：枚举常量（`enum { A, B }`）按惯例使用 UPPER_SNAKE，与 N2 一致，但当前检查器未对枚举常量做强校验（避免与接口隐式常量混淆），统一以 UPPER_SNAKE 书写即可。

---

## 2. 豁免项

- `serialVersionUID`：Java 序列化规范要求的固定字段名（含小写），列为豁免，不触发 N2。
- 字符串字面量、注释中的文本：扫描前已被剥离，不会被误判为标识符。
- 构造函数：名称与类型名相同（首字母大写），与「方法名首字母小写」区分，不触发 N3。
- 局部变量 / 参数：与方法、字段同属 N4 的 camelCase 约束，统一收敛。

---

## 3. 判定启发式（实现要点）

为避免与方法调用 / 控制流关键字 / 链式调用混淆，`check-naming.py` 采用「声明上下文」识别：

- **方法声明**：要求返回类型为「首字母大写类型 / 已知基本类型（int/long/…）/ String / void」，从而与
  `obj.foo()`、`return foo()`、`new Foo()` 等调用场景自然区分（这些场景返回类型是小写开头，不匹配）。
- **构造函数排除**：方法名与返回类型简单名相同时（如 `public Foo()`）判定为构造函数，跳过 N3。
- **字段 / 局部 / 参数**：要求类型后紧跟 `= ; , )`，从而与方法名后紧跟 `(` 区分，避免重复告警。
- 大小写判定：`PascalCase = ^[A-Z][A-Za-z0-9]*$`；`camelCase = ^[a-z][A-Za-z0-9]*$`；
  `UPPER_SNAKE = ^[A-Z][A-Z0-9_]*$`（含小写即不合规）。

---

## 4. 运行方式

```bash
# 自动发现全部 scaffold-*/src/main/java（默认模式，含模块 DAG 跳过）
python3 scripts/check-naming.py

# 指定单目录 / 多目录（单模块或回归测试）
python3 scripts/check-naming.py --src scaffold-system/src/main/java
python3 scripts/check-naming.py --src a/src/main/java --src b/src/main/java

# 仅打印违规摘要（不逐条列出）
python3 scripts/check-naming.py --quiet

# 通过统一门禁（naming 已纳入硬性步骤）
python3 scripts/check-all.py                 # 全部门禁
python3 scripts/check-all.py --only naming   # 仅命名校验
```

退出码：`0` = 仅警告或无问题；`1` = 存在 N1 / N2 硬违规；`2` = 参数 / 路径错误。

---

## 5. 与分层方案的关系

本规范是 `docs/分层目录结构方案_20261109.md` 中「文件归类规则」的**机器可检查**补充：

- 分层方案规定「`*Controller`→`api`、`*Service`→`application`、`*Rules`→`domain/rule`」等**归属层**；
- 本规范规定「无论落在哪一层，类型 / 常量 / 方法 / 字段的名字本身应符合通用大小写约定」。

两者叠加即可在 CI 中静态保证：代码既**放对地方**，又**叫对名字**。

> 参考实现：子域 `com.acme.scaffold.system.notifypref` 已落地
> `api / application / domain{model,rule}` 标准骨架，可作为新代码的合入模板。

---

## 6. 领域术语、权限码与跨层命名口径（若依对齐结论）

> 本节固化 `若依规范对齐差异清单与重构方案_20261109.md` 的落地结论。
> N1–N4 只管**大小写风格**；本节管**用词与取值口径**，由
> `scripts/check-permission.py`（P1）与人工评审共同守护。

### 6.1 同义词收敛表

同一业务概念在本仓历史上有三套词，现统一为**新代码一律用「规范词」**；
已落库的存量标识（表名、权限码、菜单 code）**保持不变**，避免改授权数据与接口契约。

| 概念 | 规范词（新代码） | 存量标识（保持不动） | 出现位置 |
|---|---|---|---|
| 组织 / 部门 | `dept` | `sys_org` 表、`system:org:*` 权限码、`OrgService`、`OrgController` | `system/org/**` |
| 同上（菜单侧） | — | `menu_code='system:department'`、`route_path='/system/department'`、`DepartmentView.vue` | `V32` L1212、`router/index.js` L62 |
| 同上（DTO 侧） | `dept` | `deptIds` / `primaryDeptId`（已符合规范词） | `CreateUserRequest` |
| 同上（任务侧） | `dept` | `DeptImportTask` / `DeptExportTask`（已符合规范词） | `system/importer|export/application` |
| 公告 / 消息中心 | `announcement` | `menu_code='system:notice'`、`NoticeView.vue`、`/system/notice` | `V32` L1216、前端路由 |
| 接口资源 | `api` | `menu_code='system:api-resource'`、`/system/api-resource` | `V32` L1214 |

**判据**：新增 Java 类 / 字段 / 方法、新增 DTO 字段、新增菜单 `menu_code` 与权限码时，
一律使用规范词；**禁止再造第四套同义词**。存量重命名（如 `sys_org` → `sys_dept`）
属于破坏性变更，须单独立项评估，不在日常收敛范围。

### 6.2 权限码格式（P1）

- 格式：`{模块}:{资源}:{动作}`，全小写，段内允许连字符，至少 3 段（2 个冒号）。
  形如 `system:form-definition:read`；**两段式 `system:user` 是 `menu_code`，不是权限码**。
- 动作词沿用本仓 REST 语义：`read` / `create` / `update` / `delete` / `manage` / `write`。
  若依的 `list` / `query` / `add` / `edit` / `remove` / `export` **不引入**（已声明偏离）。
- **同源铁律**：`sys_menu.permission_code` 必须能在以下任一处找到同字面的权限码：
  1. 任意 Controller 的 `@PreAuthorize("hasAuthority('...')")`；
  2. `sys_api_resource` 种子（历史资源表驱动鉴权，如 `report:usage:read`，见 `V32` L1268–L1270）。
  否则普通角色按菜单授权后进入页面即 403（SUPER_ADMIN 全量放行会掩盖该问题）。
- **门禁**：`python3 scripts/check-permission.py`（`check-all.py` 的 `permission` 硬性步骤，
  行为回归 `scripts/test_check_permission.py` 12 例）。规则只做单向：
  `菜单权限码 - 豁免 ⊆ 接口权限码`；反向不校验（按钮级增删改本就不挂菜单）。
- **豁免**：仅 `system:api-docs:read` 一条 —— `V72__api_docs_menu.sql` 明示预留，
  springdoc `/v3/api-docs` 为纯前端浏览端点、无后端 Controller。
  新增豁免必须在 `check-permission.py` 的 `DEFAULT_EXEMPT` 中**注明出处与理由**，
  禁止无理由全盘豁免。

### 6.3 数据库 / URL / 前端命名口径

| 层 | 本仓规范（固化） | 与若依差异 |
|---|---|---|
| 表名 | 全小写下划线、单数、**必须有模块前缀**（`sys_` / `wf_`）；新表禁止无前缀 | 若依 `sys_*` / `gen_*` / `qrtz_*` |
| 主键 / 审计列 | 统一 `id`、`created_by/created_at`、`updated_by/updated_at` | 若依 `user_id`、`create_time`、`update_by` |
| 逻辑删除 / 状态 / 排序 | `deleted TINYINT`、`status VARCHAR`、`sort_no` | 若依 `del_flag CHAR(1)`、`status CHAR(1)`、`order_num` |
| 菜单字段 | `menu_code` / `route_path` / `component_path` / `permission_code` / `keep_alive` / `menu_type=CATALOG\|MENU` | 若依 `path` / `component` / `perms` / `is_cache` / `M\|C\|F` |
| URL | `/api/system/{复数资源}`（kebab 连字符）；新增端点沿用，不改存量 | 若依 `/system/user/list`（无 `/api`、单数、动作式） |
| 前端 api 文件 | `src/api/*.js`，camelCase（如 `oauth2Token.js`） | 若依 `src/api/system/user.js` |
| 前端视图 / 路由 name | `views/{Module}XxxView.vue`（PascalCase + `View` 后缀）；`name` 小写 camelCase | 若依 `views/system/user/index.vue` + PascalCase `name` |

> 上述差异均为**已声明偏离**，改造成本高于收益（涉及 `api-contract.json`、
> 51 张表、69 个前端 api 文件与全量角色授权）。详见 `docs/RUOYI-ALIGNMENT.md` §3。

**刻意例外（P1-4 复核，2026-11-10）**：5 个「个人域 / 免登录」端点使用顶级 `/api/...`
而非 `/api/system/...`，属**有意偏离**（区分管理域与个人域、避免与管理员面通知模板混淆；
退订端点为邮件 / 短信签名链接，受 `SecurityConfig.BASE_PUBLIC_API` 白名单约束）。
完整清单与不新增前缀别名的四条理由见 `docs/RUOYI-ALIGNMENT.md` §3.2。
新增端点仍以 `/api/system/{复数资源}` 为默认；仅当确属「当前用户自身数据」或
「免登录外部链接」时可用顶级前缀，且必须在 Controller javadoc 写明理由。

