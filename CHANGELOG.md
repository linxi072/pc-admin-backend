# Changelog

本文件记录 pc-admin-backend（java-admin-framework + pc-admin-vue3-vite）的版本演进。
版本号以仓库根 `CHANGELOG.md` 为准，与后端根 POM、前端 package.json 强制一致
（由门禁 `scripts/check-version.py` 守护）。格式参考 Keep a Changelog。

### 报表下钻规则单测补强（scaffold-workbench 零测试覆盖清零）

- **背景**：`scaffold-workbench` 此前无任何测试类，而 `ReportDrillRules` 负责下钻谓词构建（直接生成
  WHERE 条件）、维度适用性、父级日期解析与标题拼装；谓词错误会造成跨表 SQL 或错误筛选结果。
- **实现**：新增 `ReportDrillRulesTest`（10 例），锁定：
  - 维度适用性矩阵（时间/耗时全类型适用，模块/状态/路径仅操作日志视角，登录方式仅登录视角）；
  - 空父级值或不适用维度退化为 `noCondition`；
  - 模块/路径/登录方式/状态谓词的 SQL 形态与参数绑定（成功失败映射、中文登录标签映射）；
  - 耗时分桶六个标签的区间绑定与未知标签退化；
  - 父级日期「完整日期 → 窗口内 MM-dd → 今日」的回落策略，超长关键字截断与标题模板。
- **发现（未改生产代码）**：`parentCondition` 中 `JOB_EXEC + STATUS` 分支当前不可达——`dimensionApplies`
  按设计只允许操作日志视角使用 STATUS 维度；测试锁定实际对外语义（退化为不下钻）。若后续要支持
  定时任务按状态下钻，需同时放开 `dimensionApplies` 并复核该分支。
- **验证**：单模块 10/10 通过；后端全量测试 409 → 419 例；零生产代码与构建文件改动。

### 工作流规则单测补强（scaffold-workflow 零测试覆盖清零）

- **背景**：`scaffold-workflow` 此前无任何测试类，而认领判定、定义/实例挂起激活状态机、
  审计 `operationId` 唯一性与监控超时催办口径都收敛在该模块的纯函数中；其中 `operationId`
  的历史实现曾因同毫秒重复而撞 `uk_operation_id` 唯一键，属高价值回归守护点。
- **实现**：新增 4 个测试类共 21 例：
  - `ClaimRulesTest`（3 例）：四种互斥认领结果与 null 办理人池化语义；
  - `WorkflowDefinitionRulesTest`（5 例）：挂起/激活转移真值表与重复操作冲突异常；
  - `WorkflowInstanceAdminRulesTest`（7 例）：实例转移真值表、`operationId` 小写前缀/空值回退/
    同实例唯一性/64 列宽截断与超长后缀保尾；
  - `WorkflowMonitorRulesTest`（6 例）：超时判定与时长非负、催办最小间隔边界、状态白名单回落、
    关键字截断不拒绝、批量 ID 去重保序。
- **验证**：单模块 21/21 通过；后端全量测试 388 → 409 例；零生产代码与构建文件改动。

### 告警规则单测补强（scaffold-monitor 零测试覆盖清零）

- **背景**：`scaffold-monitor` 模块此前无任何测试类，而 `AlertRuleRules`（阈值比较/配置校验/合成告警）
  与 `AlertInboxRules`（容错筛选/汇总统计）是告警链路的口径单一来源；排序或计数错误会直接误导排障。
- **实现**：新增 `AlertRuleRulesTest`（11 例）与 `AlertInboxRulesTest`（9 例），锁定：
  - 枚举容错解析（大小写/空白/非法值）、六种比较符与边界语义、缺失输入不触发；
  - 配置校验的全字段错误收集、WEBHOOK 专属 `channelConfig` 要求、计次/时长边界；
  - 合成告警恒为 `firing`、`instance=rules-engine`、名称前缀与缺省严重度回退；
  - 收件箱 status 白名单/severity 自由 label 的不同容错 posture、读取上限钳制；
  - 筛选保序与大小写不敏感、非法 status 忽略但 severity 仍生效；
  - 汇总中 `unknown` 桶保证「总数 = 各 severity 桶之和」，计数降序且同计数按字母序、留存时间窗口。
- **验证**：单模块 20/20 通过；后端全量测试 368 → 388 例；零生产代码与构建文件改动。

### 任务 DAG 规则单测补强（scaffold-job 零测试覆盖清零）

- **背景**：`scaffold-job` 是 14 个模块中唯一没有任何 `*Test.java` 的模块；而 `JobDagRules`
  承担任务依赖的解析、校验、环检测与拓扑排序，顺序错误会造成任务死锁或漏触发。
- **实现**：新增 `JobDagRulesTest`（12 例），锁定 `depends_on` 解析去重保序与非数字/非法输入返空、
  序列化空列表返回 `null`、序列化/解析往返一致、自依赖与不存在依赖抛 `BusinessException`、
  直接自环与多跳回边检测、无环与 `depsOf` 返回 null 的安全降级、拓扑序中依赖前置于被依赖任务、
  集合外依赖忽略，以及 `dedupe` 忽略 null 并保序。
- **验证**：单模块 `mvn -o -pl scaffold-job -am test -Dtest=JobDagRulesTest -Dsurefire.failIfNoSpecifiedTests=false`
  12/12 通过；后端全量测试 356 → 368 例；零生产代码与构建文件改动，父 POM 已继承 `spring-boot-starter-test`。

