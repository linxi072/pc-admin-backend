# 缺陷修复与优化验证手册

> 适用范围：`pc-admin-backend`（java-admin-framework + pc-admin-vue3-vite）。i18n 引擎与 pc-admin-ui 组件库已移除。
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
python3 scripts/check-all.py            # 预期 check-all EXIT=0，8/8 通过

# 前端：规范 + 单测 + 构建
cd ../pc-admin-vue3-vite
npm run lint                            # 预期 0 errors
npm test                                # 预期 Test Files 10 passed / Tests 73 passed
npm run build                           # 预期 ✓ built in ...s
```

> 门禁 `no-procedure`（禁止数据库端存储过程/函数定义与调用）与 `gate-tests`（门禁脚本单测）已计入上述 8 步。
> 单独复核：`python3 scripts/check-no-procedure.py`、`python3 scripts/tests/test_check_no_procedure.py`。

---

## 3. 分模块验证清单

### 3.1 质量门禁 / 接口契约

**优化目标**：契约扫描覆盖类级 `@RequestMapping` 数组形式、前端拼接 URL 与反引号模板串 `${...}`；
在路径口径之外按 `(method, path)` 比对 `request/requestRaw`、axios sugar 与本地 helper；
`frontend_only`、`method_mismatch` 均归零，排除 `src/api/tests/` 夹具目录避免假 ID 误报。
**验收标准**：`python3 scripts/check-api-contract.py --strict` → `EXIT=0`，输出 `frontend_only 0；method_mismatch 0；duplicate 0`。

```bash
cd java-admin-framework
python3 scripts/check-api-contract.py --strict
# 预期末行：[check-api-contract] EXIT=0
# 预期汇总：后端路由 389 条；前端路径 299 条；方法级调用 353 条；frontend_only 0；method_mismatch 0；duplicate 0
# 参考：backend_only 36 条（修复前 144），剩余为 OAuth2/探针/未接入端点
python3 scripts/check-all.py --strict
# 预期 check-all EXIT=0（契约步升级为阻断同样通过）
python3 scripts/tests/run_all.py
# 预期 33 例门禁脚本单测全过（契约扫描 8 + 存储过程禁用 13 + 前端分页/搜索一致性 8 + 导出一致性 4）
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

## 3.6a 覆盖率第二批（TEST-COVERAGE-PLAN 1.2.0）

**优化目标**：补齐安全核心与公共件的纯规则单测，推进后端测试覆盖。
**验收标准**：新增 37 例（7 测试类）全过；后端累计 114 例全绿；CI 阈值维持起步线不变。
**决策记录**：Q1 选覆盖率第二批；Q2 网关层后续走零依赖自研路线（本批不涉及）；Q3 不改 CI 阈值；Q4 集成测试用纯单测 + 内存 fake（零新依赖）；Q5 不涉及前端。

| 测试类 | 模块 | 锁定的契约（输入输出/边界/异常） |
|---|---|---|
| `TokenVersionRulesTest` | security | claim 缺失默认版本 1；版本 `>=` 判定（人为下调不锁死）；库中版本 null（用户删除）一律失效；TTL 边界含等号 |
| `SessionLimitRulesTest` | security | maxSessions<=0 不限制；达上限按 REJECT/EVICT_OLDEST 策略；淘汰按登录时间升序、同时间按最近使用兜底；不修改入参 |
| `DataScopeResultTest` | security | fail-closed 的 SELF 兜底；ofOrgs 返回不可变副本；空集合降级 ALL；SELF 机构集合为空 |
| `RateLimiterTest` | security | 固定窗口计数与重置；不同 key 独立；并发放行不超阈值（50 线程/max=20）；Deny 恒拒绝（fail-closed） |
| `IdempotencyRecordRulesTest` | common | 容错查询：非法状态/用户 ID 忽略（返回 null）；scope/keyword 截断；时间窗默认值与倒置钳制；null 不过期 |
| `TenantContextTest` | common | 未设置时 requireTenantId 抛异常（严格模式）；嵌套作用域恢复（含异常路径）；旁路需先设上下文；线程隔离 |

### 3.6b 覆盖率第二批收尾（T3 system + T1 剩余）

**验收标准**：新增 24 例（5 测试类）全过；后端累计 138 例全绿；修复 PostRules 中文编码缺陷 1 处。

| 测试类 | 模块 | 锁定的契约（输入输出/边界/异常） |
|---|---|---|
| `MenuTypeRulesTest` | system | 后端全名 CATALOG/MENU/BUTTON 与前端单字母 C/M/B 双字面量兼容；大小写敏感；null/空白拒绝 |
| `PostRulesTest` | system | 编码 trim 归一、中文/字母/数字/_/- 合法、空白与特殊字符拒绝；岗位集合去重保序、上限 10（去重后计数） |
| `FileValidatorTest` | system | 扩展名小写归一与白名单；png/jpg/gif/webp/pdf 魔数校验；不足 4 字节判损坏；未知类型按扩展名放行 |
| `FileDedupRulesTest` | system | SHA-256 摘要（含空内容常量）；64 位十六进制校验；批量 ID 去重保序；refCount≤1 才删物理文件 |
| `SessionCleanupRulesTest` | security | last_used 优先/issued 兜底；无基准保守不回收；禁用阈值不回收；恰好等于超时不回收 |

**缺陷修复**：`PostRules` 正则原仅允许 `[A-Za-z0-9_-]`，与类注释「总经理-001 这类中文编码合法」矛盾——
中文岗位编码被误拒。扩充为 `[\u4e00-\u9fffA-Za-z0-9_-]`，同步更新类/字段注释；测试锁定。

```bash
cd java-admin-framework
mvn -q test    # 预期 138 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0
```

> 注：JaCoCo 插件在离线仓库不可用，`mvn jacoco:report` 会报 `No plugin found for prefix 'jacoco'`；
> 覆盖率数值以 CI（联网环境）的 coverage-gate 为准，本批不改其阈值。

### 3.6c 代码审查专项：缺陷修复与正确性优化

**验收标准**：新增 17 例（4 测试类）全过；后端累计 167 例全绿；质量门禁 7/7 通过。每项修复均由代码证据确认，并配回归单测锁定契约。

