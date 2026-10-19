# 功能差距分析与优化建议（java-admin-framework）

> 生成日期：2026-10-15 ｜ 更新日期：2026-10-19（**Bean Validation 校验消息键化已交付**：DTO 约束 `message` 全量改为 `{validation.<键>}` 资源束键，经 `ValidationMessageConfig` 委托 `MessageSource` 按 Locale 插值，中英双语束齐备（键一致性守护测试保证键齐套）；短信通知渠道 M6 续已交付：`Channel.SMS` + `SmsRules` + LOG/HTTP 双网关条件装配 + 前端消息中心多渠道发送；**短信发送记录中心已交付**：`system/smslog`（V37 建表 + 脱敏落库 + 分页筛选 + 汇总 + 菜单种子），发送记录从「不持久化」转为「掩码存储可查询对账」；单测基线刷新为 682 例全绿）｜ 分支：`feat/V1.0.2` ｜ 视角：当前状态盘点
> 方法：**直接核查真实代码**（469 个后端主源 `.java` / 132 个测试源、682 个测试用例（surefire 汇总 0 失败 0 错误）、33 个 `@RestController`、46 个前端 `.vue`、29 个 `api/*.js`、`pom.xml` 依赖、`application*.yml` 配置），不采信文档自述。
> 对照源：设计方案 §12 API 最小集合、§20 最小可交付验收清单、`requirement-gap-analysis.md`（2026-10-10）、`module-plan`（M1–M4）、四本手册。

---

## 1. 核心结论

- **严格需求层面已闭环**：设计方案 §12/§20、历史 gap-analysis（G1–G8、A1/A2/A4）、模块计划（M1 审计 / M2 幂等 / M3 Token 版本 / M4 工作流 claim）**全部已实现或明确记录为 N/A / 范围外**。
- **直接核查确认的"已具备但未在需求文档明示"的能力**：OpenAPI/Swagger 文档、慢 SQL 监听、多数据源、文件管理、编码规范 + 零依赖校验脚本（本轮交付）。
- **真实缺口极小**：仅 §12 中少量「独立端点」被合并进 DTO（前端已适配，非阻断），以及 `instances/{id}/history` 由 `/records` 等价承载。
- **后续重点不是"补需求"，而是"增强型基础功能 + 优化"**（见 §5/§6），且其中多项受离线仓库依赖约束需联网引入或需先建基础设施。截至 2026-10-19 复核，**§5 与 §6 所列增强项与优化项已全部交付**（导出中心 CSV+XLSX 含多 Sheet 富格式 / 导入中心含预检与失败行报告 / 缓存管理含机构树分键缓存 / i18n 国际化 / 敏感数据脱敏通用化 / 通知渠道 M6 邮件 + M6 续短信（LOG/HTTP 双网关）/ 限流注解化 / N+1 告警 / keyset 深翻页 / 前端图表按需 / Bean Validation 校验消息键化）；残留方向仅为可选增强：前端 i18n（待前端配合）、`BusinessException` 业务异常消息键化（Bean Validation 校验消息键化已完成）、测试覆盖率门禁（JaCoCo，需联网验证）、短信发送记录持久化（与邮件一致的有意取舍，按需再引入）。

---

## 2. 需求 vs 实现 逐项核对

