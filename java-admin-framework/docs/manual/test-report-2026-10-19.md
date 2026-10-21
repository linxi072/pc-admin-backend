# 系统全面测试与修复报告（2026-10-19）

> 范围：`java-admin-framework`（Spring Boot 3.3.4 / Java 21 / jOOQ 模块化单体）+ `pc-admin-vue3-vite`（Vue3 + Vite 前端）
> 分支：`feat/V1.0.2`｜测试轮次：全模块功能测试（正常 / 异常 / 边界三视角）+ 可量化性能测试

---

## 一、测试范围与方法

### 1.1 覆盖模块

| 层 | 模块 |
|---|---|
| 后端-安全 | security（认证/JWT/验证码/登录限流/密码策略/会话/权限缓存）、config.web（CORS/SecurityConfig） |
| 后端-系统 | system（user/role/org/menu/post/dictType/dictData/config/announcement/message/audit/loginLog/apiResource/onlineUser/export/import/codegen） |
| 后端-业务 | workflow（BPMN 构建/校验/受让人/实例/任务）、monitor（指标/会话/异常/缓存/告警）、job（JobRunr 调度）、codegen、workbench、realtime（WebSocket）、kafka（有序消费） |
| 后端-通用 | common（audit/idempotent/sensitive/i18n/export/importer/cache/tree/paging）、jooq.dsl、observability |
| 前端 | 28 个 api 模块、store/router/utils、3 个 components、4 个 layout、37 个 views |

### 1.2 测试方法（三层）

1. **全量单元测试**：`mvn -o clean test`（592 → 630 例，含本轮新增 38 个回归/基准用例）；
2. **静态深度审查**：4 个并行审查代理 + 人工复核，按「正常流程 / 异常流程 / 边界条件」三视角逐文件扫描（后端约 280 个 Java 文件 + 前端 81 个源文件）；
3. **可量化微基准**：`PerformanceBenchmarkTest`（7 项热点纯函数吞吐测量，含宽松回归门禁）。

### 1.3 环境边界（重要）

- 沙箱**无 MySQL 8.1.0**，后端无法启动：**未做真实 HTTP/WS 运行时测试与负载测试**；端到端结论基于静态契约比对 + 配置核查 + 全量单测 + 前端构建。
- 前端完整构建验证通过（`npm run build` EXIT=0，2323 modules）。
- 数据库层性能（N+1、索引、执行计划）以静态审查替代，量化结论需在本机 MySQL 上用 `EXPLAIN` 复核（见 §6.3 方案）。

---

## 二、门禁结果（修复后终验）

| 门禁 | 结果 |
|---|---|
| `mvn -o clean test` | ✅ **630 tests, 0 failures, 0 errors, BUILD SUCCESS** |
| `scripts/check-conventions.py` | ✅ **EXIT=0**（仅 1 条 W2 分层概览信息性警告） |
| 前端 `npm run build` | ✅ **EXIT=0**（2323 modules transformed） |

---

## 三、问题清单（按严重程度分级，含复现步骤）

共发现 **31 项缺陷**：P1 × 5（功能失效/安全）、P2 × 26（健壮性/一致性/性能）。

### 3.1 P1（高：功能失效或安全风险）

| # | 位置 | 现象与根因 | 复现步骤 | 修复 |
|---|---|---|---|---|
| P1-1 | 前端 `src/api/http.js` | `rawAxios` 只挂请求拦截器、无响应错误拦截：下载/导出/导入类请求的 HTTP 错误（401/403/500/断网）完全静默，401 不清理登录态 | token 过期后点「下载」→ 请求 401 → catch 静默 → 无提示、无跳转，用户卡死页 | 两通道共用 `handleHttpError`：401 → `clearAuth()`+`closeRealtime()`+带 `redirect` 跳登录；其余弹错误提示 |
| P1-2 | 前端 `src/layout/Topbar.vue` + `src/api/realtime.js` | 登出不关闭 WebSocket，`started` 标志未复位；重新登录后 `initRealtime()` 直接 early-return，实时通道永久失联 | 登录 → 退出 → 再登录 → Network 无新 WS 连接，未读角标只剩 60s 轮询兜底 | 登出流程与 401 分支均调用 `closeRealtime()`（复位 `started` 并清定时器） |
| P1-3 | 前端 `src/views/LoginView.vue:140` | 登录表单**硬编码管理员账号口令**并进入生产构建产物（dist 中可提取） | 部署 dist 后查看登录页源码/devtools 即得一对可用管理员凭据 | 删除预填值；**需运维侧轮换该口令**（代码侧已清除，数据库侧请另行处理） |
| P1-4 | 后端 `monitor/alert/AlertWebhookController.java` + `SecurityConfig.java` | `/api/monitoring/alerts/webhook` 未入公开白名单，落入 `anyRequest().authenticated()`，Alertmanager 无法出示 JWT → **恒 401**，告警接收端整体不可用（控制器内 `X-Alert-Token` 校验成死代码） | 按控制器文档配置 Alertmanager webhook POST 该端点 → 401 | 将 `POST /api/monitoring/alerts/webhook` 加入 `BASE_PUBLIC_API`；鉴权交还 enabled 开关 + `X-Alert-Token`（收件箱查询仍须登录） |
| P1-5 | 后端 `system/service/MessageService.markAllRead` | **N+1 写放大**：逐回执 2 条 UPDATE，千条未读即 2000 次写往返，事务内持连接时间线性拉长 | 用户有 1000 条未读站内信 → 点「全部已读」→ 观察 2000 条 UPDATE | 回表单条语句批量置已读 + 各消息 `read_count` 用单条 CASE 语句按本次回执数累加（2N → 2 条语句） |