| 缺陷 / 优化 | 生产类（模块） | 根因 | 修复后契约（改动前 → 改动后） | 回归测试 |
|---|---|---|---|---|
| 并发重复放行（写放大） | `HeartbeatThrottle`（security） | `get→put` check-then-act 非原子，并发同 key 全部通过检查 | 并发同 key 可多次返回 true → 仅一次 true（compute 原子读-判-写） | `HeartbeatThrottleTest` |
| 整数溢出 / 非法参数 | `ArithmeticRules`（security） | 极端 mulMax 时 `a*b`、`b*q` int 溢出为负；上界≤0 抛 IllegalArgumentException | 负数答案/抛异常 → 上界钳制后答案恒正、算式自洽、非法归一为 1 | `ArithmeticRulesTest` |
| 缓存上限突破 | `ReplayNonceService`（security） | 仅过期时淘汰，全未过期时无界增长 | size 可超 maxSize → 严格 ≤ maxSize（淘汰最早过期项） | `ReplayNonceServiceTest` |
| 窗口口径漂移 / 除零 | `RedisRateLimiter` / `RateLimitWindowRules`（security） | currentCount 写死 60s；windowSeconds≤0 时 `%0` | 观测与实际窗口不一致/抛异常 → 统一规则、记录最近窗口、归一为 1s | `RateLimitWindowRulesTest` |
| 删除数据不一致 | `FileService`（system） | 先物理删后 DB 删，DB 回滚后「记录存在但文件丢失」 | 可能的文件丢失 → 最坏仅残留可清理孤儿文件（先逻辑删后物理删） | （集成语义，代码审查 + 既有测试守护） |
| 入参被改写隐患 | `JsonTruncator`（common） | `copy` 实为入参原引用，dropEmptyFields 就地改写 | 入参节点被修改 → deepCopy 后入参不变 | （既有审计测试守护） |

**改动前后对比要点**：

- 全部改动零新增依赖，方法签名与对外行为保持兼容（仅修复异常/错误路径与并发正确性）。
- 未改项（需业务确认）：`SensitiveMasker` 身份证正则宽松属 fail-safe 有意设计；`CaptchaService.generate` 的 synchronized 为公开端点有意为之的串行化；`FileService.batchDelete` 逐项 DB 调用属「部分成功」语义，改批量会改变契约。

```bash
cd java-admin-framework
mvn -q test    # 预期 167 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，7/7 通过
```

### 3.6d 代码审查专项 II：framework/common/security 边界与并发修复

**验收标准**：新增 9 例（3 测试类）全过；后端累计 176 例全绿；质量门禁 7/7 通过。

| 缺陷 | 生产类（模块） | 根因 | 修复后契约（改动前 → 改动后） | 回归测试 |
|---|---|---|---|---|
| HALF_OPEN 并发多放行 | `CircuitBreaker`（common） | `allowRequest` 在 HALF_OPEN 无条件 true，并发探针全部进入、结果互相覆盖 | 冷却后 n 个请求全部试探 → 仅一个探针放行，其余快速失败 | `CircuitBreakerHalfOpenTest` |
| 锁定时长溢出/空转 | `LockoutRules`（security） | 极大 maxDuration 时 `seconds*2` long 溢出为负，极大失败次数循环空转 | 可能为负时长 → 先钳制上界再安全翻倍、触顶即停、恒非负 | `LockoutRulesTest` |
| 时钟偏差溢出 | `ReplayProtectionRules`（security） | `Math.abs(client-server)` 极端值溢出为负，偏差判断失效 | 极端偏差被错误放行 → 按大小分支用无溢出差值判定 | `ReplayProtectionRulesTest` |

**改动前后对比要点**：

- 三处均为异常路径/并发正确性修复，正常路径行为不变，方法签名与对外契约兼容。
- `CircuitBreaker` 新增 `halfOpenProbeInFlight` 标志，仅在 HALF_OPEN 期间生效，成功/失败/复位三条出口均清除，不影响 CLOSED 无限放行与 OPEN 快速失败语义。
- 经审查确认无问题、本批不改：`StatisticsCache`（降级与计数）、`NPlusOneProbe`（归一化）、`IdempotencyService`（独立事务）、`ImportCenterService`（逐行语义）、`PagingRules`（溢出护栏）、`NotificationQuietHoursRules`（跨午夜窗口）。

```bash
cd java-admin-framework
mvn -q test    # 预期 176 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，7/7 通过
```

### 3.6e 代码审查专项 III：notification/form/可观测修复与盲区补强

**验收标准**：新增 14 例（3 测试类）全过；后端累计 190 例全绿；质量门禁 7/7 通过。

| 缺陷/盲区 | 生产类（模块） | 根因 | 修复后契约（改动前 → 改动后） | 回归测试 |
|---|---|---|---|---|
| 退避时长溢出 | `DeliveryRetryRules`（system） | `baseMs*attempt` 极端配置下 long 溢出为负，被 awaitBackoff 视作 <=0 跳过等待 | 溢出负值致瞬时重试 → 钳到 Long.MAX_VALUE，退避恒非负 | `DeliveryRetryRulesTest` |
| 选项白名单误放行 | `FormSubmissionRules`（system） | `String.valueOf(asText(null))` 把缺失 value 变成字符串 "null" 混入白名单 | 提交字符串 "null" 被误判合法 → 仅为真实 value 建白名单 | `FormSubmissionRulesTest` |
| 测试盲区补强 | `TraceContext`（framework） | framework 模块此前无任何单测，W3C traceparent 解析契约无回归守护 | 无覆盖 → 4 例覆盖合法解析/非法降级/采样位/父子关系 | `TraceContextTest` |

**改动前后对比要点**：

- 两处均为异常/边界路径修复，正常路径行为不变，方法签名与对外契约兼容。
- `TraceContext` 仅新增测试，未改生产代码；确认其解析、全零拒绝、采样位与父子 span 语义符合 W3C 规范。
- 经审查确认无问题、本批不改：`OrderingRules`、`ClaimRules`、`WebhookSignatureRules`、`AlertInboxRules`、
  `MaskStrategyRules`、`SmsRules`/`EmailRules`、`ReportDrillRules`、`ChaosRegistry`、`DataSourceContextHolder`。

```bash
cd java-admin-framework
mvn -q test    # 预期 190 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，7/7 通过
```

### 3.6f 方向 B 零依赖轻量网关前置层（feature-gate 默认关闭）

**功能范围与优先级**（承接 PLATFORM-EVOLUTION-PLAN 方向 B）：

| 优先级 | 功能 | 输入 → 输出 | 边界 / 异常处理 | 集成约定 |
|---|---|---|---|---|
| P0 | B-R1 路由分类 | URI → API/WS/ACTUATOR/OTHER + 是否限流 + actuator 准入 | null/空/查询串/大小写/重复斜杠；白名单空 fail-closed | 纯函数，过滤器复用 |
| P0 | B-R2 限流 | key+max+window → 放行/拒绝 | max≤0 拒绝；窗口≤0 归一 1s；key 空拒绝；并发 CAS | 口径对齐 InMemoryRateLimiter |
| P0 | B-G1 前置过滤器 | 请求 → 透传/403/429 短路 | 默认关闭；限流异常按 failClosed；错误响应统一 JSON | `@Order(HIGHEST+10)`，鉴权前 |
| P1 | B-R3 CORS | origin/method → 预检终结/Allow-Origin | 大小写不敏感/通配/白名单空拒绝 | 纯函数 |
| P1 | B-P1 装配 | `gateway.*` → Bean | 默认关闭，opt-in | `@ConditionalOnProperty` |

