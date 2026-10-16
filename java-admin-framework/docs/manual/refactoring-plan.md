# 职责拆分与分步重构方案（refactoring-plan）

> 生成日期：2026-10-16 ｜ 分支：`feat/V1.0.2` ｜ 基线：M5 缓存 / M6 邮件 / 菜单缓存 / ECharts 按需 之后
> 目标：**明确各模块边界与职责、解耦重复逻辑**，并按可验证的小步推进；所有改动保持业务功能、外部接口与表结构不变。

---

## 1. 模块边界定义（单一事实来源）

模块化单体按「特性分包 + 层子包（controller/service/domain/dto/entity）」组织，层间依赖方向
`controller → service → domain`（由 `scripts/check-conventions.py` H3 硬规则守护）。
模块间依赖遵循下表（✔=允许，✘=禁止，△=仅允许经 Port 接口，不允许直连实现）：

| 模块 | 职责（一句话） | 允许依赖 |
|---|---|---|
| `common` | 跨模块基础设施：API 包装 / 错误 / 校验 / 审计 / 幂等 / 通知编排 / 导出契约 / 通用纯函数 | 无（最底层） |
| `jooq` | 数据访问 DSL：`JooqWriters` / `TableRef` / 分页与 keyset 引擎 | common |
| `config` | 技术配置（数据源 / 缓存 / 序列化等） | common, jooq |
| `observability` | 可观测性：指标 / 链路 / 慢查询与 N+1 监听 | common |
| `realtime` | WebSocket 实时推送（载荷 / 服务） | common |
| `security` | 认证 / 授权 / 令牌 / 在线会话 / 限流；**只定义 Port，不直连其它特性模块实现** | common, realtime |
| `system` | 系统域实体与服务：用户 / 角色 / 菜单 / 机构 / 字典 / 文件 / 消息 / 在线会话 / 登录限制 | common, jooq, security(仅 context/rules/token Port) |
| `workflow` | 流程定义 / 实例 / 任务（Flowable） | common, jooq, security(context), realtime |
| `job` | 定时任务（JobRunr）：调度 / 执行 / 日志 | common, jooq, security(context) |
| `monitor` | 监控：服务器 / 缓存 / 指标查询 | common, jooq, security(entity, token Port) |
| `codegen` | 代码生成器（纯函数模板 + 表结构读取） | common, jooq |
| `workbench` | 工作台：报表聚合 / 首页 | common, jooq, security(context) |
| `kafka` | 有序消息消费 | common, jooq |

**边界原则**：
1. 特性模块之间不互相直连 Service；需要协作时经 `common` 的 Port（如 `NotificationPort`）或由上层编排。
2. 实体（`*DO`）归属其领域模块；其它模块通过该模块的 Service / Port 访问，不直接 import 其 entity（`security.permission` 历史遗留除外，见批次 3）。
3. 纯函数规则类（`*Rules` / `*Writer` / `*Builder`）随其操作的数据领域走，不放「名字听起来相关」的模块。

---

## 2. 盘点问题清单（2026-10-16）

### 2.1 重复逻辑
| # | 问题 | 位置 | 严重度 |
|---|---|---|---|
| D1 | 两套同构树构建（按 parentId 分组 + 递归挂载） | `MenuService#buildChildren` / `OrgService#toTree` | med |
| D2 | 私有 `escapeCsv` 与 `CsvExporter.escape` 转义逻辑重复 | `ReportService` vs `common/export/CsvExporter` | med |
| D3 | 报表自建 CSV 拼接与导出中心 `CsvExporter` 双轨 | `ReportService#exportCsv` | med |

### 2.2 模块边界
| # | 问题 | 位置 | 严重度 |
|---|---|---|---|
| B1 | `security.permission.PermissionService` 直 import 6 个 `system.entity` DO（鉴权直连系统域持久化对象） | `security/permission/PermissionService.java` | high |
| B2 | workflow 步骤委托直调 `system.service.MessageService` | `workflow/adapter/step/{Generic,Cc}StepDelegate` | med |
| B3 | `CodeTemplates` 死 import `job.service.JobService`（且违反自身「与 Spring 解耦」声明，形成 codegen→job 依赖边） | `codegen/CodeTemplates.java:15` | low |
| B4 | `OnlineSessionRules` / `RestrictionRules` 操作 `system.entity` 却错放 `security.online.rules`，迫使 `system.service.OnlineUserService` 反向 import security | 2 个类 | med |
| B5 | `system ↔ security` 双向耦合（B1/B4 的合称）：`UserService`/`RoleDataScopeService`/`ProfileService` 等 import `security.password/token/permission` | 5+ 处 | high |

### 2.3 职责过重
| # | 问题 | 位置 | 严重度 |
|---|---|---|---|
| C1 | `ReportService` 880 行：编排 + 统计原语 + CSV 导出三职责混杂 | `workbench/report/service/ReportService` | med |
| C2 | `ProfileService` 552 行 / `OnlineUserService` 489 行 | `system/service` | low |

### 2.4 文档失同步（视同缺陷修复）
- 「限流注解化」（§7 标已落地）与「N+1 查询告警」（§6 仍列为建议）实为已推送提交（52ac3f2 / 4eb4f5b），文档未更新。

---

## 3. 已实施步骤（2026-10-16，全部经 `mvn -o clean test` + `check-conventions.py` EXIT=0 验证）

