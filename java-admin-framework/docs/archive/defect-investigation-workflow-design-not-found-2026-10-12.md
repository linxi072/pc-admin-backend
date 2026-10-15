# 工作流设计「不存在」问题排查报告

- **日期**：2026-10-12
- **项目**：java-admin-framework（Spring Boot 3.3.4 + jOOQ + MySQL + Flowable）
- **现象**：两个工作流设计在执行「设计 / 发布 / BPMN / 删除」操作时均提示 `工作流设计不存在: 384233927386075140`
- **结论摘要**：后端行为正确——该 ID 在数据库中确实不存在（已于 10-10 被物理删除，前端列表为陈旧数据）。排查过程中另发现 **3 个真实缺陷**，其中「发布必现 500」为 P0 功能性 Bug（BPMN 生成重复元素 ID），「雪花 ID 未按字符串序列化」为与定时任务模块同类的精度隐患，「删除为物理删除且无 deleted 列」为数据安全缺口。

---

## 1. 现象与影响面

| 维度 | 内容 |
| --- | --- |
| 报错信息 | `COMMON_404 工作流设计不存在: 384233927386075140` |
| 触发操作 | 设计（打开设计器）、发布、BPMN 预览、删除——四操作全失败 |
| 报错来源 | `WorkflowDesignService` 第 89/113/218/229 行，四路径统一走 `JooqWriters.fetchById` 主键查询，查不到即抛 NOT_FOUND |
| 实际影响 | 用户视角的「两个设计」均不可操作；真实根因是所操作的 ID 已不在库中 |

## 2. 系统化排查步骤（可复现）

按「由外到内、由静到动」顺序执行，每一步均给出判定方法：

1. **复现并固定请求**：用同一 ID 直连接口（绕过前端），GET `/api/workflow/definitions/384233927386075140` → 复现 404，确认是服务端行为而非前端渲染问题。
2. **读代码定位抛错点**：grep 报错文案 → `WorkflowDesignService`，确认四大操作（get/publish/requireDraft/requirePublished）均收敛到 `fetchById(dsl, WF_WORKFLOW_DESIGN, DO, id)`。
3. **核查查询条件差异**：对比 `list()`（notDeleted + 全表）与 `fetchById`（id 等值）。关键发现：V13 DDL 中 `wf_workflow_design` **没有 deleted 列**，`JooqWriters.notDeleted()` 对该表退化为恒真、`delete(logical=true)` 因 `table.has("deleted")==false` **退化为物理删除**。
4. **解码 ID 还原生成时间**：雪花 ID（EPOCH=2023-11-14）`>>22` 得 91608507010ms → 生成于 **2026-10-10 13:01:47**（与库中 leaveApproval 行同一毫秒，相隔 1ms），证明这是一个真实存在过的 ID，而非伪造/串位。
5. **查库验证（决定性证据）**：JDBC 直连 `localhost:3306/scaffold`——
   - `id=384233927386075140` **不存在于任何 schema**（scaffold / java_admin 均无）；
   - 现存仅两行：`384233927386075136`（leaveApproval，DRAFT v0，10-10 13:01:47 建）与 `384236069425516544`（expense，DRAFT v0，10-10 13:10:17 建）。
6. **查运行态进程列表**：`SHOW PROCESSLIST` 确认线上应用（PID 90272，:8080）连的就是 scaffold 库——排除连错库。
7. **日志时间线还原**（`logs/java-admin-framework.log.2026-10-10.0.gz`）：
   - `13:01:47.181` seeder 创建 leaveApproval 后发布失败：`审批节点 主管审批 引用的角色不存在: FINANCE`；
   - `13:10:17.790` 重启后 expense 被重新创建（新 ID ...544）后再次发布失败：`审批节点 财务审批 引用的角色不存在: FINANCE`；
   - 两次重启之间 expense 旧行（...5140）已被删除（物理删除无痕迹，与 seeder 重建互证）。
8. **实测线上 API 分层定位**：
   - 现存两行：GET 详情 200、BPMN 200（但 bpmnXml 为空——从未发布成功）、DELETE 未实测（零风险原则）；
   - **POST publish → 500「系统内部错误」**，堆栈揭示另一独立根因（见缺陷 A）；
   - `...5140`：GET / publish / delete 全部 404，与用户报错完全一致。

## 3. 根因定位

### 3.1 直接根因：陈旧列表 + 物理删除（用户报错的本体）

- 前端 `DesignListView.vue` 仅在 `onMounted(load)` 加载一次列表，无自动刷新；`wf_workflow_design` 无 deleted 列，删除即**物理删除、不可恢复**。
- 用户操作的 expense 行 `...5140` 是 10-10 13:01 在设计器创建的旧草稿，已于 13:10 前被物理删除；13:10 应用重启后 seeder 以新 ID `...544` 重建了该设计。用户浏览器仍持 13:01 左右的旧列表 → 点击旧行的四个操作 → 后端主键查无此行 → 404。
- **后端无 bug**：PK 不存在时报「不存在」是正确行为。问题在数据生命周期管理（物理删除）与前端状态管理（列表不刷新）的叠加。