**验收标准**：新增 4 测试类 / 18 例全过；后端累计 208 例全绿；质量门禁 7/7 通过。

| 测试类 | 覆盖契约 |
|---|---|
| `GatewayRouteRulesTest` | 四类路由、URI 规范化、限流适用性、actuator 内网准入 fail-closed |
| `GatewayCorsRulesTest` | 预检识别、来源白名单（大小写/通配）、Allow-Origin 回显、非法拒绝 |
| `GatewayRateLimiterTest` | 固定窗口计数/重置、key 隔离、非法输入、非正窗口归一、并发不超上限 |
| `GatewayFilterTest` | actuator 公网拒绝/内网放行、CORS 预检终结、限流 429 短路（无真实容器） |

**关键逻辑与前后对比**：

- **默认零回归**：`gateway.enabled=false`（默认）→ 不注册 `GatewayFilter` Bean，链路与旧版完全一致；此前无网关能力 → 置 true 后启用准入/限流/CORS。
- **限流键口径一致**：注入全局 `ClientIpResolver`，网关限流键 = 后端审计 IP，避免「按 A 限流、按 B 审计」错位。
- **内网准入 fail-closed**：actuator 白名单为空或 IP 为空 → 拒绝；非 actuator 不受限。
- **预检终结**：合法来源的 OPTIONS 由网关直接回 204 并写 CORS 头，不转发业务；非法来源 403。
- **错误响应同形**：`{"code","message","data":null,"traceId"}`，与 `Result` 契约一致；traceId 做转义防注入。
- **无新增 Maven 模块**：落在 `scaffold-framework/config/gateway`，规避 H6 装配唯一性与 14 模块门禁白名单约束；零外部依赖。

**opt-in 配置示例**（写入 `application-prod.yml`，仅在需要网关前置层时开启）：

```yaml
gateway:
  enabled: true
  rate-limit-max: 120          # 单 IP 每窗口最大请求数；<=0 关闭限流
  rate-limit-window-seconds: 60
  fail-closed: true            # 限流器异常时拒绝
  allowed-origins:             # 为空则不处理 CORS
    - https://admin.example.com
  internal-cidrs:              # actuator 内网网段前缀
    - 127.
    - 10.
    - 192.168.
```

**后续（不在本批）**：前端 base URL 相对化、网关部署拓扑文档、多区域路由（方向 C，依赖网关成熟）。

```bash
cd java-admin-framework
mvn -q test    # 预期 208 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，7/7 通过
```

### 3.6g 方向 B4 前端 base URL 相对化

**目标**：消除直连后端 host 配置，API 基础路径统一为同源相对；默认行为零变化（空串），与轻量网关「双挂过渡」兼容。项目位置 `pc-admin-vue3-vite`。

**歧义澄清**：规划 B4 字面写「base URL 改为相对路径 `/api`」，但各 API 模块 url 已自带 `/api` 前缀，实测 `API_BASE='/api'` 会拼出 `/api/api/...` 导致全接口 401；故按「同源相对（空串）+ 显式 env 配置 + 防御护栏」落地，语义与「消除直连 host」一致且不破坏功能。

| 改动 | 文件 | 改动前 → 改动后 | 验证 |
|---|---|---|---|
| 环境配置 | `.env.development` / `.env.production`（新增） | 无 env 文件、base 靠注释约定 → 显式 `VITE_API_BASE=`（空=同源相对），dev 另有 `VITE_BACKEND_TARGET` | 构建读取正常 |
| 相对化护栏 | `src/api/config.js` | 硬编码 `API_BASE=''` → `resolveApiBase(raw)` 纯函数 + env 驱动 `API_BASE` | `config.test.js` 5 例 |
| 代理可配置 | `vite.config.js` | `defineConfig({...})` 代理目标硬编码 8080 → 回调式 `loadEnv` 读取 `VITE_BACKEND_TARGET`，默认仍 8080 | 生产构建成功 |
| 测试 | `src/api/__tests__/config.test.js`（新增） | 无 → 5 例锁定相对化契约 | 前端 8 文件 65 例全过 |

**护栏契约**（`resolveApiBase`）：

- null/undefined/空白/非字符串 → `''`；绝对 URL（`http(s)://`）与协议相对（`//`）→ `''`（禁直连 host）；
- `/api` 及 `/api/`、`/API` → `''`（避免 `/api/api` 双前缀 401）；
- 安全相对子路径（如 `/console`、`/gateway/`）→ 去尾斜杠后保留（支持网关子路径部署）。

**集成约定**：未改任何 API 模块 url（均自带 `/api`）、未改后端、前后端路由契约不变；dev 代理 `VITE_BACKEND_TARGET` 默认 8080，切网关只改 env，前端请求路径不变，实现「直连 ↔ 网关」双挂过渡。

```bash
cd pc-admin-vue3-vite
npm run lint    # 预期 0 errors（历史 warnings 为非本批）
npm test        # 预期 8 文件 / 65 例全过
npm run build   # 预期 built in ~13s，产物完整
cd ../java-admin-framework
python3 scripts/check-all.py   # 预期 EXIT=0，7/7 通过（前端路由未改，api-contract 不受影响）
```

**后续（不在本批）**：网关部署拓扑文档（B5）、多区域路由（方向 C）。

### 3.6h 任务 DAG 规则单测补强

**目标**：为 `scaffold-job`（14 个模块中唯一零测试模块）补上 DAG 核心契约回归，防止环检测/拓扑序错误导致任务死锁或漏触发。

**验收标准**：`JobDagRulesTest` 12 例全过；后端累计 368 例全绿；质量门禁 8/8 通过。

| 契约 | 覆盖点 |
|---|---|
| `parseDependsOn` / `serializeDependsOn` | 去重保序、非数字元素忽略、非法输入返空、空列表序列化为 null、往返一致 |
| `validateDependencies` | 合法/空/null 输入放行、自依赖与不存在依赖抛 `BusinessException`、`existingIds=null` 跳过存在性检查 |
| `hasCycle` / `topologicalOrder` | 直接自环与多跳回边检出、无环放行、`depsOf` 返回 null 安全降级、依赖前置于被依赖任务、集合外依赖忽略 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-job -am test -Dtest=JobDagRulesTest -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 12, Failures: 0, Errors: 0
mvn -o test          # 预期 368 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6i 告警规则单测补强

