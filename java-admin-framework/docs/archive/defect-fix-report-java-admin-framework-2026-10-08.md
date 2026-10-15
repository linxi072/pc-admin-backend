# java-admin-framework 缺陷修复清单（分级报告）

> 生成时间：2026-10-08 ｜ 基于 `review-java-admin-framework-2026-10-08.md` 的修复落地
> 仓库：`outputs/java-admin-framework`（main）+ 前端 `pc-admin-vue3-vite`
> 分级口径：严重 = P0、高 = P1、中 = P2、低 = P3
> 验证环境：沙箱无 MySQL，仅可执行 `mvn -o test-compile` / `mvn -o test`（离线单测）/ `npm run build`；集成测试（IT）与运行期多实例需本机 MySQL 验证，见文末「遗留验证清单」。

---

## 一、修复总览

| 严重程度 | 项数 | 编号 | 状态 |
|----------|------|------|------|
| 严重 | 0 | — | 无 |
| 高 | 2 | ① 数据权限多角色合并短路、② 工作流空受让人拦截 | 已全部修复并离线验证 |
| 中 | 3 | ③ 请假流程受让人动态化、④ 前端 mock 群发字段、⑤ 集成测试 N:N 回归适配 | 已全部修复并离线验证 |
| 低 | 5 | ⑥ CC/执行步骤节点落地、⑦ 注释清理、⑧ API 资源自动同步、⑨ DemoJob 复核、⑩ 降级策略确认 | 已全部处理并离线验证 |

**编译/构建验证汇总（沙箱已执行）**
- 后端：`mvn -o test-compile` 通过；`mvn -o test` 离线单测 **41 例全部通过（BUILD SUCCESS）**。
- 前端：`npm run build` 通过（耗时约 2m9s）。

---

## 二、高（High）

### ① 数据权限多角色合并短路（N:N 引入）
- **问题描述**：`DefaultDataScopeProvider.resolve()` 遍历 `sys_role_data_scope` 规则时，命中 `SELF`/`ALL` 即提前 `return` 短路。同一用户 A 角色含 `ALL`、B 角色含 `SELF` 时，若 B 规则先被遍历，会错误返回 `SELF`（应为 `ALL`）；机构类规则同理可能被漏判。
- **根因分析**：V6 单角色时代每个用户只有一组规则，遍历顺序不影响结果；纯 N:N 重构后用户绑定多角色、多组规则并存，原「遇 ALL/SELF 即 return」的短路逻辑失效，导致跨角色语义丢失。
- **具体修复方案**：
  - 将合并裁决抽离为纯函数 `mergeRules(Set<Long> roleIds, Long primaryOrgId, List<DataScopeType> ruleTypes, Set<Long> resolvedOrgIds, String resourceCode)`：先 `collect` 全部规则类型，再统一裁决——任一 `ALL` ⇒ 整体不限；否则任一 `SELF` ⇒ 本人；否则机构类 `DEPT/DEPT_AND_CHILD/CUSTOM` 取并集；存在机构类规则却解析不出机构 ⇒ fail-open 降级为不限并告警。
  - `resolve()` 仅负责分类规则类型 + 查库解析机构集合，最终调用 `mergeRules`。行为与原修复意图完全一致，且可离线单测。
  - 新增 `DefaultDataScopeProviderTest`（8 例）覆盖回归点：`[SELF,DEPT]→self`、`[ALL,SELF,DEPT]→all`、`[DEPT,DEPT_AND_CHILD]` 并集、`CUSTOM/DEPT` 解析为空降级等。
- **验证方式**：`mvn -o test` 中 `DefaultDataScopeProviderTest` 8 例全过；全量单测 41 例 BUILD SUCCESS。运行期多角色真实并集需在 **本机 MySQL** 跑 `DataScopeConditionsTest`/相关 IT 复核。

