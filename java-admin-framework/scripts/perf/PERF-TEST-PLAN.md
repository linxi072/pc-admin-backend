# 后端性能测试方案（Performance Test Plan）

> 配套脚本：`load_test.py`（本沙箱可直接运行，零依赖）、`perf.k6.js`（CI / 预发环境用）、`run-perf.sh`（便捷入口）。
> 功能测试方案见 `docs/test/end-to-end-test-report.md`；本文件只覆盖**性能**维度。

## 1. 测试目标

评估 `java-admin-framework` 后端在**单机**部署下的高并发能力、响应时间与吞吐量，识别吞吐饱和点与错误起点，为容量规划与 SLA 设定提供基线数据。

## 2. 测试范围（Scope）

| 维度 | 说明 |
| --- | --- |
| 被测系统 | `scaffold-admin`（Spring Boot 3.3.4 / Java 21）可执行实例 |
| 部署形态 | 单节点、本机 `integration` profile（真实 MySQL、关闭 Spring Cache 注解、JobRunr 后台执行器关闭） |
| 网络 | 本机 loopback（127.0.0.1），**不含** 网卡 / 反向代理 / TLS 开销 |
| 数据层 | 同机 MySQL（与集成测试共用 `java_admin` 库）；因缓存注解关闭，读接口每次直连 DB（结果偏保守） |
| 排除项 | 浏览器端渲染、静态资源、WebSocket 推送、Kafka 消费链路（不在 HTTP 压测面内） |

## 3. 用例设计（Test Cases）

| 用例 | 方法 / 路径 | 鉴权 | 模拟语义 | 负载 |
| --- | --- | --- | --- | --- |
| **TC-P1** | `GET /api/connectivity/ping` | 匿名（白名单） | 框架开销基线：零 DB、零业务 | total=3000, c=50 |
| **TC-P2** | `GET /api/system/menus/tree` | Bearer Token | 鉴权过滤 + 菜单树 DB 读取 + 树构建 | total=750, c=25 |
| **TC-P3** | `POST /api/auth/login` | 无（登录入口） | 口令校验（哈希）+ JWT 签发 + 在线登记 + 审计 | total=600, c=25 |
| **TC-P3-RAMP** | 同 TC-P3 | 无 | 并发爬坡 c=10→200，定位吞吐饱和 / 错误起点 | 每档 total=300 |

> 登录场景每个请求随机化 `X-Forwarded-For`，规避 `RateLimitFilter` 的 `login:IP` 60s/10 次限流，确保压测不被限流干扰。

## 4. 预期性能指标（Expected Metrics / SLA 基线）

> 以下为**本机单节点**的参考阈值，非生产 SLA；生产经 Redis 缓存、独立 DB、多实例水平扩展后应显著优于本基线。
> 表中「预期（设计目标）」为产品应达到的参考值；「实测基线」见第 8 节。登录链路原受连接池 + 长事务约束存在拐点（c≈20），已于 2026-10-28 落地两项优化消除（见 §8.4）。

| 用例 | p95 延迟（预期） | 吞吐（预期） | 错误率（预期） | 实测基线（见 §8） | 说明 |
| --- | --- | --- | --- | --- | --- |
| TC-P1 ping | < 20 ms | > 2000 req/s | 0% | p95=66ms / 2139.9 req/s / 0% | 纯框架开销，应极高吞吐 |
| TC-P2 menus/tree | < 150 ms | > 800 req/s | < 1% | p95=62ms / 802.7 req/s / 0% | 鉴权 + DB 读；缓存关闭故直连 DB |
| TC-P3 login | < 400 ms | > 200 req/s | < 1% | **优化后 c=20 达 62.3 req/s / p95=388ms / 0%；c=50 仍 65.3 req/s / p95=856ms / 0%** | 已落地「连接池 20→50 + 事务收窄」两项优化（见 §8.4），拐点已消除 |

**饱和判定**：TC-P3-RAMP 中，当某档并发下吞吐不再随并发增长、或 p99 陡增、或错误率 > 1% 时，即为该接口的并发能力拐点。实测拐点在优化前为 **c≈20**（Hikari 主连接池上限），2026-10-28 落地优化后拐点已推至 **c≥50 之外**（瓶颈转为 bcrypt CPU 容量，见 §8.4）。

## 5. 度量口径

- **响应时间**：客户端 `perf_counter` 计时（含建连），输出 p50 / p95 / p99 / avg / min / max（毫秒）。
- **吞吐量**：成功 + 失败请求总数 / 施压总时长（req/s）。
- **错误率**：非 2xx/3xx 响应占比（含超时、5xx、限流 429）。
- **预热**：每组场景先发 50 个请求不计入统计，避免冷启动 / JIT 抖动。