**目标**：为 `scaffold-monitor`（此前零测试模块）补齐告警口径回归，防止阈值/排序/计数错误误导排障。

**验收标准**：`AlertRuleRulesTest` 11 例 + `AlertInboxRulesTest` 9 例全过；后端累计 388 例全绿；质量门禁 8/8。

| 契约 | 覆盖点 |
|---|---|
| `AlertRuleRules` | 枚举容错解析、六种比较符与边界、null 不触发、全字段校验、WEBHOOK 渠道配置、合成告警状态/实例/缺省值 |
| `AlertInboxRules` | status 白名单 vs severity 自由 label、上限钳制、筛选保序与大小写不敏感、汇总 unknown 桶与稳定排序、留存时间窗口 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-monitor -am test -Dtest='AlertRuleRulesTest,AlertInboxRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 20, Failures: 0, Errors: 0
mvn -o test          # 预期 388 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6j 工作流规则单测补强

**目标**：为 `scaffold-workflow`（此前零测试模块）补齐认领/状态机/审计 ID/监控筛选的回归，防止状态误判与审计唯一键冲突回归。

**验收标准**：4 个测试类共 21 例全过；后端累计 409 例全绿；质量门禁 8/8。

| 测试类 | 覆盖契约 |
|---|---|
| `ClaimRulesTest` | 可认领/已认领/被他人认领/池化四种互斥结果 |
| `WorkflowDefinitionRulesTest` | 挂起/激活转移真值表与重复操作 `CONFLICT` 异常 |
| `WorkflowInstanceAdminRulesTest` | 实例转移真值表、`operationId` 唯一后缀与 64 列宽截断 |
| `WorkflowMonitorRulesTest` | 超时/停留时长非负、催办间隔边界、状态/关键字归一、批量 ID 去重 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-workflow -am test -Dtest='ClaimRulesTest,WorkflowDefinitionRulesTest,WorkflowInstanceAdminRulesTest,WorkflowMonitorRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 21, Failures: 0, Errors: 0
mvn -o test          # 预期 409 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6k 报表下钻规则单测补强

**目标**：为 `scaffold-workbench`（此前零测试模块）补齐下钻谓词与日期解析回归，防止跨表 SQL 或错误筛选。

**验收标准**：`ReportDrillRulesTest` 10 例全过；后端累计 419 例全绿；质量门禁 8/8。

| 契约 | 覆盖点 |
|---|---|
| `dimensionApplies` | 维度 × 报表类型适用性矩阵 |
| `parentCondition` | 空值/不适用维度退化、模块/路径/登录方式/状态的 SQL 与绑定、耗时分桶 |
| `resolveDay` | 完整日期 → 窗口内 MM-dd → 今日的回落 |
| `drillTitle` | 六种维度的标题模板与 null 父级 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-workbench -am test -Dtest=ReportDrillRulesTest -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 10, Failures: 0, Errors: 0
mvn -o test          # 预期 419 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

> 注意：`JOB_EXEC + STATUS` 下钻已修复（见后续批次）——`dimensionApplies` 已放行 JOB_EXEC，
> 测试锁定 `sys_job_log.status` 的 `SUCCESS`/`FAILED` 参数绑定；本批次的历史断言描述已失效。

### 3.6l 实时网关单测补强

**目标**：为 `scaffold-realtime`（此前零测试模块）补齐心跳/慢消费者/背压/令牌单测，防止误断链、误告警或令牌校验绕过。

**验收标准**：4 个测试类共 19 例全过；后端累计 438 例全绿；质量门禁 8/8。

| 测试类 | 覆盖契约 |
|---|---|
| `HeartbeatPolicyTest` | 构造时序约束、ping/空闲/路由 TTL 边界（路由用严格大于） |
| `SlowConsumerPolicyTest` | 队列上限边界、L1 丢弃 vs L2 断链、节点熔断严格高水位 |
| `BackpressureCounterTest` | 七类事件独立计数、不可变快照、并发原子性 |
| `TokenCodecTest` | 签发/解析往返、篡改与错误密钥拒绝、畸形输入、`verify` 仅验签职责 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-realtime -am test -Dtest='HeartbeatPolicyTest,SlowConsumerPolicyTest,BackpressureCounterTest,TokenCodecTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 19, Failures: 0, Errors: 0
mvn -o test          # 预期 438 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6m Kafka 保序与拓扑单测补强

**目标**：为 `scaffold-kafka`（此前零测试模块）锁定 murmur2 位级兼容、版本乱序兜底与分区/扇出拓扑。

**验收标准**：3 个测试类共 18 例全过；后端累计 456 例全绿；质量门禁 8/8。

| 测试类 | 覆盖契约 |
|---|---|
| `OrderingRulesTest` | murmur2 独立实现交叉参考向量、`toPositive`、分区稳定与 UTF-8、版本严格大于才处理 |
| `TopologyRulesTest` | acks 白名单、分区推导公式与向上取整、热点严格大于 2× 均值 |
| `FanoutRouterTest` | 64 分片、统一哈希复用、广播全量/单播归属、null 键回退 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-kafka -am test -Dtest='OrderingRulesTest,TopologyRulesTest,FanoutRouterTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 18, Failures: 0, Errors: 0
mvn -o test          # 预期 456 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6n 站内消息规则层抽取与单测

**目标**：为 `scaffold-im`（此前零测试模块）抽出纯规则层并清零测试盲区，防止会话创建/已读游标口径漂移。

**验收标准**：`ImRulesTest` 10 例全过；后端累计 466 例全绿；质量门禁 8/8。

| 契约 | 覆盖点 |
|---|---|
| `validateCreateConversation` | 类型白名单、空成员、P2P 人数、创建者本人入会 |
| `resolveMemberIds` | 创建者置首、按入参顺序去重 |
| `normalizeMsgType` / `preview` | 空值回退 TEXT、超长截断省略号与边界 |
| `shouldAdvanceReadCursor` | 无消息/重复/回退均跳过，仅向前推进 |
| `compareLastMessageAt` | 最近消息倒序、无消息排末尾 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-im -am test -Dtest=ImRulesTest -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 10, Failures: 0, Errors: 0
mvn -o test          # 预期 466 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6o 装配模块单测补强（14 模块全覆盖）

**目标**：为最后一个零测试模块 `scaffold-admin` 补齐时区守卫与 JSON 日志编码回归。

**验收标准**：2 个测试类共 11 例全过；后端累计 477 例全绿；质量门禁 8/8。