| 需求（来源） | 实现证据（真实代码） | 状态 |
|---|---|---|
| 认证 login/refresh/logout/me（§12） | `AuthController` `@RequestMapping("/api/auth")` | ✅ |
| 刷新令牌轮换 / 重放检测（§20） | `RefreshTokenService` + `RotatingJwtDecoder`/`JwtKeyRotationService` | ✅ |
| 用户/机构/角色/菜单/接口资源 CRUD（§20） | `User/Org/Role/Menu/ApiResource` Controller | ✅ |
| 接口资源扫描登记（模块计划） | `ApiResourceScanner` + V11 种子 | ✅ |
| 数据权限五类 + AOP 注入（§12） | `DefaultDataScopeProvider` + `DataScopeAspect` | ✅ |
| 操作审计 + 脱敏 + traceId 查询（§20） | `AuditAspect` + `AuditLogController`（`/page` `/trace/{id}` `/summary`）+ `SensitiveMasker` | ✅ |
| 幂等 Idempotency-Key（§12，审批强制） | `IdempotencyAspect`/`Service`/`RecordDO` + `IdempotencyCleanupJob` | ✅ |
| Token 版本失效（§20） | `TokenVersionVerifier` + `TokenVersionCache` | ✅ |
| 工作流 内置+自定义设计器/发布/会签或签/驳回/转办/认领（§12） | `WorkflowDefinition/Task/Instance` Controller + `BpmnWorkflowBuilder` | ✅ |
| 工作流实例详情 `GET /instances/{id}`（G6） | `InstanceController.detail` → `InstanceDetailView` | ✅ |
| 系统模块 部门/字典/变量/公告/站内信（模块计划） | 对应 Controller 齐备 | ✅ |
| 实时推送 WebSocket（模块计划） | `RealtimeWebSocketHandler`/`SessionManager`/`PushService` | ✅ |
| 服务端排序白名单（模块计划） | `JooqSorts` 接入 users/announcements/error-logs | ✅ |
| 工作台聚合统计（模块计划） | `WorkbenchController` `/stats` + `/recent-activities` | ✅ |
| OTel 链路 G1 / A1 导出器 | `observability/TraceContext` + `export/OtlpSpanExporter`（默认关闭，配置点亮） | ✅（代码） |
| Grafana/Alertmanager G2 / A2 | `monitoring/` 配置 + `AlertWebhookController` 应用内接收端 | ✅（代码） |
| 登录验证码 G3 | `CaptchaService` + `CaptchaController` + 前端对接 | ✅ |
| 密钥轮换 G5a / 外部密钥 A4 | `JwtKeyRotationService` + `security/secret/*` | ✅ |
| 限流/防重放 G5b | `RateLimitFilter` + `AntiReplayFilter`（opt-in） | ✅ |
| 密码策略 G7 | `PasswordPolicy` + 写库前校验 | ✅ |
| CI 门禁 G4 | `.github/workflows/ci.yml` | ✅ |
| 健康探针 /livez /readyz（§20） | `monitor/health` 包 + `ProbeController` | ✅ |
| 报表管理 + 工作台优化（§10） | `workbench/report/*` + `ReportController` + 前端三报表页 | ✅ |
| 在线定时任务管理（§11） | `job/management/*` + `JobController` + 前端 `JobListView` | ✅ |
| 多数据源（本轮） | `config/datasource/*` + `application*.yml` 外部化 | ✅（代码，待提交文档） |
| 文件管理（本轮） | `system/file/*` + `V33` 迁移 + 前端 `FileManagementView` | ✅（代码，待提交） |
| 编码规范 + 零依赖校验（本轮） | `coding-standard.md` + `scripts/check-conventions.py` + `.editorconfig` | ✅（待提交） |
| API 文档（框架基线） | `springdoc-openapi 2.6.0` + `@Tag/@Operation` + `SecurityConfig` 放行 + yml 配路径 | ✅（已启用，未在需求文档明示） |
| 慢 SQL 监听（性能基线） | `monitor/SlowQueryListener` 注册于 `JooqConfig` | ✅（已启用，未在需求文档明示） |
| 岗位管理（系统模块增强，2026-10-16） | `PostController` `@RequestMapping("/api/system/posts")`（page/options/create/update/delete/assign）+ `PostService`（编码校验+唯一性+逻辑删除+用户岗位分配+防 N+1 装载）+ `SysPostDO`/`SysUserPostDO` + V35 迁移（菜单 `system:post` 绑定 SUPER_ADMIN）+ `PostRules` 纯函数；前端 `PostView.vue` + 用户表单岗位多选集成（`postIds`/`postNames` 贯穿创建/编辑/列表） | ✅ |

---

## 3. 真实的"未实现"项（对照 §12/§20）

