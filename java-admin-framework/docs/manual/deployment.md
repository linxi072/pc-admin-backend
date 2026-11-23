# 部署手册：轻量网关前置层与同源静态资源

> 对应 `PLATFORM-EVOLUTION-PLAN.md` 方向 B5。描述零依赖自研网关前置层的部署拓扑、
> 配置项、灰度双挂过渡与验收标准。本项目**不使用 Docker**，全部以原生进程运行。

## 1. 部署拓扑

```
                          ┌─────────────────────────────────────┐
   浏览器 / 客户端 ──HTTPS──▶  Nginx（TLS 终止 + 静态资源托管）        │
                          │  /                → pc-admin-vue3-vite/dist（同源）
                          │  /api|/ws|/oauth2 → 127.0.0.1:8080（反向代理）
                          └──────────────┬──────────────────────┘
                                         │ 同源转发
                          ┌──────────────▼──────────────────────┐
                          │  scaffold-admin（单体，端口 8080）        │
                          │   ├─ GatewayFilter（gateway.enabled=true）│
                          │   │    · 内网准入 / CORS 预检 / 全局限流  │
                          │   ├─ TraceIdFilter（MDC traceId）        │
                          │   ├─ Spring Security 鉴权链              │
                          │   └─ 业务 Controller（/api/**）           │
                          └──────────────┬──────────────────────┘
                                         │
                          ┌──────────────▼──────────────┐
                          │  MySQL（db/init 手工初始化）    │
                          │  Redis（令牌版本/幂等/会话）     │
                          └───────────────────────────────┘
```

**关键约定**：

- 前端已 B4 改造，`API_BASE` 为空串（同源相对），所有 API 模块 url 自带 `/api` 前缀。
  生产由 Nginx 与后端同域部署，**前端无直连 host 配置**。
- 网关前置层是 `scaffold-admin` 进程内的 Servlet 过滤器（`config.gateway.GatewayFilter`），
  非独立部署单元；启用后与 TraceIdFilter/鉴权链处于同一过滤器链，业务 API 路径与契约不变。
- Actuator（`/actuator/**`、`/livez`、`/readyz`）仅内网准入；CORS 预检由网关终结（后端 CORS 可下线）。

## 2. 网关配置（`gateway.*`）

`GatewayProperties` 配置前缀 `gateway`，**默认全关**（`enabled=false`），关闭时不注册任何网关 Bean，与单体直连行为零回归。

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `gateway.enabled` | `false` | 总开关，opt-in 启用 |
| `gateway.rate-limit-max` | `120` | 单 IP 每窗口最大请求数；≤0 关闭限流 |
| `gateway.rate-limit-window-seconds` | `60` | 限流窗口秒数；≤0 归一为 1s |
| `gateway.fail-closed` | `true` | 限流器异常时拒绝（true）/放行（false） |
| `gateway.allowed-origins` | `[]` | CORS 来源白名单；为空不处理 CORS |
| `gateway.internal-cidrs` | `[127.,10.,192.168.,172.16.~172.2]` | actuator 内网网段前缀 |
| `gateway.canary-percent` | `0` | 灰度权重 0~100；0 全 stable、100 全 canary（E10） |
| `gateway.canary-header` | `""` | 强制 canary 的 header 名（值为 `true` 命中，优先于权重） |
| `gateway.canary-ips` | `[]` | 强制 canary 的客户端 IP 白名单（精确匹配，优先于权重） |

**启用示例**（`application-prod.yml`）：

```yaml
gateway:
  enabled: true
  rate-limit-max: 120
  rate-limit-window-seconds: 60
  fail-closed: true
  allowed-origins:
    - https://admin.example.com
  internal-cidrs:
    - 127.
    - 10.
    - 192.168.
```

**限流行为**：超限返回 `429`，响应体 `{"code":"GATEWAY_RATE_LIMITED","message":"请求过于频繁，请稍后重试","data":null,"traceId":"..."}`，并带 `Retry-After` 头；前端 http 层已有统一错误提示（可理解的中文文案）。

**IP 口径**：网关复用全局 `ClientIpResolver`（含可信代理层级 `app.client-ip.trusted-proxy-count`），确保「网关限流键 = 后端审计 IP」，需与 Nginx `X-Forwarded-For` 配置一致。若 Nginx 与后端同机，`clientIp` 可能为 `127.0.0.1`，此时应调整可信代理数或让 Nginx 透传并仅信任其地址。

## 2.5 文件存储后端（E4）

