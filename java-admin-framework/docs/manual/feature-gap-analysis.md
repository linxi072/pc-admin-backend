# 功能差距分析与优化建议（java-admin-framework）

> 生成日期：2026-10-15 ｜ 分支：`feat/V1.0.2` ｜ 视角：当前状态盘点
> 方法：**直接核查真实代码**（357 个后端 `.java` 的 `@RestController`/`@RequestMapping`、33 个前端 `.vue`、25 个 `api/*.js`、`pom.xml` 依赖、`application*.yml` 配置），不采信文档自述。
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
| **缓存管理（命中率查看 + 清除）** | 中 | 当前**无 Spring Cache 抽象**（仅 `MonitorService` 内存统计，无 `CacheManager`） | P2 | 先引入 `Caffeine`/`Redis` `CacheManager` 并接 `CacheStatView`；再补 `CacheMonitorView` 清除按钮 + 后端清除端点 |
| **通知渠道抽象（邮件/短信/Webhook）** | 中 | `MessageService` 现仅站内信 | P3（站内信/Webhook/**WebSocket 实时推送** 已完成） | 抽 `NotificationPort`（站内信/Webhook/WebSocket 实现 + `NotificationService` 编排路由 + `app.notifications.webhook.*` 配置化）；WebSocket 复用既有 `RealtimePushService`（定向/广播），EMAIL/SMS 仍待网关依赖，列入后续 |
| **i18n 国际化** | 低 | 无 | P3 | `MessageSource` + 前端 i18n，工作量大 |
| **限流/防重放注解化** | 中 | `RateLimitFilter` 已 opt-in，但无注解驱动 | P3 | 抽 `@RateLimit` 注解 + AOP 拦截，配置化阈值 |

---

## 6. 待优化（性能 / 体验 / 代码结构）

| 项 | 维度 | 现状 | 建议 |
|---|---|---|---|
| **N+1 查询** | 性能 | `UserService` 已用 bulk-fetch + 内存组装（无 N+1）；其余列表沿用 `JooqWriters.page` | 新增 jOOQ `ExecuteListener` 做 N+1 启发式告警（`SlowQueryListener` 已覆盖慢查询，可扩展同监听器） |
| **深翻页** | 性能 | `PageQuery` 钳制 size，但未做游标/keyset | 已落地：common/api 新增 `KeysetPageQuery`/`KeysetPageResult`/`CursorCodec`（纯函数游标编解码），`jooq/JooqKeysetPager` 引擎（seek + size+1 判定 hasMore）；`FileService.keysetPage` + `FileController GET /api/system/files/keyset` 试点（固定 `created_at DESC, id DESC` 排序，含 id tie-breaker） |
| **前端首屏 / 图表按需** | 体验 | Vue SFC 已按需；ECharts 是否按需 import 待核 | 路由级懒加载 + 大图表组件按需 `import()` |
| **测试覆盖率门禁** | 质量 | CI 已接单元 + 集成，无覆盖率门禁 | 加 JaCoCo 阈值（先非阻塞，后转阻塞） |
| **平铺模块分层** | 结构 | `monitor`/`job.management`/`workbench`/`realtime`/`observability`/`codegen` 平铺，已在 `coding-standard.md` 定为"规模增大再拆层子包" | 维持现状，规模增大再分层（文档已约定） |

---

## 7. 优先级矩阵与建议落地顺序

| 层级 | 内容 | 动作 |
|---|---|---|
| **P0** | 严格需求缺口 | **无** |
| **P1** | 工作区卫生 | 先把上一轮未提交的文档/脚本（多数据源指南、编码规范、`check-conventions.py`、`.editorconfig`、四手册编辑）本地提交，保持 `feat/V1.0.2` 工作区干净 |
| **P2** | 增强型基础功能 | 数据导出中心（需联网引入 POI）；缓存管理（需先建 `CacheManager`） |
| **P3** | 可选增强 | 通知渠道抽象（站内信/Webhook，**本次已落地**）、限流注解化（上一轮已落地）、i18n（待办） |

---

## 8. 建议

1. 严格需求已闭环，后续以"增强型基础功能 + 持续优化"为主，按业务优先级排期，不必为"补需求"而补。
2. 先把上一轮已完成的文档/脚本提交，避免工作区长期 dirty 带来集成风险。
3. 需要联网引入依赖的项（POI 等）留待本机/联网环境执行，沙箱仅做结构设计与离线可编译部分。