| 设计方案条目 | 实际落地 | 是否阻断 |
|---|---|---|
| `GET /api/auth/csrf` | JWT 无状态认证，无 CSRF 依赖 | 否（N/A，已记录） |
| `PUT /roles/{id}/menus`、`/apis`、`PUT /users/{id}/status`、`/roles` | 并入 `create/update` DTO（`menuIds`/`apiIds`/`status`/`roleIds`） | 否（前端已适配，已记录） |
| `POST /definitions/{key}/publish` | 实际 `/definitions/{id}/publish`（按 id 发布） | 否（功能等价） |
| `GET /instances/{id}/history` | 由 `GET /{id}/records`（审批记录列表）承载；`approve/reject` 并入 `POST /tasks/{id}/complete` 决策参数 | 否（语义等价） |

> 结论：**严格需求层面无可阻断缺口**。以上均为"形态偏差"，已在 `requirement-gap-analysis.md §2` 记录，不影响功能可用性与对外契约（前端已按实际形态对接）。

---

## 4. 已具备但未在需求文档明示的能力（资产盘点）

- **API 文档**：`springdoc-openapi 2.6.0` 已在 `pom.xml`；`MonitorController` 带 `@Tag`/`@Operation`；`SecurityConfig.PUBLIC_API` 已放行 `/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`；`application-local.yml`/`application-prod.yml` 配置 `springdoc.swagger-ui.path=/swagger-ui.html`。→ 访问 `/swagger-ui.html` 即可。
- **慢 SQL 监听**：`monitor/SlowQueryListener` 已注册于 `JooqConfig`，输出慢查询日志。→ 性能可观测基线已具备。
- **多数据源 / 文件管理 / 编码规范 + 校验脚本**：本轮交付（代码已落地，文档与脚本待提交）。

---

## 5. 增强型基础功能（贴合框架定位）

> 下列为"框架作为可复用基座"通常应具备、且曾一度缺失的能力。均**超出原设计方案范围**，属增强型。**截至 2026-10-17，本表全部条目状态均为「已完成」**，此处保留以记录落地形态与残留增强方向。可行性受离线仓库约束标注。

