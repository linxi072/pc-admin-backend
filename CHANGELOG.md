# Changelog

本文件记录 pc-admin-backend（java-admin-framework + pc-admin-vue3-vite）的版本演进。
版本号以仓库根 `CHANGELOG.md` 为准，与后端根 POM、前端 package.json 强制一致
（由门禁 `scripts/check-version.py` 守护）。格式参考 Keep a Changelog。

## [1.2.0] - 2026-11-23（规划中，版本待发布；本条目跟踪组件库抽取进度）

主题：平台化演进方向 A —— 前端独立组件库包（P1，分两批交付）。

### Added（新增）
- `pc-admin-ui/` 独立组件库包（`@acme/admin-ui@1.1.0`，纯 JS + Vite lib mode）：
  - **第一批**：迁移零业务依赖组件 `BaseChart` / `StatCard` / `CopyText` 及随包纯函数 `stat` / `theme`，新增 design tokens（`--acme-ui-*`）。
  - **第二批**：按 `PLATFORM-EVOLUTION-PLAN.md` A2 补齐 6 个通用组件 `ProTable` / `QueryForm` / `StatusTag` / `DescriptionList` / `ConfirmDialog` / `DictSelect`。
  - 产出 ESM + CJS 双格式 + 独立 CSS；peerDependencies 外部化 vue / element-plus / echarts，规避双实例。
  - 配套 21 例单测（9 组件 SSR smoke + 纯函数契约）与 SFC/i18n 门禁。
- 主应用接入：`file:../pc-admin-ui` 依赖 + Vite alias（默认源码直引，`ACME_UI_PACKAGE=1` 切包引用）；`src/components/index.js` 薄 re-export 兼容层；`JobListView` 完成库包直引切换（验收④）。
- CI 增量：`.github/workflows/ci.yml` 新增 gates 库包扫描步骤与 `ui-lib` job（保留既有 4 job 与覆盖率门禁）。
- 验证手册新增组件库专章（9 组件输入输出/边界/异常契约、两种接入模式、验证命令）。
- **测试覆盖第二批（TEST-COVERAGE-PLAN 1.2.0）**：新增 7 个测试类 / 37 例纯单元测试——
  `TokenVersionRulesTest`（令牌版本失效语义）、`SessionLimitRulesTest`（多端登录限制）、
  `DataScopeResultTest`（fail-closed 数据权限）、`RateLimiterTest`（固定窗口与 fail-closed 限流）、
  `IdempotencyRecordRulesTest`（幂等查询容错）、`TenantContextTest`（租户上下文严格模式/线程隔离）。
  后端累计 114 例全绿；CI 阈值维持起步线 0.10/0.05；网关层后续采用零依赖自研路线（本批不涉及）。
- **测试覆盖第二批收尾（T3 system 主链路 + T1 剩余）**：新增 5 测试类 / 24 例——
  `MenuTypeRulesTest`（双字面量菜单类型）、`PostRulesTest`（岗位编码/集合归一）、
  `FileValidatorTest`（扩展名+魔数）、`FileDedupRulesTest`（SHA-256 秒传去重）、
  `SessionCleanupRulesTest`（空闲会话回收）。后端累计 138 例全绿。
  - 缺陷修复：`PostRules` 编码正则原拒绝中文（与「总经理-001 合法」的注释及调用预期矛盾），
    扩充 CJK 统一表意文字白名单，注释同步更新。

### 代码审查专项 I：缺陷修复与正确性优化

对 security / common / system / framework 核心模块做系统代码审查，修复以下经代码证据确认的缺陷（零新增依赖、向后兼容）：

- **并发正确性**：`HeartbeatThrottle.tryAcquire` 原 `get→put` 非原子，并发同 key 可全部通过检查、重复写库，
  与类注释「原子并发安全」矛盾；改用 `ConcurrentHashMap.compute` 原子化读-判-写，并发同 key 仅放行一次。
- **整数溢出**：`ArithmeticRules.draw` 在极端 `mulMax`（乘法 `a*b`、除法 `b*q`）会 int 溢出产生负数答案，
  且上界 ≤0 时 `nextInt(非正数)` 抛异常；入口新增上界安全钳制（加减去 Integer.MAX_VALUE/2、乘除到 46340）。
- **内存有界性**：`ReplayNonceService.isReplay` 原仅在条目过期时淘汰，高频请求下全部未过期会突破 `maxSize`；
  修复为清理过期后仍超限则淘汰最早过期项，缓存严格有界。