| 测试类 | 覆盖契约 |
|---|---|
| `DataSourceTimeZoneGuardTest` | 时区参数解析、偏移换算、fail-fast 与只告警分支、URL 回落 |
| `JsonLogEncoderTest` | 标准字段、MDC 透出与冲突保护、异常扁平化、单行换行 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-admin -am test -Dtest='DataSourceTimeZoneGuardTest,JsonLogEncoderTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 11, Failures: 0, Errors: 0
mvn -o test          # 预期 477 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，8/8 通过
```

### 3.6p 开放平台令牌自测错误提示

**目标**：令牌签发失败（如密钥错误返回 401 `invalid_client`）必须在页面显式提示，不得静默。

**验收标准**：`openApiClient.test.js` 4 例全过；前端累计 122 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `issueClientToken` | form-urlencoded 请求体、`skipAuthErrorHandling: true`（不跳登录） |
| `resolveOAuthErrorMessage` | `error_description → message → error → Error.message → 兜底` 优先级 |

```bash
cd pc-admin-vue3-vite
npm test -- --run
# 预期 Test Files 19 passed / Tests 122 passed
```

### 3.6q 前端 CSV 公式注入防护

**目标**：前端「导出当前页」与后端 CSV 导出采用同一套公式注入中和口径（CWE-1236）。

**验收标准**：`csv.test.js` 8 例全过；前端累计 130 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `neutralizeFormula` | `= + - @` Tab/CR 六类危险首字符、数值例外（`-5`/`+3.14`）、仅判定首字符 |
| `escapeCsvField` | 先中和后 RFC 4180 加引号、CR 前缀的引号包裹 |
| `toCsv` / BOM | 逐单元格防护与 UTF-8 BOM |

```bash
cd pc-admin-vue3-vite
npm test -- --run
# 预期 Test Files 20 passed / Tests 130 passed
```

### 3.6r 报表导出失败状态隔离

**目标**：导出失败只提示，不得写入页面主错误状态而抹掉已加载的报表正文。

**验收标准**：导出一致性静态测试 4 例全过；门禁单测累计 33 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `ReportPanel.download()` | 四类失败分支改用 `ElMessage.error`，保留 `error.value` 给主加载流程 |
| 静态检测 | `download`/`onExport*`/`export*` 函数体禁止 `error.value =`；`load()` 不受限 |

```bash
cd java-admin-framework
python3 scripts/tests/test_frontend_export_consistency.py
# 预期 Ran 4 tests ... OK
python3 scripts/check-all.py   # 预期 EXIT=0，9/9 通过
```

### 3.6s 游标分页页栈回退再前进

**目标**：深翻页回看后必须能再次前进，回看重载失败也不得让「下一页」永久禁用。

**验收标准**：`keyset.test.js` 10 例全过；前端累计 140 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `canGoNext` / `goNext` | `nextCursor` 优先，否则用 `cursors[index+1]` 保留游标；无游标原样返回 |
| `goPrev` / `canGoPrev` | 只回退下标、保留更深层游标 |
| 其余纯函数 | OFFSET 软阈值、护栏错误识别、非法栈归一、页码 |

```bash
cd pc-admin-vue3-vite
npm test -- --run
# 预期 Test Files 21 passed / Tests 140 passed
```

### 3.6t 健康探针错误文案

**目标**：i18n 引擎已移除，健康探针失败必须返回可直接展示的中文诊断，不得泄漏 i18n key。

**验收标准**：`health.test.js` 4 例全过；前端累计 144 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `fetchProbe` | `/livez`、`/readyz` 路径，`validateStatus` 全放行（503 DOWN 合法） |
| 失败文案 | 格式异常含 HTTP 状态码；网络失败含原始错误；两者都不以 `health.` 开头 |

```bash
cd pc-admin-vue3-vite
npm test -- --run
# 预期 Test Files 22 passed / Tests 144 passed
```

### 3.6u 生产外部密钥 fail-fast 守卫

**目标**：生产环境漏配 `JWT_SECRET` / `APP_OAUTH2_PRIVATE_KEY` 时不得「正常」启动并使用临时密钥。

**验收标准**：`ProductionSecretsGuardTest` 8 例全过；后端累计 485 例全绿；前端与门禁不受影响。

| 契约 | 覆盖点 |
|---|---|
| `ProductionSecretsGuard` | `@Profile("prod")`、`ApplicationRunner`、默认启用、逃生开关 |
| 缺失校验 | JWT 单项缺失、OAuth2 单项缺失、双缺失、空白字符串视作缺失 |
| 错误信息 | 只列环境变量名/配置键与关闭开关，不输出密钥内容 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-admin -am test -Dtest='ProductionSecretsGuardTest,DataSourceTimeZoneGuardTest,JsonLogEncoderTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 19, Failures: 0, Errors: 0
mvn -o test          # 预期 485 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0，门禁全过
```

### 3.6v 前端下载通道收敛与门禁补强

**目标**：视图/组件不得手写 Blob 下载链路，空内容、JSON 业务错误与文件名解析统一由 `utils/download` 负责。

**验收标准**：`download.test.js` 10 例全过；前端累计 154 例、门禁累计 37 例、后端 485 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `parseFileName` | `filename*` 优先并解码、普通 `filename`、非法编码与回退名 |
| `saveBlob` | 创建临时链接、点击、`removeChild`、`revokeObjectURL` 生命周期 |
| `saveBlobFromResponse` | 空响应、空 Blob、JSON 错误 message、非 JSON 兜底、正常文件、非 Blob 包装 |
| 导出门禁 | `onDownload*`/箭头函数识别；视图/组件禁止直接 `URL.createObjectURL` |

```bash
cd pc-admin-vue3-vite
npm test -- --run src/utils/tests/download.test.js
# 预期 Test Files 1 passed / Tests 10 passed
npm test -- --run
# 预期 Test Files 23 passed / Tests 154 passed
npm run build
cd ../java-admin-framework
python3 scripts/tests/test_frontend_export_consistency.py
# 预期 Ran 8 tests / OK
python3 scripts/check-all.py   # 预期 EXIT=0，gate-tests 37/37
```

### 3.6w 操作日志游标降级摘要复用

**目标**：OFFSET 分页触发游标降级时不得重复请求统计摘要，也不得丢弃首屏已成功的摘要结果。

**验收标准**：`auditLogSummary.test.js` 4 例全过；前端累计 158 例、后端 485 例、门禁 37 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `settlePageWithSummary` 正常路径 | 分页与摘要并行结算，两者均返回 fulfilled |
| 成功预取复用 | 降级时不再调用 `fetchSummary`，直接返回首屏已结算结果 |
| 失败预取重试 | 预取 rejected 时在游标模式重试一次并采用新结果 |
| 分页失败隔离 | 分页 rejected 不阻断摘要结算，错误对象原样返回 |

```bash
cd pc-admin-vue3-vite
npm test -- --run src/utils/tests/auditLogSummary.test.js
# 预期 Test Files 1 passed / Tests 4 passed
npm test -- --run
# 预期 Test Files 24 passed / Tests 158 passed
npm run build
```

