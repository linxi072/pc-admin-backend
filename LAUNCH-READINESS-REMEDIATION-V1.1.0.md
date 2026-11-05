# 上线就绪评估 V1.1.0 整改核对说明

> 配套文档：`LAUNCH-READINESS-ASSESSMENT-V1.1.0.md`（复评报告，含「标准—评估—整改」清单）。
> 分支：`feat/V1.0.7`（本地提交，未推送 origin；由用户本机评审后推送）。
> 范围：报告标记为「未达标 / 部分达标」的功能项 **P4、P7、C6、C9、S10、ST5、F5**；F4 列为后续迭代（仅在本说明中标注，不实现功能代码）。

---

## 0. 双门禁复验结果（整改后）

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 单元测试全量 | `mvn -o clean test`（JDK 21，离线） | **BUILD SUCCESS**，17 个模块全绿，0 失败 0 错误 |
| 约定校验 | `scripts/check-conventions.py` | **EXIT=0**，无 H1–H5 硬违规（仅 1 项 W* 模块分层提示，非阻断） |

> 注：前端 `pc-admin-vue3-vite` 本轮未改动，未重复 `vite build`；后端双门禁为本次验收阈值。

---

## 1. P4 — 主数据深翻页 OFFSET 护栏 + keyset 游标入口；role/org 全量加载下推 LIMIT

- **原评估（未达标→部分达标）**：`user`/`message` 列表走 OFFSET，百万级深翻页线性退化；`role`/`org` 无 LIMIT 全量加载，存在内存与响应时间风险。
- **整改**：
  1. **深翻页偏移护栏**：`UserService.list` / `MessageService.pageMine` / `pageSent` 增加全局偏移上限（默认 5000 行），超限抛 `BusinessException` 并提示改用 keyset 游标端点，消除性能悬崖且不破坏既有 `PageResult.total` 契约。
  2. **keyset 游标端点（深滚首选）**：新增 `GET /api/system/users/page-keyset`、`/api/system/messages/mine-keyset`、`/api/system/messages/sent-keyset`，复用 `JooqKeysetPager.query`（取 size+1 判 hasNext、不透明游标、按 `(created_at,id)` 与 `(is_read,id)` 等确定排序键 seek）。
  3. **role/org 有界加载**：`RoleService.list()` 改 `JooqWriters.fetchTop(..., MAX_ROLE_ROWS=1000, sort_no ASC)`；`OrgService.tree()/loadAll()` 改走新增 `JooqWriters.fetchListBounded(..., MAX_ORG_ROWS=5000, sort_no ASC)`，触顶告警（避免异常数据增长把全表拖入堆内 OOM），保留数据权限范围签名分键缓存不变。
- **验证**：单元测试 `OrgServiceCacheTest` 4 例（跨权限缓存隔离/驱逐）适配新 `fetchListBounded` 打桩后复绿；全量 `mvn -o clean test` 通过。

涉及文件：`UserService.java`、`UserController.java`、`MessageService.java`、`MessageController.java`、`RoleService.java`、`OrgService.java`、`JooqWriters.java`（新增 `fetchListBounded`）、`OrgServiceCacheTest.java`。

## 2. P7 — 通知外发通道熔断覆盖

- **原评估（部分达标）**：自研 `CircuitBreaker` 已用于通知/登录日志/文件/审计，但读多写少的核心外发通道（短信/邮件/Webhook/企微/钉钉）未系统性包裹。
- **整改**：`NotificationResilienceConfig` 新增 5 个独立 `CircuitBreaker`（阈值 5、OPEN 30s）并聚为 `Map<Channel, CircuitBreaker> externalChannelBreakers`；`NotificationService.dispatchInternal` 对进程外通道经对应熔断器 `execute` 包裹（进程内通道不包裹）。字段以 `@Autowired(required=false)` 注入，保障既有 `new` 直接构造的单元测试零回归。
- **验证**：`mvn -o clean test` 通过（含 `CoreApiE2eTest` 等 mock 场景）。

涉及文件：`NotificationResilienceConfig.java`、`NotificationService.java`。

## 3. C6 — 补齐 OTLP Collector（trace 汇聚可达）

- **原评估（部分达标）**：JSON 日志已落地、OTLP 可开关，但 `docker-compose` 仅含 app/mysql/prometheus，缺 OpenTelemetry Collector，容器内 `localhost:4318` 不可达，trace 实际丢弃。
- **整改**：新增 `deploy/otel-collector-config.yaml`（`otlp` http:4318 / grpc:4317 接收；`debug` + `prometheus` :8889 导出；`batch` 处理）；`docker-compose.yml` 增加 `otel-collector` 服务，`OTLP_ENDPOINT` 默认指向 `http://otel-collector:4318/v1/traces` 并 `depends_on`；`prometheus.yml` 新增 `otel-collector:8889` 抓取作业。
- **验证**：YAML 语法经 `mvn` 无关，靠约定校验器扫描通过；编排语义与既有 app 服务一致。

涉及文件：`deploy/otel-collector-config.yaml`（新）、`deploy/docker-compose.yml`、`deploy/prometheus/prometheus.yml`。

## 4. C9 — 实际运行 SCA（CI 可复现、CVSS≥9 失败阻断）

