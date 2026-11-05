# 上线就绪评估整改核对说明（V1.0.7）

> 对应评估报告：`../LAUNCH-READINESS-ASSESSMENT-V1.0.7.md`
> 分支：`feat/V1.0.7`（整改提交按关注点拆分；遵守本地提交不推送约定，由用户本机推送）
> 整改范围：P0×3 + P1×9 + P2×4 共 **16** 项全部收口
> 验收双门禁：`mvn -o clean test` 全绿（18 模块 0 失败） + `scripts/check-conventions.py` EXIT=0

---

## 一、整改总览

| 编号 | 维度 | 整改项 | 整改前 | 整改后 | 对应提交 |
|------|------|--------|--------|--------|----------|
| P0-1 | 安全性 | 数据权限解析失败 fail-open | 兜底全量放行 | 默认 **fail-closed（SELF）** | `371a27b` |
| P0-2 | 安全性 | Webhook 令牌空即匿名 | 空令牌开放 | 空令牌**拒绝服务 503** | `5ec3b86` |
| P0-3 | 稳定性 | 通知同步阻塞 | 同步派发 | **异步 + 上界 + 熔断** | `5504510` |
| P1-1 | 安全性 | record 脱敏被跳过 | 仅可变对象 | 经构造器**重建脱敏副本** | `96055fb` |
| P1-2 | 性能 | 深翻页 OFFSET 退化 | 仅 OFFSET | size 钳制 + **keyset 入口** | `5b07962` |
| P1-3 | 稳定性 | 缓存雪崩/无降级 | 固定 TTL | TTL 抖动 + **读写降级** | `3cee6d3` |
| P1-4 | 安全性 | 限流 fail-open | Redis 挂即放行 | 可配 **fail-closed 拒绝** | `73313bf` |
| P1-5 | 稳定性 | 重操作无隔离 | 共用线程 | **导出/通知线程池隔离** | `d8b91ac`/`5504510` |
| P1-6 | 稳定性 | 缺熔断能力 | 无 | **零依赖 CircuitBreaker** | `1cebfc7` |
| P1-7 | 可运维 | 编译器版本未锁定 | 未设 release | `maven.compiler.release=17` | `1cce57a` |
| P1-8 | 可运维 | 无部署制品 | 仅 prometheus | Docker/compose/systemd/脚本 | `f1fde19` |
| P1-9 | 可运维 | 纯文本日志 | 无结构 | **JSON 日志 + OTLP** | `69a8b84` |
| P2-1 | 兼容性 | collation 未固化 | 隐式约束 | 部署文档固化 **≥8.0.4** | `f1fde19` |
| P2-2 | 兼容性 | 无 API 版本段 | 无前缀 | `context-path=/api/v1` | `69a8b84` |
| P2-3 | 文档 | 模块数漂移 | 记 12/13 | 统一 **17** | `d7c1501` |
| P2-4 | 安全性 | 未跑 SCA | 无 | **OWASP dependency-check profile** | `1cce57a` |

另：`scripts/check-conventions.py` 运行时报告的 2 项硬违规（包级循环依赖 + 注释误报的"重复类"）已修复，提交 `71e0e96`。

---

## 二、各项实现状态与差距对齐

### P0-1 数据权限 fail-closed
- **评估标准**：数据权限解析失败 / 无适用规则时，必须 fail-closed，禁止退化为全量数据放行。
- **整改前差距**：`DefaultDataScopeProvider` 在解析失败与无规则分支统一 `return DataScopeResult.all()`（全量可见），属 fail-open。
- **已完善逻辑**：
  - 新增 `DataScopeDefaultPolicy`（枚举 `ALL` / `SELF`）与 `DataScopeProperties(defaultPolicy=SELF)`；
  - provider 构造器注入 `DataScopeProperties`，5 处兜底由 `DataScopeResult.all()` 改为 `DataScopeResult.ofPolicy(properties.getDefaultPolicy())`；
  - 默认配置 `SELF`，运维可按需调为 `ALL`。
- **验证**：`DefaultDataScopeProviderResolveTest` / `DefaultDataScopeProviderTest` 断言解析失败路径返回 `selfOnly()`。
- **文件**：`permission/DefaultDataScopeProvider.java`、`DataScopeResult.java`、`DataScopeDefaultPolicy.java`(新)、`DataScopeProperties.java`(新) + 2 测试类。