### ② 工作流审批节点无可用受让人时静默失效
- **问题描述**：`WorkflowDesignService.buildStartVariables()` 在 `AssigneeResolver.resolve(node,...)` 返回空列表时，仍把空 `node_<id>_assignees` 注入多实例 `UserTask`。`INITIATOR_MANAGER` 但发起人无主部门、或 `ROLE=FINANCE` 未配置时，受让人为空，Flowable 多实例集合为空可能跳过节点或抛异常，审批链断裂/流程卡死。
- **根因分析**：发布/发起流程只做 BPMN 编译，缺「发布期受让人可解析性」前置校验；运行期空集合未被拦截。
- **具体修复方案**：
  - `AssigneeResolver` 新增 `validateConfig(WorkflowNodeDTO node)`：发布期校验 `ROLE`（角色码须存在）/ `ORG`（部门须存在）；`USER/INITIATOR/INITIATOR_MANAGER` 由运行期解析校验。新增 `nodeDesc()` 辅助描述节点。
  - `WorkflowDesignService.buildStartVariables()`：将受让人注入由仅 APPROVAL 扩展到 **APPROVAL / CC / SERVICE 三类节点**；APPROVAL 节点 `assignees.isEmpty()` 直接抛 `BusinessException(VALIDATION_ERROR, "审批节点「X」未解析出任何有效审批人…")`。
  - `publish()` 在 `BpmnWorkflowBuilder.build` 前插入：`for (node : nodes) if (APPROVAL) assigneeResolver.validateConfig(node);` 实现发布期拦截。
- **验证方式**：`mvn -o test-compile` 通过；`BuiltinWorkflowSeederTest`/`BpmnWorkflowBuilderTest` 等构建类测试通过。发布期校验与运行期多实例落人需在 **本机 MySQL** 跑 `LeaveApprovalWorkflowIT` 复核。

---

## 三、中（Medium）

### ③ 内置请假流程受让人写死为 USER id=1（admin）占位
- **问题描述**：内置「请假审批」两个审批节点 `assigneeType=USER, assigneeExpression="1"`，注释自陈「仅作占位」。运维若不改设计器，所有请假审批都落到 admin(id=1)，而报销流程已用 `INITIATOR_MANAGER`/`ROLE=FINANCE` 动态解析，两者不一致。
- **根因分析**：种子流程为快速打通链路而写死占位，未随组织模型（纯 N:N）动态化。
- **具体修复方案**：`BuiltinWorkflowSeeder.buildLeaveApprovalDesign()` 改为 `countersign → INITIATOR_MANAGER`、`manager → ROLE "FINANCE"`，与报销流程一致；注释同步说明可在设计器按需调整为具体人员/部门/角色。
- **验证方式**：`BuiltinWorkflowSeederTest.leaveApprovalSeedBuildsValidBpmn` 新增断言锁定两个节点受让人类型（`INITIATOR_MANAGER` / `ROLE=FINANCE`）；`mvn -o test` 通过。

### ④ 前端 mock 消息群发按角色/部门筛选引用已移除字段（N:N 漏改）
- **问题描述**：`pc-admin-vue3-vite/src/api/mock/db.js` 群发 handler 用 `data.roleIds.includes(u.roleId)` / `data.orgIds.includes(u.orgId)` 过滤，但用户种子已改为 `roleIds`/`deptIds` 多值数组，单值字段 `roleId`/`orgId` 已不存在。mock 下「按角色/部门群发」永远匹配不到人，报 `CONFLICT: 按当前筛选条件未匹配到任何接收人`。
- **根因分析**：N:N 重构改了用户数据模型但未同步 mock 拦截层；注释仍写「V6 收敛后单角色单部门」。
- **具体修复方案**：群发过滤改为多值包含匹配：
  ```js
  .filter((u) => (hasRole && (u.roleIds || []).some((rid) => data.roleIds.includes(Number(rid))))
             || (hasOrg  && (u.deptIds || []).some((did) => data.orgIds.includes(Number(did)))))
  ```
  同步更正用户区与群发区注释（V6 收敛 → 纯 N:N：多角色 `roleIds` + 多部门 `deptIds` 数组）。
- **验证方式**：`npm run build` 通过（2m9s）。逻辑等价性以源码比对确认（沙箱无浏览器运行时，交互行为需本机 `USE_MOCK` 联调复核）。