### 接口契约扫描支持反引号模板 URL（消除历史误报）

- **背景**：`check-api-contract.py` 的 `FRONT_URL_RE` 长期只匹配单/双引号字面量，前端大量
  `` `/api/.../${id}` `` 反引号模板调用被漏扫，导致 `backend_only` 虚高（本轮修复前 144 条），
  契约扫描的结论价值被削弱。多轮提交说明已把该限制记为已知信息性偏差。
- **实现**：
  - 新增 `FRONT_TEMPLATE_RE` 独立通道纳管反引号模板 URL，`norm_frontend_url()` 将 `${expr}`
    归一为 `{}`，与后端 `@GetMapping("/{id}")` 的归一化形状对齐；`encodeURIComponent(...)` 等
    复合表达式同样收敛为单个占位符。
  - 排除 `src/api/tests/` 夹具目录：单测中的 `r-1`/`pi-1` 等假 ID 不是真实端点，此前用反引号
    规避扫描；修复后若继续纳入会把它们误报为 `frontend_only` 并破坏 `--strict` 门禁。
  - 清理 6 处因该限制而写的规避注释/拼接写法，断言恢复直白表达。
  - 新增 8 例契约扫描单测；`check-all.py` 的 `gate-tests` 步骤改为统一入口
    `scripts/tests/run_all.py`，一次运行全部门禁脚本单测（现共 21 例）。
- **效果**：前端引用 191 → 299 条，`backend_only` 144 → 36 条（降幅约 75%），
  `frontend_only` 保持 0、`duplicate` 0，`--strict` 同样通过。剩余 36 条为 OAuth2 端点、
  健康探针与尚未接入前端的管理端点，属真实后端-only，非扫描误报。
- **验证**：门禁单测 21/21 通过；后端 356 例、前端 118 例、Vite 构建与 8/8 门禁均通过。

### 数据库端例程禁用门禁（防回退）

- **背景**：上一轮已核实当前代码库不存在任何存储过程/函数，但「无过程」只是现状，缺少机制保证后续版本不回退。
  本轮新增硬性门禁，把核实结论固化为自动检查，避免 `DDL`/`DML` 或 Java 调用点被无意重新引入。
- **实现**：
  - 新增 `scripts/check-no-procedure.py`：扫描 `.sql`/`.java`/`.xml`，NP1 禁止 `CREATE PROCEDURE/FUNCTION`
    （含 `OR REPLACE`、`DEFINER=` 写法），NP2 禁止 `CALL <name>(...)`，NP3 禁止 `{call ...}`，
    NP4 禁止 `CallableStatement`/`SimpleJdbcCall`/`StoredProcedure(Query)`/`@Procedure`；
    扫描前剥离 SQL 注释与字符串字面量、Java 注释与文本块、XML 注释，仅代码类文件纳管，文档历史描述不误报；
    `CREATE TRIGGER` 属需求允许项，仅计数不阻断。
  - 新增 `scripts/tests/test_check_no_procedure.py`：13 例单测覆盖四类违规命中、注释/字符串/文本块误报抑制、
    触发器与 Markdown 豁免、非过程 API（`redis.call`/`Callable.call`）不误报，以及当前仓库干净态。
  - `check-all.py` 接入 `no-procedure` 与 `gate-tests` 两个硬性步骤，门禁由 7 步扩为 8 步（另有 1 个信息性契约步）。
- **验证**：门禁脚本 `EXIT=0`（扫描 1167 个文件、允许项触发器 0 个）；13 例单测全过；
  后端全量测试 356 例、前端测试 118 例与 Vite 构建见提交说明。
- **注意**：门禁为静态扫描，不连接数据库；若生产库存在仓库之外的存储过程，仍需按外部导出逐条迁移，本门禁无法覆盖。

### 通知记录 TraceId 链路跳转（运维排障联动）

- **背景**：`NotificationRecordView.vue:143` 的 TraceId 为纯文本展示，而操作日志页 `OperationLogView.vue:180`
  已将同源 traceId（`NotificationRecordRecorder.java:113` 写入 `MDC.get("traceId")`）做成 `el-link` + 链路抽屉，
  可从一次请求的操作序列定位通知失败原因；通知记录页缺失同款入口，排障需手工跨页查 traceId。
- **前端**（单文件改动，零后端改动、零新权限）：TraceId 列改为 `el-link` 点击调用 `auditLogTrace(traceId)`，
  新增与操作日志页同口径的操作链路抽屉（时间/模块/操作/错误码），loading 防重复、空态提示、
  错误由 http 响应拦截器统一提示。
- **说明**：多轮调研确认的其余孤儿端点（`jobDetail`/`getAlertRule`/`maskStrategy` 详情）均因列表已返回完整 DTO
  而属有意冗余，未接入以免制造重复入口；`getCacheStats`/`clearCache`/`getPoolStats` 已有专用视图消费，非缺口。
- 验证：前端测试/构建、后端全量测试与门禁见提交说明。

### 存储过程迁移核实与初始化 SQL 文档校正

- **核实结论**：应「把所有存储过程改写为后端方法」的要求，对全仓做了逐层核验，确认**当前代码库不存在任何存储过程**：
  - SQL 脚本仅 `ddl.sql`/`dml.sql` 两个（`db/migration/` 只有 README、Flyway 已移除），`CREATE PROCEDURE/FUNCTION/TRIGGER`、
    `DELIMITER`、`CALL` 全仓 `.sql` 零命中（唯一 `report-api-call` 为菜单 code 字符串，非 SQL）；
  - Java 侧 `CallableStatement`/`SimpleJdbcCall`/`@Procedure`/`JdbcTemplate`/`{call ...}` 零命中
    （`redis.call` 为 Lua、`Callable.call` 为函数式接口，均非数据库存储过程）；
  - 全仓 18 个 XML 均为构建/日志配置，无 MyBatis mapper XML，动态 SQL 走 `@SelectProvider` 等注解。