文件物理存储经 `FileStorageRouter` 统一路由，业务 `FileService` 无感：

- **默认（local）**：本地磁盘 `app.file.storage-dir`（默认 `./uploads/file`），与旧版行为完全一致。
- **远端（remote）**：需外部 jar 提供 `ObjectStoragePort` Bean（s3/oss/minio 适配，框架本体零依赖），
  且配置 `app.file.storage-backend=remote` 才切换；两者缺一即回退本地（零回归）。
- DB 的 `storage_path` 列：本地为绝对路径（兼容旧数据），远端为 `remote://{backendId}/{key}` 逻辑 URI；
  下载/删除只依赖 `stored_name`，不解析该列，故远端无本地路径语义也不影响功能。
- 秒传去重（SHA-256）与引用计数删除保护在两种后端下语义一致。

## 3. 灰度双挂过渡（B1 验收④）

分三阶段，全程业务 API 路径不变：

1. **阶段一（直连）**：`gateway.enabled=false`，前端 dev proxy 直连 `:8080`（`VITE_BACKEND_TARGET=http://127.0.0.1:8080`），监控基线 QPS/延迟/错误率。
2. **阶段二（双挂）**：生产启用 `gateway.enabled=true`；灰度批次流量经网关，保留直连入口作为回退；对比两路指标，确认限流误杀率与 CORS 正常。
3. **阶段三（收敛）**：监控确认直连入口无流量后下线直连，仅保留网关路径；后端 CORS 配置可随之移除。

回退预案：任一阶段异常，将 `gateway.enabled` 置 false 并重启实例即回到直连行为（feature-gate 零回归）。

### 3.5 灰度路由决策（E10）

`GatewayCanaryRules` 提供纯函数灰度决策，按优先级：

1. **显式 header**：配置 `gateway.canary-header=X-Canary` 后，请求头 `X-Canary: true` 强制走 canary；
2. **显式 IP**：`gateway.canary-ips` 白名单内的客户端 IP 强制走 canary；
3. **权重分桶**：无显式命中时，按 stickyKey（userId/deviceId/IP）做 0~99 稳定哈希分桶，
   `bucket < gateway.canary-percent` 走 canary，否则 stable。同一 key 恒定同桶，不抖动。

权重边界：`canary-percent=0` 全 stable（默认，零回归），`=100` 全 canary；越界配置自动钳制到 0~100。
显式规则优先于权重，因此权重 0 时仍可用 header/IP 定点验证 canary 实例，验证通过后调权重渐进放量。

> 当前实现为**决策规则层**：规则算出目标（stable/canary）与命中原因，供过滤器/上层集成；
> 实际到不同后端实例的转发由部署侧（Nginx / 服务网格）按决策结果执行，框架本体不引入反向代理依赖。

## 4. 启动方式（原生进程）

```bash
# 1) 依赖：MySQL / Redis 原生安装；执行 db/init/ddl.sql + dml.sql 初始化
# 2) 编译（离线仓库）
cd java-admin-framework
mvn -o clean package -DskipTests
# 3) 启动（网关按需通过配置开启）
java -jar scaffold-admin/target/scaffold-admin-1.1.0.jar \
  --spring.profiles.active=prod
# 4) 前端：npm run build 后由 Nginx 托管 dist/
```

端口：业务 API `8080`；Actuator/ Prometheus `9090`（仅内网）；Swagger `localhost:8080/swagger-ui.html`（仅 local）。

## 5. 验收标准

| 项 | 标准 | 方法 |
|---|---|---|
| 门禁 | `check-all.py` 7/7 EXIT=0；`mvn -o` 编译成功 | 统一门禁 |
| 限流 | 超限返回 429 + `Retry-After` + 可理解提示；并发下恰好放行 max 次 | `GatewayRateLimiterTest` + 压测脚本 |
| CORS | 白名单来源回 Allow-Origin；非法来源 403；预检 204 终结 | `GatewayCorsRulesTest` + `GatewayFilterTest` |
| 内网准入 | actuator 公网 IP 403、内网 IP 放行 | `GatewayRouteRulesTest` + `GatewayFilterTest` |
| QPS 损耗 | 网关路径相对直连 < 10% | `scripts/perf/gateway_bench.py` |
| 零回归 | `gateway.enabled=false` 时行为与旧版一致 | 后端全量测试 + 手动冒烟 |

## 6. 压测脚本

见 `scripts/perf/README.md` 与 `gateway_bench.py`（纯 Python 标准库，零外部依赖）。