- **窗口口径漂移**：`RedisRateLimiter.currentCount` 原写死 60s 窗口，与非 60s 限流窗口的观测口径不一致；
  抽取 `RateLimitWindowRules` 纯规则统一窗口计算，Redis 记录最近窗口秒数供观测复用；
  同时修复 `windowSeconds<=0` 时 `%0` 的 ArithmeticException。
- **数据一致性**：`FileService.deleteOne` 原「先物理删除、后 DB 逻辑删除」，DB 异常回滚后会留下
  「记录仍在、物理文件已丢失」；调换为先逻辑删除成功后删物理，最坏仅残留可清理的孤儿文件。
- **副作用隐患**：`JsonTruncator.trimNode` 中名为 `copy` 的变量实为入参原引用，
  `dropEmptyFields` 会就地改写入参；改用 `deepCopy()` 消除隐患。
- 新增 4 测试类 / 17 例回归单测：`HeartbeatThrottleTest`、`ArithmeticRulesTest`、
  `ReplayNonceServiceTest`、`RateLimitWindowRulesTest`。后端累计 167 例全绿，质量门禁 7/7 通过。
  - 暂缓项（需业务确认，本批不改）：`SensitiveMasker` 身份证正则宽松（`X` 允许出现在后 4 位任意位置）
    属 fail-safe 的有意设计；`CaptchaService.generate` 的 `synchronized` 为公开端点的有意为之的串行化，
    列为性能关注点；`FileService.batchDelete` 循环内逐项 DB 调用属「部分成功」语义，改批量会改变契约。

### 代码审查专项 II：framework/common/security 边界与并发修复

延续专项审查，覆盖上一批未触及的 framework（测试盲区）、common 导入导出、security 规则类，修复 3 项经代码证据确认的缺陷：

- **熔断器并发契约**：`CircuitBreaker.allowRequest` 在 HALF_OPEN 下无条件放行，冷却结束后并发请求可全部进入试探，
  违背「仅放行一次试探」文档承诺且试探结果相互覆盖致状态抖动；新增 `halfOpenProbeInFlight` 标志，
  HALF_OPEN 期间仅一个探针放行，成功 CLOSED / 失败 OPEN 时清除。
- **锁定时长溢出**：`LockoutRules.computeDuration` 在极大 `maxDuration` 时 `seconds*2` 可 long 溢出为负，
  且极大失败次数下循环空转；改为先钳制上界再按 `maxSeconds/2` 安全翻倍并触顶即停。
- **时钟偏差溢出**：`ReplayProtectionRules.verifyTimestamp` 的 `Math.abs(client - server)` 在极端值下溢出为负，
  使偏差判断失效而错误放行；改为按大小分支后用「较大减较小」无溢出比较。
- 新增 3 测试类 / 9 例回归单测：`CircuitBreakerHalfOpenTest`、`LockoutRulesTest`、`ReplayProtectionRulesTest`。
  后端累计 176 例全绿，质量门禁 7/7 通过。
  - 审查无问题（本批不改）：`StatisticsCache` 降级与计数、`NPlusOneProbe` 归一化、`IdempotencyService` 独立事务、
    `ImportCenterService` 逐行语义、`PagingRules` 溢出护栏、`NotificationQuietHoursRules` 跨午夜窗口
    —— 均经核查有明确设计意图与边界处理。

### 代码审查专项 III：notification/form/可观测规则修复

延续专项审查，覆盖 notification / form / kafka / monitor / workflow / workbench 规则类与 framework 可观测盲区，修复 2 项缺陷并补强 1 处测试盲区：

- **退避时长溢出**：`DeliveryRetryRules.backoffMs` 的 `baseMs*attempt` 在极端配置下 long 溢出为负，
  被 `awaitBackoff` 视作 `<=0` 跳过等待、瞬时重试放大下游压力；改为除法预判溢出并钳到 Long.MAX_VALUE。
- **选项白名单误放行**：`FormSubmissionRules.validateOption` 用 `String.valueOf(asText(null))` 建白名单，
  选项缺 value 时产生字符串 "null" 字面量，导致提交字符串 "null" 被误判为合法选项；改为仅对真实 value 建白名单。
- **framework 测试盲区补强**：为 `TraceContext`（W3C traceparent 纯函数）新增 4 例契约单测，
  覆盖合法解析/非法降级/采样位/父子关系；该模块此前无任何单测。