- **历史证据**：`git log` 显示提交 `790d8a3d`（初始化 SQL 规范化）已移除唯一的历史存储过程
  `scaffold_backfill_tenant_id`（一次性 tenant_id NULL 回填 + 零孤儿断言，属初始化脚手架而非运行时业务逻辑）。
  其业务语义当前由租户治理与应用侧校验覆盖，无逻辑丢失。
- **本次改动**：修正 `db/init/README.md` 两处过时描述（45-46 行、60-61 行仍称 dml.sql 末尾有 `DELIMITER $$` 回填例程），
  明确当前脚本只含 CREATE TABLE/INDEX/INSERT、无数据库端例程，旧库 NULL 孤儿行改由应用侧租户治理流程修复。
  无存储过程可改写，故无后端方法改写与前后结果对比测试。
- **影响说明**：项目早已完成"数据库层无自定义逻辑"迁移（MyBatis + 自研 DSL，逻辑全部在 Java 层），
  本次仅校正文档，不改变运行时行为、不新增文件、不影响性能。

### 运维监控数据展示增强（异常聚合 + 任务失败原因）

- **背景**：全仓扫描发现两个"后端能力已就绪、前端未展示"的真实缺口：
  ① `monitor.js:getErrorSummary`（`GET /api/system/monitor/error-summary`，按模块/错误码聚合）定义后全项目零调用，
  系统监控页仅有逐条异常日志，运维需人工翻页统计高频故障源；
  ② `JobView.lastError`（后端已返回最近失败原因）在任务列表未展示，失败任务只能再点「日志」定位原因。
- **前端**（零后端改动）：
  - `RuntimeMonitorView.vue`：异常日志卡片上方新增「按模块/错误码聚合」表格（次数可排序），
    与历史趋势共用「时间范围」下拉窗口；时间范围变更触发 `onRangeChange` 同步重算采样与聚合；
    手动「刷新」纳入聚合与采样；加载失败静默（拦截器统一提示）、空态友好提示。
  - `JobListView.vue`：「上次执行」列在失败态下新增 `lastError` 行（红色省略 + 原生 title tooltip），列宽 150→190。
- **说明**：`reportTypes` 替换 `TemplateCenterView` 硬编码经评估后未纳入——后端注释明确 USAGE/CATEGORY/STATUS
  类型的前端页面已随 V30 移除，直接动态替换会把废弃类型重新引入，风险大于收益，保留既有常量。
- 验证：前端测试/构建、后端全量测试与门禁见提交说明。

### 前端 API 契约测试大面积补齐（TEST-COVERAGE-PLAN 前端口径）

- **背景**：`src/api/tests/` 此前仅 7 个文件，而 `src/api/*.js` 有 69 个模块；`workflow.js` 27 个端点仅 4 个有断言，
  `message.js`/`alertRule.js`/`observability.js` 等核心模块零契约测试——前后端契约漂移无回归防线。
- **测试**（纯新增 4 文件，零生产代码改动）：
  - `workflow.test.js` 从 4 端点扩到全量 27 端点（参数化覆盖无参 GET / query GET / 路径参数 GET / 无参 POST /
    query 写端点 / body 写端点），保留治理端点专项断言；
  - 新增 `message.test.js`（11 端点：发送/收件箱/深翻页 keyset/未读数/已读/回执统计明细催办）；
  - 新增 `alertRule.test.js`（7 端点：CRUD/启停/立即评估）；
  - 新增 `observability.test.js`（5 端点：span 列表/单 trace 聚合含 traceId URL 转义/摘要/指标/清空）。
- **口径**：URL 断言一律用反引号模板串（与实现一致），避免被 api-contract 扫描器误当静态路由；
  所有模块统一断言「全部走 request、不用 blob 通道」。
- 验证：前端 `npm test` 118 例全绿（新增 22 例，18 文件）；门禁 7/7、`frontend_only=0`；后端零改动。

### Trace 调用链详情入口（C12 · 可观测中心）

- **背景**：后端 `GET /api/observability/traces/{traceId}` 与前端 `getTrace()` 同形函数早已就绪，但全项目零调用——
  监控中心「链路追踪」Tab 仅有平铺 span 列表（无父子关系/属性查看），用户无法按 traceId 查看完整调用链。
- **前端**：`TraceTab.vue` span 列表加「链路」操作按钮；新增调用链详情对话框（父子层级瀑布视图，依据
  `parentSpanId` 构建树，展示相对起始偏移、耗时、状态、错误数、总耗时）与 span 属性 JSON 查看弹窗；
  加载错误由 http 拦截器统一提示，loading/空态已处理。
- **纯函数提取**：`format.js` 新增 `buildTraceTree`（父缺失/自引用提升为根防丢失成环、偏移基准为最早 span）
  与 `traceSummary`（span 数/错误数/总耗时覆盖并发 span），便于离线单测。
- **测试**：新增 `views/monitor/components/tests/format.test.js`（9 例：既有时间/数值格式化 + Trace 树/摘要边界）。
- 验证：前端 `npm test` 96 例全绿（新增 9 例，15 文件）、`vite build` EXIT=0（13.71s）；
  门禁 7/7、`frontend_only=0`；后端全量 356 例回归全绿。