### 3.6x API 契约门禁方法级比对

**目标**：前后端契约校验不得因路径相同而忽略 HTTP 方法漂移；`--strict` 必须能拦截 method_mismatch。

**验收标准**：`test_check_api_contract.py` 14 例全过；门禁累计 43 例全绿；真实仓库
`frontend_only 0`、`method_mismatch 0`。

| 契约 | 覆盖点 |
|---|---|
| `frontend_calls_from_text` | `request/requestRaw` 对象字面量、跨行与嵌套花括号、模板串、尾斜杠拼接 |
| 方法来源 | axios sugar（`.post(...)`）、本地 helper（函数体含 method、调用点传 url） |
| 忽略项 | 注释 URL、缺少 method 的调用不进入方法级判定 |
| 真实仓库 | 方法级调用 353 条、未识别方法 0 条、方法级差异为 0 |

```bash
cd java-admin-framework
python3 scripts/tests/test_check_api_contract.py
# 预期 Ran 14 tests / OK
python3 scripts/check-api-contract.py --strict
# 预期 method_mismatch 0；EXIT=0
python3 scripts/check-all.py --strict   # 预期 EXIT=0
python3 scripts/tests/run_all.py        # 预期 Ran 43 tests / OK
```

### 3.6y 企业微信机器人签名 URL 编码

**目标**：标准 base64 签名中的 `+ / =` 必须先 URL 编码再拼入 query，避免接收方把裸 `+` 解为空格导致偶发验签失败。

**验收标准**：`RobotSigningRulesTest` 5 例全过；后端累计 490 例、前端 158 例、门禁 43 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `sign` | 固定 timestamp/secret 产出原始 base64（含 `+ / =`），HMAC 原语不变 |
| `signWecom` / `signDingtalk` | 两平台均返回 URL 编码签名，编码结果一致 |
| `buildWecomUrl` | query 仅含 `%2B/%2F/%3D`，不出现裸 base64；无 query 基址用 `?` 拼接 |
| 参数边界 | 空/空白 secret 被拒绝 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-system -am test -Dtest='RobotSigningRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 5, Failures: 0, Errors: 0
mvn -o test          # 预期 490 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6z OAuth2 redirect_uri IPv6 回环支持

**目标**：注释声明允许的 `http://[::1]` 必须通过校验，同时不得因括号解析放宽 userinfo 或非回环主机。

**验收标准**：`OpenApiClientRulesTest` 4 例全过；后端累计 494 例、前端 158 例、门禁 43 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 合法 URI | https 任意主机；http 的 localhost / 127.0.0.1 / `[::1]`（含端口与路径） |
| 拒绝项 | 非回环 http、`[::1]@evil.com`、畸形 IPv6、非法端口、`https://` |
| 错误文案 | 允许形态提示包含 `[::1]` |
| 白名单匹配 | 非空白名单精确匹配；空白名单保持向后兼容 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-common -am test -Dtest='OpenApiClientRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 4, Failures: 0, Errors: 0
mvn -o test          # 预期 494 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6aa 通知渠道归一锁定 Locale.ROOT

**目标**：渠道名大小写归一不得受 JVM 默认 Locale 影响，避免 `tr-TR` 下 `in_app` / `dingtalk` 静默筛选失败。

**验收标准**：`ChannelNormalizeRulesTest` 3 例全过；后端累计 497 例、前端 158 例、门禁 43 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 全渠道归一 | 所有 `Channel` 枚举名以 ROOT Locale 小写后均可还原 |
| Locale 隔离 | 默认 `tr-TR` 时 `in_app` / `dingtalk` 仍归一为枚举名 |
| 边界值 | null、空白、未知值返回 null |

```bash
cd java-admin-framework
mvn -o -pl scaffold-common -am test -Dtest='ChannelNormalizeRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 3, Failures: 0, Errors: 0
mvn -o test          # 预期 497 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ab 渐进式锁定到期放行

**目标**：带 `lockedUntil` 的临时锁到期后账号可重新登录，不得因残留 `status=LOCKED` 永久锁定。

**验收标准**：`LockoutRulesTest` + `AuthServiceLockoutTest` 9 例全过；后端累计 502 例、前端 158 例、门禁 43 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `LockoutRules.isLocked` | 临时锁未到期/到期/边界；人工锁无到期时间；ACTIVE + 未来到期 |
| `AuthService.isLocked` | 登录/改密/忘记密码统一调用到期感知判定 |
| `resetLoginFailure` | 成功登录两个分支均把 `status` 归一为 `ACTIVE` 并清空计数/到期时间 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-security -am test -Dtest='LockoutRulesTest,AuthServiceLockoutTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 9, Failures: 0, Errors: 0
mvn -o test          # 预期 502 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ac WebP 上传魔数校验补齐 fourcc

**目标**：webp 扩展名上传必须校验 `RIFF....WEBP`，不得让 WAV/AVI 等 RIFF 容器改扩展名绕过。

**验收标准**：`FileValidatorTest` 4 例、`test_file_upload_security.py` 3 例全过；后端 502 例、前端 158 例、门禁 46 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `FileValidator.verifyContent` | 合法 `RIFFxxxxWEBP` 放行；`RIFFxxxxWAVE` 与 11 字节截断头拒绝 |
| `ProfileController.validateImageContent` | 头像上传同步校验偏移 8~11 与读取长度 |
| 静态门禁 | 生产 Java 源码禁止 RIFF-only WebP 判定 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-system -am test -Dtest='FileValidatorTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 4, Failures: 0, Errors: 0
python3 scripts/tests/test_file_upload_security.py
# 预期 Ran 3 tests / OK
python3 scripts/check-all.py --strict   # 预期 EXIT=0，gate-tests 46/46
```

### 3.6ad OAuth2 授权码并发兑换防双花

**目标**：同一授权码的「检查 → 校验 → 标记已使用」必须原子化，并发兑换只能成功一次；非法请求不得消耗授权码。

**验收标准**：`OAuth2AuthorizationCodeServiceTest` 3 例全过；后端累计 505 例、前端 158 例、门禁 46 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 顺序重放 | 同一 code 首次兑换成功，第二次抛出 INVALID_GRANT |
| 并发兑换 | 4 线程栅栏同步兑换，成功数恰为 1 |
| 非法请求 | client_id 不匹配抛错且不消耗，原 code 仍可正常兑换 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-security -am test -Dtest='OAuth2AuthorizationCodeServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 3, Failures: 0, Errors: 0
mvn -o test          # 预期 505 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ae 规则层枚举归一锁定 Locale.ROOT

**目标**：规则层所有枚举/状态码大小写归一不受 JVM 默认 Locale 影响，`tr-TR` 下合法值仍可解析。

