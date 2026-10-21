# 监控：N+1 查询探测与疑似事件留存

> 模块：`com.acme.scaffold.monitor.detection` + `monitor.controller.NPlusOneController` ｜ 交付日期：2026-10-20 ｜ 状态：已交付（800 例全绿，`check-conventions.py` EXIT=0）

## 1. 背景与能力

`monitor/detection` 包此前已有 N+1 探测三件套——`NPlusOneProbe`（线程级累计同构 SQL 执行次数，归一化折叠空白与字面量/数字为占位符）、`NPlusOneListener`（越阈值打告警日志）、`NPlusOneResetFilter`（请求级重置）——但存在两个缺口：

1. **检测数据只写不读**：越阈值仅在日志里出现一条告警，运维没有接口查询「当前哪些 SQL 疑似 N+1、出现频次如何、分布在哪些线程」；
2. **命名数据源 DSL 缺少观测**：`JooqConfig` 构建的主 `DSLContext` 已接线，但 `DataSourceConfig` 为多数据源显式访问构建的 `Map<String, DSLContext>` 只挂了慢 SQL 监听。

本轮新增 `NPlusOneSuspectRegistry`（有界事件留存）+ `NPlusOneController` / `NPlusOneService` / 2 个视图 DTO，并补齐 `DataSourceConfig` 的 N+1 装配，使告警从「一次性日志」升级为**可查询、可复盘、可清理**的运行态数据。

## 2. 模块结构

```
monitor/
├── detection/
│   ├── NPlusOneProbe.java            # 既有：同构 SQL 线程级计数（阈值恰达时返回 true，每轮每指纹仅一次）
│   ├── NPlusOneListener.java         # 改：构造注入 registry，越阈值登记事件（带 try-catch 旁路防护）
│   ├── NPlusOneResetFilter.java      # 既有：请求进入时重置 probe 线程局部计数
│   ├── NPlusOneSuspect.java          # 新：疑似事件领域对象（指纹/样本/计数/线程名/时间戳）
│   └── NPlusOneSuspectRegistry.java  # 新：有界环形缓冲留存（默认 200 条）+ 累计计数
├── dto/
│   ├── NPlusOneSuspectView.java      # 新：疑似事件只读视图
│   └── NPlusOneStatusView.java       # 新：探测运行态视图（阈值/容量/存量/累计）
├── service/
│   └── NPlusOneService.java          # 新：limit 输入校验 + 视图映射 + 状态聚合 + 清空委托
└── controller/
    └── NPlusOneController.java       # 新：/api/system/monitor/n-plus-one 三个端点
```

装配位置（`config` 模块，零新增配置类）：

- `JooqConfig`：注册 `NPlusOneProbe` 与 `NPlusOneSuspectRegistry` Bean，`dslContext` 挂载 `NPlusOneListener`（原有）；
- `DataSourceConfig`：`dslContexts` / `buildDsl` 补挂 `NPlusOneListener`，与主 DSLContext **共用同一 probe 与留存组件**（本轮补齐）。

## 3. 快速启用

留存与探测默认开启，无需额外开关；两项参数已在 `application.yml` 显式声明：

```yaml
app:
  jooq:
    # N+1 查询探测：同构 SQL 在单线程（通常对应一次请求）内执行达阈值即告警
    nplus1-threshold: 10
    # N+1 疑似事件留存容量（有界环形缓冲，超出按入队序淘汰最旧）
    nplus1-max-suspects: 200
```

## 4. 接口与权限

统一要求 `system:monitor:read`（与 `MonitorController` 同权，`ApiResourceScanner` 自动登记为 API 资源）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/system/monitor/n-plus-one/suspects?limit=50` | 疑似事件列表（最新在前；limit 1~200，越界 400 `COMMON_001`） |
| GET | `/api/system/monitor/n-plus-one/status` | 探测运行态：阈值 / 容量 / 当前条数 / 累计越限次数 / 最近登记时间 |
| DELETE | `/api/system/monitor/n-plus-one/suspects` | 清空留存列表（累计计数不清零） |

```bash
# 最近 50 条疑似事件（指纹可直接定位代码中的查询）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/system/monitor/n-plus-one/suspects?limit=50"

# 探测运行态
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/system/monitor/n-plus-one/status"
```

## 5. 关键设计点

- **有界内存、恒定占用**：留存为有界环形缓冲（默认 200 条），超出按入队序淘汰最旧；样本 SQL 截断至 500 字符；与 probe 的 ThreadLocal 计数（随请求结束清理）互补——前者跨请求留存，后者单请求判定。
- **累计口径单调**：`totalBreaches` 自进程启动累计，`clear()` 不清零，便于评估告警频率趋势；`lastBreachAt` 记录最近一次越限时间。
- **旁路零风险**：`NPlusOneListener` 登记事件带 try-catch 防护——即使留存组件自身异常也仅记 debug 日志，绝不向 jOOQ 执行链路传播；探测本身只读（不改写 SQL、不拦截执行）。
- **双路一致观测**：主 DSLContext（`JooqConfig`）与命名数据源 DSLContext（`DataSourceConfig`）共用同一 probe 与 registry，两条访问路径的告警与留存行为一致。
- **输入校验走服务层**：limit 越界抛 `BusinessException.badRequest`（`VALIDATION_ERROR`）→ 经 `GlobalExceptionHandler` 映射 400 / `COMMON_001`（与 Kafka 监控模块同一模式；框架未处理路径变量级 `ConstraintViolationException`，故不采用 `@Validated`）。
- **无数据库依赖**：不引入任何 DDL 或外部依赖；留存为进程内存态，重启清零。

## 6. 测试

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `NPlusOneProbeTest`（既有） | 4 | 指纹归一化 / 阈值恰达 / reset / 多指纹分离 |
| `NPlusOneSuspectRegistryTest` | 10 | 倒序检索 / limit 钳制 / 容量淘汰 / 累计覆盖被淘汰事件 / 清空保累计 / 空指纹忽略 / 样本截断 / 线程名捕获 / 并发有界守恒 |
| `NPlusOneListenerTest` | 4 | 越限登记（指纹/样本/计数）/ 未达阈值不登记 / 每轮每指纹至多一次 / null SQL 安全 |
| `NPlusOneServiceTest` | 6 | limit 0 / 越上限 / 负值校验 / 视图映射 / 状态聚合 / 清空委托 |
| `NPlusOneE2eTest` | 5 | NP-01~05：列表 / 运行态 / limit 越界 400 `COMMON_001` / 清空 / 缺省 limit |

## 7. 离线构建与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -o clean test          # 800 例全绿
python3 scripts/check-conventions.py   # EXIT=0
```

配套文档：`docs/test/end-to-end-test-report.md` §11（集成说明）、`docs/test/test-case-specification.md` §9（用例规格）、`docs/test/sequence-diagrams.html` SD-08（时序图）。