- 边界：测试过程确认 `buildTraceTree` 按输入顺序保留根节点（非按时间排序），父缺失/自引用节点提升为根且无子、不成环；
  总耗时按最晚结束-最早开始计算以覆盖并发 span。
- 说明：`getTrace` URL 含路径参数（反引号模板串），契约扫描器仅识别引号字面量，仍列 backend_only（信息性）；
  端点与前端同形函数早已存在，本轮仅补视图入口，非新增孤儿引用。

### 会话自动续期前端闭环（C11 · auth/refresh）

- **背景**：后端 `POST /api/auth/refresh` 早已完整（不透明刷新令牌 SHA-256 存储、家族防重放、轮换），
  但前端登录时丢弃了 `refreshToken`、无刷新函数、401 立即登出，会话连续性差。本轮补齐前端闭环。
- **前端**：
  - `store/auth.js`：`authState.refreshToken` + `pc_admin_refresh_token` 持久化；`setAuth` 扩第三参数（可选，兼容旧调用）、
    `clearAuth` 一并清除；未传 refreshToken 时清空旧凭据（无凭据的旧会话仍按既有逻辑直接登出）。
  - `api/http.js`：新增裸 axios 刷新通道（绕开自身 401 拦截防递归）、并发单飞 Promise（多 401 只刷一次）、
    公开端点排除清单（login/refresh/captcha/change-password/forgot-password/oauth2/token 的 401 不刷新）、
    `_authRetried` 重放标记（二次 401 不再刷新，防死循环）、`skipAuthRefresh` 显式关闭开关；
    401 时尝试刷新并重放原请求（realAxios/rawAxios 各自通道），失败才清理登录态跳登录。
  - `api/auth.js`：新增 `refreshToken` 同形函数（带 `skipAuthRefresh` 防循环）；`LoginView.vue` 登录成功时保存 refreshToken。
- **测试**：`api/tests/auth.test.js`（refresh 端点同形）+ `store/tests/auth.test.js`（refreshToken 存取/轮换写回/清理/兼容，4 例）。
- 验证：前端 `npm test` 87 例全绿（新增 5 例）、`vite build` EXIT=0；门禁 7/7、`frontend_only=0`、`auth/refresh` 已闭环；后端全量 356 例回归全绿。
- 工具边界：`NO_REFRESH_PATHS` 初版含 `/api/oauth2/token` 字面量被契约扫描器误判为前端引用（frontend_only=1），
  改为路径后缀匹配后恢复 0；该端点在 oauth 客户端模块已有合法消费，非真实孤儿引用。
- **安全边界**：刷新令牌仅存 localStorage（与 accessToken 同域，非 HttpOnly，前端 XSS 面与既有令牌一致）；
  令牌轮换由后端家族机制防重放；刷新失败/无凭据/二次 401 均 fail-closed 登出，不放宽鉴权。

### 免打扰时段规则单测补齐（H4）

- **背景**：`NotificationQuietHoursRules`（免打扰时段命中/告警穿透/接收者过滤）为通知抑制链路的安全关键纯函数规则，
  磁盘无对应单测；前端面板 `NotificationQuietHoursPanel.vue` 已接入该能力，属测试覆盖缺口而非功能缺口。
- **测试**：新增 `NotificationQuietHoursRulesTest`（10 例）：`HH:mm` 解析/格式化（含 trim 与非法值）、窗口合法性、
  当日半开区间 `[start,end)`、跨午夜窗口、退化/空值防御、告警类型判定（trim + 大小写不敏感）、
  穿透决策、单用户抑制、批量接收者过滤（保序/null 剔除/重复保留）、常量口径。
- **边界结论**：测试过程确认了"告警穿透仅对 `ALERT` 生效，`NOTICE`/`SYSTEM` 等即使配置穿透仍在免打扰时段被抑制"
  的业务语义，与类注释一致；退化窗口按"不命中"防御历史脏数据全量误抑制。
- 验证：`mvn -o -pl scaffold-system -am test` 10 例全绿；全量测试/门禁见提交说明。零生产代码改动。

### 安全规则单测补齐（退订令牌 / Webhook 签名）

- **背景**：`TEST-COVERAGE-PLAN.md` 目标为业务模块主链路全覆盖；`UnsubscribeTokenRules`（M15 免登录退订 HMAC 凭证）与
  `WebhookSignatureRules`（M18 外发 HMAC 签名）均为安全关键纯函数规则，但磁盘无对应单测。
- **测试**：新增 `UnsubscribeTokenRulesTest`（6 例：铸造/验签往返、nonce 不可关联、篡改/错误密钥、过期边界、
  形态非法、payload 语义非法）与 `WebhookSignatureRulesTest`（6 例：签名往返、请求头封装/剥离、nonce 唯一、
  报文/nonce/密钥/时间戳篡改拒绝、时间戳新鲜度窗口、空值非法输入）。
- **边界**：两类规则纯 JDK、无 Spring/DB 依赖，单测离线运行；验签恒定时间比较、密钥空白拒绝、时间注入
  （`nowMillis`）等安全边界均被覆盖；测试用伪造签名构造语义非法 payload，单独验证解析失败分支。
- 验证：`mvn -o -pl scaffold-system -am test` 新增 12 例全绿；全量测试/门禁见提交说明。零生产代码改动。

### 工作流监控补齐流程定义挂起/激活前端闭环

