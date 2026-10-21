# 告警收件箱管理（Alert Inbox Management）

> 模块路径：`com.acme.scaffold.monitor.alert`（接收链路）+ `monitor.alert.service` / `monitor.alert.controller`（管理面）+ `monitor.alert.rules`（纯函数规则）+ `monitor.dto`（视图）
> 关联：Alertmanager webhook 接收（`AlertWebhookController`）、N+1 查询探测（`monitor/detection`）、可观测性（`observability`）

## 1. 背景与缺口

告警接收链路自 M 系列早期即已交付：Alertmanager 通过 `webhook_configs` 将告警 POST 到
`POST /api/monitoring/alerts/webhook`，`AlertReceiverService` 解析负载、写入**内存收件箱**
（有界环形，默认 200 条）并发布 `AlertReceivedEvent`。但在本轮之前，管理侧存在三个真实缺口
（均经代码核查确认，非文档自述）：

| 缺口 | 现状（本轮前） | 风险 |
|---|---|---|
| 读取端点无权限 | `GET /api/monitoring/alerts/webhook/inbox` 匿名可拉取全部收件箱 | 告警数据属敏感运维信息（实例地址、错误率阈值），越权面真实存在 |
| 领域对象直出 | 该端点直接返回 `AlertInboxEntry` 领域对象 | 违反全项目「*View DTO 只读投影」惯例，字段增减直接破坏前端契约 |
| 无管理能力 | 无 severity/status 筛选、无汇总、无清空 | 复盘只能全量翻列表；`AlertReceivedEvent` 发布后无任何监听器（扩展点死端，见 §6） |

## 2. 模块结构

```
monitor/alert/
├── AlertWebhookController.java      # 存量：webhook 接收（机器向，令牌鉴权）+ 兼容读取端点（本轮补权限）
├── AlertReceiverService.java        # 存量：接收/入箱/事件发布；本轮增量增加 capacity() 与 clearInbox()
├── AlertPayload.java                # 存量：webhook 推送体（v3 兼容）
├── AlertProperties.java             # 存量：monitoring.alert.* 配置
├── AlertReceivedEvent.java          # 存量：入箱后事件（扩展点，见 §6）
├── AlertInboxEntry.java             # 存量：内存条目（领域对象）
├── rules/AlertInboxRules.java       # 新增：纯函数（筛选归一/limit 钳制/汇总）
├── service/AlertInboxService.java   # 新增：管理面编排（只读快照 + 整体清空）
└── controller/AlertInboxController.java  # 新增：/api/monitoring/alerts/inbox 管理端点（人向，RBAC）
monitor/dto/
├── AlertInboxItemView.java          # 新增：条目视图（receivedAt→epochMillis，summary 截断）
└── AlertInboxStatsView.java         # 新增：汇总视图（容量/分布/时间戳）
```

## 3. 接口与权限

统一前缀 `/api/monitoring/alerts/inbox`，权限 `system:monitor:read`
（与 `MonitorController`、`NPlusOneController` 同权；权限码由 `ApiResourceAutoSyncRunner`
启动扫描 `@PreAuthorize` 自动登记并授予 SUPER_ADMIN，无需手工播种）。

| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/api/monitoring/alerts/inbox` | `severity`（可选，大小写不敏感）、`status`（可选，firing/resolved）、`limit`（可选，默认 100） | `Result<List<AlertInboxItemView>>` | 最新在前；limit 按收件箱实际容量自适应钳制 |
| GET | `/api/monitoring/alerts/inbox/stats` | — | `Result<AlertInboxStatsView>` | 容量/总数/状态分布/severity 分布/最新与最旧留存时间 |
| DELETE | `/api/monitoring/alerts/inbox` | — | `Result<Integer>` | 整体清空，返回清理条数（已发布的事件不受影响） |

请求示例：

```bash
# 只看 critical 且仍在 firing 的告警，最多 20 条
curl -H "Authorization: Bearer <token>" \
  "$BASE/api/monitoring/alerts/inbox?severity=critical&status=firing&limit=20"

