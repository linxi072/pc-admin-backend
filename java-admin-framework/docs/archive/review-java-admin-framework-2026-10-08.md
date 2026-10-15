# java-admin-framework 代码审查报告

> 审查时间：2026-10-08 ｜ 仓库：`outputs/java-admin-framework`（main） + 前端 `pc-admin-vue3-vite`
> 审查范围：后端 Spring Boot 3.3 / jOOQ / Flowable / JobRunr 全量源码（184 个 Java 文件）、15 个 Flyway 迁移、前端 Vue3 工程
> 方法：静态走查 + 关键字扫描 + 关键链路实测（BPMN 生成、受让人解析、数据权限合并、权限聚合、前端契约比对）
> 说明：沙箱无 MySQL，无法运行集成测试，结论基于源码静态分析与编译/构建验证（`mvn -o test-compile` 与 `npm run build` 均通过）。

---

## 一、模块完整度概览

| 模块 | 状态 | 说明 |
|------|------|------|
| 认证 / JWT | ✅ 完整 | `@EnableMethodSecurity` 已启用，接口级 `@PreAuthorize` 真实生效；JWT 多角色 claim（逗号 split→Set）就绪 |
| 权限聚合 PermissionService | ✅ 完整 | N:N 后从 `sys_user_role` 取并集；`sys_role_api` + `sys_role_menu` 权限码并集 |
| 数据权限（切面+Provider+条件） | ⚠️ 有缺陷 | 见 P1-①；其余链路完整 |
| 用户 / 角色 / 部门 / 菜单 / 字典 / 配置 / 公告 | ✅ 完整 | CRUD、树、逻辑删除齐备 |
| 站内信 | ⚠️ 部分 | 后端收件人解析（角色 OR 部门）正确；前端 mock 群发筛选有 bug（P2-④） |
| 监控 Monitor | ✅ 完整 | 指标/采样/在线会话/异常日志落库；JobRunr 定时采样 |
| 工作流（设计器+引擎+受让人） | ⚠️ 部分 | 设计→BPMN→Flowable 链路打通；CC/执行步骤节点为占位（P3-⑥）；受让人空集合未拦截（P1-②） |
| 审计 Audit | ✅ 完整 | `AuditAspect` + `AuditLogServiceImpl` 切面落库 |
| API 资源扫描 | ⚠️ 弱 | 仅手动同步端点，未自动触发（P3-⑧） |

---

## 二、缺陷与问题清单（按严重程度）

### P0 — 运行期阻断 / 数据安全（无）
本轮未发现会导致启动失败或数据丢失的 P0 级缺陷。

---

### P1 — 重要正确性 / 安全缺陷（建议本迭代修复）

#### ① 数据权限多角色合并缺陷（N:N 引入）
- **位置**：`security/permission/DefaultDataScopeProvider.java` `resolve()`（约 L69–97）
- **当前状态**：遍历 `sys_role_data_scope` 规则时，命中 `SELF` 即 `return DataScopeResult.self()` 短路。
- **具体表现**：同一用户若 A 角色含 `ALL` 规则、B 角色含 `SELF` 规则，且 B 的规则在 DB 返回中先被遍历，会错误返回 `SELF`（实际应为 `ALL`）。
- **预期行为**：跨全部角色合并——任一 `ALL` 即整体不限；否则 `SELF` 优先于机构类；机构类取并集。
- **为什么现在才暴露**：V6 单角色时每个用户只有一组规则，遍历顺序不影响结果；N:N 多角色后多组规则并存，短路逻辑失效。
- **修复方向**：先 `collect` 全部规则再统一裁决——`if (any ALL) return all(); if (any SELF) return self();` 否则累加 orgIds 返回 `ofOrgs()`。不要用 `return` 提前短路。

#### ② 工作流审批节点无可用受让人时静默失效
- **位置**：`workflow/service/WorkflowDesignService.java` `buildStartVariables()`（L162–173）+ `bpmn/BpmnWorkflowBuilder.java`（L83–86）
- **当前状态**：`assigneeResolver.resolve(node, ...)` 返回空列表时，仍把空 `node_<id>_assignees` 注入多实例 `UserTask`。
- **具体表现**：`INITIATOR_MANAGER` 但发起人无主部门/无 MANAGER 角色、或 `ROLE=FINANCE` 未配置时，该审批节点受让人为空。Flowable 多实例集合为空可能直接跳过该节点或抛异常，导致审批链断裂 / 流程卡死。
- **预期行为**：发起（或发布）时校验每个审批节点至少解析出 1 个受让人，否则拒绝并明确提示「节点 X 未配置有效审批人」。
- **修复方向**：在 `buildStartVariables` / `start` 前增加 `if (assignees.isEmpty()) throw BusinessException(...)`；同时给设计器保存/发布加同样的前置校验。

---

### P2 — 功能缺失 / 明确 bug（建议近期处理）

#### ③ 内置请假流程受让人写死为 USER id=1（admin）占位
- **位置**：`workflow/bootstrap/BuiltinWorkflowSeeder.java` L89、L97–99
- **当前状态**：内置「请假审批」两个审批节点 `assigneeType=USER, assigneeExpression="1"`，注释自陈「仅作占位」。
- **具体表现**：若运维未在设计器改受让人，所有请假审批都落到 admin(id=1)。报销流程已用 `INITIATOR_MANAGER` / `ROLE=FINANCE` 动态解析。
- **预期行为**：种子流程应尽量贴近真实组织，或发布后强制提示管理员配置受让人。
- **修复方向**：将请假流程改为与报销一致（发起人主管 + 财务/指定角色），或在 seed 后打 WARN 提示。