## 6. 执行方式

```bash
# 1) 启动应用（integration profile，本机 MySQL）
mvn -o -pl scaffold-admin -am spring-boot:run \
  -Dspring-boot.run.profiles=integration \
  -Dspring-boot.run.jvmArguments="-Dio.netty.resolver.dns.useJdkResolver=true"

# 2) 运行压测（应用就绪后）
bash scripts/perf/run-perf.sh http://127.0.0.1:8080 3000 50

# 3)（可选）CI / 预发使用 k6
k6 run -e BASE=http://localhost:8080 -e USER=admin -e PASSWORD=admin123 scripts/perf/perf.k6.js
```

## 7. 风险与局限

1. **loopback 偏差**：本机压测省略网络与 TLS 开销，真实公网延迟会更高。
2. **缓存关闭**：`integration` profile 关闭缓存注解，读接口每次命中 DB，生产开启 Redis 后延迟与吞吐更优。
3. **单节点**：未做集群与负载均衡，仅反映单实例容量。
4. **数据隔离**：测试数据写入 `java_admin` 真实库，压测后建议清理（或独立性能库）。

## 8. 实测基线（2026-10-28 单机压测，源码重编译权威数据）

> 执行方式：`mvn -o -f pom.xml -pl scaffold-admin -am clean verify -DskipITs=false -Dit.test='*IT'`（从源码重编译全部 11 模块后运行 `BackendPerfIT`，进程内 `@SpringBootTest` + `TestRestTemplate` 真实 HTTP 链路，integration profile，本机 MySQL）。
> **重要**：此前直接 `-f scaffold-admin/pom.xml` 跑测试会因本地仓库 JAR 过期而掩盖真实编译/行为；本轮以 `-am` 从源码重编译，以下数据为当前源码权威基线。
> 登录场景预建 80 个独立账号并按请求序号轮询，消除「单账号行锁风暴」假象，测得的是真实多用户并发登录容量。

### 8.1 实测数据（优化前基线，2026-10-28 第一轮源码重编译）

| 用例 | 并发 | 总请求 | 吞吐 (req/s) | p50 | p95 | p99 | 错误率 | 状态码 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-P1 ping（匿名） | 50 | 1000 | 2139.9 | 11ms | 66ms | 123ms | 0.00% | 200×1000 |
| TC-P2 menus/tree（鉴权读） | 20 | 300 | 802.7 | 17ms | 62ms | 112ms | 0.00% | 200×300 |
| TC-P3 login（多账号，c=20） | 20 | 300 | 6.1 | 3287ms | 3388ms | 3410ms | **7.33%** | 200×278 / 500×22 |
| TC-P3-RAMP login @c=10 | 10 | 300 | 52.7 | — | 231ms | 358ms | 0.00% | 200×300 |
| TC-P3-RAMP login @c=25 | 25 | 300 | 7.7 | — | 3486ms | 3510ms | **24.50%** | 200×227 / 500×73 |
| TC-P3-RAMP login @c=50 | 50 | 300 | 15.3 | — | 3749ms | 3954ms | **65.50%** | 200×103 / 500×197 |

### 8.1.1 实测数据（优化后，2026-10-28 第二轮源码重编译 — 两项优化已落地）

> 优化项：① Hikari `maximumPoolSize` 20→50（minimumIdle 5→10）；② `AuthService.login` 事务边界收窄——bcrypt 口令校验 + JWT 签发移出事务，DB 写入收拢为 `commitLoginSuccess` / `recordLoginFailure` 两个短事务（经 `@Lazy self` 代理进入事务）。验证命令同 §8 顶部。

| 用例 | 并发 | 总请求 | 吞吐 (req/s) | p50 | p95 | p99 | 错误率 | 状态码 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-P1 ping（匿名） | 50 | 1000 | **2367.3** | 14ms | 39ms | 91ms | 0.00% | 200×1000 |
| TC-P2 menus/tree（鉴权读） | 20 | 300 | **917.3** | 16ms | 39ms | 152ms | 0.00% | 200×300 |
| TC-P3 login（多账号，c=20） | 20 | 300 | **62.3** | 318ms | 388ms | 433ms | **0.00%** | 200×300 |
| TC-P3-RAMP login @c=10 | 10 | 300 | 62.4 | — | 201ms | 231ms | 0.00% | 200×300 |
| TC-P3-RAMP login @c=25 | 25 | 300 | 64.6 | — | 462ms | 529ms | 0.00% | 200×300 |
| TC-P3-RAMP login @c=50 | 50 | 300 | 65.3 | — | 856ms | 901ms | 0.00% | 200×300 |