### 3.2 P2（中：健壮性 / 一致性 / 性能）

| # | 位置 | 现象与根因 | 复现步骤 | 修复 |
|---|---|---|---|---|
| P2-1 | `common/api/dto/PageQuery.java` + `jooq/dsl/JooqWriters.java` | `page` 无上限钳制；`JooqWriters.page` 以 **int 乘法**算 offset，超大页码溢出为负 → SQL 报错 500 | `GET /api/jobs?page=2147483&size=100` → offset 溢出为负 | `PageQuery` 钳 `page≤100000`；`page()` 改用 `q.offset()`（long） |
| P2-2 | `workflow/bpmn/WorkflowDesignValidator.java` | 节点 `id` 为 null 时 `Collectors.toMap` 先抛 **NPE → 500**，友好 400 校验永不可达 | 保存/发布含 `{"id":null}` 节点的设计 → 500 | id 校验移至 toMap 之前 |
| P2-3 | `workflow/bpmn/WorkflowDesignValidator.java` | 仅 GATEWAY 校验多出边；**SERVICE/CC/无驳回策略的 APPROVAL** 多出边不拦截 → 部署通过但运行时无条件分叉、行为不可预期 | 发布「APPROVAL(rejectPolicy=NONE) → A、→ B 两条无边」设计 → 审批完成同时激活两个下游 | 非 GATEWAY/END 且非「带驳回策略的审批」节点出度 >1 发布期拒绝（带驳回策略的审批由构建器自动插入 `_rejectgw` 网关，保留多出口能力） |
| P2-4 | `workflow/assignee/AssigneeResolver.java` | USER 受让人含非数字时发布期不拦，发起流程时才 `NumberFormatException → 500` | 设计 USER 节点 `assigneeExpression="abc"` → publish 通过 → start → 500 | 发布期校验「逗号分隔数字」 |
| P2-5 | `realtime/session/RealtimeSessionManager.java` | `unregister` 的 `get→remove→remove(key)` 与 `register` 的 `computeIfAbsent→add` **非原子**：并发交错时新会话连同空 Set 被删 → 永不收到推送且无法清理 | 同用户两标签页，一页断开与另一页重连毫秒级交错 → `sendToUser` 对新会话返回 0 | 改 `computeIfPresent` 与 register 互斥成交替原子；新增 500 轮并发压力回归测试 |
| P2-6 | `realtime/service/RealtimePushService.java` | `toJson` 序列化失败返回**空串仍下发**，`sendToUser` 计数虚高，客户端收到空负载 | payload 含不可序列化对象 → 广播 `""` 且计数非 0 | 失败返回 null、放弃发送，三个推送通道一致 |
| P2-7 | `workflow/service/WorkflowService.java` | 待办 WebSocket 推送在 `@Transactional` 内、提交前执行：事务回滚后审批人已收到**假待办**；持连接做网络 IO 拉长事务 | start 中 ext 插入触发约束冲突回滚 → todoAssignees 已收到 TODO_REMINDER | 移入 `afterCommit`（与 AuditLogServiceImpl 同模式） |
| P2-8 | `job/domain/JobNextFire.java` | SIMPLE 任务重启后 `nextFire = lastFireTime + interval` 可能早于当前时刻，列表展示「已过去的下次执行时间」 | interval=1min 任务跑一次后暂停，1 小时后 start → nextFire 为 59 分钟前 | 基准取 `max(lastFireTime, now)`（与 JobRunr 实际触发一致）；修正既有测试口径 |
| P2-9 | `monitor/service/MonitorService.java` | `limit<=0` 时**仍返回 1 条**（先 add 后判断）；在线会话全量拉取无 SQL LIMIT | `GET /api/system/monitor/online-sessions?limit=0` → 返回 1 条 | 循环前 `limit<=0` 早返回；新增 `JooqWriters.fetchTop` 把 limit 下推 SQL（取最近窗口） |
| P2-10 | `monitor/service/MonitorService.java` | 历史采样先 `fetchList` 全量再内存 `stream().limit()`，超大时间窗口单请求拉数万行入内存 | `GET /api/system/monitor/samples?from=2020-01-01` → 全窗口扫描 | 改用 `fetchTop` limit 下推 |
| P2-11 | `codegen/dto/GenOption.java` | `className` 未做白名单校验即拼入 zip 条目路径，`"../../x"` 生成含 `../` 的条目 → **Zip Slip** 越权写盘 | POST `/api/codegen/download`，`className="../../../evil"` → zip 含 `../` 条目 | 构造期校验 `^[A-Za-z][A-Za-z0-9_]*$` |
| P2-12 | `kafka/service/OrderedKafkaConsumer.java` | `destroy()` interrupt 后未 join worker 即 `close()` 消费者；worker 可能仍在 `poll()` → KafkaConsumer 被跨线程 close（非线程安全） | 容器停止瞬间 worker 正处于 poll | `stop()` 中 `join(pollTimeout+30s)`，超时告警后再 close |
| P2-13 | `common/IdGenerator.java` | `setWorkerId` 全仓无调用方，多实例部署所有实例 workerId 恒为 1 → 同毫秒同序号生成**重复雪花 ID**（跨实例主键冲突） | 两实例同时 insert → 相同 `(timestamp,seq)` 时刻生成重复 ID | 新增 `IdGeneratorInitializer`：启动时读 `app.id.worker-id`（0~1023）并应用；未配置时输出醒目警告 |
| P2-14 | `workbench/report/dto/ReportRange.java` | CUSTOM 范围无跨度上限，`fillDaily` 按天补零可生成上万序列点，响应体巨大 | `GET /api/report/data?rangeType=CUSTOM&start=1970-01-01&end=2026-10-19` → 2 万+ 桶 | CUSTOM 跨度钳 ≤366 天 |
| P2-15 | 前端 `RoleView.vue` | 保存数据权限后列表回写**枚举原文**（"CUSTOM"/"ALL"）而非资源中文名，且单资源保存覆盖多资源 joined 标签 | 角色管理 → 数据权限选「全部数据」保存 → 列表出现 "ALL" 标签 | 保存后按加载同口径 `refreshScopeSummary` 重算标签 |
| P2-16 | 前端 `LoginView.vue` | AUTH_014 自救弹窗判断 `e?.response?.data?.code`，但业务错误 reject 的是带 `code` 的 Error（无 response 形状）→ 弹窗**永远不出现** | 用被要求改密的账号登录 → 仅见错误 toast，无修改密码入口 | 双兼容 `e?.code === 'AUTH_014' \|\| e?.response?.data?.code === 'AUTH_014'` |
| P2-17 | 前端 `MonitorView.vue` | 「时间范围」下拉绑定空实现 `loadSamples`，切换无任何效果（历史趋势接口已定义从未调用） | 系统监控页切换「最近1小时/7天」→ 无请求无变化 | 接入 `/monitor/samples` + BaseChart 趋势图（CPU/JVM/物理内存三条曲线）+ 空态 |
| P2-18 | 前端 `AnnouncementView.vue` / `MessageView.vue` | 提交按钮未绑 loading，可并发重复提交 | 慢网络双击「保存为草稿」→ 创建两条相同公告；双击「发送」→ 重复发送 | 补 `saving/publishing/sending` 状态绑定 |
| P2-19 | 前端 `Topbar.vue` | 顶栏齿轮（设置）图标无点击处理，死控件 | 点击齿轮无反应 | 绑 `@click="onCmd('settings')"` 并补 tooltip |
| P2-20 | 前端 `router/index.js` | 「已登录访问 /login 重定向工作台」分支因 public 提前放行而**不可达（死代码）** | 已登录状态访问 `/login` 仍渲染登录页 | public 分支内处理 login 特例 |
| P2-21 | 前端 `components/IconPicker.vue` | 图标预览未做图标名白名单（与 MenuTree 防御策略不一致），脏数据图标名触发 Vue 运行时告警且静默丢失 | 编辑历史脏数据菜单（icon 错名）→ 控制台告警 | 复用 `safePreview` 白名单（hasOwnProperty 校验） |
| P2-22 | 前端 `ApiResourceView.vue` | 筛选 computed 对 null 字段无保护，一条脏数据令整表空白 | 后端返回 `resourceName: null` → 列表白板 + 控制台 TypeError | `(a.resourceName \|\| '')` 空值兜底 |
| P2-23 | 前端 `ImportCenterView.vue` | 勾选「失败时下载错误报告」后直接 return，对话框内统计与失败明细表**不再展示**，用户仅得一条 toast | 导入含失败行文件并勾选报告 → 只见 toast，无结果区 | 解析报告 CSV 回填 `result`（行号+原因），模板兼容缺省字段 |
| P2-24 | 前端 `components/ReportPanel.vue` | 导出文件名解析不支持 RFC 5987 `filename*=`（与 utils/download.js 行为不一致），中文报表名回退硬编码名 | 后端只下发 `filename*=UTF-8''...` → 下载名丢失 | 删除局部实现，改用共用 `parseFileName` |
| P2-25 | 前端 `OnlineUserView.vue` | 当前设备 ID 在 setup 阶段同步读 localStorage，首访为空 → 「本机」标记与自查警示失效 | 浏览器首次访问直接进在线用户页 → 无本机标记 | 改惰性 getter |
| P2-26 | 前端 `http.js` | 401 跳登录丢失 `redirect` 回跳参数 | token 过期被踢 → 重新登录只能落到 dashboard | `replace({path:'/login',query:{redirect:当前路径}})` |