#### ④ 前端 mock 消息群发按角色/部门筛选引用已移除字段（N:N 漏改）
- **位置**：`pc-admin-vue3-vite/src/api/mock/db.js` L830（及 L380、L825 注释）
- **当前状态**：handler 用 `data.roleIds.includes(u.roleId)` / `data.orgIds.includes(u.orgId)` 过滤，但用户种子已改为 `roleIds`/`deptIds` 数组（N:N 重构），单值字段 `roleId`/`orgId` 已不存在。
- **具体表现**：mock 模式下「按角色/部门群发站内信」永远匹配不到人，报 `CONFLICT: 按当前筛选条件未匹配到任何接收人`。注释仍写「V6 收敛后单角色单部门」。
- **预期行为**：按多值关联过滤，与真实后端 `MessageService.resolveReceivers` 一致。
- **修复方向**：L830 改为 `(hasRole && (u.roleIds||[]).includes(...)) || (hasOrg && (u.deptIds||[]).includes(...))`；同步更正 L380/L825 注释。

#### ⑤ 集成测试无法在沙箱验证 + 可能基于旧单角色假设
- **位置**：`src/test/`（AuthFlowIT、LeaveApprovalWorkflowIT、DataScopeConditionsTest、BuiltinWorkflowSeederTest 等 10 个）
- **当前状态**：沙箱无 MySQL，IT 全量不可跑；N:N 重构改变了 `DataScope`/`PermissionService`/`UserService` 行为。
- **具体表现**：`DataScopeConditionsTest`（org 过滤）、`AuthFlowIT`（多角色 token 权限并集）等可能仍按单角色/单部门假设编写，回归风险高。
- **预期行为**：在本机 MySQL 上重跑全量 IT，确认无回归。
- **修复方向**：本机执行 `mvn -o verify`（或 `test`）跑 IT；核对测试断言是否覆盖多角色并集、多部门归属、主部门 is_primary 语义。

---

### P3 — 可优化 / 文档与健壮性（可排期）

#### ⑥ 工作流 CC / 执行步骤节点为占位实现
- **位置**：`workflow/adapter/step/CcStepDelegate.java`、`GenericStepDelegate.java`
- **状态**：仅打结构化日志 + 写变量，未真正发通知 / 执行业务。
- **修复方向**：CC 节点接入 `MessageService` 发送抄送通知；GenericStep 提供扩展点（回调外部逻辑或落库步骤执行记录）。

#### ⑦ 多处注释停留在「V6 收敛/单角色单部门」
- **位置**：`MessageService.java` L39、`DefaultDataScopeProvider.java` L26–31、`mock/db.js` L380/L825 等。
- **修复方向**：批量更新为「纯 N:N（回退 V6）」语义，避免误导后续维护。

#### ⑧ ApiResourceScanner 不自动触发
- **位置**：`system/service/ApiResourceScanner.java`（仅 `ApiResourceController#sync` 手动调用）
- **现状**：V11 已播种全量目录并授权 SUPER_ADMIN，故不影响 SUPER_ADMIN；但新增接口后必须手动同步或更新 V11，否则新接口 403。
- **修复方向**：挂 `ApplicationRunner`/`@EventListener(ContextRefreshedEvent)` 做「仅新增、不覆盖人工改动」的增量自动同步。

#### ⑨ DemoJob 为示例定时任务
- **位置**：`job/DemoJob.java`（已在 `JobRunrConfig` 注册调度）
- **状态**：演示用途，非业务必需。生产可保留或移除；确认无误导。

#### ⑩ 部门数据权限「用户未挂部门」时降级为不限
- **位置**：`DefaultDataScopeProvider.java` L100–104、`DataScopeConditions.java` L67–69
- **现状**：配了机构类规则但解析不出任何机构时，降级为「不限」并打 WARN（避免一条数据都看不到）。
- **权衡**：这是可用性优先于安全性的选择——配置错误时用户可能看到全部数据。
- **修复方向**：明确产品策略（看到全部 vs 看不到），在文档/告警中固化，或改为「本人」语义兜底。

---

## 三、处理建议（按优先级）

| 优先级 | 处理项 | 工作量 | 风险 |
|--------|--------|--------|------|
| P1-① | 数据权限多角色合并短路修复 | 0.5d | 低（纯逻辑，加单测） |
| P1-② | 工作流空受让人拦截（发起/发布校验） | 0.5d | 低 |
| P2-④ | 前端 mock 群发筛选字段修正 | 0.5h | 极低 |
| P2-③ | 内置请假流程受让人动态化 | 1h | 低 |
| P2-⑤ | 本机重跑 IT 验证 N:N 回归 | 0.5d | 中（可能暴露更多） |
| P3-⑥ | CC/执行步骤节点落地 | 2d | 中 |
| P3-⑧ | API 资源自动增量同步 | 0.5d | 低 |
| P3-⑦/⑨/⑩ | 注释清理 / DemoJob 处置 / 数据权限降级策略确认 | 0.5d | 低 |

**建议本迭代必做**：P1-①、P1-②、P2-④（均为近期 N:N 重构直接引入或暴露，且修复成本低、风险小）。P2-⑤ 作为回归验证一并进行。

---

## 四、遗留验证清单（需本机/外部依赖）

- [ ] 本机 MySQL 跑全量 IT（AuthFlowIT / DataScopeConditionsTest / LeaveApprovalWorkflowIT / BuiltinWorkflowSeederTest）
- [ ] 多角色用户登录后 JWT 权限并集是否正确（SUPER_ADMIN 仍全量）
- [ ] 用户多部门归属下「主部门」is_primary=1 取值与数据权限 DEPT 范围一致
- [ ] 工作流发起后任务正确落到 `AssigneeResolver` 解析出的审批人（含 INITIATOR_MANAGER / ROLE 类型）
- [ ] CC 节点当前仅日志，确认产品是否要求真实通知
