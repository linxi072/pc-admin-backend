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
`frontend_only` 归零，排除 `src/api/tests/` 夹具目录避免假 ID 误报。
**验收标准**：`python3 scripts/check-api-contract.py --strict` → `EXIT=0`，输出 `frontend_only 0；duplicate 0`。

```bash
cd java-admin-framework
python3 scripts/check-api-contract.py --strict
# 预期末行：[check-api-contract] EXIT=0
# 预期汇总：后端路由 389 条；前端引用 299 条；frontend_only 0；duplicate 0
# 参考：backend_only 36 条（修复前 144），剩余为 OAuth2/探针/未接入端点
python3 scripts/check-all.py --strict
# 预期 check-all EXIT=0（契约步升级为阻断同样通过）
python3 scripts/tests/run_all.py
# 预期 21 例门禁脚本单测全过（契约扫描 8 例 + 存储过程禁用 13 例）
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