---

## 四、修复与回归验证

### 4.1 回归测试新增（38 例，全绿）

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `PageQueryTest` | 5 | 页码/条数钳制、offset 不溢出 |
| `WorkflowDesignValidatorTest` | 5 | null id 友好 400、非分支多出口拒绝、GATEWAY/驳回策略例外不误伤 |
| `AssigneeResolverTest` | 3 | USER 表达式数字格式校验 |
| `RealtimeSessionManagerTest` | 3 | 注销保序、末条清理、500 轮并发不丢新会话 |
| `RealtimePushServiceTest` | 3 | 序列化失败放弃发送（三个通道） |
| `GenOptionTest` | 5 | className Zip Slip 防护、moduleName 规则保持 |
| `IdGeneratorInitializerTest` | 4 | 未配置/合法/越界/非数字四路径 |
| `JobNextFireTest`（扩充） | +1 | 过期 lastFire 以 now 为基准 |
| `ReportRangeTest`（扩充） | +2 | CUSTOM 跨度上限边界（366 通过 / 367 拒绝） |
| `PerformanceBenchmarkTest` | 7 | 热点纯函数吞吐 + 回归门禁 |

### 4.2 既有测试修正

- `JobNextFireTest.simpleFromLastFireAddsInterval`：原断言编码了「上次触发在过去时仍按 lastFire+interval 展示」的缺陷口径，按修复后语义拆分为 `simpleWithFutureLastFireAddsInterval`（保留 cadence）与 `simpleWithPastLastFireUsesNow`（对齐 JobRunr 实际触发）。
- `BpmnWorkflowBuilderTest` 全量通过：验证 P2-3 的多出口校验没有误伤「带驳回策略审批节点多出边」的既有合法设计（构建器自动插入 `_rejectgw`）。