**验收标准**：新增 7 例 tr-TR 回归测试与 3 例静态门禁全过；后端累计 512 例、前端 158 例、门禁 49 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 告警规则 | `INFO` / `CRITICAL` / `DISK_USAGE` / `ONLINE_USERS` / `INBOX` 解析；`FIRING` 状态与 severity 归一 |
| 幂等与内容 | `PROCESSING`、发布态 `PUBLISHED` / `OFFLINE` 归一 |
| 日志与租户 | `SKIPPED` / `DIGEST_PENDING` / `SUCCESS`；租户编码 `TENANT_ADMIN_INVY` |
| 工作流 | `RATIO` / `INITIATOR` / `PREVIOUS` 审批配置在 tr-TR 下校验通过 |
| 静态门禁 | 规则包禁止无参 `toUpperCase()` / `toLowerCase()` |

```bash
cd java-admin-framework
mvn -o -pl scaffold-common,scaffold-monitor,scaffold-cms,scaffold-system,scaffold-workflow -am test -Dtest='RuleLocaleTest,IdempotencyLocaleTest,CmsStatusRulesLocaleTest,LogAndTenantLocaleTest,WorkflowDesignValidatorLocaleTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 BUILD SUCCESS
python3 scripts/tests/test_rule_locale_security.py
# 预期 Ran 3 tests / OK
mvn -o test          # 预期 512 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0，gate-tests 49/49
```

### 3.6af 审计摘要凭证别名与银行卡脱敏

**目标**：审计落库/查询共用的 `maskSummary()` 必须抹除常见凭证别名与银行卡号，避免明文泄漏。

**验收标准**：`SensitiveMaskerTest` 15 例全过；后端累计 515 例、前端 158 例、门禁 49 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 凭证键别名 | apiKey / api_key / x-api-key / accessKey / secretKey / idToken / sessionId / cookie / previewToken |
| 银行卡 | 16 位卡号保留后 4 位、等长星号；重复调用幂等 |
| 顺序安全 | 已掩码的 18 位身份证不会被银行卡规则二次处理 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-common -am test -Dtest='SensitiveMaskerTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 15, Failures: 0, Errors: 0
mvn -o test          # 预期 515 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ag 生产代码大小写归一锁定 Locale.ROOT

**目标**：全部生产 Java 源码中的标识符/枚举/状态码归一不受 JVM 默认 Locale 影响，门禁不留规则包盲区。

**验收标准**：新增 4 例 tr-TR 回归测试与 4 例静态门禁全过；后端累计 519 例、前端 158 例、门禁 50 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 任务与报表 | `minutes` / `simple` 解析；`api_call` 不回落 `USAGE` |
| 配置与口令 | `inactive` 判定为停用；`ADMIN123` 仍命中弱口令 |
| 静态门禁 | 扫描所有 `src/main/java`，排除 `src/test`；仅允许带 `Locale.ROOT` 的大小写转换 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-job,scaffold-workbench,scaffold-system,scaffold-security -am test -Dtest='JobLocaleTest,ReportExportTaskLocaleTest,SystemConfigPortLocaleTest,PasswordPolicyLocaleTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 BUILD SUCCESS
python3 scripts/tests/test_rule_locale_security.py
# 预期 Ran 4 tests / OK
mvn -o test          # 预期 519 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0，gate-tests 50/50
```

### 3.6ah 列权限脱敏 fail-open 修复

**目标**：开启列级权限后，任何未声明敏感类型、字段读写失败或 record 重建失败都不得返回受控字段明文。

**验收标准**：`ColumnPermissionApplierTest` 3 例全过；后端累计 522 例、前端 158 例、门禁 50 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `maskValue` | null / NONE 走 `genericMask`，两条脱敏路径口径一致 |
| record 副本 | 按组件类型定位规范构造器，原 record 不变、副本已脱敏 |
| 失败安全 | 紧凑构造器拒绝掩码值时抛 `IllegalStateException`，不回落原对象 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-security -am test -Dtest='ColumnPermissionApplierTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 3, Failures: 0, Errors: 0
mvn -o test          # 预期 522 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ai 脱敏策略中心 fail-open 与租户默认策略

**目标**：判定要脱敏就必须产出掩码；租户 0 的平台默认策略对业务租户生效，租户同优先级策略可覆盖。

**验收标准**：策略中心 9 例全过；后端累计 531 例、前端 158 例、门禁 50 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 策略命中 | 平台默认继承；租户同优先级覆盖；更高优先级仍胜出 |
| fail-closed | 非法/空类型 + fallbackType；非法/空类型 + NONE 走通用掩码 |
| 保持不变 | 角色豁免分支仍返回明文 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-system -am test -Dtest='MaskStrategyRulesTest,MaskStrategyResolverTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 9, Failures: 0, Errors: 0
mvn -o test          # 预期 531 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6aj 权限门禁仅从生产源码采集权限码

**目标**：P1 菜单权限码一致性门禁不得把 `src/test` 夹具中的 `hasAuthority` 当作生产接口实现。

**验收标准**：`test_check_permission.py` 3 例全过；门禁累计 53 例、后端 531 例、前端 158 例全绿。

| 契约 | 覆盖点 |
|---|---|
| `permission_codes_from_text` | 单双引号 `hasAuthority('...')` 提取 |
| `is_production_java` | 仅 `src/main/java` 放行；`src/test` 与 `src/main/resources` 拒绝 |
| 真实仓库扫描 | 生产源码提取结果非空 |

```bash
cd java-admin-framework
python3 scripts/tests/test_check_permission.py
# 预期 Ran 3 tests / OK
python3 scripts/check-permission.py
# 预期 EXIT=0
python3 scripts/check-all.py --strict   # 预期 EXIT=0，gate-tests 53/53
```

### 3.6ak 告警 Webhook 令牌恒定时间比较

**目标**：匿名可达的 webhook 端点不得用 `String.equals` 比较共享令牌，统一走 `ConstantTime.equals`。

**验收标准**：`AlertWebhookControllerTest` 4 例、`test_token_comparison.py` 3 例全过；后端 535 例、前端 158 例、门禁 56 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 鉴权结果 | 匹配 202；错误/空令牌 401 且服务不被调用；空配置 503 |
| 比较原语 | `ConstantTime.equals(expected, token)`，无 `String.equals(token)` 短路比较 |
| 静态门禁 | 生产 Java 禁止直接比较 token/secret/signature |

```bash
cd java-admin-framework
mvn -o -pl scaffold-monitor -am test -Dtest='AlertWebhookControllerTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 4, Failures: 0, Errors: 0
python3 scripts/tests/test_token_comparison.py
# 预期 Ran 3 tests / OK
mvn -o test          # 预期 535 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0，gate-tests 56/56
```

### 3.6al XLSX 导出净化 XML 非法控制字符

**目标**：任意导出字段含 C0 控制字符时仍生成可被 Excel/WPS 打开的工作簿，合法文本与实体转义不丢失。

**验收标准**：`XlsxDataExporterTest` 2 例全过；后端累计 537 例、前端 158 例、门禁 56 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 单表导出 | `\u0001/\u000B/\u001F` 被剔除；`& < > "`、中文与合法空白保留 |
| 多 Sheet 导出 | 每个 sheet XML part 均可被 `DocumentBuilder` 解析 |
| XML part | ZIP 内全部 `.xml` / `.rels` 无 `SAXParseException` |