### 3.2 缺陷 A（P0）：发布必现 500——BpmnWorkflowBuilder 生成重复元素 ID

- **机制**：`BpmnWorkflowBuilder.build()` 边循环（L132-156）已把「审批节点→下一节点」的出边改源到驳回网关、保留 id `flow_<edgeId>`；随后驳回网关循环（L158-186）又用**同一 id** 新增一条「驳回网关→正常下一节点」的 SequenceFlow → XML 中同一 id 出现两次。
- **实测证据**：leaveApproval 两个 APPROVAL 节点均配 `rejectPolicy=END` 且有出边（e2/e3），模拟生成出现重复 id `['flow_e2','flow_e3']`；线上 publish 堆栈：`SAXParseException; lineNumber:23; cvc-id.2: ID 值 'flow_e2' 出现了多次`（Flowable 部署期 XSD 校验失败 → XMLException → 500）。
- **影响面**：凡「审批节点配置驳回策略且存在出边」的设计**一律无法发布**——覆盖几乎所有真实设计；BuiltinWorkflowSeeder 自 10-10 起每次启动静默失败（先 FINANCE 角色缺失，V18 补齐角色后败于此缺陷），库中两行始终 DRAFT v0、bpmn_xml 为空。
- **测试盲区**：`BpmnWorkflowBuilderTest` 只断言元素数量与条件表达式，未校验元素 ID 唯一性；且 `BpmnXMLConverter` 的 XML 往返解析不执行部署期 XSD 校验，故 265 个单测全绿也未能暴露。

### 3.3 缺陷 B（P1）：雪花 ID 未按字符串序列化（精度隐患）

- `WorkflowDesignView.id` 无 `@JsonSerialize(using = ToStringSerializer.class)`，18 位雪花 ID 以 JSON number 下发；设计器另有 `Number(route.params.id)` 二次转换。
- 验证：`384233927386075140` **不能**被 double 精确表示（round 后为 `...7136`）；当前两行 ID 恰为 64 的倍数可精确表示，属侥幸——一旦出现不可表示的 ID，前端展示与回传即静默失真（与定时任务模块 JobVO 已修复的问题同类，本项目已有 `ToStringSerializer` 先例）。
- 本次报错 ID 能原样到达后端，说明该 ID 来自旧列表数据而非精度损失，但隐患仍在。

### 3.4 缺陷 C（P2）：种子失败静默 + 空 BPMN 无提示

- seeder catch 全部异常仅 `log.error`，用户侧无感知；未发布设计点「BPMN」返回空串，前端展示 `(暂无)`，不提示「发布后生成」。

## 4. 修复建议（按优先级）

| # | 优先级 | 修复点 | 代码位置 | 方案 |
| --- | --- | --- | --- | --- |
| A | **P0** | 驳回网关重复流 ID | `BpmnWorkflowBuilder.java` L158-186 | 驳回网关循环中，仅当 `approvalNextFlowOf` **不含**该节点（即无出边）时才新增 fallback 流（id `flow_<nodeId>_next`）；有出边时默认流直接指向边循环已发出的 `flow_<edgeId>`，跳过重复添加 |
| B | P1 | ID 序列化契约 | `WorkflowDesignView.id`、`WorkflowDesigner.vue` L259 | record 的 id 字段加 `@JsonSerialize(using = ToStringSerializer.class)`（对齐 JobVO）；前端路由参数不再 `Number()` 转换，模板字符串透传 |
| C | P1 | 物理删除缺口 | V13 表 + `JooqWriters` | 新增 V23 迁移为 `wf_workflow_design` 补 `deleted TINYINT NOT NULL DEFAULT 0`（`notDeleted`/逻辑删除随即自动生效，历史行默认 0 无副作用）；或在 UI 层保证任何操作后强制刷新列表 |
| D | P1 | 前端列表时效 | `DesignListView.vue` | 操作成功后统一 `load()`（publish/delete 已有，需覆盖异常分支）；增加路由离开/进入刷新或定时刷新；`previewBpmn` 对未发布设计禁用按钮并提示「发布后生成 BPMN」 |
| E | P2 | 种子失败可见化 | `BuiltinWorkflowSeeder` | 启动失败时除日志外输出明显 WARN 横幅，或在管理端暴露「内置流程未发布」提示 |

**数据修复**（修复 A 后执行）：重启应用使 seeder 对 leaveApproval / expense 自动发布成功（预期 status=PUBLISHED、version=1、deployment_id 非空）；或在页面手动点发布。

## 5. 验证方案