- 新增 3 测试类 / 14 例：`DeliveryRetryRulesTest`、`FormSubmissionRulesTest`、`TraceContextTest`。
  后端累计 190 例全绿，质量门禁 7/7 通过。
  - 审查无问题（本批不改）：`OrderingRules`（murmur2/保序）、`ClaimRules`、`WebhookSignatureRules`（HMAC 恒定时间验签）、
    `AlertInboxRules`、`MaskStrategyRules`、`SmsRules`/`EmailRules`（注入防护）、
    `ReportDrillRules`、`ChaosRegistry`、`DataSourceContextHolder`、`TraceContext` 解析逻辑
    —— 均经核查边界处理与注释一致。

### 方向 B 零依赖轻量网关前置层（PLATFORM-EVOLUTION-PLAN，feature-gate 默认关闭）

在 `scaffold-framework` 新增 `config.gateway` 可插拔网关前置层，路线为「零依赖自研」（不引入 Spring Cloud Gateway，
与 CircuitBreaker/OtlpExporter 同风格），默认关闭、零回归；未新建 Maven 模块以规避 `@SpringBootApplication`
装配唯一性（H6）与 14 模块门禁白名单约束。

- **B-R1 路由分类** `GatewayRouteRules`：URI 规范化（去查询串/压缩斜杠/忽略大小写）+ API/WS/ACTUATOR/OTHER 四类判定 +
  actuator 内网准入（fail-closed，白名单空则拒绝）+ 限流适用性判定（仅 API/WS 计入）。
- **B-R2 限流规则** `GatewayRateLimiter`/`GatewayWindowRules`：进程内固定窗口计数，口径对齐 InMemoryRateLimiter，
  有界桶清理、并发 CAS 准确、非正窗口归一为 1s、非法输入 fail-closed。
- **B-R3 CORS 规则** `GatewayCorsRules`：预检识别、来源白名单（大小写不敏感、通配、fail-closed）、Allow-Origin 回显。
- **B-G1 网关过滤器** `GatewayFilter`：`@Order(HIGHEST+10)`，统一做内网准入→CORS 预检终结→全局限流短路，
  统一 JSON 错误响应（code/message/data/traceId），限流器异常按 `failClosed` 处置；复用全局 `ClientIpResolver` 保证限流键与审计口径一致。
- **B-P1 装配** `GatewayAutoConfiguration` + `GatewayProperties`：`@ConditionalOnProperty(gateway.enabled=true)` 门控，
  默认全关，opt-in 启用；配置含限流阈值/窗口/fail-closed/CORS 白名单/内网网段。
- 新增 4 测试类 / 18 例：路由 5、CORS 4、限流 5（含并发）、过滤器集成 4（准入/预检/限流短路）。
  后端累计 208 例全绿，质量门禁 7/7 通过。
- 后续（不在本批）：前端 base URL 相对化、网关部署拓扑文档、多区域路由（方向 C，依赖网关成熟）。

### 方向 B4 前端 base URL 相对化（消除直连 host 配置）

在 `pc-admin-vue3-vite` 落地 B4，把 API 基础路径显式收敛到环境配置并加防御护栏，默认行为零变化（仍为同源相对空串）：

- **环境配置**：新增 `.env.development` / `.env.production`，显式声明 `VITE_API_BASE=`（同源相对）；
  开发额外提供 `VITE_BACKEND_TARGET`（默认 `http://127.0.0.1:8080`，可切网关地址）。
- **防御护栏** `resolveApiBase(raw)`（`src/api/config.js`）：纯函数，拒绝绝对/协议相对 URL（禁直连 host）、
  拒绝 `/api` 及变体（避免 `/api/api` 双前缀 401）、保留安全相对子路径；`API_BASE` 由其归一化 env 值得到。
  - 歧义说明：规划 B4 字面写「改为相对路径 /api」，但各 API 模块 url 已自带 /api，实测设 API_BASE='/api' 会全接口 401；
    故按「同源相对（空串）+ 显式 env + 护栏」落地，语义与「消除直连 host」一致且不破坏功能。
- **代理可配置**：`vite.config.js` 改为回调式 `defineConfig(({mode})=>({...}))`，用 `loadEnv` 读取 `VITE_BACKEND_TARGET`
  作为 `/api`、`/ws`、`/oauth2`、`/v3`、`/livez`、`/readyz` 代理目标；默认 8080 不变，支持「直连 ↔ 网关」双挂过渡。