```bash
cd java-admin-framework
mvn -o -pl scaffold-common -am test -Dtest='XlsxDataExporterTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 2, Failures: 0, Errors: 0
mvn -o test          # 预期 537 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6am 告警 severity 筛选与汇总口径统一

**目标**：同一 severity label 在汇总里可见时，使用展示值筛选必须命中，不再出现「汇总可见、筛选为空」。

**验收标准**：`AlertInboxRulesTest` 新增 2 例全过；后端累计 539 例、前端 158 例、门禁 56 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 首尾空格 | 条目 `"  CRITICAL  "` 用 `critical` 筛选命中，汇总桶键为 `critical` |
| 超长值 | 50 字符自定义 severity 与 32 字符截断桶键筛选/汇总一致 |
| 兼容 | 标准短 severity 与空值 `unknown` 桶行为不变 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-monitor -am test -Dtest='AlertInboxRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 11, Failures: 0, Errors: 0
mvn -o test          # 预期 539 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6an OAuth2 password 授权补齐锁定到期语义

**目标**：临时锁到期后，`grant_type=password` 与交互式登录口径一致，不再要求用户先做一次前端登录解锁。

**验收标准**：`OAuth2UserAuthenticatorTest` 4 例全过；后端累计 543 例、前端 158 例、门禁 56 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 临时锁 | `LOCKED` + 过期 `lockedUntil` 放行 |
| 长期锁 | `LOCKED` + `lockedUntil=null` 拒绝 |
| 其他状态 | 未来临时锁拒绝；`DISABLED` 拒绝 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-security -am test -Dtest='OAuth2UserAuthenticatorTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 4, Failures: 0, Errors: 0
mvn -o test          # 预期 543 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ao OAuth2 自省端点畸形 Basic 头返回 401

**目标**：公开自省端点遇到畸形 Basic Base64 头时不再穿透为 500，与调用方未认证统一返回 401 `invalid_client`。

**验收标准**：`OAuth2IntrospectionEndpointTest` 9 例全过；后端累计 552 例、前端 158 例、门禁 56 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 畸形 Basic | `Basic !!!` 返回 401 `invalid_client`，不触达 JWT 解码器与客户端端口 |
| 凭证形态 | 无冒号 Basic、错误 Basic 凭证、缺失 Authorization 均返回 401 |
| 合法调用方 | 合法 Basic / Bearer 可继续自省；非法 Bearer 返回 401 |
| 自省语义 | 调用方合法但 token 为空返回 400 `invalid_request`；token 无效仍返回 200 + `active=false` |

```bash
cd java-admin-framework
mvn -o -pl scaffold-security -am test -Dtest='OAuth2IntrospectionEndpointTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 Tests run: 9, Failures: 0, Errors: 0
mvn -o test          # 预期 552 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

### 3.6ap 租户请求头覆盖收敛为平台管理员专用

**目标**：普通租户用户即使伪造 `X-Tenant-Id`，执行作用域仍以已签名 Token Claim 为准，避免跨租户越权读写。

**验收标准**：`TenantResolverTest` 5 例与 `TenantInterceptorTest` 3 例全过；后端累计 560 例、前端 158 例、门禁 56 例全绿。

| 契约 | 覆盖点 |
|---|---|
| 普通用户 | 请求头覆盖被忽略，使用 Claim `tid=2`；非法覆盖尝试记录告警且不输出头值 |
| 平台管理员 | 请求头可覆盖 Claim，租户上下文同时保留 `platformAdmin=true` |
| 回落语义 | 非法头回落 Claim；隔离关闭回落默认租户；匿名请求回落默认租户 |
| 兼容 | `resolveTenantIdFromClaims` 仍按配置 Claim 名解析，异步任务调用方式不变 |

```bash
cd java-admin-framework
mvn -o -pl scaffold-common,scaffold-framework -am test -Dtest='TenantResolverTest,TenantInterceptorTest' -Dsurefire.failIfNoSpecifiedTests=false
# 预期 TenantResolverTest 5 例、TenantInterceptorTest 3 例，Failures: 0, Errors: 0
mvn -o test          # 预期 560 例全过，BUILD SUCCESS
python3 scripts/check-all.py --strict   # 预期 EXIT=0
```

---

## 3.7 组件库（已移除）

`@acme/admin-ui` 组件库（`pc-admin-ui/` 独立包）与 i18n 引擎已移除。所有视图组件由主应用
`pc-admin-vue3-vite/src/components/` 直接提供（如 `BaseChart.vue`），文案统一内联中文，
不再有 `file:` 依赖、Vite alias、CI ui-lib job 或 `lang` 请求参数。相关验证以主应用
`npm test` / `npm run build` 为准（见 §2）。

---

## 4. 兼容性保证

- **对外接口**：所有优化均未新增/删除/重命名 HTTP 端点与请求/响应字段；`authorizeNavigation` 为内部抽取，不改变守卫行为。
- **配置**：新增 `auth.captcha.max-pending` 有默认值，不配置时行为与优化前一致。
- **方法签名**：`CaptchaService.generate/verify/pendingCount` 签名不变；仅将 `generate` 标记为 `synchronized`（线程安全增强，调用方无感）。
- **第三轮优化**：`CursorCodec` / `IdempotencyRules` / 前端 `result.js` 三轮均为**纯测试新增**，未改动任何生产代码，行为与接口 100% 不变。
- **组件库（V1.2 方向 A，已移除）**：该轮曾以独立包形式提供 9 个组件；后续已整体移除，组件由主应用 `src/components/` 直接提供（详见 §3.7）。

---

## 5. 历史可追溯性

- 本轮所有修复与优化按功能模块拆分为独立提交，提交信息采用 `fix|test|refactor|docs(模块): 中文摘要` 格式。
- 每个提交附带多行 body 说明具体改动点，可用 `git log --format='%h %s%n%b'` 查看完整脉络。
- 建议按 `git log --oneline` 的提交序列逐批回滚定位问题（每个提交均可独立 `git show` 审阅）。