# 汇总
curl -H "Authorization: Bearer <token>" "$BASE/api/monitoring/alerts/inbox/stats"

# 一键清空（返回清理条数）
curl -X DELETE -H "Authorization: Bearer <token>" "$BASE/api/monitoring/alerts/inbox"
```

## 4. 快速启用

无需新增配置——复用既有 `monitoring.alert.inbox-capacity`（`application.yml` 第 226~231 行）：

```yaml
monitoring:
  alert:
    webhook:
      enabled: true
      token: ${ALERT_WEBHOOK_TOKEN:}
    inbox-capacity: 200   # 收件箱容量；管理端 limit 上限按此自适应
```

Alertmanager 侧配置（接收链路，与本模块管理面正交）：

```yaml
receivers:
  - name: 'app-inbox'
    webhook_configs:
      - url: 'http://<app-host>/api/monitoring/alerts/webhook'
        http_config:
          authorization:
            credentials: '<ALERT_WEBHOOK_TOKEN 对应值>'   # 以 X-Alert-Token 头发送
```

## 5. 关键设计点

1. **读写分离**：接收端点（`/webhook`）面向机器、令牌鉴权、无 RBAC；管理端点
   （`/inbox`）面向运维人员、RBAC 权限。两者共用同一内存收件箱。
2. **不提供单条删除**：收件箱是「最近 N 条」的有界窗口，单条删除会制造「跳过某条」的
   错觉；清空（整体丢弃窗口）语义清晰无歧义，返回条数便于审计。
3. **容错查询 posture**：与登录日志/短信记录/幂等记录页一致——非法筛选值**忽略而非报错**
   （`AlertInboxRules.normalizeStatus` 白名单外返回 `null` = 不附加条件），管理页粘贴即搜。
4. **汇总守恒**：severity 分布含 `unknown` 桶（条目缺 severity 时归入），保证
   「总数 = 各 severity 桶之和」，畸形推送不在汇总里消失；分布按计数降序输出，前端免排序。
5. **快照读取**：管理面读取的是 `AlertReceiverService.inbox()` 返回的副本，筛选/截断在副本上
   完成，不触碰接收线程的在途状态。
6. **存量端点硬化**：`GET /webhook/inbox` 补 `system:monitor:read` 权限并标注 deprecated 注释
   （保留原返回形态仅为兼容旧前端/脚本）；新代码一律使用管理端点。
7. **多实例说明**：收件箱为进程本地视图，多实例部署下各实例只见本实例收到的告警；
   跨实例汇总需走指标/日志侧（与 N+1 疑似事件留存同口径）。

## 6. 关于 AlertReceivedEvent（扩展点）

`AlertReceivedEvent` 在入箱后发布，当前**无应用内监听器**——这是有意的：事件是留给
「转发外部网关 / 站内信 / WebSocket 推送」等集成方的扩展点（实时推送可直接复用
`realtime` 模块的会话管理）。如需内置消费者，建议以独立 `@EventListener` 组件接入，
勿在接收线程内做阻塞 IO（会拖慢 webhook 响应）。

## 7. 测试

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `AlertInboxRulesTest` | 11 | 状态/severity 归一、limit 钳制（含容量误填保底）、纯筛选顺序与容错、汇总守恒/时间戳/null 安全、截断 |
| `AlertInboxServiceTest` | 6 | 列表字段映射与筛选委托、limit 自适应、空箱与 summary 截断、汇总透传、清空委托 |
| `AlertInboxE2eTest` | 7 | HTTP 契约（列表/汇总/清空）、参数透传分层守护、存量端点兼容、服务层异常经真实 handler 映射 |

存量回归：`AlertWebhookControllerTest`（4 例）与 `AlertReceiverServiceTest`（3 例）全绿
（本轮对两个类只有增量方法，无行为变更）。

## 8. 离线构建与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn -o clean test   # 852 例全绿（2026-10-21 运行时联调轮由 848 增至 852）
python3 scripts/check-conventions.py                                            # EXIT=0
```

无新增外部依赖、无 DDL、无配置变更（复用 `monitoring.alert.inbox-capacity`）。