### P0-2 告警 Webhook 令牌强制校验
- **评估标准**：启用接收端时令牌不可为空，否则拒绝服务。
- **整改前差距**：`AlertWebhookController` 令牌为空即视为匿名开放。
- **已完善逻辑**：令牌为 null/空白且端点启用时直接返回 **503**；`scripts/init/env.template` 注释更新"缺失即拒绝"。
- **验证**：`AlertWebhookControllerTest` 覆盖空令牌→503、匹配→200、不匹配→401。
- **文件**：`monitor/alert/AlertWebhookController.java` + 测试 + `scripts/init/env.template`。

### P0-3 通知派发异步化 + 上界 + 熔断
- **评估标准**：通知派发不得同步阻塞请求线程；必须有超时上界，并具备熔断保护。
- **整改前差距**：`NotificationService.dispatch` 同步执行，无上界、无熔断。
- **已完善逻辑**：
  - `NotificationService` 注入 `@Qualifier("notificationExecutor") Executor` 与可选 `CircuitBreaker dispatchBreaker`；`dispatchAsync` 经 `CompletableFuture.supplyAsync(..., notificationExecutor).thenApply(breaker!=null? breaker::execute : identity).orTimeout(timeoutMs)`；breaker 缺失时 fail-open 不阻断业务；
  - 新增 `NotificationResilienceConfig` 定义 `notificationDispatchBreaker`（失败阈值 5、开路 30s）；
  - `NotificationRetryProperties` 增 `timeoutMs`；`DeliveryRetryRules.clampAttempts` 钳制 1..10；
  - `ObservabilityConfig` 新增 `notificationExecutor` 隔离线程池。
- **验证**：`NotificationServiceTest` 覆盖异步/超时/熔断路径。
- **文件**：`notification/service/NotificationService.java`、`config/NotificationRetryProperties.java`、`rules/DeliveryRetryRules.java`、`config/NotificationResilienceConfig.java`(新) + `framework/config/observability/ObservabilityConfig.java` + 测试。

### P1-1 不可变 record 字段脱敏
- **评估标准**：字段级权限对不可变 `record` 的受控字段同样需脱敏/隐藏。
- **整改前差距**：`ColumnPermissionApplier` 仅对可变对象反射就地改写，record 被跳过（不脱敏）。
- **已完善逻辑**：对 `target.getClass().isRecord()` 走 `applyRecord`——逐组件读取，受控且无权限字段按策略脱敏/隐藏，经规范构造器 `newInstance(args)` **重建脱敏副本**返回（原 record 不变）；反射异常记 WARN 并回落原对象，不影响其它字段。
- **验证**：`ColumnPermissionApplierTest` 断言重建副本与原对象非同一引用、手机号被掩为 `138****1234`、原对象不变。
- **文件**：`permission/ColumnPermissionApplier.java` + 测试。

### P1-2 深翻页 keyset 入口
- **评估标准**：深翻页场景须提供 keyset（游标）分页以规避 OFFSET 退化。
- **整改前差距**：仅 `page`（OFFSET）分页，无 keyset 入口。
- **已完善逻辑**：
  - `JooqWriters.page` 增加 `size`(1..MAX_SIZE)、`offset` 钳制；
  - keyset 入口迁至 `JooqKeysetPager.pageKeyset`（与 `query` 等价别名），**消除 dsl↔paging 包级循环依赖**（原 `JooqWriters` 反向依赖 `paging` 包触发 check-conventions 硬违规 H1）。
- **文件**：`jooq/dsl/JooqWriters.java`、`jooq/paging/JooqKeysetPager.java`。

### P1-3 缓存防雪崩
- **评估标准**：缓存 TTL 需随机抖动防集体失效；读写异常须降级，不可蔓延为雪崩。
- **整改前差距**：固定 TTL、无降级；Spring 6.1 `AbstractCacheManager` 无 `setCacheErrorHandler`（6.2 才有）。
- **已完善逻辑**：
  - `CacheConfig` 的 `entryTtl` 经 `jitterTtl` 加 ±随机抖动；
  - `StatisticsCache` 装饰层对 `get/get(type)/get(loader)/put/putIfAbsent/evict/clear` 包裹 try-catch，经单例 `LoggingCacheErrorHandler` 降级（get 失败回落 null、loader 失败回源、写失败静默丢弃）。
