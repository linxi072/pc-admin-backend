# 缺陷修复与优化验证手册

> 适用范围：`pc-admin-backend`（java-admin-framework + pc-admin-vue3-vite）。
> 目的：为每轮缺陷修复与后续优化提供**可执行的验证步骤**与**预期结果**，确保行为兼容、回归可追溯。

---

## 1. 验证环境前置

| 项 | 命令 | 预期 |
|---|---|---|
| Java | `java -version` | JDK 17 |
| Maven | `mvn -version` | 3.x（离线需本地仓库已缓存依赖；本项目使用 Spring Boot 3.3.4 parent） |
| Node | `node -v && npm -v` | Node 18+ / npm 9+ |
| Python | `python3 --version` | 3.10+（门禁脚本零第三方依赖） |

后端 Maven 可执行文件若不在 PATH，可使用本地安装：`/Users/athena/Documents/localRepository/maven/bin/mvn`（按实际环境替换）。

---

## 2. 快速全量回归（推荐每次提交前执行）

```bash
# 后端：编译 + 测试 + 静态门禁
cd java-admin-framework
mvn -q clean test                       # 预期 BUILD SUCCESS，Tests run 全部 Failures=0 Errors=0
python3 scripts/check-all.py            # 预期 check-all EXIT=0，7/7 通过

# 前端：规范 + 单测 + 构建
cd ../pc-admin-vue3-vite
npm run lint                            # 预期 0 errors
npm test                                # 预期 Test Files 7 passed / Tests 60 passed
npm run build                           # 预期 ✓ built in ...s
```

---

## 3. 分模块验证清单

### 3.1 质量门禁 / 接口契约

**优化目标**：契约扫描覆盖类级 `@RequestMapping` 数组形式、前端拼接 URL；`frontend_only` 归零。
**验收标准**：`python3 scripts/check-api-contract.py --strict` → `EXIT=0`，输出 `frontend_only 0；duplicate 0`。

```bash
cd java-admin-framework
python3 scripts/check-api-contract.py --strict
# 预期末行：[check-api-contract] EXIT=0
# 预期汇总：后端路由 370 条；前端引用 184 条；frontend_only 0；duplicate 0
python3 scripts/check-all.py --strict
# 预期 check-all EXIT=0（契约步升级为阻断同样通过）
```

### 3.2 安全模块（JWT 租户 / 验证码 / 防重放 / 登录锁定）

**优化目标**：验证码容量上限配置化、并发容量控制原子化；补充离线单测。
**验收标准**：CaptchaServiceTest 5 例全过（含并发容量用例）；对外方法签名不变。

```bash
cd java-admin-framework
mvn -q -pl scaffold-security -am test
# 预期 CaptchaServiceTest、PasswordPolicyTest 全部通过
```

配置项：`auth.captcha.max-pending`（默认 1024），小于 1 时按 1 处理。

### 3.3 公共模块（幂等 / 游标分页）

**验收标准**：幂等过期记录在读路径被兜底清理；非法游标返回 400 而非 500。

```bash
cd java-admin-framework
mvn -q -pl scaffold-common -am test
# 预期 ResultTest / CircuitBreakerTest 全部通过
```

### 3.4 业务模块（任务日志 / 工作流委派 / 角色权限）

**验收标准**：多租户下任务日志非空（租户条件单一来源）；委派办结后任务回到原审批人；角色权限变更后持有者旧令牌即时失效。

```bash
cd java-admin-framework
mvn -q test       # 全模块回归
python3 scripts/check-permission.py   # 预期 EXIT=0（菜单权限码 ⊆ 接口权限码）
```

### 3.5 前端核心（401 / 路由鉴权 / 实时通道）

**优化目标**：路由鉴权决策抽取为纯函数，补 13 例单测防 fail-open 回归。
**验收标准**：`authGuard.test.js` 13 例全过。

```bash
cd pc-admin-vue3-vite
npm test    # 预期 Test Files 7 passed / Tests 60 passed
npm run build
```

### 3.6 第三轮优化：游标 / 幂等规则 / Result 拆包（纯函数测试补强）

**优化目标**：为三个高频核心纯函数补齐契约测试，锁定行为防回归。
**验收标准**：后端 common 模块 45 例全过（新增 CursorCodecTest 7 例 + IdempotencyRulesTest 7 例）；前端 60 例全过（新增 result.test.js 7 例）。

| 模块 | 测试锁定点 | 行为兼容性 |
|---|---|---|
| `CursorCodec` | 空列表编码、多值往返、null 空段、非法 base64 降级首页、URL 安全字符集 | 未改 `encode/decode` 实现，纯测试 |
| `IdempotencyRules` | 键长度边界、URL 安全字符、作用域优先级、SHA-256 指纹敏感性、状态机 PROCEED/REPLAY/CONFLICT/MISMATCH | 未改规则实现，纯测试；注释说明「指纹算法变更会破坏历史记录，需单独评估」 |
| 前端 `result.js` | 成功码 `"0"` 与 `0` 双兼容、`ResultError` 携带 code/data、失败默认文案 | 未改拆包实现，纯测试 |

```bash
cd java-admin-framework
mvn -q -pl scaffold-common -am test    # 预期 CursorCodecTest / IdempotencyRulesTest 全过
cd ../pc-admin-vue3-vite
npm test                                 # 预期 Test Files 7 passed / Tests 60 passed
```

---

## 4. 兼容性保证

- **对外接口**：所有优化均未新增/删除/重命名 HTTP 端点与请求/响应字段；`authorizeNavigation` 为内部抽取，不改变守卫行为。
- **配置**：新增 `auth.captcha.max-pending` 有默认值，不配置时行为与优化前一致。
- **方法签名**：`CaptchaService.generate/verify/pendingCount` 签名不变；仅将 `generate` 标记为 `synchronized`（线程安全增强，调用方无感）。
- **第三轮优化**：`CursorCodec` / `IdempotencyRules` / 前端 `result.js` 三轮均为**纯测试新增**，未改动任何生产代码，行为与接口 100% 不变。

---

## 5. 历史可追溯性

- 本轮所有修复与优化按功能模块拆分为独立提交，提交信息采用 `fix|test|refactor|docs(模块): 中文摘要` 格式。
- 每个提交附带多行 body 说明具体改动点，可用 `git log --format='%h %s%n%b'` 查看完整脉络。
- 建议按 `git log --oneline` 的提交序列逐批回滚定位问题（每个提交均可独立 `git show` 审阅）。
