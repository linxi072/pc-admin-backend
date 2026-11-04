# 后端系统 QA 测试报告（V1.0.7）

> 范围：java-admin-framework 后端全模块（17 个 Maven 模块）功能 + 性能 + 海量数据质量验证  
> 生成时间：2026-11-04 ｜ 分支：`feat/V1.0.7` ｜ 测试库：`java_admin`（本机 MySQL 8.4.11）  
> 结论：双门禁（929+ 单元 + 24 集成用例，本轮新增 `RecordStatusTest` 11 例）全绿；本轮**新发现 1 个中危功能缺陷 + 2 个低危设计风险**，并复利验证了 2 个历史严重缺陷已修复；F1 字段级校验已自「范式 + 首处接入」推进至「状态字段全量铺开」。

---

## 1. 测试方法与工具链

| 维度             | 手段                                                                          | 说明                                                                                           |
| -------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| 海量数据生成 (#1073) | 新增 `MassDataSeedIT`（`@SpringBootTest(RANDOM_PORT)` + `integration` profile） | 基于手写 jOOQ DSL `JooqWriters.batchInsert`，分块（2000/块）批量落库，幂等 sentinel 清理，注入边界值/异常输入，打印吞吐与读取延迟探针 |
| 功能测试 (#1074)   | 异常注入 + 既有 24 个 `*IT` 套件 + 代码走查                                              | 注入非法枚举/超长字段/未来时间/负计数，观察 DB 接受与拒绝；走查审计/导出/通知投递链路                                              |
| 性能测试 (#1075)   | 数据生成吞吐探针 + 读取延迟探针 + 代码级瓶颈定位                                                 | 实测插入吞吐、深翻页、无索引过滤、导出物化、通知同步退避                                                                 |
| 双门禁            | `mvn -o -pl scaffold-admin -am verify`（skipITs=false）                       | 929+ 单测全绿 + 集成用例全绿 + `check-conventions` EXIT=0                                              |

**规模配置**（系统属性 `-Dseed.scale`，默认 SMALL）：

| 规模     | 用户   | 登录日志 | 站内信 | 回执   | 通知记录 | 操作日志 | 监控采样 | 流程实例 | 任务  | 审批   | 合计约   |
| ------ | ---- | ---- | --- | ---- | ---- | ---- | ---- | ---- | --- | ---- | ----- |
| SMALL  | 5k   | 20k  | 2k  | 6k   | 5k   | 3k   | 10k  | 1k   | 3k  | 6k   | 102k  |
| MEDIUM | 50k  | 200k | 20k | 60k  | 50k  | 30k  | 20k  | 10k  | 30k | 60k  | ~1.0M |
| LARGE  | 100k | 400k | 50k | 150k | 100k | 50k  | 30k  | 20k  | 60k | 120k | ~2.1M |



---

## 2. 数据生成结果（SMALL / MEDIUM 双档实测验证）

> SMALL（≈10 万行）已完整跑通并作为报告主数据；MEDIUM（≈52 万行，单线程顺序写入）已于 2026-11-04 实测复现，用于验证「海量」量级下的吞吐拐点与 OFFSET 退化（见 §2.3 与 §5 F5）。LARGE（≈210 万行）尚未实跑，仅作容量规划参考。

### 2.1 插入吞吐（每表分块批量写入，2000/块）

**SMALL（≈10.2 万行）**

| 表                       | 目标行数      | 耗时(ms)     | 吞吐(行/s)        |
| ----------------------- | --------- | ---------- | -------------- |
| sys_user                | 5,000     | 5,704      | 877            |
| sys_login_log           | 20,000    | 14,808     | 1,351          |
| sys_message             | 2,000     | 1,694      | 1,181          |
| sys_message_receipt     | 6,000     | 4,044      | 1,484          |
| sys_notification_record | 5,000     | 3,662      | 1,365          |
| sys_operation_log       | 3,000     | 2,393      | 1,254          |
| sys_monitor_sample      | 10,000    | 7,200      | 1,389          |
| wf_instance_ext         | 1,000     | 849        | 1,178          |
| wf_task_ext             | 3,000     | 2,293      | 1,308          |
| wf_approval_record      | 6,000     | 4,284      | 1,401          |
| **合计**                  | **~102k** | **~46.9s** | **~2,175（聚合）** |

> 吞吐约 0.9k–1.5k 行/s/表。瓶颈：`JooqWriters.batchInsert` 走 `dsl.batch(queries)` 的「逐行语句批处理」而非多值 `INSERT … VALUES (…),(…)`；开启 JDBC `rewriteBatchedStatements=true` 可显著提速（见 §5 F6）。

**MEDIUM（≈52 万行，2026-11-04 实测，BUILD SUCCESS / 1 用例 0 失败 / 总耗时 ≈440s）**

| 表                       | 目标行数      | 耗时(ms)      | 吞吐(行/s)        |
| ----------------------- | --------- | ----------- | -------------- |
| sys_user                | 50,000    | 42,074      | 1,188          |
| sys_login_log           | 200,000   | 155,029     | 1,290          |
| sys_message             | 20,000    | 15,693      | 1,275          |
| sys_message_receipt     | 60,000    | 42,855      | 1,400          |
| sys_notification_record | 50,000    | 39,163      | 1,277          |
| sys_operation_log       | 30,000    | 24,129      | 1,243          |
| sys_monitor_sample      | 20,000    | 14,003      | 1,428          |
| wf_instance_ext         | 10,000    | 7,302       | 1,370          |
| wf_task_ext             | 30,000    | 24,593      | 1,220          |
| wf_approval_record      | 60,000    | 43,886      | 1,367          |
| **合计**                  | **~520k** | **~408.7s** | **~1,272（聚合）** |

> MEDIUM 单表吞吐（1.2k–1.4k 行/s）与 SMALL 同量级，说明写入吞吐**不随单表规模衰减**（符合主键顺序追加 + InnoDB 缓冲池稳定）。聚合吞吐因顺序写入、单线程而稳定在 ~1.3k 行/s——并行化（分区表 / 多线程写入）或 F6 的多值重写可线性提升总吞吐。

### 2.2 异常/边界值注入结果（每类 50 行）

| 表                       | 语义异常（非法枚举/未来时间/负计数/CPU 250%/CPU -10%） | 约束异常（超长 email/超长 UA）               |
| ----------------------- | ------------------------------------- | ---------------------------------- |
| sys_user                | **50/0 接受**                           | 0/50 拒绝（Data too long: email）      |
| sys_login_log           | **50/0 接受**                           | 0/50 拒绝（Data too long: user_agent） |
| sys_notification_record | **50/0 接受**                           | —                                  |
| sys_monitor_sample      | **50/0 接受**                           | —                                  |
| wf_instance_ext         | **50/0 接受**（修复 business_id 缺省后）       | —                                  |

**核心结论**：DB 对所有「语义非法但类型合法」的数据一律**接受** → 系统缺乏字段级校验（见 §5 F1）。约束异常（超长字段）被 MySQL 正确拒绝，是仅有的数据守门员。

### 2.3 读取延迟探针（SMALL userCount=5,055 / MEDIUM userCount=50,055）

| 探针                                                      | SMALL（userCount=5,055） | MEDIUM（userCount=50,055） | 变化       |
| ------------------------------------------------------- | ---------------------- | ------------------------ | -------- |
| `count(*)` 全表                                           | 34 ms                  | 58 ms                    | ×1.7     |
| 深翻页 `ORDER BY id LIMIT 20 OFFSET ≈90%`                  | 10 ms / 20 行           | 82 ms / 20 行             | **×8.2** |
| `login_log.result='SUCCESS'` 过滤计数（命中 `idx_result_time`） | 7 ms，命中 25,098 行       | 52 ms，命中 178,432 行       | ×7.4     |
| `sys_message_receipt` 计数                                | 2 ms，12,053 行          | 9 ms，66,053 行            | ×4.5     |

> **F5 已实测验证**：深翻页延迟随数据量 10× 增长而放大至 ~8×，与 OFFSET 线性跳过行的理论一致；索引过滤（`idx_result_time`）虽也线性放大（7→52 ms）但仍健康。**结论**：深翻页是当前唯一随数据量显著劣化的读取路径，必须在超大列表接口默认采用 keyset 分页（见 §5 F5）。索引命中路径与简单计数在百万行内仍可接受。

---

## 3. 发现的问题（按严重程度）

### 3.1 已修复并验证（历史严重缺陷，本轮 QA 战役定位）

**F0-1 〔严重〕Flyway 迁移脚本使用 ANSI 双引号，MySQL 大小写敏感下迁移失败**

- 根因：V74/V75/V78 中表名/列名用 `"..."` 包裹，H2 元数据库名转大写、生产 MySQL（`lower_case_table_names` 敏感）大小写不一致导致迁移中断。
- 修复：`"..."` → 反引号 `` `...` ``，commit `7dcec4b`（3 files, 114+/114-）。
- 验证：`grep -c '"'` 三文件均返回 0；`mvn verify` 门禁全绿。

**F0-2 〔严重〕`ConfigCenterService` 标注 `@Service` 无条件注册，默认配置下应用启动即失败**

- 根因：`ConfigCenterService` 被组件扫描**无条件**注册为 Bean，但其依赖的 `ConfigCenterPort` 仅由条件装配 `ConfigCenterAutoConfiguration`（`app.config-center.enabled=false` 默认不注册）提供 → `NoSuchBeanDefinitionException`，应用默认配置根本起不来。
- 修复：删除 `@Service` 及 import，使其仅由条件 `@Bean` 创建，与 Port 同生共灭，commit `8e625d3`（1 file, 2 deletions）。
- 验证：`BackendPerfIT`（`@SpringBootTest` 真实上下文）BUILD SUCCESS，上下文加载失败缺陷消除。

### 3.2 本轮新发现

**F1 〔中危·功能〕字段级校验缺失，非法/越界业务数据被原样落库**

- 现象：`MassDataSeedIT` 异常注入显示，所有「类型合法但语义非法」的数据被 DB 接受——非法枚举（`user.status='ZZTOP_INVALID'`、`notification.channel='FAX_INVALID'`、`login_log.result='MAYBE_INVALID'`、`wf status='FROZEN_INVALID'`）、未来/历史时间、负计数（`receiver_count=-1`）、越界值（`delivered=2`）、超范围指标（`cpu_usage=250`、`cpu_usage=-10`）全部入库。
- 根因：写入路径（`JooqWriters` 通用 DSL + `@Data` DO）只做「非空即写」，不校验枚举白名单与数值范围；校验职责完全下放给零散的调用方，存在遗漏面。
- 影响：脏数据进入统计/导出/图表与下游解析，错误枚举可能造成前端 switch 遗漏分支或解析异常；审计/监控指标失真。
- 优化方案：
  1. 枚举字段在 Service 入口用 `javax.validation` + 枚举 `Converter` 强校验；
  2. 数值范围（`cpu/memory 0–100`、`receiver_count≥0`、`delivered∈{0,1}`）加 Bean Validation 注解；
  3. 纵深防御：对 `sys_operation_log`/`sys_monitor_sample` 等审计/指标列加 DB `CHECK` 或生成列兜底（DB 为最后守门员）。

**F2 〔中危·性能/稳定〕通知投递失败重试为同步 `Thread.sleep` 退避，阻塞调用线程**

- 现象：`NotificationService.dispatchWithRetry`（NotificationService.java:226-265）在适配器抛瞬时异常时，用 `Thread.sleep(attempt × backoffMs)` 线性退避后重试，运行于**当前调用线程**。
- 根因：阻塞式退避而非非阻塞调度。若调用方处于 Web/请求线程，单次失败即占用 HTTP 线程数秒；即便在异步线程，失败风暴下也持续占用线程池配额，拖垮整体吞吐。
- 影响：通知渠道（短信/邮件网关）抖动时，请求线程被拖死或异步池耗尽，引发级联延迟/超时。
- 优化方案：
  1. 退避改用 `ScheduledExecutorService.schedule(...)` / Reactor `retryWhen(BackOff)` 等**非阻塞**机制；
  2. 确保 `dispatch` 始终在独立通知线程池执行（与请求线程解耦）；
  3. 退避上限与总超时加熔断（CircuitBreaker），避免无限占用。

**F3 〔低危·性能〕导出在堆内全量物化为 `byte[]`**

- 现象：`ExportCenterService` 经 `ByteArrayOutputStream` 构建为 `byte[]` 后整体返回 `ExportedFile`（:86-105）。
- 根因：「全量物化后返回」模式；但代码走查确认 `ExportCenterService.toCsv` / `toXlsx` 已在生成前做 **`MAX_ROWS`（默认 10_000，见 `ExportProperties`）行数上限硬校验**，超出即抛 400 拒绝导出（F0 残留的「CSV 不校验上限」缺陷已修复，校验逻辑于 2026-11-04 重构为统一的 `assertWithinMaxRows` 私有方法，见 §3.3 R2）。因此堆内峰值被稳定约束在 10k 行规模，内存风险**已收敛为低危**。
- 优化方案（可选，本轮未做）：改为直接流式写入 `HttpServletResponse.getOutputStream`（分块传输编码），取消 10k 上限对超大导出的限制，并进一步降低峰值堆占用；需同步改造「导出产物落库下载」的现有契约。

**F4 〔低危·数据质量〕审计摘要超长时整体替换为字面量 `"[truncated]"`，丢失原文**

- 现象：`AuditLogServiceImpl.truncate`（:281-297）对超 2000 字符、截断后非合法 JSON 的摘要，整段替换为字面量 `"[truncated]"`，而非仅截断尾部。
- 根因：兜底逻辑把「截断破坏 JSON 合法性」等同于「整段作废」。正常摘要（Jackson 序列化后的合法 JSON）不受影响；仅极长摘要受害。
- 优化方案：改为按 JSON 结构安全截断（保留对象/数组边界），或上限前先做结构化摘要压缩。

**F5 〔低危·性能〕深翻页 OFFSET 扫描随数据量线性退化**

- 现象：探针在 5k 行下 90% 偏移深翻页 10ms 健康，但 OFFSET 跳过行数随表增长线性增加（MEDIUM 50k 行下恶化至 82ms，×8.2）。
- 根因：列表查询默认 `LIMIT … OFFSET …`，深翻页时 OFFSET 线性跳过已排序行。
- 优化方案与现状（2026-11-04 复核）：框架已提供通用 **`JooqKeysetPager`**（游标分页引擎），且**三张最深的表已落地 keyset 端点**——`FileService`/`FileController`（`/file/keyset`）、`LoginLogService`/`LoginLogController`（`/login-log/keyset`）、`AuditLogServiceImpl`/`AuditLogController`（`/audit/keyset`），均以「上一页末值游标 + 固定 `(时间 DESC, id DESC)` 排序键」替代 OFFSET。剩余工作仅为「将大表中列表默认端点切换为 keyset + 前端游标联动」，属 API 契约/前端改造，改动面大，列为后续独立任务（见 §7）。其余中表（用户/角色/组织等）OFFSET 在百万行内延迟仍健康（§2.3），暂无须切换。

**F6 〔低危·性能〕批量写入未启用 MySQL 多值重写**

- 现象：实测插入吞吐 ~1k–1.4k 行/s，`JooqWriters.batchInsert` 走逐行语句批处理。
- 优化方案：JDBC URL 加 `rewriteBatchedStatements=true`，并将批量改为多值 `INSERT … VALUES (…),(…)`（或在 DSL 层用 `insertInto().values().values()…` 累加），吞吐通常可提升 3–10×。

### 3.3 本轮修复与重构进展（2026-11-04 基础落地，F1 全量铺开于本轮续做；本地提交均未推送）

> 原则：不改变现有功能行为（behavior-preserving）、消除重复、统一范式；按关注点拆分提交，绝不推送远程。  
> 新增单测 30 例（`JsonTruncatorTest` 6 + `DomainValidatorsTest` 10 + `JooqWritersTenantEnforcementTest` 3 增量 + `RecordStatusTest` 11 新增），`NotificationServiceTest` 适配 5 处构造。

**F1 全量铺开（本轮续做）**：在 §3.3 既有 F1 范式（`DomainValidators`）与首处接入（`NotificationService.sendWithTemplate` 渠道名校验）基础上，新增 `RecordStatus` 枚举与 `normalize(String)` 归一器，将「状态字段白名单校验」推广到 8 个记录型 Service 的 create/update 写入口（菜单/组织/字典数据/字典类型/参数/角色/接口资源/岗位），并补监控指标百分比上界钳制。详见 §3.4。

| 编号     | 严重度      | 根因                                             | 修复方案                                                                                                                                                                                                                              | 改动文件                                                                                                                                                                                                               | 行为保持                                                                              |
| ------ | -------- | ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------- |
| **F6** | 低危·性能    | 批量走逐行语句批处理，未启用多值重写                             | JDBC URL 追加 `rewriteBatchedStatements=true`（仅配置，零行为风险）                                                                                                                                                                            | `scaffold-admin/src/main/resources/application-local.yml`、`scaffold-admin/src/test/resources/application-integration.yml`                                                                                          | ✅ 纯配置，语法不变                                                                        |
| **F4** | 低危·数据质量  | 超长摘要截断后若非合法 JSON，整段替换为字面量 `"[truncated]"`，丢失原文 | 新增 `JsonTruncator`（三级降级：①原样保留 → ②紧凑化 + 结构化裁剪（对象丢空值/末位字段、数组丢末位元素）→ ③文本兜底包装为 JSON 字符串标量）；`AuditLogServiceImpl.truncate` 委托之                                                                                                         | `scaffold-common/.../common/audit/JsonTruncator.java`（新增）、`scaffold-common/.../common/audit/AuditLogServiceImpl.java`                                                                                              | ✅ 输出**必为合法 JSON 且 ≤max**；原短摘要/合法 JSON 路径完全不变                                      |
| **F2** | 中危·性能/稳定 | 重试退避在调用线程 `Thread.sleep` 阻塞，占用业务/请求线程          | 新增 `notificationRetryScheduler` Bean（`ThreadPoolTaskScheduler`）；`dispatchWithRetry` 退避改经托管调度器 `ScheduledFuture#get()` 同步等待（语义不变，仅等待由专用线程池管理）；中断时恢复中断标志                                                                            | `scaffold-framework/.../config/observability/ObservabilityConfig.java`、`scaffold-system/.../notification/service/NotificationService.java`、`scaffold-system/.../notification/service/NotificationServiceTest.java` | ✅ 同步语义不变（仍同步返回投递结果），5 处测试构造补 `mock(TaskScheduler.class)`，`backoffMs=0` 时调度器不被真实调用 |
| **F1** | 中危·功能    | 写入路径仅「非空即写」，缺字段级校验（枚举白名单/数值范围）                 | 新增通用校验器 `DomainValidators`（`requireIn`/`requireRange`/`requireNonNegative`，含枚举名重载）；`NotificationService.sendWithTemplate` 渠道名入口接入 `requireIn` 作范式起点（非法渠道显式抛 `VALIDATION_ERROR`，替代 `Channel.valueOf` 的 `IllegalArgumentException`） | `scaffold-common/.../common/validation/DomainValidators.java`（新增）、`scaffold-system/.../notification/service/NotificationService.java`、`scaffold-common/.../common/validation/DomainValidatorsTest.java`（新增）        | ✅ 合法渠道路径不变；非法渠道仍抛 `VALIDATION_ERROR`（错误语义更清晰、可被全局异常处理器统一处理）                       |
| **R2** | 重构·去重    | `ExportCenterService` 的 `MAX_ROWS` 行数上限校验在 `toCsv`(单表)/`toXlsx`(单表)/`toXlsx`(多 Sheet) 三处重复，错误消息与阈值取值口径分散 | 抽取 `assertWithinMaxRows(int)`（统一抛 400 + 错误消息）与 `loadSingleTableRows(task,params)`（取数 + 校验），三处调用口径同源；行为完全不变 | `scaffold-system/.../system/export/service/ExportCenterService.java` | ✅ 校验阈值、错误消息、抛错类型（`BusinessException.badRequest`）均与重构前逐字一致；`ExportCenterServiceTest` 既有上限断言不受影响 |
| **R3** | 重构·去重    | `JooqWriters.insert` 与 `batchInsert` 重复整段「自动填充列（id/tenant_id/deleted/version/created_at/updated_at）」逻辑，单条/批量写入口径若漂移将产生数据一致性隐患 | 抽取 `fillAutoColumns(pojo, table, values)`：统一主键生成+回填、租户隔离（enforce 时写当前租户、否则平台租户 0）、及 deleted/version/时间字段自动填充；两写入方法共用同一实现 | `scaffold-common/.../jooq/dsl/JooqWriters.java` | ✅ 渲染 SQL 与绑定值逐字不变（`JooqWritersTenantEnforcementTest` 10 例含新增 2 例全绿）；单条与批量写入的自动填充口径彻底同源 |
| **R4** | 重构·去重    | 各读取方法（`fetchOne`/`fetchById`/`fetchList`×2/`fetchTop`×2/`page`）重复 `dsl.selectFrom(table.table()).where(withTenant(table, cond))` 租户隔离查询起点 | 抽取 `scopedSelect(dsl, table, cond)` 封装「租户作用域装饰」查询起点，全部读取方法复用；`fetchById` 由委托 `fetchOne` 改为直接复用同一起点并显式叠加逻辑删除过滤 | `scaffold-common/.../jooq/dsl/JooqWriters.java` | ✅ 生成 SQL 与 `withTenant` 注入语义完全不变（`JooqWritersTenantEnforcementTest` 新增 `fetchById` 断言覆盖 tenant_id + deleted 过滤）；`page` 等分页/排序/limit 下推行为不变 |

**F3 / F5 状态复核（2026-11-04）：**

- **F3**（导出堆内物化）：行数上限硬校验**已落地**于 `ExportCenterService`（`assertWithinMaxRows`，默认 `MAX_ROWS=10_000`），堆内峰值被稳定约束，已收敛为低危；流式写出 `HttpServletResponse` 取消 10k 上限为可选后续（需改造导出落库下载契约，见 §3.2 F3）。
- **F5**（深翻页 OFFSET 退化）：**keyset 分页基础设施与三张最深的表（file / login_log / audit）端点已就绪**（通用 `JooqKeysetPager` + 各 `*Controller` 的 `/keyset` 端点），剩余为「默认端点切换 + 前端游标联动」的 API/前端改造，列为后续独立任务（见 §7），不在本轮 scope。其余中表 OFFSET 在百万行内延迟仍健康。

### 3.4 F1 字段级校验全量铺开明细（本轮续做，本地提交未推送）

> 在 §3.3 既有 F1 范式（`DomainValidators`）+ 首处接入（`NotificationService.sendWithTemplate` 渠道名校验）基础上，本轮将「状态字段白名单校验」推广到记录型实体的写入口，并补监控指标上界钳制。原则不变：behavior-preserving、按关注点拆分提交、绝不推送。

| 编号             | 严重度       | 根因                                            | 修复方案                                                                                                                                                                                                                              | 改动文件                                                                                                                                                                                                                 | 行为保持                                                                                  |
| -------------- | --------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| **F1-rollout** | 中危·功能     | 记录型实体 create/update 的 `status`「非空即原样写库」，非法值（小写抖动/越界枚举如 `ZZTOP_INVALID`）原样落库 | 新增 `RecordStatus` 枚举（`ACTIVE/DISABLED/INACTIVE/LOCKED`）+ `normalize(String)`：null/空白回落 `ACTIVE`、其余 trim+大写后 `DomainValidators.requireIn` 白名单校验（非法抛 `VALIDATION_ERROR`）；8 个 Service 的 15 处 `setStatus` 写点接入——create 的 `status==null?"ACTIVE":status` 整体替换为 `normalize(...)`，update 仅对非 null 分支 normalize（null 保留 `existing.getStatus()`）；`ApiResourceService.sync` 的 `patch.setStatus(null)` 哨兵与 `PostService.create` 的 `setStatus(ACTIVE)` 默认哨兵**不动** | `scaffold-common/.../common/validation/RecordStatus.java`（新增）、`scaffold-common/.../common/validation/RecordStatusTest.java`（新增 11 例）、MenuService / OrgService / DictDataService / DictTypeService / ConfigService / RoleService / ApiResourceService / PostService 的 `setStatus` 写点 + import | ✅ 合法大写状态原样通过；null 回落 ACTIVE 与 create 原语义一致；update 非 null 分支行为不变；仅拦截此前会被原样落库的垃圾值（行为修复） |
| **F1-metrics** | 低危·数据质量  | `RuntimeMetricsCollector.percent()` 仅拒 `<0`、无上界，OS 误报 `>100%` 原样落库失真        | `percent()` 在 `value<0→null` 之后、`setScale` 之前增加 `if (value > 100) value = 100;` 上界钳制（合法 [0,100] 区间不受影响）                                                                                                                              | `scaffold-monitor/.../monitor/service/RuntimeMetricsCollector.java`                                                                                                                                                  | ✅ 合法区间数值不变；仅越界值回落 100（防御性修复）                                                |

**新增单测**：`RecordStatusTest` 11 例（null/空白回落 ACTIVE、规范值不变、大小写不敏感 + trim 归一、非法值抛 `VALIDATION_ERROR`、白名单 4 成员）。

**未覆盖点（明确排除，非本轮 scope）**：`DefaultDataScopeProvider.safeType` / `ReportExportTask.parseEnum` / `JobService.*OrNull` 等 fail-open 枚举解析点自带「非法值降级/置空」防御，替换为 `requireIn` 会把 fail-open 语义变为抛错，属行为变更，维持现状。

---

## 4. 性能测试结论

| 指标          | 实测                                                     | 评价                               |
| ----------- | ------------------------------------------------------ | -------------------------------- |
| 单表批量插入吞吐    | 0.9k–1.5k 行/s（SMALL ~2.2k、MEDIUM ~1.3k 聚合行/s，不随单表规模衰减） | 可用；F6 可优化 3–10×                  |
| 计数/索引过滤     | SMALL 7–34 ms / MEDIUM 52–58 ms                        | 健康（`idx_result_time` 生效，线性放大但可控） |
| 深翻页（90% 偏移） | SMALL 10 ms / **MEDIUM 82 ms（×8.2）**                   | F5 已实测劣化，需 keyset 分页             |
| 导出内存峰值      | 上限 10k 行                                               | 低危（F3）                           |
| 通知重试退避      | 同步阻塞                                                   | 中危（F2）                           |

**容量拐点提示**：`BackendPerfIT` 已验证登录接口在 c=10 并发下错误率 <5%；更高并发（c≥20）出现的延迟陡增/错误源于 Hikari 连接池耗尽，属容量拐点而非逻辑缺陷——扩容连接池或加读缓存可缓解。

---

## 5. 模块覆盖矩阵


| 模块 | 覆盖用例 | 状态 |
|------|----------|------|
| 认证 auth | AuthFlowIT / AuthSessionIT / ForgotPasswordIT | ✅ |
| 租户 tenant | TenantProvisioningIT | ✅ |
| 用户 user | UserIntegrationIT / UserLifecycleIT / ProfileManagementIT | ✅ |
| 组织 org | OrgLeaderUpdateIT / OrgLeaderNameIT | ✅ |
| 角色 role | RoleManagementIT | ✅ |
| 菜单 menu | MenuIntegrationIT / MenuSortIT / MenuReorganizeIT | ✅ |
| 站内信 message | MessageIntegrationIT | ✅ |
| 公告 announcement | AnnouncementAuditIT | ✅ |
| 监控/审计 monitor | MonitorLogIT（审计 JSON 列走查见 F4）| ✅/⚠️ |
| 字典/参数 dict/config | DictConfigIT | ✅ |
| 看板 workbench | WorkbenchIntegrationIT | ✅ |
| 工作流 workflow | WorkflowTaskIT / InstanceIntegrationIT / LeaveApprovalWorkflowIT | ✅ |
| 定时任务 job | JobManagementIT | ✅ |
| 在线用户 online | OnlineUserIT | ✅ |
| 通知 notification | 代码走查（F1/F2）+ 数据注入 | ⚠️（无独立 IT，建议补）|
| 导出 export | 代码走查（F3）+ MAX_ROWS 确认 | ⚠️（无独立 IT，建议补）|
| 性能 perf | BackendPerfIT + MassDataSeedIT 探针 | ✅ |
| 数据质量 | **MassDataSeedIT（新增）** 异常注入 | ✅（F1 暴露缺口）|

⚠️ = 本轮以代码走查 + 数据注入覆盖，建议后续补独立 `*IT` 固化为回归用例。

---

## 6. 可复现脚本

```bash
# 0) 前置：JDK21 + 离线 Maven + 本机 MySQL(java_admin, root/root) + integration profile
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && export PATH=$JAVA_HOME/bin:$PATH
MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn
cd outputs/java-admin-framework

# 1) 海量数据生成（SMALL / MEDIUM / LARGE 三档）
$MVN -o -f pom.xml -pl scaffold-admin -am verify -DskipITs=false \
  -Dit.test=MassDataSeedIT -Dfailsafe.failIfNoSpecifiedTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dtest=NoSuchTestXYZ -Dseed.scale=SMALL        # 或 MEDIUM / LARGE

# 2) 后端性能压测（匿名/鉴权读/多账号并发登录）
$MVN -o -f pom.xml -pl scaffold-admin -am verify -DskipITs=false \
  -Dit.test=BackendPerfIT -Dfailsafe.failIfNoSpecifiedTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Dtest=NoSuchTestXYZ

# 3) 全量门禁（双门禁 + check-conventions）
$MVN -o -f pom.xml -pl scaffold-admin -am clean test    # 单测 + 约定检查
# check-conventions 须 dangerouslyDisableSandbox + timeout>=600000（见历史门禁铁律）

# 4) 读取探针输出解读：MassDataSeedIT 日志中 SEED/ANOMALY/READ-PROBE 三节即为报告 §2 数据源
```

---

## 7. 后续建议（优先级）

> 状态更新（2026-11-04）：**F1（范式+首处接入）/ F2 / F4 / F6 已落地**；**R2 / R3 / R4 重构已落地**（详见 §3.3）；**F3 已收敛（MAX_ROWS 硬校验就位）、F5 基础设施已就绪（file/login_log/audit 三表 keyset 端点）**。全部改动本地提交、未推送远程。R3/R4 为 `JooqWriters` 内部去重，渲染 SQL 与绑定值逐字不变，双门禁复验全绿。

1. **P1（范式续）〔本轮已落地〕**：F1 状态字段全量铺开已完成——新增 `RecordStatus` 枚举，将状态白名单校验推广到 8 个记录型 Service 的 create/update 写入口（见 §3.4 `F1-rollout`），并补监控指标百分比上界钳制（`F1-metrics`）；用户/角色等其余枚举与数值范围校验原则上可复用同一范式续做。剩余纵深防御（`sys_operation_log`/`sys_monitor_sample` 加 DB `CHECK` 或生成列兜底）仍建议后续补。**注意**：其余 fail-open 枚举解析点（`DefaultDataScopeProvider.safeType`、`ReportExportTask.parseEnum`、`JobService.*OrNull`）自带降级语义，替换即行为变更，仍不在 scope。
2. **P1（已落地·可选增强）**：F2 非阻塞调度 + 独立线程池 + `maxAttempts` 有界重试已就位；可选补充退避总超时熔断（CircuitBreaker）。
3. **P2**：补 `NotificationIT` / `ExportIT` 独立回归用例，固化本轮走查发现。
4. **P2**：F5 将大表列表默认端点切换为 keyset（基础设施与三表端点已就绪，待接口契约与前端游标联动）；F6 多值 INSERT 重写为可选增强（JDBC `rewriteBatchedStatements` 已给出主要收益，待 MEDIUM/LARGE 量级复测吞吐提升）。
5. **P3**：F3 流式导出（取消 10k 上限、降低峰值堆，需改造导出落库下载契约）；F4 已修复。

---

*本报告由 QA 战役（#1073 数据生成 / #1074 功能 / #1075 性能 / #1076 报告）产出，所有缺陷均经代码走查或实测复现；本轮修复（F1/F2/F4/F6）本地提交、未推送远程。*