| 项 | 业务价值 | 可行性（离线沙箱） | 优先级 | 建议实现方式 |
|---|---|---|---|---|
| **数据导入/导出中心（Excel）** | 高 | ✅ **导出与导入均已落地（2026-10-16，全程无需 POI）**。**导出**：在既有 CSV 导出中心（`system/controller/ExportController` + `ExportTask` 模板注册 + `ExportCenterView.vue`）**增量**补 XLSX——`/download` 增加 `format=CSV\|XLSX`（缺省 CSV 向后兼容）、`/templates` 返回各模板 `formats`；新增 `common/export/ExportFormat`（枚举）+ `XlsxDataExporter`（仅用 JDK `java.util.zip` 手写最小合规 OOXML，inlineStr + XML 转义，**零新依赖、离线可编**）+ `ExportProperties`（`app.export.max-rows` 默认 10 万行）；多段结构模板（报表）仍仅 CSV。**导入**：新增与导出严格对称的 `common/importer`（`ImportTask` 模板注册 + `TabularData` 统一中间模型 + `ImportValidators` 纯函数校验构件）+ `CsvDataReader`（RFC4180 状态机、剥离 BOM、三种行分隔）+ `XlsxDataReader`（仅 JDK `ZipInputStream` + StAX 解析 OOXML，支持共享字符串/内联字符串，**按 `r` 属性定位列**避免空单元格错位，禁用 DTD 防 XXE）+ `system/service/ImportCenterService`（格式识别→解析→表头校验→逐行校验/落库）+ `ImportController`（`/templates`、`/template` 下载空白模板、`POST /{code}?dryRun=` 上传）+ `ImportProperties`（`app.import.max-rows` 5000 / `max-errors` 200）+ 试点 `DictDataImportTask`；前端 `ImportCenterView.vue` + `api/import.js` + 路由 `/system/import` + `V36` 菜单种子 | P2 → 已完成 | ✅ 双格式闭环（导出→编辑→导入）；`failureCount` 与 `errors` 明细分离（明细受上限约束）；POI 富格式（单元格样式/多 sheet/公式）与多段结构导入按需再引入 |
| **缓存管理（命中率查看 + 清除）** | 中 | ✅ **已落地（M5，2026-10-16）**：`config/cache` 提供 `@EnableCaching` + RedisCacheManager（TTL/前缀可配），`StatisticsCache`/`StatisticsCacheManager` 装饰器做进程内命中/未命中/淘汰记账；`MonitorService.cacheStats()/clearCache()` 扩展至 Spring Cache，前端缓存监控页零改动；试点 `DictDataService.listByType` `@Cacheable`（create 精确驱逐、update/delete 全量驱逐）。**菜单缓存（M5 续，同日）**：`MenuService.tree()`（`menu-tree` 单一 `ALL` 键）与 `menusForCurrentUser()`（`user-menus` 按用户 id 键）纳入缓存；菜单增删改排序整缓存驱逐，`RoleService` 角色菜单绑定 / `UserService` 用户角色变更按语义跨服务驱逐 | P2 → 已完成 | ✅ **机构树分键缓存已纳入（M5 续，2026-10-16）**：`OrgService.tree()` 带 `@DataScope`，按「数据权限范围签名」分键（`orgScopeKey()`：`ALL` / `SELF:<userId>` / `ORG:<id 升序>`），规避受限用户命中管理员缓存造成的越权可见；create/update/delete 整缓存驱逐；`DataScopeAspect` 加 `@Order(HIGHEST_PRECEDENCE)` 保证缓存键计算时 `DataScopeContext` 已就绪；`OrgServiceCacheTest` 6 例覆盖跨权限隔离与写路径驱逐（同一 CacheManager） |
| **通知渠道抽象（邮件/短信/Webhook）** | 中 | ✅ **已落地（M6 + M6 续，2026-10-16/19）**：`Channel.EMAIL` + `EmailNotificationAdapter`（`app.notifications.email.*` 默认关闭）+ `EmailRules` 纯函数（地址校验/CRLF 净化防头注入/收件人归一与上限）+ `JavaMailSenderClient`（spring-context-support 6.1.15 + jakarta.mail 2.1.3 + angus-mail 2.0.3，均离线可用）；**短信 `Channel.SMS`**（2026-10-19，零新依赖、厂商无关）：`SmsRules` 纯函数（号码归一容忍 +86/86 前缀与分隔符、单次上限 100、正文 500、签名 CR/LF 净化与【】补齐）+ `SmsProperties`（`app.notifications.sms.*`，默认关闭）+ `SmsGateway` 端口二实现条件装配互斥（`LoggingSmsGateway` 默认打脱敏日志复用 `SensitiveMasker.maskPhone`；`HttpSmsGateway` POST JSON 到可配 `gateway-url`，最小契约 appId/appKey/mobiles/content）+ `SmsNotificationAdapter`；前端消息中心发送弹窗增渠道选择（站内信/邮件/短信）与 `api/notification.js` | P3 → 已完成 | 站内信/Webhook/WebSocket/邮件/短信五渠道齐备；短信发送记录持久化为有意取舍（与邮件一致，避免 PII 落库），按需再引入；前端 i18n 仍待前端配合 |
| **i18n 国际化** | 低 | ✅ **已落地（2026-10-16）**：`common/i18n` 提供 `MessageSource`（`ReloadableResourceBundleMessageSource`，UTF-8，基名 `i18n/messages`）+ `SessionLocaleResolver`（默认 zh_CN）+ `LocaleChangeInterceptor`（`?lang=xx` 切换，注册于 `I18nConfig implements WebMvcConfigurer`）；`I18nService` 封装按当前请求语言解析（缺失键返回键本身兜底），`GlobalExceptionHandler` 框架级错误已本地化（zh_CN / en_US 双语资源束）；`I18nServiceTest`(6) + `I18nLocaleInterceptorTest`(2) 覆盖多语言解析/回退/插值/切换 | P3 → 已完成 | 前端 i18n 仍待前端配合；Bean Validation 约束 `message` 键化（如 `{error.field.required}`）与 `BusinessException` 消息键化为后续增强 |
| **限流/防重放注解化** | 中 | ✅ **已落地**：`security/ratelimit` 新增 `@RateLimit` 注解 + `RateLimitAspect`（IP / 用户 / 全局三维度，复用固定窗口 `RateLimiter`，超限 429），试点上传 / 配置更新接口；与 URL 级 `RateLimitFilter` 互补 | P3 → 已完成 | 多实例部署时可把计数替换为 Redis 共享（保持 key 维度一致） |
| **敏感数据脱敏（通用化）** | 中 | ✅ **已落地（2026-10-16）**：`common/sensitive` 提供 `SensitiveType`（MOBILE/ID_CARD/BANK_CARD/EMAIL/NAME/ADDRESS/IP/CREDENTIAL_TEXT/NONE）+ `SensitiveMasker`（纯函数、零依赖）——该类由 `common/audit` 私有实现**提升为框架级通用能力**，既有方法签名与行为**零变更**（原 12 例断言原样保留）；新增 `mask(value,type)` 统一分派与 `maskBankCard`/`maskName`/`maskAddress`。落地三处应用：审计日志（原有）、**登录日志**（`LoginLogView.of(DO, sensitiveVisible)` 默认掩码 IP，补齐该类既有的「展示层掩码」待办）、**登录日志导出**（`LoginLogExportTask` 接入导出中心，导出文件一律掩码，零迁移零前端） | P2 → 已完成 | 安全默认：`system:audit:sensitive` / `system:login-log:sensitive` 均不预置种子，需按需授予角色；扩展仅需加 `SensitiveType` 常量 + `SensitiveMasker.mask` 的 switch 分支 |