| 步骤 | 内容 | 验证 |
|---|---|---|
| **R1** | 删 `CodeTemplates` 死 import `job.service.JobService`，消除 codegen→job 依赖边（B3） | 编译 + 全量 432 例 |
| **R2** | 新增 `common/tree/TreeBuilder` 泛型树构建（含环防护），`MenuService#buildChildren` 与 `OrgService#toTree` 两套重复实现删除，统一复用（D1） | `TreeBuilderTest` 6 例 + 既有菜单/机构测试不动 |
| **B1a** | `OnlineSessionRules` / `RestrictionRules` 移至 `system.online.rules`（git mv 保历史），生产引用全在 system 模块，security 侧仅 Javadoc 文本随更；消除 system→security 两条反向边（B4） | 20 例 rules 测试随迁全绿 |
| **R3** | 抽 `workbench/report/csv/ReportCsvWriter` 纯函数行模型（严格复刻原 CSV 行结构），`ReportService#exportCsv` 委托之；私有 `escapeCsv` 删除，转义 / BOM / 行尾统一走 `CsvExporter`（D2/D3，行为差异仅 CRLF 行尾与 `\r` 转义补强） | `ReportCsvWriterTest` 7 例 |
| **F1** | **功能开发：报表导出纳入导出中心**。① `ExportTask` 加 `default csv(params)`（单表默认实现 + 多段可覆写）；② `ExportController#download` 参数透传通用化（`@RequestParam Map` 全量透传，原三键行为不变）；③ 新增 `workbench/report/export/ReportExportTask`（code=`report`，与 `ReportController#export` 同一数据与行结构，**内嵌 `report:read` 权限兜底**防导出中心入口权限放大）；④ 前端 `ExportCenterView` 按模板显示参数区（报表类型 / 统计窗口 / 关键字） | `ReportExportTaskTest` 5 例 + 前端 build EXIT=0 |

> F1 接口/数据结构说明：无新端点、无请求响应结构变更；`GET /api/system/export/download` 的查询参数集合由固定三键扩展为全量透传（旧调用方完全兼容），新增模板编码 `report`。前端 `/api/report/export` 报表页导出入口保留不动（两入口共用 ReportCsvWriter）。

---

## 4. 后续批次计划（按风险从低到高，每步独立提交、独立验证）

### 批次 2：Port 解耦 workflow → system（B2）
- **范围**：`common/notification` 或 `workflow` 定义 `StepNotifyPort`（`sendCc(List<Long> userIds, String title, String content)`）；`system` 提供适配器委托 `MessageService#sendCcNotification`；两个 StepDelegate 改注 Port。
- **风险**：低-中。工作流步骤通知行为不变（仍发站内消息），仅依赖方向由「workflow→system 实现」改为「workflow→Port，system→适配器」。
- **回滚**：单提交 revert。
- **验证**：`mvn -o clean test` + check-conventions；新增 `StepNotifyPort` 适配器单测（复用既有 MessageService mock 范式）。

### 批次 3：security → system entity 直连改 Port（B1/B5，最重）
- **范围**：`security.permission` 定义 `PermissionDataPort`（按角色查菜单/API 资源、按用户查角色等），返回 security 自有的快照 record（如 `RolePermissionSnapshot`）；`system` 提供实现（内部查 `sys_role_menu` 等表）。同步评估 `system → security.token.TokenVersionService` 等反向依赖是否随 `system.online.rules` 归位后自然消解。
- **风险**：高。触碰鉴权主链路；须先补 PermissionService 现有行为的黄金测试（对照 MySQL IT 行为），再切换。
- **前置**：MySQL 集成测试环境可跑（本地/CI）。
- **验证**：PermissionService 相关单测 + `*IT` 全量 + 手工登录/菜单/接口授权回归。

### 批次 4：ReportService 继续拆分（C1 余量）
- **范围**：统计原语（`fillHourly/fillDaily/toDistribution/p95Of/durationCounts` 等纯函数）抽 `workbench/report/stats/ReportStats`；`ReportService` 仅保留编排与条件构造。
- **风险**：低（纯函数移动 + static import 调整）。
- **验证**：既有 ReportService 单测随迁 + `mvn -o clean test`。

### 批次 5：ProfileService / OnlineUserService 拆分（C2）
- **范围**：`ProfileService`（552 行）按「资料维护 / 密码修改 / 令牌管理」拆辅助类；`OnlineUserService`（489 行）抽出会话查询与登录限制两个协作者。
- **风险**：中。构造注入面扩大，须保持公开方法签名不变。
- **验证**：全量单测 + check-conventions。

---

## 5. 执行原则（每步必守）

1. **先核磁盘再动手**：文档/汇总与磁盘不一致时以磁盘为准（本轮即发现两处文档失同步）。
2. **小步可回滚**：一个关注点一个本地提交（feat/test/refactor/docs），任一步失败只 revert 该提交。
3. **不改外部契约**：接口路径、请求/响应结构、表结构不变；确需变更时先获显式确认。
4. **双验证门槛**：`mvn -o clean test` 全量绿（禁增量假阳性）+ `scripts/check-conventions.py` EXIT=0（H* 硬规则）。
5. **不推送**：本地提交由用户本机推送 `origin/feat/V1.0.2`。