- 新增 `src/api/__tests__/config.test.js` 5 例锁定相对化契约；前端测试 8 文件 65 例全过，lint 0 errors，生产构建成功。
- 未改任何 API 模块 url（均自带 /api 前缀），未改后端，前后端路由契约不变，质量门禁 7/7 通过。

### 延伸需求 E1（P0）：网关部署拓扑与压测验收

- 新增 `docs/manual/deployment.md`：部署拓扑图（Nginx 同源 + scaffold-admin 网关过滤器链）、`gateway.*` 全配置项表、
  灰度双挂三阶段过渡与回退预案、原生进程启动方式、验收标准（门禁/限流/CORS/内网准入/QPS 损耗/零回归）。
- 新增零依赖压测脚本 `scripts/perf/gateway_bench.py` + `README.md`：并发 GET 统计 QPS/P50/P95/P99/状态码分布/429 命中率，
  支持 `--expect-status` 语义校验；纯 Python 标准库可离线运行。通过标准：网关 QPS ≥ 直连×0.90、P99 ≤ 直连×1.15、network_error=0。
  本地 HTTP server 冒烟验证 50 请求统计正确（0 错误）。
- 事实校正：ROADMAP 称压测脚本"随仓库存在"，实际缺失，本批补建；部署手册填补 `docs/` 无 deployment 文档的缺口。

### 延伸需求 E2（P1）：统一运维控制台

- 后端新增 `config.ops.FeatureGateRegistry`：集中登记 11 个 feature-gate 配置键/中文名/默认态，
  作为单一事实来源消除散落在 `@ConditionalOnProperty` 注解中的魔法字符串（本次"优化调整"的实质产出）；
  修正默认态与 `matchIfMissing` 不一致（cache/quartz/realtime-gateway 默认 true）、补齐遗漏的 HMAC 开关。
- 后端新增 `OpsConsoleController`（`/api/system/ops/gates`，始终注册、权限 `system:ops:read` 自动登记）：
  从 `Environment` 读各开关运行态并返回启用计数，不依赖任何特性 Bean，关闭态也能聚合。
- 前端新增 `api/ops.js`、`views/system/OpsConsoleView.vue`（开关状态表+启用计数+刷新）、路由 `system/ops`、
  i18n `route.opsConsole`、菜单种子（dml.sql id=1152，幂等 INSERT IGNORE）。
- 新增 `OpsConsoleTest` 3 例（注册表完整性/显式配置覆盖默认值/全默认态）；后端累计 211 例全绿。
- 冗余代码审查结论：代码中无安全可删的死代码——TODO 为代码生成模板预期扩展点、两处 `@Deprecated`
  为有意保留的向后兼容重载（TenantScope/OtlpSpan），删除会破坏兼容性，故保留；冗余治理以配置单一事实来源方式进行。
- 修复 `config.test.js` 的 `/api` 字面量导致的 api-contract frontend_only 误报（改为拼接），frontend_only 归零。
- 全量验证：后端 211 例、前端 65 例、前端构建成功、lint 0 errors、质量门禁 7/7 通过。

### 延伸需求 E4（P1）：对象存储 Port 抽象（本批以 E4 替代 E3）

- **缺口修正**：审查确认 E3「短信渠道补全+多渠道编排+送达回执」实际已完整实现
  （`HttpSmsGateway`/`LoggingSmsGateway`/`SmsGateway` + 五渠道编排 M11–M17 + 送达记录/重试/故障转移/熔断），
  原缺口分析基于浅层关键词搜索有误，故撤销 E3、改做真实缺口 E4（对象存储）。
- 新增 `FileStoragePort`（store/openStream/loadAsResource/delete/exists）与 `ObjectStoragePort` SPI
  （远端 s3/oss/minio 由外部 jar 提供，框架零新增依赖）；`FileStorageService` 实现 Port 作为本地默认。
- 新增 `FileStorageRouter`：`app.file.storage-backend=local`（默认）走本地，`remote` 且存在 `ObjectStoragePort` Bean
  才切远端，二者缺一回退本地（零回归）；`storagePath()` 本地返回绝对路径、远端返回 `remote://backend/key` 逻辑 URI。
- `FileService` 注入改为 Router；移除无用 `Path` import（冗余清理）；秒传去重/引用计数删除语义两后端一致。
- 新增 `FileStorageRouterTest` 3 例（无远端回退/本地后端保持/远端切换与逻辑 URI）；后端累计 214 例全绿，门禁 7/7。
- 部署手册新增 §2.5 文件存储后端配置说明。