### ⑤ 集成测试无法在沙箱验证 + 可能基于旧单角色假设
- **问题描述**：沙箱无 MySQL，IT 全量不可跑；N:N 重构改变了 `DataScope`/`PermissionService`/`UserService` 行为，原测试可能仍按单角色/单部门编写，回归风险高。
- **根因分析**：数据权限合并缺陷（①）恰为测试盲区——无单测覆盖多角色合并裁决；部分测试断言未随 N:N 模型更新。
- **具体修复方案**：
  - 全量 `src/test` 关键字扫描确认 **无 `roleId`/`orgId` 单值字段残留引用**；`AuthFlowIT` 仅测登录/刷新，不受 N:N 影响。
  - 将多角色合并裁决抽为纯函数（见①）并补 **8 例单测**，使该缺陷回归被锁定。
  - `BuiltinWorkflowSeederTest` 新增请假受让人动态化断言（见③），锁定 N:N 受让人改造。
  - 其余 DB-backed IT（`AuthFlowIT`/`LeaveApprovalWorkflowIT`/`DataScopeConditionsTest` 等）保留，待本机 MySQL 跑全量回归。
- **验证方式**：沙箱内 `mvn -o test` 41 例全过（纯函数/构建类离线可跑）。**全量 IT 需本机执行**：`mvn -o verify -Dspring.profiles.active=integration -DTEST_DB_URL=… -DTEST_DB_USER=… -DTEST_DB_PASSWORD=…`。

---

## 四、低（Low）

### ⑥ 工作流 CC / 执行步骤节点为占位实现
- **问题描述**：`CcStepDelegate`/`GenericStepDelegate` 仅打结构化日志 + 写变量，未真正发通知/执行业务。
- **根因分析**：设计→BPMN→Flowable 链路先打通，CC/执行步骤的业务副作用留作占位。
- **具体修复方案**：
  - `MessageService` 新增 `@Transactional sendCcNotification(List<Long> userIds, String title, String content)`：构造 `SendMessageRequest(..., "CC", userIds, null, null)` 调 `send()`，空列表返回 0。
  - `CcStepDelegate`（重写）：`@RequiredArgsConstructor` 注入 `MessageService`，`execute` 读取 `node_<id>_assignees` 变量→解析为 Long 列表→调 `sendCcNotification` 发抄送，失败仅记日志不阻断；置 `cc_<id>_done=true`。
  - `GenericStepDelegate`（重写）：注入 `MessageService`，记录结构化日志 + 向关注人发通知（`sendCcNotification`）；置 `step_<id>_done=true`。两者均含 `parseUserIds(Object)` 兼容 `List<Long>`/`List<String>`/`Number`。
- **验证方式**：`mvn -o test-compile` 通过。真实发信/落库需 **本机 IT** 复核。

### ⑦ 多处注释停留在「V6 收敛/单角色单部门」
- **问题描述**：`MessageService.java`、`DefaultDataScopeProvider.java`、`SendMessageRequest.java`、`db.js` 等注释仍写「V6 收敛后单角色单部门」，与纯 N:N 模型不符，易误导维护。
- **根因分析**：N:N 重构未同步清理历史注释。
- **具体修复方案**：批量更新注释为「纯 N:N（回退 V6）」语义——用户多角色 `roleIds` + 多部门 `deptIds`；数据权限合并按多角色规则并集裁决。
- **验证方式**：编译/构建通过；关键字复核无遗留「V6 收敛后单角色单部门」表述。

### ⑧ ApiResourceScanner 不自动触发
- **问题描述**：`ApiResourceScanner` 仅由 `ApiResourceController#sync` 手动调用。新增接口后必须手动同步或更新 V11 种子，否则新接口 403。
- **根因分析**：缺乏启动期自动增量同步钩子。
- **具体修复方案**：新增 `system/bootstrap/ApiResourceAutoSyncRunner.java`（`@Component @Order(300) implements ApplicationRunner`）：`run()` 调 `apiResourceService.sync()` 做「仅新增、不覆盖人工改动」的增量同步；异常仅记日志不阻塞启动。初版误用 `result.inserted()`/`updated()`/`unchanged()`/`orphaned()`，已修正为 record 真实访问器 `insertedCount()`/`updatedCount()`/`unchangedCount()`/`skippedCount()`/`orphanCount()`（修复后 `mvn -o test-compile` 通过）。
- **验证方式**：`mvn -o test-compile` 通过（编译错误已修复）。启动期实际同步效果需 **本机 MySQL** 启动应用复核。

