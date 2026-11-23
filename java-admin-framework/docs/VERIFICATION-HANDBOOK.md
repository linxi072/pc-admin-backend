# 缺陷修复与优化验证手册

> 适用范围：`pc-admin-backend`（java-admin-framework + pc-admin-vue3-vite + pc-admin-ui 组件库）。
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

# 组件库：单测 + lib 构建（V1.2 方向 A）
cd ../pc-admin-ui
npm install                             # 首次（devDependencies，peer 外部化）
npm test                                # 预期 Test Files 3 passed / Tests 21 passed
npm run build                           # 预期产出 dist/admin-ui.js + admin-ui.cjs + style.css
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

```bash
cd java-admin-framework
mvn -q test    # 预期 114 例全过，BUILD SUCCESS
python3 scripts/check-all.py   # 预期 EXIT=0
```

> 注：JaCoCo 插件在离线仓库不可用，`mvn jacoco:report` 会报 `No plugin found for prefix 'jacoco'`；
> 覆盖率数值以 CI（联网环境）的 coverage-gate 为准，本批不改其阈值。

---

## 3.7 组件库 @acme/admin-ui（V1.2 方向 A）

**需求来源**：`PLATFORM-EVOLUTION-PLAN.md` 方向 A（P1），A1 独立包 / A2 组件清单 / A3 样式令牌 / A4 CI。
**决策记录**：纯 JS（Q2）、新建 CI job（Q3）、独立目录 + file 引用 + alias（Q4）、StatCard 消费方验证（Q5，实际改用 BaseChart 消费方 JobListView——StatCard 组件无实际视图消费）。

### 组件清单与契约（输入输出 / 边界 / 异常）

| 组件 | 输入（Props） | 输出（事件/插槽） | 边界与异常 |
|---|---|---|---|
| **StatCard** | label/value/unit/decimals/baseline/showTrend/trendColorMode/icon/loading | 图标与 footer 插槽 | value 为 null/非法显示 `-`；baseline 为 0 或缺失时不显示趋势；loading 显示骨架 |
| **BaseChart** | option(必填)/height | `chart-click` 下钻参数 | option 变化/主题切换重绘；ResizeObserver + window resize 双监听，卸载时清理；SSR 场景不使用 |
| **CopyText** | text/display/copyable/block/showText | 复制成功/失败 ElMessage | text 为空拒绝复制；clipboard API 不可用时 execCommand 兜底；无 document 时抛错被捕获 |
| **ProTable** | data/columns/loading/pagination/total/page/pageSize/pageSizes/rowKey/defaultSort | update:page/update:pageSize/sort-change/selection-change + prepend/默认/列插槽 | columns=null 按空数组；total<0 按 0；pageSize 钳制到 pageSizes |
| **QueryForm** | modelValue(v-model)/inline/labelWidth/loading/searchText/resetText | submit/reset/update:modelValue + actions 插槽 | modelValue=null 按空对象；重置将各键置 null 不删键；暴露 validate/resetFields |
| **StatusTag** | value/mapping/size/effect/defaultType | 纯展示 | 未命中映射显示原值 + defaultType；value=null 显示 `-`；mapping 非对象按空映射 |
| **DescriptionList** | items/column/border/size | 以 item.key 命名的具名插槽 | items=null 按空数组；value 为 null 显示 `-`；span 缺省 1 |
| **ConfirmDialog** | modelValue(v-model)/title/message/width/loading/danger/confirmText/cancelText/icon | confirm/cancel/closed/update:modelValue | message 支持函数式求值；danger 时确认按钮为 danger；异步操作由调用方控制 loading 与关闭 |
| **DictSelect** | modelValue(v-model)/options/placeholder/clearable/disabled/multiple/filterable/loading | update:modelValue/change | options=null 按空数组；支持单选/多选；选项 disabled 生效 |

### 包结构与接入

```
pc-admin-ui/                 独立包（纯 JS，Vite lib mode）
├── package.json             @acme/admin-ui@1.1.0，peerDependencies 外部化 vue/element-plus/echarts
├── vite.config.js           ESM + CJS 双产物 + CSS 抽取
└── src/
    ├── index.js             统一出口（9 组件 + 4 纯函数）
    ├── components/          9 个 SFC + 2 个测试文件
    ├── utils/{stat,theme}.js 随包纯函数（与主应用同源，去 DOM 副作用）
    └── styles/tokens.css    design tokens（--acme-ui-*，可覆盖）
```

主应用通过 `file:../pc-admin-ui` 依赖 + Vite alias 接入：
- **默认（源码直引）**：`@acme/admin-ui` → `pc-admin-ui/src/index.js`，dev/test 免预构建。
  - 关键约束：库包源码位于主应用根目录之外，其 peer 裸导入（vue/element-plus/@element-plus/icons-vue/echarts）需在
    主应用 `vite.config.js` 的 `resolve.dedupe` 中登记，强制从主应用 node_modules 解析，否则构建报
    `Rollup failed to resolve import`（且不依赖 pc-admin-ui/node_modules 软链）；dedupe 同时保证单实例。
- **包引用**：`ACME_UI_PACKAGE=1 npm run build` → 指向 `dist/admin-ui.js`，需先 `npm run build:ui`。
- `src/components/index.js` 保留薄 re-export（3 个迁移组件），既有 `import { Xxx } from '@/components'` 调用方零改动。

### 验证命令

```bash
cd pc-admin-ui && npm test          # 21 例：9 组件 SSR smoke + stat/theme 纯函数契约
cd pc-admin-ui && npm run build     # dist/admin-ui.js + admin-ui.cjs + style.css
cd pc-admin-vue3-vite && npm test   # 60 例（接入后无回归）
cd pc-admin-vue3-vite && npm run build                    # 源码直引模式
cd pc-admin-vue3-vite && ACME_UI_PACKAGE=1 npm run build  # 包引用模式
node java-admin-framework/scripts/check-sfc.mjs $(find pc-admin-ui/src -name '*.vue')
node java-admin-framework/scripts/i18n-leak-scan.mjs pc-admin-ui/src
```

---

## 4. 兼容性保证

- **对外接口**：所有优化均未新增/删除/重命名 HTTP 端点与请求/响应字段；`authorizeNavigation` 为内部抽取，不改变守卫行为。
- **配置**：新增 `auth.captcha.max-pending` 有默认值，不配置时行为与优化前一致。
- **方法签名**：`CaptchaService.generate/verify/pendingCount` 签名不变；仅将 `generate` 标记为 `synchronized`（线程安全增强，调用方无感）。
- **第三轮优化**：`CursorCodec` / `IdempotencyRules` / 前端 `result.js` 三轮均为**纯测试新增**，未改动任何生产代码，行为与接口 100% 不变。
- **组件库（V1.2 方向 A）**：9 个组件均保持与主应用原组件同源的 props/事件/插槽契约；`components/index.js` 薄 re-export 保证既有 import 路径零改动；主应用默认源码直引，不依赖预构建产物；peerDependencies 外部化避免 vue/element-plus 双实例；新增组件为**增量能力**，不影响既有视图。

---

## 5. 历史可追溯性

- 本轮所有修复与优化按功能模块拆分为独立提交，提交信息采用 `fix|test|refactor|docs(模块): 中文摘要` 格式。
- 每个提交附带多行 body 说明具体改动点，可用 `git log --format='%h %s%n%b'` 查看完整脉络。
- 建议按 `git log --oneline` 的提交序列逐批回滚定位问题（每个提交均可独立 `git show` 审阅）。