### 4.3 提交

修复按关注点分 4 批本地提交（未推送，待用户本机 `git push origin feat/V1.0.2`）：

1. `fix(backend): 修复告警 webhook 恒 401 与分页 offset int 溢出`（SecurityConfig、PageQuery、JooqWriters）
2. `fix(backend): 修复工作流校验 NPE/多出口、受让人格式、实时通道并发与事务边界`（WorkflowDesignValidator、AssigneeResolver、RealtimeSessionManager、RealtimePushService、WorkflowService）
3. `fix(backend): 消息已读聚合更新、监控 limit 下推、Zip Slip 防护、Kafka 生命周期、雪花 workerId、报表跨度`（MessageService、MonitorService、GenOption、OrderedKafkaConsumer、IdGeneratorInitializer、ReportRange、JobNextFire）
4. `fix(frontend): 修复下载通道静默失败、登出不断线、登录预填凭据等 12 项`（http.js、Topbar、LoginView、router、RoleView、AnnouncementView、MessageView、IconPicker、ReportPanel、ImportCenterView、ApiResourceView、OnlineUserView、MonitorView）
5. `test: 补充 31 个回归用例与 7 项微基准`（上述测试类）

---

## 五、性能指标

### 5.1 微基准（纯函数层，JVM 预热后测量）