---

## 6. 待优化（性能 / 体验 / 代码结构）

> 除「测试覆盖率门禁」外，下表条目均已完成；保留现状与做法说明以便追溯。

| 项 | 维度 | 现状 | 建议 |
|---|---|---|---|
| **N+1 查询** | 性能 | ✅ **已落地**：`monitor/detection/NPlusOneListener` + `NPlusOneProbe`（线程级累计同构 SQL 执行次数）+ `NPlusOneResetFilter`（请求级重置），`JooqConfig`/`DataSourceConfig` 双注册；同构 SQL 单请求内重复超阈值即告警（含指纹），与 `SlowQueryListener` 同为只读观测 | — → 已完成 | 阈值可配置化；多实例场景可对接指标导出 |
| **深翻页** | 性能 | `PageQuery` 钳制 size，但未做游标/keyset | 已落地：common/api 新增 `KeysetPageQuery`/`KeysetPageResult`/`CursorCodec`（纯函数游标编解码），`jooq/JooqKeysetPager` 引擎（seek + size+1 判定 hasMore）；`FileService.keysetPage` + `FileController GET /api/system/files/keyset` 试点（固定 `created_at DESC, id DESC` 排序，含 id tie-breaker） |
| **前端首屏 / 图表按需** | 体验 | ✅ **已落地**：`BaseChart.vue` 由全量 `import * as echarts` 改为 `echarts/core` 按需注册（bar/line/pie + grid/tooltip/legend/title + CanvasRenderer，注册项经 node_modules 安装链核实无遗漏），产物 **1.0M → 522KB（gzip 176KB）**；路由级懒加载既有，echarts 仅随报表/任务路由 chunk 加载，不进首屏 | — → 已完成 | 新增图表类型时在 `BaseChart.vue` 顶部同步补充 import |
| **测试覆盖率门禁** | 质量 | CI 已接单元 + 集成，无覆盖率门禁 | 加 JaCoCo 阈值（先非阻塞，后转阻塞） |
| **平铺模块分层** | 结构 | 经 `scripts/check-conventions.py` 复核（447 主源，H1–H4 与 W1 均无告警）：`codegen`/`common`/`job`/`kafka`/`monitor`/`realtime`/`security`/`system`/`workbench`/`workflow` 已按层子包（`controller`/`service`/`entity`/`dto` …）组织；仅 `config`（23）/ `jooq`（7）/ `observability`（12）仍为平铺，属《编码规范》允许的「通用包按关注点拆子包、不带层名」（如 `config.web`/`config.jooq`/`config.redis`/`config.cache`/`config.datasource`/`config.jobrunr`/`config.observability`） | 维持现状；`config`/`jooq`/`observability` 按关注点拆子包即可，无需补层名，规模进一步增大再评估 |