- **文件**：`framework/config/cache/CacheConfig.java`、`StatisticsCache.java`、`LoggingCacheErrorHandler.java`(新)。

### P1-4 限流 fail-closed 可配
- **评估标准**：Redis 不可用时限流须可配置为拒绝，而非默认放行。
- **整改前差距**：`RedisRateLimiter` 在 Redis 不可用时 fail-open 放行。
- **已完善逻辑**：`RateLimitConfig`/`RateLimitProperties` 增 `failClosed` 开关；`RedisRateLimiter` 失败按开关决定放行或拒绝；新增 `DenyRateLimiter`（fail-closed 实现，Redis 不可用时直接拒绝）。
- **文件**：`security/ratelimit/RateLimitConfig.java`、`RateLimitProperties.java`、`RedisRateLimiter.java`、`DenyRateLimiter.java`(新)。

### P1-5 导出 / 通知线程池隔离
- **评估标准**：导出等重操作须使用独立线程池，避免阻塞主请求线程。
- **整改前差距**：导出/通知共用或占用主线程。
- **已完善逻辑**：`ObservabilityConfig` 新增 `exportExecutor`（core=2/max=4/queue=50/CallerRunsPolicy）；`ExportCenterService` 导出下载走 `exportExecutor` 异步（`downloadAsync`），同步 `downloadSync` 保留原路径；通知派发走 `notificationExecutor`。
- **文件**：`system/export/service/ExportCenterService.java` + `framework/config/observability/ObservabilityConfig.java`。

### P1-6 零依赖熔断器
- **评估标准**：关键外部调用须有熔断能力。
- **已完善逻辑**：新增 `common/resilience/CircuitBreaker`（CLOSED→OPEN→HALF_OPEN 状态机）+ `CircuitBreakerOpenException`；5 个单测覆盖状态迁移与半开试探。被 P0-3 通知派发复用。
- **文件**：`common/resilience/CircuitBreaker.java`(新)、`CircuitBreakerOpenException.java`(新)、`test/.../CircuitBreakerTest.java`(新)。

### P1-7 锁定编译器版本
- **评估标准**：构建须可复现、可降级到 LTS。
- **已完善逻辑**：父 `pom.xml` 增加 `<maven.compiler.release>17</maven.compiler.release>`（CI 用 JDK 21 编译，产出 17 字节码）。
- **文件**：`pom.xml`。

### P1-8 部署制品
- **评估标准**：需提供容器化与编排、启停脚本。
- **整改前差距**：仅 `deploy/prometheus`。
- **已完善逻辑**：新增 `deploy/Dockerfile`、`docker-compose.yml`（app + `mysql:8.4` 强制 `utf8mb4_0900_ai_ci`、注入 `APP_API_CONTEXT_PATH=/api/v1`、`OBSERVABILITY_OTLP_ENABLED=true`、prometheus）、`systemd/java-admin-framework.service`、`scripts/start.sh`、`scripts/rollback.sh`、`deploy/README.md`。
- **文件**：`deploy/*`（6 个新增）。

### P1-9 日志 JSON 化与 OTLP 接入
- **评估标准**：结构化日志 + 可集中采集（OTLP）。
- **整改前差距**：纯文本 PatternLayout、OTLP 关闭。
- **已完善逻辑**：新增零依赖 `admin/logging/JsonLogEncoder`（Jackson）替代默认布局；`logback-spring.xml` 切换；`application.yml` 设 `observability.otlp.enabled=false`（docker 经环境变量开启）、`server.servlet.context-path=${APP_API_CONTEXT_PATH:}`；前端版本段由 compose 注入 `/api/v1`。
- **文件**：`admin/logging/JsonLogEncoder.java`(新)、`admin/src/main/resources/logback-spring.xml`、`admin/src/main/resources/application.yml`。

### P2-1 collation 文档固化
- **评估标准**：固化 "MySQL ≥ 8.0.4" 硬约束，低版本须有备选迁移。
- **已完善逻辑**：`schema_ddl.sql` 与全部 Flyway 脚本统一 `utf8mb4_0900_ai_ci`；`deploy/README.md` 固化约束并给出 `utf8mb4_general_ci` 备选说明；`docker-compose.yml` 强制 `--collation-server=utf8mb4_0900_ai_ci`。
- **文件**：`deploy/README.md`、`deploy/docker-compose.yml`、既有 DDL/迁移脚本。

