# 压测脚本（零外部依赖）

本目录脚本仅用 Python 标准库，可离线运行，用于网关路径性能验收与限流语义验证。

## gateway_bench.py

并发 GET 压测，统计 QPS、时延分位（P50/P95/P99）、状态码分布与 429 命中率。

```bash
# 基线：直连路径（gateway.enabled=false）
python3 gateway_bench.py --url http://127.0.0.1:8080/api/system/user/list \
  --requests 2000 --concurrency 20 --label direct --out /tmp/direct.json

# 对照：网关路径（gateway.enabled=true，同机同配置）
python3 gateway_bench.py --url http://127.0.0.1:8080/api/system/user/list \
  --requests 2000 --concurrency 20 --label gateway --out /tmp/gateway.json

# 限流语义校验：低 max + 高并发，期望部分请求被 429
python3 gateway_bench.py --url http://127.0.0.1:8080/api/system/user/list \
  --requests 200 --concurrency 30 --expect-status 429 --label ratelimit

# 内网准入校验：公网来源访问 actuator 期望 403（本机默认 127. 属内网，
# 需从非内网 IP 或调整 internal-cidrs 后验证）
python3 gateway_bench.py --url http://127.0.0.1:8080/actuator/health \
  --requests 10 --concurrency 2 --expect-status 200 --label actuator
```

## 测试范围与通过标准（对应 deployment.md §5）

| 维度 | 范围 | 通过标准 |
|---|---|---|
| 功能-限流 | 窗口内 max 次放行、超限 429 + Retry-After、并发不超阈值 | `GatewayRateLimiterTest` 全绿；压测 429 分布符合 max/window 配置 |
| 功能-CORS | 预检 204、白名单回显、非法来源 403 | `GatewayCorsRulesTest` + `GatewayFilterTest` 全绿 |
| 功能-内网准入 | actuator 公网 403 / 内网放行 | `GatewayRouteRulesTest` + `GatewayFilterTest` 全绿 |
| 性能-QPS 损耗 | 同机直连 vs 网关路径，2000 请求 / 20 并发 | 网关 QPS ≥ 直连 QPS × 0.90（损耗 < 10%） |
| 性能-时延 | 同上，P99 对比 | 网关 P99 ≤ 直连 P99 × 1.15 |
| 稳定性 | 2000 请求全程 | network_error = 0；anomalies 仅含预期 429 |

> 说明：脚本统计的是端到端 HTTP 行为。QPS 损耗对比须在相同机器、相同后端实例、
> 相同 warmup 条件下进行（建议各先跑 200 请求预热），排除网络与冷启动噪声。
