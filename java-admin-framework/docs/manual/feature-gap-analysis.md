# 功能差距分析与优化建议（java-admin-framework）

> 生成日期：2026-10-15 ｜ 更新日期：2026-10-16（M5 缓存管理含菜单树 / M6 邮件渠道落地；N+1 告警与图表按需收口；岗位管理模块后端+前端全链路交付；机构树分键缓存（M5 续）落地）｜ 分支：`feat/V1.0.2` ｜ 视角：当前状态盘点
> 方法：**直接核查真实代码**（401 个后端 `.java` 的 `@RestController`/`@RequestMapping`、33 个前端 `.vue`、25 个 `api/*.js`、`pom.xml` 依赖、`application*.yml` 配置），不采信文档自述。
> 对照源：设计方案 §12 API 最小集合、§20 最小可交付验收清单、`requirement-gap-analysis.md`（2026-10-10）、`module-plan`（M1–M4）、四本手册。

---

## 1. 核心结论

- **严格需求层面已闭环**：设计方案 §12/§20、历史 gap-analysis（G1–G8、A1/A2/A4）、模块计划（M1 审计 / M2 幂等 / M3 Token 版本 / M4 工作流 claim）**全部已实现或明确记录为 N/A / 范围外**。
- **直接核查确认的"已具备但未在需求文档明示"的能力**：OpenAPI/Swagger 文档、慢 SQL 监听、多数据源、文件管理、编码规范 + 零依赖校验脚本（本轮交付）。
- **真实缺口极小**：仅 §12 中少量「独立端点」被合并进 DTO（前端已适配，非阻断），以及 `instances/{id}/history` 由 `/records` 等价承载。
- **后续重点不是"补需求"，而是"增强型基础功能 + 优化"**（见 §5/§6），且其中多项受离线仓库依赖约束需联网引入或需先建基础设施。

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

## 5. 缺失的必要基础功能（贴合框架定位）

> 下列为"框架作为可复用基座"通常应具备、但本项目尚未提供的能力。均**超出原设计方案范围**，属增强型，按业务需要排期。可行性受离线仓库约束标注。

| 项 | 业务价值 | 可行性（离线沙箱） | 优先级 | 建议实现方式 |
|---|---|---|---|---|
| **数据导入/导出中心（Excel）** | 高 | POI/EasyExcel **不在**离线仓库，需联网 `mvn` 引入 | P2（递延，待联网） | 引入 POI + 通用 `ExportService`（CSV 已具备 `report/export`）；前端导出中心页；先以 CSV 兜底，Excel 待依赖就绪 |
| **缓存管理（命中率查看 + 清除）** | 中 | ✅ **已落地（M5，2026-10-16）**：`config/cache` 提供 `@EnableCaching` + RedisCacheManager（TTL/前缀可配），`StatisticsCache`/`StatisticsCacheManager` 装饰器做进程内命中/未命中/淘汰记账；`MonitorService.cacheStats()/clearCache()` 扩展至 Spring Cache，前端缓存监控页零改动；试点 `DictDataService.listByType` `@Cacheable`（create 精确驱逐、update/delete 全量驱逐）。**菜单缓存（M5 续，同日）**：`MenuService.tree()`（`menu-tree` 单一 `ALL` 键）与 `menusForCurrentUser()`（`user-menus` 按用户 id 键）纳入缓存；菜单增删改排序整缓存驱逐，`RoleService` 角色菜单绑定 / `UserService` 用户角色变更按语义跨服务驱逐 | P2 → 已完成 | ✅ **机构树分键缓存已纳入（M5 续，2026-10-16）**：`OrgService.tree()` 带 `@DataScope`，按「数据权限范围签名」分键（`orgScopeKey()`：`ALL` / `SELF:<userId>` / `ORG:<id 升序>`），规避受限用户命中管理员缓存造成的越权可见；create/update/delete 整缓存驱逐；`DataScopeAspect` 加 `@Order(HIGHEST_PRECEDENCE)` 保证缓存键计算时 `DataScopeContext` 已就绪；`OrgServiceCacheTest` 6 例覆盖跨权限隔离与写路径驱逐（同一 CacheManager） |
| **通知渠道抽象（邮件/短信/Webhook）** | 中 | ✅ **已落地（M6，2026-10-16）**：`Channel.EMAIL` + `EmailNotificationAdapter`（`app.notifications.email.*` 默认关闭）+ `EmailRules` 纯函数（地址校验/CRLF 净化防头注入/收件人归一与上限）+ `JavaMailSenderClient`（spring-context-support 6.1.15 + jakarta.mail 2.1.3 + angus-mail 2.0.3，均离线可用） | P3 → 已完成（仅 SMS 仍待网关） | SMS 仍待短信网关依赖；站内信/Webhook/WebSocket/邮件四渠道已齐备 |
| **i18n 国际化** | 低 | 无 | P3 | `MessageSource` + 前端 i18n，工作量大 |
| **限流/防重放注解化** | 中 | ✅ **已落地**：`security/ratelimit` 新增 `@RateLimit` 注解 + `RateLimitAspect`（IP / 用户 / 全局三维度，复用固定窗口 `RateLimiter`，超限 429），试点上传 / 配置更新接口；与 URL 级 `RateLimitFilter` 互补 | P3 → 已完成 | 多实例部署时可把计数替换为 Redis 共享（保持 key 维度一致） |