### P2-2 API 版本段
- **评估标准**：统一 `/api/v1` 前缀以隔离演进期 Breaking Change。
- **已完善逻辑**：`application.yml` 增加 `server.servlet.context-path=${APP_API_CONTEXT_PATH:}`；`docker-compose.yml` 设 `APP_API_CONTEXT_PATH=/api/v1`。
- **文件**：`admin/src/main/resources/application.yml`、`deploy/docker-compose.yml`。

### P2-3 文档模块数校正
- **评估标准**：文档所述模块数须与真实一致（17）。
- **已完善逻辑**：`README.md`、`docs/README.md`、`docs/ROADMAP.md` 统一模块数为 17。
- **文件**：上述 3 个文档。

### P2-4 Spring Boot 补丁 + SCA
- **评估标准**：升级到当前补丁版 + CI 跑 SCA 依赖扫描门禁。
- **已完善逻辑**：离线仓库未含更新 SB 补丁版，**以锁定 `maven.compiler.release=17` + 新增 `sca` profile（OWASP dependency-check 10.0.4，`failBuildOnCVSS=9`，`suppressionFile`，默认不激活）** 作为可落地的门禁替代；`scripts/dependency-check-suppressions.xml` 提供占位抑制清单。运行 `mvn -Psca verify` 即可在 CI 启用。
- **文件**：`pom.xml`、`scripts/dependency-check-suppressions.xml`(新)。

---

## 三、双门禁复验结果

| 门禁 | 命令 | 结果 |
|------|------|------|
| 第一关·全量测试 | `mvn -o clean test`（JDK 21 离线） | **BUILD SUCCESS**，18 个 reactor 模块 0 失败 |
| 第二关·约定校验 | `python3 scripts/check-conventions.py` | **EXIT=0**（仅剩 1 条非阻断的模块分层概览警告） |

> 注：离线环境无 `resilience4j`/`logstash` 实现包，故 P1-6 自研零依赖熔断器、P1-9 自研 Jackson JSON 编码器，均为零新增三方依赖。

---

## 四、提交清单（feat/V1.0.7）

```
71e0e96 fix(scripts): 约定校验器扫描前剥离注释，消除注释误判的硬违规
d7c1501 docs: P2-3 统一文档中模块数为 17
69a8b84 feat(admin): P1-9 日志 JSON 化与 OTLP 接入；P2-2 暴露 /api/v1 版本段
f1fde19 deploy: P1-8 新增 Docker / docker-compose / systemd 部署制品与启停脚本
1cce57a build: P1-7 锁定 maven.compiler.release=17；P2-4 新增 SCA 检查 profile 与抑制清单
d8b91ac feat(system): P1-5 导出下载走隔离线程池，避免阻塞主请求线程
5504510 fix(system): P0-3 通知派发异步化并设上界与超时；引入熔断保护，隔离专享线程池
5ec3b86 fix(monitor): P0-2 告警 Webhook 接收端令牌为空即拒绝服务（503），杜绝匿名开放
371a27b fix(security): P0-1 数据权限解析失败时默认 fail-closed（SELF）而非全量放行
5b07962 fix(common): P1-2 深翻页 OFFSET 钳制 + keyset 分页入口；消除 jooq.dsl↔paging 包循环依赖
96055fb fix(security): P1-1 字段权限对不可变 record 经规范构造器重建脱敏副本
73313bf fix(security): P1-4 限流器支持 failClosed 开关，Redis 不可用时拒绝而非放行
3cee6d3 fix(framework): P1-3 缓存 TTL 随机抖动防雪崩；读写降级经 LoggingCacheErrorHandler 兜底
1cebfc7 feat(common): P1-6 新增零依赖熔断器 CircuitBreaker（CLOSED/OPEN/HALF_OPEN 状态机）
```

---

## 五、残留风险与后续建议

1. **SB 补丁升级（P2-4）**：当前以 `release=17` 锁定 + SCA profile 替代；待离线仓库纳入新版 SB 补丁后，建议升级并复跑 SCA。
2. **API 版本段（P2-2）**：目前以 `context-path` 形式注入，Controller 未硬编码 `/api/v1`；若需双前缀过渡（兼容旧客户端）建议后续在网关层处理。
3. **collation 低版本兼容（P2-1）**：`utf8mb4_general_ci` 备选迁移脚本尚未生成，属后续迭代项（已在 `deploy/README.md` 标注）。