### ⑨ DemoJob 为示例定时任务
- **问题描述**：`job/DemoJob.java` 为演示用途非业务必需，原文建议「确认无误导」。
- **根因分析**：迁移自 XXL-JOB 的 `demoJobHandler`，保留作示例。
- **具体修复方案**：复核确认——该任务注释清晰说明「JobRunr 直接调度 Spring Bean 方法、任务元数据持久化在业务库」，无单角色/单部门假设，亦无 N:N 误导。**保留原样，无代码改动必要**。
- **验证方式**：`mvn -o test-compile` 通过；源文件复核无误。

### ⑩ 部门数据权限「用户未挂部门」时降级为不限
- **问题描述**：配了机构类规则但解析不出任何机构时，降级为「不限」并打 WARN——可用性优先于安全性，配置错误时用户可能看到全部数据。
- **根因分析**：原降级策略未在产品层面固化，后续维护者可能误以为是 bug。
- **具体修复方案**：在 `DefaultDataScopeProvider.mergeRules()` 的 fail-open 分支注释中固化策略：「故意 fail-open，可见性优先于安全性；若产品要求『看不到任何数据』语义，请在此改为 `return DataScopeResult.self()`」，并保留 WARN 指向配置问题。
- **验证方式**：`mvn -o test` 中 `DefaultDataScopeProviderTest.orgRuleWithEmptyResolvedDegradesToAll` / `customRuleWithEmptyResolvedDegradesToAll` 两例覆盖该降级分支，均通过。

---

## 五、遗留验证清单（需本机 / 外部依赖）

以下项沙箱无法执行（无 MySQL），需在 **本机原生 MySQL 8.x** 上验证：

- [ ] 后端全量 IT：`mvn -o verify -Dspring.profiles.active=integration -DTEST_DB_URL=… -DTEST_DB_USER=… -DTEST_DB_PASSWORD=…`
  - `AuthFlowIT`：多角色用户登录后 JWT 权限并集是否正确（SUPER_ADMIN 仍全量）。
  - `DataScopeConditionsTest` / `DefaultDataScopeProvider` 运行期：多部门归属下「主部门」`is_primary=1` 与 DEPT 范围一致。
  - `LeaveApprovalWorkflowIT`：工作流发起后任务正确落到 `AssigneeResolver` 解析出的审批人（含 `INITIATOR_MANAGER` / `ROLE` 类型）；空受让人拦截在 publish 路径生效。
- [ ] CC 节点真实发信：`CcStepDelegate`/`GenericStepDelegate` 经 `MessageService.sendCcNotification` 落库站内信（沙箱仅验证编译 + 逻辑）。
- [ ] `ApiResourceAutoSyncRunner` 启动期增量同步：新增 `@PreAuthorize` 接口后无需手动 sync 即不 403。
- [ ] 前端 `USE_MOCK` 下群发筛选交互（④ 逻辑已修，需联调确认 UI 行为）。

## 六、改动文件清单

后端（java-admin-framework）
- `security/permission/DefaultDataScopeProvider.java`（合并逻辑抽纯函数 + 降级注释）
- `security/permission/DataScopeResult.java`（不变，仅被单测引用，确认语义）
- `workflow/assignee/AssigneeResolver.java`（新增 `validateConfig`/`nodeDesc`）
- `workflow/service/WorkflowDesignService.java`（三类节点受让人注入 + 空受让人拦截 + publish 校验）
- `workflow/bootstrap/BuiltinWorkflowSeeder.java`（请假受让人动态化）
- `system/service/MessageService.java`（新增 `sendCcNotification`）
- `system/dto/SendMessageRequest.java`（注释清理）
- `workflow/adapter/step/CcStepDelegate.java`（重写，接入抄送通知）
- `workflow/adapter/step/GenericStepDelegate.java`（重写，结构化日志 + 关注人通知）
- `system/bootstrap/ApiResourceAutoSyncRunner.java`（**新增**，启动期自动增量同步）

前端（pc-admin-vue3-vite）
- `src/api/mock/db.js`（群发多值匹配 + 注释清理）

测试（新增/增强）
- `src/test/java/com/acme/scaffold/security/permission/DefaultDataScopeProviderTest.java`（**新增**，8 例）
- `src/test/java/com/acme/scaffold/workflow/bootstrap/BuiltinWorkflowSeederTest.java`（新增请假受让人断言）