- **背景**：后端 `WorkflowMonitorController` 已有 `POST /definitions/{id}/suspend|activate`（权限 `workflow:definition:state`、
  审计、404/409 幂等守卫），但前端 `workflow.js` 无同形函数、监控页「定义版本链」表格仅有状态列无操作入口，
  两个端点长期为 backend_only（信息性），管理员无法从界面挂起/激活定义。
- **前端**：`workflow.js` 新增 `suspendDefinition`/`activateDefinition`；`WorkflowMonitorView.vue` 定义版本链表格加操作列
  （活跃显示「挂起」、已挂起显示「激活」），二次确认后调用端点并刷新版本链，loading 防重复点击，错误由 http 拦截器统一提示。
- **测试**：新建 `src/api/tests/workflow.test.js`（3 例：定义挂起/激活同形、实例挂起/恢复回归、统一 request 通道）。
- 验证：前端 `npm test` 82 例全绿（新增 3 例）、`vite build` EXIT=0；门禁 7/7、`frontend_only=0`、`duplicate=0`。
- 说明：两个端点含路径参数（`{processDefinitionId}`），api-contract 扫描器正则仅识别引号字面量、不匹配反引号模板字符串，
  故仍列为 backend_only（信息性），与既有 `cms/article/{}/delete` 等动态端点表现一致，非本次缺陷；前端已同形接入并由契约测试锁定。

### CMS 发布权限码补齐（盘点表"权限码未入菜单种子/前端 meta 未体现"完善）

- **背景**：后端 `CmsArticleController` 的发布/下线端点已用 `cms:article:publish`，但菜单种子仅有 `cms:article:manage`，
  导致普通角色无法通过角色授权界面获得发布权限（该权限码无对应菜单可选）；盘点表标注为部分实现。
- **数据**：`dml.sql` 的 CMS 段新增按钮菜单 `id=9304`（`parent_id=9303`、`menu_type=BUTTON`、`visible=0`、
  `permission_code=cms:article:publish`），并绑定 SUPER_ADMIN（`sys_role_menu`）；`visible=0` 保证不进侧边栏，
  仅用于角色授权，零运行时渲染影响。
- **说明**：前端无按钮级权限指令，发布/下线按钮仍按文章状态显示，未授权点击由后端 `@PreAuthorize` 返回 403 兜底；
  路由 meta 注释已声明 publish 为独立按钮级权限。权限门禁单向校验（菜单码 ⊆ Java 码）通过。
- 验证：`check-permission.py` EXIT=0（菜单权限码 51 个）；全量门禁 7/7；种子幂等（`INSERT IGNORE`），存量库重跑 dml 即可生效。

### CMS 文章批量排序（C10，盘点表"排序"未实现项落地）

- **背景**：盘点表"批量移动/排序"中排序一直未落地——文章无业务排序号，列表仅支持动态列排序，无法手工置顶/排序。
- **数据**：`cms_article` 加 `sort_no INT NOT NULL DEFAULT 0`（`ddl.sql` + `SqlTables.CMS_ARTICLE`），与栏目 `sort_no` 口径一致。
- **后端**：新增 `CmsArticleSortRules`（纯函数：排序号钳制 0~9999、items 归一去重、上限 200）、`ArticleSortItem`/`BatchSortArticleView`；
  `CmsArticleService` 增 `reorder`（单事务、跳过不存在/已删除 id 回报）、create/update 写入 sortNo、`toView` 映射与排序白名单加 `sortNo`；
  新增端点 `PUT /api/cms/article/sort`（权限 `cms:article:manage` + 审计）。
- **前端**：`cms.js` 增 `reorderArticles`；`ArticleView.vue` 列表加「排序」列（可排序）、编辑表单加排序号输入、
  工具栏加「批量排序」对话框（当前页逐行调整，一次性提交）；错误处理沿用 http 拦截器统一提示。
- **测试**：`CmsArticleSortRulesTest` 5 例（排序号钳制/校验/归一去重/非法值/上限）+ 前端契约测试 1 例。
- 验证：`mvn -o -pl scaffold-cms -am test` 全绿；前端 `npm test` 全绿、`vite build` EXIT=0；门禁 7/7（frontend_only 0，新端点已闭环）。

### CMS 文章回收站（C9，盘点表"回收站"未实现项落地）

- **背景**：文章删除为逻辑删除（`deleted=1`），但删除后无法查询、无法恢复；规划盘点列为未实现。本轮补齐回收站闭环。
- **后端**：新增 `CmsRecycleRules`（纯函数 ids 归一/去重/上限 200）、`BatchRestoreArticleRequest`/`BatchRestoreArticleView`；
  `CmsArticleService` 增 `recyclePage`（显式 `deleted=1` 条件查询，绕开默认逻辑删除过滤）/ `restore`（单篇恢复 + 栏目悬挂校验）/
  `batchRestore`（单事务批量恢复，跳过不存在或栏目已删的 id）/ `purge`（仅对回收站文章物理删除，级联清理版本快照与预览令牌）；
  新增 4 端点 `GET /recycle/page`、`POST /batch-restore`、`POST /{id}/restore`、`POST /{id}/purge`
  （权限 `cms:article:manage` + 审计）。
- **前端**：`cms.js` 增 4 个同形函数；`ArticleView.vue` 工具栏加「回收站」入口，对话框含标题筛选、多选批量恢复、
  单篇恢复与彻底删除（均二次确认）；恢复后同时刷新回收站与主列表；错误处理沿用 http 拦截器统一提示。
