# 可观测性：span 本地留存与追踪回溯

## 1. 背景与能力

`observability` 包原有能力：请求级 span 采集（`OtlpTracingFilter`）、零依赖 OTLP/HTTP 导出（`OtlpSpanExporter`，默认关闭）、Micrometer 指标门面（`MetricsRegistry`）。缺口有两个：

1. **span 导出后本地零留存**——Collector（Jaeger / Tempo / OTel Collector）未部署或 `observability.otlp.enabled=false` 时，运维无法回答「这个 traceId 刚才经过了哪些请求、耗时与状态」；
2. **指标无管理面板视角的按名检索**——Prometheus 抓取是「推」模型，管理后台缺少「按名称前缀查当前 meter 快照」的「拉」入口。

本轮新增 `SpanRecorder`（本地留存）+ `ObservabilityController` / `ObservabilityService`（管理入口）补齐两者。

## 2. 模块结构

```
observability/
├── export/
│   ├── ObservabilityProperties.java   # + recorder 配置段（enabled/capacity）
│   ├── OtlpSpan.java                  # OTLP 对齐 span 模型（纯数据结构）
│   ├── OtlpSpanExporter.java          # OTLP/HTTP sink（默认关闭）
│   ├── OtlpSpanSerializer.java        # OTLP JSON 序列化
│   ├── OtlpTracingFilter.java         # 改：List<SpanExporter> 多 sink 分发
│   └── SpanExporter.java              # sink 抽象（原注释即预留多实现）
├── recorder/
│   └── SpanRecorder.java              # 新：有界内存环形缓冲，implements SpanExporter
├── metrics/
│   └── MetricsRegistry.java           # Micrometer 门面（未改动）
├── trace/
│   └── TraceContext.java              # W3C traceparent 解析（未改动）
├── service/
│   └── ObservabilityService.java      # 新：校验 + 聚合 + 视图映射
├── dto/
│   ├── SpanView.java                  # 新：span 只读视图（毫秒折算）
│   ├── TraceSummaryView.java          # 新：双链路运行态摘要
│   └── MetricView.java                # 新：指标快照视图
└── controller/
    └── ObservabilityController.java   # 新：/api/observability 5 端点
```

## 3. 快速启用

本地留存**默认开启**（`observability.recorder.enabled=true`，容量 200），无需任何操作即可使用：

```yaml
observability:
  recorder:
    enabled: true      # 本地留存开关（与 otlp.enabled 正交）
    capacity: 200      # 有界缓冲上限，超出按入队序淘汰最旧
  otlp:
    enabled: false     # Collector 导出开关（既有，默认关）
```

调用示例：

```bash
# 最近 50 条 span（可按 status=ERROR / traceId 过滤）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/observability/traces?limit=50&status=ERROR"

# 按 traceId 回溯同一追踪（traceId 与日志 MDC 一致，可互相跳转）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/observability/traces/4bf92f3577b34da6a3ce929d0e0e4736"

# 双链路运行态摘要（留存水位、OK/ERROR 累计、导出器开关）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/observability/summary"

# 指标快照（按名称前缀过滤）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/observability/metrics?prefix=app.security"

# 清空本地留存（不影响 OTLP 导出）
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/observability/traces"
```

## 4. 接口与权限

| 端点 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/api/observability/traces` | GET | `system:observability:read` | 最近 span（`limit` 缺省 50、范围 1~capacity；`status` 限 UNSET/OK/ERROR；`traceId` 可选） |
| `/api/observability/traces/{traceId}` | GET | `system:observability:read` | 同一追踪全部 span（时间正序） |
| `/api/observability/summary` | GET | `system:observability:read` | 记录器与导出器双链路摘要 |
| `/api/observability/metrics` | GET | `system:observability:read` | Micrometer 指标快照（`prefix` 可选） |
| `/api/observability/traces` | DELETE | `system:observability:manage` | 清空本地留存，返回清除条数 |

权限码由 `ApiResourceScanner` 启动时自动登记至 `sys_api_resource` 并授予 SUPER_ADMIN，同时登记 `sys_menu` 菜单（route=/observability/traces）。

## 5. 关键设计点

1. **多 sink 分发（复用既有抽象）**：`OtlpTracingFilter` 构造由单 exporter 改为 `List<SpanExporter>`，Spring 自动聚合 `OtlpSpanExporter`（推 Collector）与 `SpanRecorder`（本地留存）两个 bean。这正落在原作者 `SpanExporter` 接口注释预留的设计上（「解耦过滤器与具体导出实现，便于在单测中使用内存记录器替换」）。新增 sink 只需实现该接口，过滤器零改动。
2. **双开关正交**：`recorder.enabled` 与 `otlp.enabled` 互相独立——本地留存开启时无需 Collector 即可回溯；两个开关均关闭时过滤器直接透传（保持既有「零开销」语义）。该正交性由 `OtlpTracingFilterTest` 守护。
3. **包无环**：`SpanRecorder` 位于 `recorder` 包，单向依赖 `export` 包（接口与模型）；过滤器只依赖同包接口，不再 import recorder 具体类——规避 `check-conventions.py` H1 包级循环依赖硬违规（首版实现曾因 filter→recorder + recorder→export 构成环，按此方案重构消除）。
4. **有界内存、线程安全**：`ArrayDeque` + 监视器锁串行化读写，容量默认 200（约百 KB 级），超出按入队序淘汰最旧；状态计数用 `LongAdder`。span 生产速率低（每请求 1 条），锁竞争可忽略。
5. **traceId 双向跳转**：span 的 traceId 复用 MDC `traceId`，与审计日志、全局异常处理器、W3C `traceparent` 一致——日志中拿到 traceId 即可调 `/traces/{traceId}` 看请求链路。
6. **输入校验分层**：查询参数宽容归一（null/空白按缺省），显式非法值（limit 越界、status 非枚举、traceId 空串）由服务层抛 `BusinessException.badRequest`，经 `GlobalExceptionHandler` 映射 400 + `COMMON_001`——与 Kafka 监控模块同一策略（路径变量 Bean Validation 在框架中不受支持，勿用 `@Validated`）。

## 6. 测试

| 测试类 | 例数 | 覆盖 |
| --- | --- | --- |
| `SpanRecorderTest` | 10 | 倒序检索、容量淘汰、traceId 聚合、状态统计、清空、禁用语义、`export` 批量、null 安全、并发有界守恒 |
| `ObservabilityServiceTest` | 8 | limit/status/traceId 校验、过滤映射、clear 委托、双链路摘要、指标前缀过滤与排序 |
| `ObservabilityE2eTest` | 6 | HTTP 契约：列表/按 traceId/摘要/指标/400 映射/清空（standalone MockMvc + 真实控制器与异常处理器） |
| `ObservabilityPropertiesTest` | 3 | `application.yml` recorder 段绑定、otlp 回归、默认值与 yml 一致 |
| `OtlpTracingFilterTest` | 3 | 双 sink 消费、双关零开销透传、仅本地留存时仍记录（本轮 +1） |

## 7. 离线构建说明

- 无新增外部依赖：本地留存为纯 JDK 结构；指标快照复用既有 Micrometer（actuator + prometheus registry 已在 classpath）。
- 唯一配置变更为 `application.yml` 的 `observability.recorder` 段（默认值与属性类一致，并有单测守护）。
- 与官方 OpenTelemetry SDK 的关系不变：联网环境仍可平滑切换（`-Potel`），届时 `TraceContext` 的 traceId/spanId 由 SDK 接管，本地留存可继续作为兜底回溯。