---

## 7. 优先级矩阵与建议落地顺序

| 层级 | 内容 | 动作 |
|---|---|---|
| **P0** | 严格需求缺口 | **无** |
| **P1** | 工作区卫生 | 已保持 `feat/V1.0.2` 工作区干净（每轮按关注点分批本地提交） |
| **P2** | 增强型基础功能 | ~~数据导出中心~~ + ~~数据导入中心~~（**均已落地 2026-10-16：导出侧既有 CSV 中心增量支持零依赖 XLSX；导入侧新增模板注册运行时 + CSV/XLSX 零依赖解析 + 预检，全程无需 POI**）；~~缓存管理~~（**已落地 M5，含菜单树缓存、机构树分键缓存与跨服务驱逐**）；~~敏感数据脱敏~~（**已落地 2026-10-16：common/sensitive 通用化（SensitiveType + SensitiveMasker）+ 登录日志脱敏（默认掩码 IP）+ 登录日志导出模板（一律掩码）**） |
| **P3** | 可选增强 | ~~通知渠道抽象~~（**已落地 M6 + M6 续：EMAIL 邮件 + SMS 短信（LOG/HTTP 双网关条件装配，默认关闭）五渠道齐备**）、~~限流注解化~~（已落地）、~~N+1 告警 / 前端图表按需~~（已落地）、~~i18n 国际化~~（**已落地 2026-10-16**：后端双语资源束 + `GlobalExceptionHandler` 本地化；前端 i18n 待前端配合）、~~XLSX 富格式~~（**已落地 2026-10-16**：零依赖多 Sheet 工作簿——styles 表头加粗灰底 + 列宽自适应 + 表名规范化，`ExportTask.xlsxSheets` 契约使报表等多段结构解除「仅 CSV」限制，口径与 CSV 同源） |

---

## 8. 建议

1. 严格需求已闭环，后续以"增强型基础功能 + 持续优化"为主，按业务优先级排期，不必为"补需求"而补。
2. 每轮完成的代码/文档按关注点分批本地提交，避免工作区长期 dirty 带来集成风险。
3. 原「需联网引入 POI」的 Excel 导出/导入已通过**零依赖 OOXML（写出 `XlsxDataExporter` / 解析 `XlsxDataReader`，仅用 JDK `java.util.zip`）**化解，无需联网；剩余需联网项（JaCoCo 覆盖率门禁验证）留待本机/联网环境执行，沙箱仅做结构设计与离线可编译部分。
4. 导入中心为**模板注册**模式（同导出中心）：新增一种数据的导入只需新增一个 `ImportTask` Bean 并实现 `headers()`/`validate()`/`importRow()`，无需新增接口、迁移或前端页面（前端按 `/templates` 自动渲染）。
5. 导出中心同理由 `ExportTask` Bean 自动扩充：如本轮新增 `LoginLogExportTask`（登录日志导出）**零迁移、零前端**，仅在 `system/export` 下加一个 `@Component` 即被 `/templates` 自动列举。脱敏遵循「类型驱动 + 安全默认」：对外视图/导出默认掩码，明文可见需显式授予 `system:*:sensitive` 权限。
6. 测试基线：当前测试源共 **675 个用例全绿**（surefire 汇总 0 失败 / 0 错误 / 0 跳过；含 2026-10-19 全模块测试轮 38 例、短信渠道 18 例与短信记录中心 27 例）。新增功能请同步补测，避免基线回退。

---

> 更新日期：2026-10-19（本轮交付短信通知渠道 M6 续与短信发送记录中心：主源 447→468、测试基线 630→675 例全绿；§5/§6/§7 口径同步改写，通知渠道增强项补 SMS 五渠道齐备状态，短信记录从「不持久化」转为「掩码存储可查询对账」）