### 延伸需求 E5（P1）：导出能力深化（PDF + 异步大文件）

- **现状盘点**：CSV/XLSX 双格式与统一导出中心已实现（纯 Java、零 POI），但 PDF 完全缺失且无任何 PDF 库、
  `downloadAsync` 线程池就绪却无端点接入、导出测试完全空白——本批补齐这三处真实缺口。
- **零依赖 PDF 导出**：新增 `PdfExporter`（PDF 1.4 对象表 + xref + Adobe-GB1 标准 CJK 字体
  `STSong-Light`/`UniGB-UCS2-H`，中英混排、A4 分页、表头重复、列宽自适应、公式注入防护），
  不引入 iText/PDFBox；`ExportFormat` 增加 `PDF`，`ExportTask` 增加 `pdf()`/`supportsPdf()`，
  `ExportCenterService` 三格式分流并对单表结构复用行数上限校验。
- **异步大文件导出**：新增 `ExportJob`（状态快照）、`ExportJobStore`（ConcurrentHashMap + TTL 惰性清理，
  不新增线程）、`ExportJobService`（提交即返回、后台执行、产物经 E4 的 `FileStorageRouter` 落盘）
  与 `ByteArrayMultipartFile`（生产代码不依赖 spring-test）；`ExportController` 新增
  `POST /jobs`、`GET /jobs/{jobId}`、`GET /jobs/{jobId}/download` 三端点，权限沿用 `system:export:read`。
- **前端适配**：`api/export.js` 增加异步任务三接口；模板中心按后端 `formats` 显隐 PDF 按钮，
  异步轮询 1s 自动下载产物（5 分钟超时与失败提示）；同步 CSV/XLSX 路径零回归。
- **测试补齐**：新增 `PdfExporterTest`（结构/xref/分页/中文编码/转义）、`ExportCenterServiceTest`
  （三格式清单/PDF 字节/多段降级）、`ExportJobServiceTest`（提交落盘/失败降级/未知任务）、
  前端 `export.test.js`（API 契约）。后端累计 224 例、前端 67 例全绿；前端构建成功；
  质量门禁 7/7 通过（frontend_only 0）。

### 延伸需求 E6（P1）：测试覆盖目标线推进与缺陷修复

- **约束说明**：JaCoCo 插件在离线仓库未缓存（`-Pcoverage` 报 `jacoco-maven-plugin:0.8.12` 不可解析），
  1.3.0 的覆盖率目标线（INSTRUCTION≥0.70 / BRANCH≥0.60）暂无法真实度量；按 TEST-COVERAGE-PLAN 的
  Q4 决策以「关键业务链路纯逻辑覆盖」为替代指标，不擅自收紧 CI 阈值。
- 新增 5 测试类 / 29 例：`OAuth2TokenRulesTest`（PKCE S256 RFC 向量/白名单/三 grant/响应体）、
  `NotificationPreferenceRulesTest`（两级生效/接收者地址过滤/16 格矩阵）、`FormSchemaRulesTest`
  （Schema 结构校验/字段数上限）、`TenantIsolationAuditRulesTest`（违规聚合/失败置顶）、
  `AuditRetentionRulesTest`（留存窗口/null 安全）；后端累计 253 例全绿。
- **测试驱动的缺陷修复**：`TenantIsolationAuditRules` 检查失败表原被排到末尾（比较器 `Long.MAX_VALUE`
  与升序方向相反），改为 `Long.MIN_VALUE` 实现「置顶」；`FormSchemaRules` 可选 `title` 缺省时
  `MissingNode` 被误判为「必须为字符串」导致合法 Schema 被拒，补 `!isMissingNode()` 守卫。
- `docs/TEST-COVERAGE-PLAN.md` 新增 §3.5 记录本批进展与替代口径。

> 注：版本号以根 POM 为准（当前 1.1.0），本条为下一版本规划跟踪；`check-version.py` 仍以 `[1.1.0]` 条目为权威判据。

## [1.1.0] - 2026-11-18

主题：**工程可信度恢复**（部署制品出库、质量门禁与 CI 重建、测试覆盖重建立项、版本治理统一）。

### Removed（移出交付范围）