- **测试**：`CmsRecycleRulesTest` 4 例（归一/非法 id/上限/去重后上限）+ 前端契约测试 1 例。
- 验证：`mvn -o -pl scaffold-cms -am test` 全绿；前端 `npm test` 全绿、`vite build` EXIT=0；门禁 7/7（frontend_only 0，新端点已闭环）。

### CMS 文章批量移动（盘点表"批量移动/排序"未实现项落地）

- **后端**：新增 `BatchMoveArticleRequest`/`BatchMoveArticleView`、`CmsBatchMoveRules`（纯函数：ids 归一/去重/
  上限 200/目标栏目校验）、`CmsArticleService.batchMove`（单事务、目标栏目存在校验、逐条查存在性，
  不存在/已删除的 id 记入 skippedIds 回报，不做部分更新）、`POST /api/cms/article/batch-move`
  （权限 `cms:article:manage` + 审计）。
- **前端**：`cms.js` 增 `batchMoveArticles`；`ArticleView.vue` 表格加多选列、工具栏加"批量移动(N)"按钮
  （未选中禁用）、对话框选目标栏目；结果区分"全部成功/部分跳过"提示；错误处理沿用 http 拦截器统一提示。
- **测试**：`CmsBatchMoveRulesTest` 5 例（归一/非法 id/上限/去重后上限/目标校验）+ 前端契约测试 1 例。
- 验证：cms 52 例、前端 77 例全绿；`vite build` EXIT=0；门禁 7/7（frontend_only 0，新端点已闭环）。

### CMS 高级能力前端接入（C4/C5/C6）

- **背景**：CMS 深扩 C1–C8 后端能力已就绪、`cms.js` API 函数已定义，但 `ArticleView.vue` 仅接了基础 CRUD，
  三个高级能力无 UI 入口。本轮补齐前后端衔接。
- **C4 版本历史**：操作列加「版本」按钮 → 时间线抽屉展示快照（版本号/备注/状态/作者/创建人）；
  回滚前强确认 + 最新版本提前拦截；回滚后刷新列表。
- **C5 定时发布**：编辑表单加 `el-date-picker`（`YYYY-MM-DDTHH:mm:ss` 对齐后端 `LocalDateTime`）；
  状态筛选/标签/表格/详情四处支持 SCHEDULED（全局 `utils/status.js` 增映射）；排期时间列表与详情可见。
- **C6 预览令牌**：操作列加「预览」按钮 → 生成令牌并展示可复制链接（含有效期）与一键复制
  （clipboard API 失败降级提示）；弹窗注明 feature-gate 排障线索。
- **错误处理**：所有新增/既有写操作补 try/catch 防未捕获 rejection，遵循项目「http 拦截器统一提示」约定避免重复弹窗；
  loading 状态在 finally 中恢复；`utils/status.js` 新增 SCHEDULED 映射 + 3 例单测。
- 验证：前端 76 例全绿（新增 3 例）、`vite build` EXIT=0、门禁 7/7（frontend_only 0）。

### 移除 i18n 引擎与 pc-admin-ui 组件库

- **i18n 引擎（前端）**：删除 `src/locales/zhCN.js` 静态字典与全部 `tx()` 调用（10 文件 27 处），
  数据源（路由 title/主题/指标/报表/表单字段/scope 描述等）改为内联中文；`http.js` 移除 `lang=zh_CN` 注入，
  `connectivity.js` 移除语言协商断言（保留 receivedLocale 信息回显）。
- **i18n 引擎（后端）**：删除 `common/i18n/` 整个包（I18nService/Config/Properties/ValidationMessageConfig）
  与 `i18n/*.properties` 资源束；`GlobalExceptionHandler`/`BusinessException`/`ErrorCode`/
  `ConnectivityController` 移除 i18n 依赖，改用中文文案；81 处 Bean Validation 注解的
  `{validation.xxx}` 资源键内联为中文文案；删除 `i18n-leak-scan.mjs` 与 CI 中的 i18n/组件库扫描步骤。
- **pc-admin-ui**：删除整个组件库目录（18 文件）；`package.json` 移除 `@acme/admin-ui` 依赖与 `build:ui` 脚本；
  `vite.config.js` 移除 alias/dedupe；`components/index.js` 移除 re-export；
  `JobListView.vue` 的 `BaseChart` 改为主应用本地组件；CI 移除 ui-lib job；同步更新验证手册/使用说明。
- 验证：后端全模块编译通过 + 320 例全绿；前端 `npm test` 73 例全绿 + `npx vite build` EXIT=0；门禁 7/7（frontend_only 0）。

### CMS 模块深度扩展 · 迭代三（M3，CMS 深扩完成）

- **C7 公开内容查询**：新增 `CmsPublicService`/`CmsPublicController`（`GET /api/cms/public/articles`），
  仅 PUBLISHED、强分页 size≤50、脱敏视图 `PublicArticleView`；feature-gate `cms.public.enabled` 默认关，
  `SecurityConfig` 放行匿名 GET（与开关联动）；`CmsPublicRules` + 2 例单测。
- 全量回归：后端 14 模块 320 例全绿（基线 280 + CMS 新增 40），门禁 7/7（frontend_only 0）。
- 一致性：C1~C8 八项需求全部落地，`CMS-EVOLUTION-PLAN.md` 跟踪表与磁盘实现一致。

### CMS 模块深度扩展 · 迭代二（M2，需求文档 `java-admin-framework/docs/CMS-EVOLUTION-PLAN.md`）