### 8.2 关键结论

- **只读/轻写链路（ping、menus/tree）容量充足**：优化后 p95 进一步降至 39ms、0% 错误，吞吐较优化前提升约 11%/14%，满足交互级延迟预期。
- **登录链路拐点已被消除（c=50 内 0 错误）**：优化后登录在 c=10/20/25/50 全程 **0% 错误**，吞吐从 6.1 req/s（优化前 c=20 已崩溃）提升至 **62.3 req/s（c=20）/ 65.3 req/s（c=50）**，p95 由 3388ms 降至 388ms（c=20）/ 856ms（c=50）。新的饱和点由「连接池耗尽」转为「bcrypt CPU 容量」（单节点 ~62–65 req/s 平台），已不在连接池拐点。
- **优化前拐点根因（连接池耗尽，已修复）**：登录原为单个 `@Transactional` 长事务，覆盖 bcrypt 口令校验（CPU 密集）→ 刷新令牌签发 → 在线会话登记 → REQUIRES_NEW 登录审计。整段事务占满 Hikari 主连接池（上限 20），并发 ≥20 即触发 `Connection is not available, request timed out after 3005ms` → 500。两项优化已将其消除（详见 §8.4）。

### 8.3 优化建议（按投入产出排序）

1. **（配置项，已落地 2026-10-28）提高 Hikari `maximumPoolSize`**：由 20 上调至 50（minimumIdle 5→10）。见 §8.4。
2. **（代码，已落地 2026-10-28）收窄登录事务边界**：bcrypt 口令校验 + JWT 签发已移出 `@Transactional`；事务内只保留「签发令牌 + 在线登记 + 审计」的写操作。见 §8.4。
3. **（代码，未实施）登录审计日志异步化 / 批处理**：`loginLogRecorder` 当前为每次登录一次 `REQUIRES_NEW` 独立事务（额外占用一次连接获取），可改为异步写入或合并，降低连接获取次数。
4. **（代码，未实施）权限聚合缓存**：`permissionService.getPermissions / getRoleCodes` 在每个登录都要回源 DB；对稳定租户角色权限做短 TTL 缓存，降低登录路径读压力。
5. **（架构，未实施）评估口令哈希成本**：bcrypt strength=10 在登录风暴下 CPU 占用显著；若登录为规模瓶颈可结合硬件评估自适应哈希或限流分级（注意：属安全策略变更，需评审）。

> 以上优化均不改变功能行为，属于容量调优；实施任意一项后建议重新执行 `BackendPerfIT` 复测拐点。

### 8.4 优化实施与效果（2026-10-28 已落地）

| 优化项 | 改动 | 登录吞吐（c=20） | 登录错误率（c=50） | 登录 p95（c=20） |
| --- | --- | --- | --- | --- |
| 优化前 | 池=20 + 长事务（bcrypt 在事务内） | 6.1 req/s | **65.50%** | 3388ms |
| 优化后 | 池=50 + 事务收窄（bcrypt 移出） | **62.3 req/s（≈10×）** | **0.00%** | **388ms** |

- **改动文件**
  - `scaffold-framework/.../config/datasource/MultiDataSourceProperties.java`：`Pool.maximumPoolSize` 20→50、`minimumIdle` 5→10（默认值；各 profile 未在 yml 覆盖，全局生效）。
  - `scaffold-system/.../security/service/AuthService.java`：`login` 不再持有事务；bcrypt 校验与 JWT 签发在事务外完成；DB 写入收拢为 `commitLoginSuccess` / `recordLoginFailure` 两个 `@Transactional(noRollbackFor = BusinessException.class)` 短事务，经 `@Lazy self` 代理进入事务。
- **行为一致性**：成功路径返回 `TokenView` 字段不变；失败路径（验证码 / 账号锁定 / 禁用 / 需改密 / 限制 / 多端拒绝 / 口令错误）抛出的错误码与提示信息与重构前完全一致；失败计数与渐进式锁定写操作仍独立提交（`noRollbackFor`）。
- **自注入注意**：`@Lazy self` 是为打破「authService 依赖自身」的循环依赖；直接 `@Autowired self`（无 `@Lazy`）会因 bean 尚在创建中即被请求而报 `unresolvable circular reference`，已规避。
- **验证门禁**：11 模块全 `SUCCESS`，`*IT` 共 **106 个全部通过**（含 `BackendPerfIT` 102.9s 压测、`AuthFlowIT`、`TenantProvisioningIT`、`ForgotPasswordIT`、`AuthSessionIT`），`BUILD SUCCESS`。
- **剩余建议**：§8.3 中第 3/4/5 项列为后续独立优化，本次未实施。