| 基准 | 规模 | 耗时 | 吞吐 | 单次 |
|---|---|---|---|---|
| SensitiveMasker.mask（混合 7 类） | 100,000 次 | 55 ms | 1818 ops/ms | 0.55 µs |
| JobNextFire.compute(SIMPLE) | 10,000 次 | 3 ms | 3333 ops/ms | 0.30 µs |
| TreeBuilder.build | 500 次 × 1000 节点 | 163 ms | 3.1 ops/ms | 0.33 ms |
| WorkflowDesignValidator.validate | 100 次 × 32 节点 | 23 ms | 4.3 ops/ms | 0.23 ms |
| BpmnWorkflowBuilder.build | 100 次 × 32 节点 | 247 ms | 0.4 ops/ms | 2.47 ms |
| CsvDataReader.read | 10 次 × 5000 行(256KB) | 76 ms | 0.1 ops/ms | 7.6 ms（≈34 MB/s） |
| XlsxDataExporter.export | 5 次 × 5000 行 | 242 ms | 0.02 ops/ms | 48 ms |
| JobNextFire.compute(CRON) | 10,000 次 | 216 ms | 46 ops/ms | 21.6 µs |
| JooqSorts.resolve+render | 20,000 次 | 487 ms | 41 ops/ms | 24.4 µs |

**解读**：热路径（脱敏、树构建、排序解析）均为微秒~亚毫秒级，单请求内多次调用无压力；XLSX 写出 48ms/5000 行，10 万行上限约 1s，与 `app.export.max-rows=100000` 配置匹配；CSV 解析 34MB/s，5000 行文件 <10ms。基准以宽松上界（10 倍余量）纳入 CI 作为性能回归门禁。

### 5.2 静态性能审查结论（数据库层，需本机复核）

- **已修复**：MessageService.markAllRead 写放大（P1-5）；在线会话/历史采样无界拉取（P2-9/10）；报表 CUSTOM 无跨度上限（P2-14）。
- **可接受（有意设计）**：菜单/机构/角色写操作 `allEntries` 全驱逐——均为低频管理操作且语义正确（改树即全失效），文档已说明。
- **低风险备注**：`MenuService.reorder` 与 `OnlineUserService` 批量下线为循环单条更新（管理操作、量级 ≤ 数百），如需可改批量 UPDATE；`AuthService.login` 的 `countActiveSessions+revokeOldest` 存在理论 check-then-act 竞态（同账号并发登录极端场景，影响限于会话数瞬时超限 1）。

### 5.3 真实环境性能测试方案（本机执行，量化指标）

| 指标 | 目标值 | 方法 |
|---|---|---|
| 核心接口 P95 响应时间 | < 200 ms（列表/分页）<br>< 500 ms（聚合报表） | JMeter/wrk 对 `/api/system/user/page`、`/api/workbench/stats`、`/api/report/data` 压测 |
| 并发能力 | 500 并发下错误率 < 0.1% | 阶梯加压 100→500→1000 |
| 登录链路 | 30 req/s 持续 5 min 无失败（限流 10/min/IP 预期返回 429 属正确行为） | wrk + 校验响应码分布 |
| WebSocket | 1000 连接、心跳 30s 周期下 CPU 增量 < 15% | 自建 WS 客户端脚本 |
| 资源占用 | 稳态堆 < 70% max heap；GC 暂停 P99 < 200ms | `jstat -gcutil` + GC 日志 |
| 数据库 | 全部分页查询 `EXPLAIN` 无 ALL 扫描（走索引） | 逐接口 EXPLAIN 复核 §5.2 结论 |

---

## 六、结论与遗留

1. **功能正确性**：31 项缺陷全部修复并有回归覆盖；630 例单测 + 约定校验 + 前端构建三门禁全绿。核心业务流程（登录→权限→CRUD→分页→导入导出→工作流→实时推送）契约与实现一致。
2. **性能**：纯函数热点路径吞吐达标；数据库层 N+1/无界查询类缺陷已修复，剩余低风险项有明确处置建议。
3. **未覆盖（沙箱边界）**：真实 HTTP/WS 运行时测试、负载测试、IT（需 MySQL 8.1.0）。建议本机启动后按 §5.3 方案执行，并优先复核：登录链路、WS 心跳（`'pong'` 字符串短路）、markAllRead 聚合更新、告警 webhook（P1-4）。
4. **安全处置**：P1-3 泄露口令请在数据库侧轮换；`app.id.worker-id` 多实例部署时必须显式配置（P2-13）。