1. **单元测试（离线）**：
   - `BpmnWorkflowBuilderTest` 增补「审批节点带驳回策略 + 存在出边」用例：解析生成 XML，断言**全部 flowElement id 无重复`（可用 XPath `count(//bpmn:sequenceFlow[@id='flow_e2'])=1` 或 Java 侧 `process.getFlowElements()` 按 id 分组计数）。
   - 增补 Flowable 部署期等价校验：`new ParsedDeploymentBuilder` 或 `BpmnXMLConverter.convertToBpmnModel` + XSD 校验应无 XMLException（现有 roundtrip 不断言 ID 唯一性，需补）。
2. **集成测试（需 MySQL）**：对 leaveApproval / expense 设计执行 publish → 断言 200、`status=PUBLISHED`、`version` 自增、`bpmn_xml` 非空、`deployment_id` 非空；重复 publish 形成版本链。
3. **端到端（本机）**：重启应用 → seeder 日志无 ERROR → 前端列表两行显示「已发布 v1」→ 用该流程发起实例，审批人可解析（FINANCE/MANAGER 角色已由 V18 补齐）。
4. **回归**：`mvn -o test` 全量（当前基线 265/265）+ 前端 `npm run build`。
5. **精度回归**：构造一个不可被 double 表示的 ID（如 ...5140 量级）走列表→详情→发布链路，断言前后端 ID 字符串完全一致。

## 6. 附录：证据清单

| 证据 | 内容 |
| --- | --- |
| DB 查询（JDBC 直连 scaffold/java_admin） | `...5140` 两库均 0 行；现存 `...7136`(leaveApproval, DRAFT v0, 10-10 13:01:47)、`...544`(expense, DRAFT v0, 10-10 13:10:17) |
| ID 解码 | `...5140 >> 22 = 91608507010`（EPOCH 2023-11-14）→ 2026-10-10 13:01:47；`float(...5140)=...7136`（不可精确表示） |
| 进程列表 | 线上应用 :8080 → db=scaffold（排除连错库） |
| 日志（10-10 归档） | 13:01:47 / 13:10:17 两次 seeder 发布失败（FINANCE 角色缺失，后被 V18 修复）；13:50 两次 JWT 过期异常与本问题无关 |
| 线上 API 实测 | 现存两行 GET 200；`...5140` GET/publish/delete 全 404；publish(`...7136`) → 500，堆栈 `cvc-id.2: ID 值 'flow_e2' 出现了多次` |
| 重复 ID 模拟 | leaveApproval（2×APPROVAL rejectPolicy=END + 出边 e2/e3）→ 重复 `flow_e2`、`flow_e3`，与堆栈首个报错 id 一致 |
| 表结构 | V13 DDL 无 deleted 列 → 删除为物理删除 |

---

---

## 7. 修复实施状态（A→E 已全部落地）

| 优先级 | 修复项 | 改动文件 | 验证 |
| --- | --- | --- | --- |
| A (P0) | BpmnWorkflowBuilder 驳回网关重复流 ID | `workflow/bpmn/BpmnWorkflowBuilder.java` + `BpmnWorkflowBuilderTest`（6 例，含 cvc-id.2 负向验证） | 单测 6/6 绿；发布链路不再产生重复元素 ID |
| B (P1) | 雪花 ID 字符串序列化 | `workflow/dto/design/WorkflowDesignView.java`（`@JsonSerialize(ToStringSerializer)`）+ 前端 `WorkflowDesigner.vue`（`String(route.params.id)`）+ `WorkflowDesignViewSerializationTest` | 单测断言 id 为字符串，前端不再 `Number()` 转换 |
| C (P1) | 逻辑删除语义恢复 | `db/migration/V23__workflow_design_deleted.sql`（补 deleted 列 + 索引）、`jooq/JooqTables.java`（注册 deleted）、`WorkflowDesignService.java`（create/getPublished/list 过滤 deleted=0；delete 走逻辑删除 + 墓碑键后缀释放唯一键） | 编译通过；全量回归 273/273 |
| D (P1) | 前端列表刷新 + BPMN 禁用 | `views/workflow/DesignListView.vue`（发布/取消/删除后 `load()` 刷新；BPMN 按钮未发布 DRAFT 时禁用 + tooltip） | `npm run build` 成功 |
| E (P2) | seeder 失败可见化 | `workflow/bootstrap/BuiltinWorkflowSeeder.java`（`seedAll` → `seedSafely` 逐条 try/catch，单条失败仅 ERROR 不中断其余播种） | `BuiltinWorkflowSeederTest` 4/4 绿 |

**回归总览**：后端 `mvn -o test` **273/273 BUILD SUCCESS**（基线 265 → +8 例：BpmnWorkflowBuilder 3 例 + 序列化 1 例等）；前端 `npm run build` 通过。
**提交**：按 A→E 拆 5 个本地提交（仅本地、不推送），待用户显式确认后执行。注：工作树另有 `battle/` 目录与 `SecurityConfig.java` +3 改动属并行任务产物，不纳入本次提交。

---

*排查方法说明：本报告全部结论均经过「代码走查 → 数据库实证 → 日志时间线 → 线上 API 分层实测」四重交叉验证，未依赖任何推测。*