---

## 6. 待优化（性能 / 体验 / 代码结构）

| 项 | 维度 | 现状 | 建议 |
|---|---|---|---|
| **N+1 查询** | 性能 | ✅ **已落地**：`monitor/detection/NPlusOneListener` + `NPlusOneProbe`（线程级累计同构 SQL 执行次数）+ `NPlusOneResetFilter`（请求级重置），`JooqConfig`/`DataSourceConfig` 双注册；同构 SQL 单请求内重复超阈值即告警（含指纹），与 `SlowQueryListener` 同为只读观测 | — → 已完成 | 阈值可配置化；多实例场景可对接指标导出 |
| **深翻页** | 性能 | `PageQuery` 钳制 size，但未做游标/keyset | 已落地：common/api 新增 `KeysetPageQuery`/`KeysetPageResult`/`CursorCodec`（纯函数游标编解码），`jooq/JooqKeysetPager` 引擎（seek + size+1 判定 hasMore）；`FileService.keysetPage` + `FileController GET /api/system/files/keyset` 试点（固定 `created_at DESC, id DESC` 排序，含 id tie-breaker） |
| **前端首屏 / 图表按需** | 体验 | ✅ **已落地**：`BaseChart.vue` 由全量 `import * as echarts` 改为 `echarts/core` 按需注册（bar/line/pie + grid/tooltip/legend/title + CanvasRenderer，注册项经 node_modules 安装链核实无遗漏），产物 **1.0M → 522KB（gzip 176KB）**；路由级懒加载既有，echarts 仅随报表/任务路由 chunk 加载，不进首屏 | — → 已完成 | 新增图表类型时在 `BaseChart.vue` 顶部同步补充 import |
| **测试覆盖率门禁** | 质量 | CI 已接单元 + 集成，无覆盖率门禁 | 加 JaCoCo 阈值（先非阻塞，后转阻塞） |
| **平铺模块分层** | 结构 | `monitor`/`job.management`/`workbench`/`realtime`/`observability`/`codegen` 平铺，已在 `coding-standard.md` 定为"规模增大再拆层子包" | 维持现状，规模增大再分层（文档已约定） |

---

## 7. 优先级矩阵与建议落地顺序

| 层级 | 内容 | 动作 |
|---|---|---|
| **P0** | 严格需求缺口 | **无** |
| **P1** | 工作区卫生 | 已保持 `feat/V1.0.2` 工作区干净（每轮按关注点分批本地提交） |
| **P2** | 增强型基础功能 | 数据导出中心（需联网引入 POI，**待联网**）；~~缓存管理~~（**已落地 M5，含菜单树缓存、机构树分键缓存与跨服务驱逐**） |
| **P3** | 可选增强 | ~~通知渠道抽象~~（**已落地 M6，EMAIL 齐备，仅 SMS 待网关**）、~~限流注解化~~（已落地）、~~N+1 告警 / 前端图表按需~~（已落地）、i18n（待办） |

---

## 8. 建议

1. 严格需求已闭环，后续以"增强型基础功能 + 持续优化"为主，按业务优先级排期，不必为"补需求"而补。
2. 每轮完成的代码/文档按关注点分批本地提交，避免工作区长期 dirty 带来集成风险。
3. 需要联网引入依赖的项（POI 等）留待本机/联网环境执行，沙箱仅做结构设计与离线可编译部分。
