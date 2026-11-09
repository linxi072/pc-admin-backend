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