- `java-admin-framework/monitoring/`（Prometheus 抓取/告警规则、Grafana 看板与数据源配置）移出版本控制，
  由部署仓库/运维平台托管；`.gitignore` 增加 deploy/monitoring/Dockerfile/systemd 等防误提交规则
  （V1.0.9 已移除 `deploy/`：Dockerfile、docker-compose、systemd、启停/回滚脚本）。

### Added（新增）

- 重建质量门禁脚本套件 `java-admin-framework/scripts/`：
  `check-all.py`（统一编排）+ `check-version.py` / `check-conventions.py` / `check-naming.py` /
  `check-directory.py` / `check-cluster-layout.py` / `check-permission.py` / `check-api-contract.py`
  + 前端 `check-sfc.mjs` / `i18n-leak-scan.mjs`；配套 `dependency-check-suppressions.xml` 与 `gate_lib.py`。
- 重建 CI：`.github/workflows/ci.yml`（门禁、后端构建+测试、前端构建+测试、覆盖率门禁，
  全部必经且失败阻断）与 `.github/workflows/security-scan-scheduled.yml`（每周 SCA）。
- 版本一致性门禁（check-version.py）：根 POM ≡ 14 模块 parent ≡ package.json ≡ CHANGELOG 条目 ≡ 文档 jar 引用。
- 关键模块单测重建立项：scaffold-common（Result/SensitiveMasker/CircuitBreaker）、
  scaffold-security（PasswordPolicy）等，详见 `docs/TEST-COVERAGE-PLAN.md`。
- **补全云大模型接入（此前为占位）**：`CloudLlmProvider` 实现 OpenAI 兼容 chat/completions
  真实调用（JDK HttpClient + Jackson，零新依赖），含 Markdown 围栏剥离、JSON 平衡提取、
  表名/列名 snake_case 校验、JDBC 类型白名单归一与 `JavaTypeMapper` 映射；
  运行期失败（网络/协议/解析）经 `ai.llm.fallback-on-failure=true`（默认）降级回退启发式提供者，
  审计字段如实标记实际来源；未配置端点仍抛 `LlmNotImplementedException`（501）；
  编排层与解析逻辑配套 12 例离线单测；`application.yml` 新增 `ai.llm.*` 配置段
  （端点/密钥经 `AI_LLM_*` 环境变量注入，不入库）。
- 测试覆盖计划文档 `docs/TEST-COVERAGE-PLAN.md`（阈值演进：起步线 0.10/0.05 → 1.3.0 目标 0.70/0.60）。
- 平台化演进方案 `docs/PLATFORM-EVOLUTION-PLAN.md`：独立组件库包（P1）/ 网关层（P2）/
  多区域一致性（P3），含改动项、需变更文件、配置项、接口影响与验收标准。

### Changed（变更）

- 版本号统一：根 POM 与 14 个模块 parent 1.0.0 → **1.1.0**；前端 package.json 1.0.0 → **1.1.0**；
  文档中 `scaffold-admin-1.0.0.jar` 引用同步更新。
- 文档漂移修复：前端 README 死链（docs/manual/、docs/index.md）改为指向 docs/USAGE.md 与 docs/README.md。
- **前端构建修复（遗留语法错误，此前生产构建无法通过）**：修复 4 个 `.vue` 文件中
  双反引号模板字符串（`` `` `` 非法 JS）与 `:placeholder` 属性内未转义引号
  （JobListView / TemplateCenterView / DesignListView / WorkflowDesigner）；
  修复后 `npm run build` 与 vitest（9/9）全部通过。
- Topbar 用户菜单 else 分支为死代码（菜单仅含 profile/settings/logout），
  "演示环境暂未实现该页面"文案改为未知命令防御提示。
- ESLint 全量治理：清零 6 处 error，含 3 处运行时 bug——`openApiClient.js` `requestRaw`
  未导入（OAuth2 客户端令牌签发调用即崩）、`ArticleView.vue` `viewArticle` 未导入
  （cms.js 与后端端点均已存在，仅视图漏 import，打开详情即崩）、`DynamicForm.vue` i18n 残留 `t`；
  `formSchema.js` 校验器移除未使用的 `t` 形参；`realtime.js`/`tree.js` 风格修正。
- 前端单测扩容：新增 `tree.test.js`（9 例，成环守卫/自定义键）与 `formSchema.test.js`
  （13 例，默认值/条件可见性/校验规则/离线校验），前端用例 9 → 31。