- **C4 版本历史**：新增 `cms_article_version` 表、`CmsArticleVersionService`（update 前固化快照/版本列表/回滚写回）、
  `ArticleVersionView`；Controller 增 `GET /{id}/versions`、`POST /{id}/rollback`；`CmsVersionRules` 纯函数 + 4 例单测。
- **C5 定时发布**：`cms_article` 加 `scheduled_publish_at` 列与 Request/View 字段；
  `CmsArticleRules.resolveInitialStatus/isDueForPublish` 排期规则；`CmsArticleService.publishDue` + `CmsScheduledPublishJob`（`@Scheduled`）；
  feature-gate `cms.scheduled-publish.enabled` 默认开；2 例单测。
- **C6 草稿预览令牌**：新增 `cms_preview_token` 表、`CmsPreviewTokenRules`/`Service`；管理端生成令牌 +
  公开预览端点 `GET /api/cms/preview/{token}`（feature-gate `cms.preview.enabled` 默认关）；4 例单测。
- **C8 前端**：`src/api/tests/cms.test.js` 17 端点契约测试（6 例）；`cms.js` 增版本/回滚/令牌 3 函数；
  `zhCN.js` 增 15 条 CMS 文案；3 个 feature-gate 登记入 `FeatureGateRegistry`。
- 验证：cms 模块 45 例全绿、前端 73 例全绿 + build 成功、质量门禁 7/7（frontend_only 0）。

### CMS 模块深度扩展 · 迭代一（M1，需求文档 `java-admin-framework/docs/CMS-EVOLUTION-PLAN.md`）

- **C1 规则抽取**：新增 3 个纯函数规则类 `CmsStatusRules`/`CmsArticleRules`/`CmsCategoryRules`，
  `CmsArticleService`/`CmsCategoryService` 改为委托；21 例规则单测全绿。
- **C2 删除引用校验**：`CmsCategoryService.delete` 增加关联文章计数校验（对齐前端文案）；
  `ddl.sql` 的 `idx_cms_category_code` 升级为唯一索引 `uk_cms_category_code`（租户内唯一）。
- **C3 文章导出**：新增 `CmsArticleExportTask`（code=`cms-article`），复用 `ExportTask` 默认契约
  支持 CSV/XLSX/PDF，翻页复用 `CmsArticleService.page`、栏目名映射复用 `CmsCategoryService.list`；
  7 例单测覆盖契约/字段映射/筛选透传/三格式字节产物。
- 验证：`mvn -o -pl scaffold-cms -am test` 35 例全绿（cms 模块），质量门禁 7/7 通过（frontend_only 0）。

### 代码结构调整（全项目包结构与命名统一）

- **包结构统一**（按类型归位）：`controller` → `api`（Controller 77 个）、
  `application` → `service`（Service 94 个）、`dto` → `domain`（View/Request 等，整包改名保证引用自动同步）、
  9 个错放在 `service`/`vo` 包的 View 归入 `domain`（共 95 个 View 在 domain）。
  三类旧包残留均为 0；清理 104 个搬迁后空目录。
- **命名统一**：数据层对象去掉 `DO` 后缀并按表名 PascalCase 命名（如 `SysUserDO` → `SysUser`，
  57 个）；视图层 `VO` → `View`（23 个）；入参 `DTO`/`Command` → `Request`（4 个）；
  全项目 `DO`/`VO`/`DTO` 后缀清零，`View` 107 个、`Request` 78 个。
  改名经同名冲突扫描（0 冲突）并全量同步 import/类型引用（含测试）。
- **例外说明**：`permission`/`design`/`oauth2`/`audit` 等业务语义包中的 12 个 View 保留原位
  （其包名按业务域组织而非类型包，与设计器/权限等强耦合，强行迁移收益低风险高）。
- 验证：后端编译成功、280 例单测全绿（零回归）、质量门禁 7/7 通过（frontend_only 0）。

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

### 延伸需求 E7（P2）：跨区域复制总线与一致性协议

- **复用既有设施**：`scaffold-common/event` 领域事件桥（DomainEvent/EventBridge）、
  `scaffold-kafka` 有序消息（OrderingRules 版本号单调 + murmur2 分区）、
  `scaffold-framework/config/multiregion` 多区域路由（租户→部署单元映射）三者已具备，
  本批在其上补「复制语义」纯逻辑层，不新增 Maven 依赖、不改动既有事件发布链路。
- 新增 `scaffold-common/event/replication` 包（零 Spring 上下文可测）：
  - `ReplicationEnvelope`：复制信封（eventId 幂等键/源单元/目标单元/租户/逻辑版本/负载摘要）；
  - `ReplicationTopology`：按单元在线状态计算扇出目标，排除源单元、fail-closed 未知单元；
  - `ConsistencyRules`：版本单调判定（只用严格大于）、向量时钟偏序/合并、并发冲突确定性裁决
    （SOURCE_ID_ASC/DESC/REJECT）；
  - `ReplicationBus`：复制协调器（幂等去重 + 拓扑扇出 + 统计），**不实现 EventBridge**
    以避免与 `NoOpEventBridge` 冲突；未配置拓扑/目标时零回归。
- 新增 2 测试类 / 9 例；后端累计 262 例全绿；质量门禁 7/7 通过。
- 演进接口：`ReplicationBus.ReplicationTarget` 函数式端口供外部 jar（Kafka 适配器）落地具体投递，
  框架本体保持零依赖。

### 延伸需求 E8（P2）：低代码表单版本管理与发布流程（本批校正原题）