- **原评估（部分达标）**：`sca` profile 已定义但默认不激活；CI 的 `sca` 作业用 `mvn ... org.owasp:dependency-check-maven:...:check -DfailOnError=false` 直接调插件且 `continue-on-error: true`，**绕过 profile 配置（failBuildOnCVSS=9 + suppressionFile），本质从未执行策略**。
- **整改**：
  1. CI `sca` 作业与周扫描 `security-scan-scheduled.yml` 均改为 `mvn -B -Psca -DskipTests -DskipITs verify`，真正激活 pom 内 `sca` profile 策略（CVSS≥9 失败阻断，低/中危仅入报告）。
  2. `pom.xml` `sca` profile 显式 `dataDirectory` 锁定到 `~/.m2/repository/org/owasp/dependency-check-data`，与 CI `actions/cache` 缓存路径一致，保证离线/私服镜像可复现扫描。
  3. `scripts/dependency-check-suppressions.xml` 从空占位改为带策略说明的模板：铁律「禁止无理由全盘抑制、每条须注明 CVE 与抑制理由」，含注释示例；不掩盖真实高危。
- **验证**：配置变更经 `check-conventions` 与全量 `mvn` 复验通过；实际 CVE 扫描需在联网/已缓存 NVD 的 CI 环境执行（报告 §4.4 要求：待离线仓库纳入 Spring Boot 补丁版后复跑并更新 suppression 清单）。

涉及文件：`.github/workflows/ci.yml`、`.github/workflows/security-scan-scheduled.yml`、`pom.xml`、`scripts/dependency-check-suppressions.xml`。

## 5. S10 — 限流默认 fail-closed 显式化

- **原评估（部分达标）**：`RateLimitProperties.failClosed` 与 `DenyRateLimiter` 已提供，但默认语义为 Redis 不可用时回落本地计数（分布式下等效限流失效）。
- **整改**：`application-prod.yml` 显式声明 `app.ratelimit.fail-closed: true`（分布式部署 Redis 不可用时拒绝请求而非 fail-open 放行）。
- **验证**：配置项全量 `mvn` 复验通过。

涉及文件：`scaffold-admin/src/main/resources/application-prod.yml`。

## 6. ST5 — N+1 探测 fail 模式（阈值越限即阻断）

- **原评估（部分达标）**：`NPlusOneProbe` 仅检测告警，不阻断，反模式可潜入生产。
- **整改**：`NPlusOneProbe` 新增 `failMode` 构造参数与 `isFailMode()`；`NPlusOneListener.executeEnd` 在 fail 模式下越限抛 `NPlusOneThresholdExceededException`（新建）；`JooqConfig` 经 `app.jooq.nplus1-fail-on-threshold` 注入（默认 `false`，CI/IT 置 `true` 即门禁）；`application.yml` 新增该开关（默认仅告警）。`NPlusOneListenerTest` 增补 fail/非 fail 两路径。
- **验证**：`NPlusOneListenerTest` 复绿；全量 `mvn -o clean test` 通过。

涉及文件：`NPlusOneProbe.java`、`NPlusOneListener.java`、`NPlusOneThresholdExceededException.java`（新）、`JooqConfig.java`、`application.yml`、`NPlusOneListenerTest.java`。

## 7. F5 — 云模型未就绪返回 501（而非笼统 500）

- **原评估（部分达标）**：`CloudLlmProvider` 云接入仍为占位异常（默认关闭，可接受但「功能未就绪」语义不清）。
- **整改**：新增 `LlmNotImplementedException`（`@ResponseStatus(NOT_IMPLEMENTED)`）；新增 `AiExceptionAdvice`（`@RestControllerAdvice(basePackages="com.acme.scaffold.ai")`、`@Order(HIGHEST_PRECEDENCE)`），确保云模型未配置/未实现时返回 **501** 而非被全局 catch-all 改写为 500；`CloudLlmProvider.parse()` 两处抛此异常。
- **验证**：全量 `mvn -o clean test` 通过（含 `scaffold-ai` 模块编译与既有测试）。

涉及文件：`LlmNotImplementedException.java`（新）、`AiExceptionAdvice.java`（新）、`CloudLlmProvider.java`。

## 8. F4 — 占位/启发式模块（后续迭代，本轮不实现）

- **原评估（部分达标，沿用 V1.0.7）**：`ai`/`im`/`cms`/`approval`/`plugin`/`codegen` 部分占位/启发式，单测薄弱、无独立 IT。
- **本轮处置**：按任务范围列为后续迭代，**未改动功能代码**；仅随全量双门禁复验确保其既有测试仍绿（本次 `mvn -o clean test` 中 cms/im/plugin/codegen/ai 模块均 SUCCESS）。建议在首版后迭代中补齐最小可用实现与独立 IT，并在对外暴露前标注「实验性/非 GA」。

---

## 9. 提交清单（按关注点，本地提交未推送）

| 提交 | type | 范围 |
| --- | --- | --- |
| 1 | feat(system) | P4 深翻页护栏 + keyset 游标 + role/org 有界加载 |
| 2 | feat(system) | P7 外发通道熔断覆盖 |
| 3 | deploy | C6 补齐 OTLP Collector |
| 4 | build(ci) | C9 实际运行 SCA（-Psca + CVSS≥9 阻断 + 离线缓存） |
| 5 | feat(admin) | S10 限流默认 fail-closed |
| 6 | feat(framework) | ST5 N+1 探测 fail 模式 |
| 7 | feat(ai) | F5 云模型未就绪返回 501 |
| 8 | docs | 本整改核对说明 + 复评报告归档 |

---

## 10. 遗留与后续

- **C9 真实 CVE 扫描**：需联网/已缓存 NVD 的 CI 环境执行 `mvn -Psca verify` 获取真实报告，据结果补充 `dependency-check-suppressions.xml` 具体条目（当前为策略模板）。
- **F4**：首版后迭代补齐 `ai/im/cms/approval/plugin/codegen` 最小可用实现与独立 IT。
- **P4 存量 OFFSET 端点**：`user`/`message` 主列表仍提供 OFFSET 端点（保留前端契约），深翻页统一引导至 keyset 端点，属渐进收敛，非阻断。