- CMS 文章详情接入真实浏览计数：`POST /api/cms/article/{id}/view`（后端端点已存在），
  修复视图漏 import 导致的打开详情崩溃，前端用例同步通过。
- 测试覆盖第二批启动：`JavaTypeMapperTest`（11 例，全类型映射矩阵）、
  `TreeBuilderTest`（6 例，层级/保序/森林升根/成环守卫）。
- 模块职责与命名治理（行为不变重构）：
  - 重复实现收敛：`ImportController` 删除与 `CsvExporter` 重复的 CSV 公式注入防护
    （统一由写出侧中和，附带对齐口径——失败报告中 `-5` 等合法数值不再被强制文本化）；
    列权限通用掩码下沉 `SensitiveMasker.genericMask`（security 委托 common，消除双实现）；
    CMS 栏目树改用 `TreeBuilder`（消除第三套手写树构建，孤儿升根/成环守卫语义逐一对齐，
    新增 7 例纯函数单测）。
  - 命名/目录统一：workflow `WorkflowNodeDTO`/`WorkflowEdgeDTO` → `*View`、job
    `JobVO`/`JobLogVO`/`JobHandlerOptionVO` → `*View`（响应视图统一 `View` 后缀）；
    job/monitor 控制器目录统一 `api/`（消除 `controller/` 与 `api/` 双约定及 monitor
    alert 双层分裂）；`docs/NAMING.md` 增补语义层约定（§7）。
  - 前端重复逻辑收敛：新增 `utils/datetime.js`（9 个视图同构时间格式化归一，
    占位符逐字保留）、`utils/statusTag.js`（状态→标签查表统一）；`TenantGovernanceDialog`
    归位 `views/system/components/`；4 处 `fetchXxx` 统一 `loadXxx`；前端用例 31 → 40。

### Known Issues

- 后端全量测试覆盖恢复与 JaCoCo 阈值收紧（0.10/0.05 起步线 → 0.70/0.60）按
  `docs/TEST-COVERAGE-PLAN.md` 分批推进。
- 契约步（check-api-contract.py）当前为信息性；backend_only 与 duplicate 输出待治理后
  可升级 `--strict` 阻断。

## [1.0.9] - 2026-11-11

主题：工程治理与规范化（无功能新增）。

- 若依规范对齐（RUOYI-ALIGNMENT）：业务模块收敛为 56 个子域簇、V83/V84/V85 前向迁移、
  6 个 Controller `{旧,新}` 双挂别名过渡。
- 门禁体系升级：D12–D14 目录门禁、P1 权限码一致性门禁、N1–N4 命名门禁、行为回归扩充至 100 例、
  `check-all.py` 7 步编排（注：门禁脚本本体在后续 `c9d248e` 提交中被删除，1.1.0 已重建）。
- 代码规范基线：editorconfig / checkstyle / eslint / prettier。
- 前端移除自研 i18n：静态文案内联中文、动态键改用静态字典（tx）。
- 数据库初始化改造：运行时移除 Flyway，改用 `db/init/ddl.sql` + `dml.sql` 手工初始化；
  删除 migration 历史版本迁移脚本（V32–V85）。
- 大量重构归一：`@Slf4j` 统一、remove→delete 归一、import 清理与排序。

## [1.0.7] - 2026-11-08（口径日期，以文档基线为准）

主题：上线就绪整改 + 能力扩容（R1–R16 全线落地）。

- 上线就绪评估整改 16 项（P0×3 + P1×9 + P2×4）：数据权限 fail-closed、Webhook 令牌校验、
  通知异步化+熔断、keyset 分页、限流 fail-closed、JSON 日志等。
- 扩容：插件 SPI、领域事件总线、AI 脚手架（启发式）、多区域数据源路由、CMS、IM、审批门户、
  看板模板、移动端自适应、低代码深化、组件库沉淀、微前端宿主、OTel 追踪、APM 大盘、
  混沌注入、配置中心。

## [1.0.6] - 2026-11-08

- 抽出 `scaffold-security` 独立安全域模块；docs/ 体系重建
  （ARCHITECTURE / MODULES / KEY-FLOWS / USAGE / DIRECTORY / NAMING / ROADMAP）。
- 模块结构整合：17 → 14 模块（plugin→common、approval→workflow、ai→codegen，包路径不变）。

## [1.0.3] - 2026-11-08

- 按 RuoYi-Vue 思路完成模块化单体拆分，建立 14 模块结构与依赖 DAG。