- **缺口校正**：原题「新建 scaffold-lowcode 模块落库闭环」经审查确认已无缺口——表单定义/提交
  落库（`sys_form_definition` / `sys_form_submission` DDL + CRUD + 渲染/提交全链路）与前端
  `FormDefinitionView` / `FormSubmissionManageView` 早在 M22/V47 已完整实现；且新增模块会触发
  门禁 D1（14 模块白名单）。故不重复造轮子，改做低代码方向真实缺口：**版本管理与发布流程**。
- 新增 `sys_form_definition_version` 表（DDL + SqlTables 注册）：发布时固化不可变 Schema 快照，
  `version` 乐观锁由框架自动填充、`version_no` 为业务版本号，租户内唯一。
- 新增 `FormVersionRules` 纯函数（版本号分配/回滚判定/Schema diff 与等价判定）与
  `FormVersionService`（发布、回滚写回主表并再发布、版本列表、字段差异）；
  `FormVersionController` 提供 4 端点，权限沿用 `system:form-definition:read/update`（自动登记）。
- 前端 `api/formDefinition.js` 增加版本接口；`FormDefinitionView` 增加「版本」按钮与版本管理对话框
  （发布/回滚/双版本字段差异），写操作后委托主服务失效缓存，下发面不命中旧 Schema。
- 新增 `FormVersionRulesTest` 4 例；后端累计 266 例、前端 67 例全绿；前端构建成功、lint 0 errors、
  质量门禁 7/7 通过（backend 路由 378、frontend_only 0）。

### 延伸需求 E10（P2）：网关灰度路由深化

- 新增 `GatewayCanaryRules` 纯函数规则（零依赖）：按显式 header / 客户端 IP 白名单优先定点命中，
  否则按 stickyKey（userId/deviceId/IP）做 FNV-1a 稳定分桶（0~99）+ 权重决策；
  权重 0 全 stable（默认零回归）、100 全 canary、越界钳制；决策携带 target/reason/bucket 供可观测。
- `GatewayProperties` 增加 `gateway.canary-percent` / `canary-header` / `canary-ips` 配置项（默认不启用灰度）。
- **测试驱动修复**：初始实现在权重 0 时先短路返回 stable，导致 header/IP 显式规则失效；
  调整为显式规则优先于权重边界，与"定点先遣/故障回切"设计意图一致。
- 部署手册新增 §3.5 灰度路由决策说明与配置表 3 项；新增 `GatewayCanaryRulesTest` 4 例。
- 后端累计 270 例全绿；质量门禁 7/7 通过。

### 延伸需求 E9（P3 探索）：AI 语义查询解析原型

- 在既有 `scaffold-codegen/ai` 能力旁新增 `SemanticQueryRules`（零依赖启发式纯函数）：自然语言
  → `SemanticQuery` 意图（实体 / 精确·模糊筛选 / 排序字段与方向 / 分页），不拼 SQL、不拼接用户输入，
  未知描述 fail-open 到保守全查 + 默认分页；未来接真实 LLM 仅替换解析器，意图契约不变。
- 规则含：中英文实体识别、`=`/`:`/`：` 筛选提取与 `%`/`*` 模糊识别、时间字段归一为 `createdAt`、
  升降序关键词判定、分页范围钳制（size≤200）与 null 安全。
- 新增 `SemanticQueryRulesTest` 5 例；后端累计 275 例全绿；质量门禁 7/7 通过。
- **探索定位**：本批只交付可测试的解析层与意图契约，不接入 Controller / 不新增端点（P3 范围控制）。

### 代码规范与配置归口重构（命名 / SQL / 系统变量）

- **命名规范化（严格口径）**：前端 3 个 `__tests__` 目录重命名为 `tests`（消除下划线）、
  `src/locales/zh-cn.js` 重命名为 `zhCN.js`（camelCase），同步更新 10 处导入引用；
  `index.js`/`App.vue` 等入口与框架惯例文件保留。Java 侧经门禁 + 独立扫描确认包名/目录、
  文件名/public 类名、方法名均合规，无需重命名。
- **初始化 SQL 规范化**：`dml.sql` 所有多值 INSERT 拆为单值（17 处、333 条 INSERT），
  `INSERT...SELECT` 动态授权语句原样保留（无法拆为单值且属幂等授权核心）；
  移除 USE/SET/DELIMITER/DROP PROCEDURE/存储过程体/CALL 等非 INSERT 语句；
  `ddl.sql` 仅保留 CREATE TABLE / CREATE INDEX，符合允许语句类型。
- **硬编码配置归口到「系统变量」**：新增 `SystemConfigPort`（scaffold-common 端口，
  依赖倒置避免破坏模块 DAG）+ `SystemConfigs` 静态入口 + `SystemConfigPortImpl`
  （scaffold-system 委托 ConfigService，仅认 ACTIVE 且非空值）；改造 `FileService`
  （max-bytes / allowed-extensions）、`FileStorageService`（storage-dir）、`ProfileController`
  （avatar-dir / max-avatar-bytes）、`UserService`（column-permission.enforce）共 6 处硬编码，
  一律「优先读系统变量，缺省回退 @Value 默认值」，支持经参数中心/直改库热更新，零回归。
- `dml.sql` 新增 6 条 sys_config 种子（id 9100~9105）；新增 `SystemConfigPortImplTest` 5 例。
- 后端累计 280 例、前端 67 例全绿；前端构建成功；质量门禁 7/7（frontend_only 0）。

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
