# M33 任务依赖编排 DAG（使用与实现说明）

> 版本：`feat/V1.0.4` ｜ 日期：2026-10-28 ｜ 状态：✅ 已交付 ｜ 关联文档：[`roadmap-m19-plus.md` §4.1.7](./roadmap-m19-plus.md)

## 1. 能力简介

在既有定时任务（JobRunr 双层调度：`sys_job`/`sys_job_log` 自管 + `JobSchedulerPort` 调度）基础上，新增**任务依赖编排**能力：

- 任务 A 可声明「依赖 B」（`sys_job.depends_on`）。
- 仅当 B **至少成功执行一次**后，A 才被自动触发。
- 依赖未满足时，A 进入 `WAITING`（等待依赖）挂起态，**不向调度器注册**，避免无效轮询与空跑。
- 本质是「完成事件驱动的即时触发」DAG，而非经典 cron 级联。

**零新表**（仅 `sys_job` 增 `depends_on VARCHAR(1024)` 列）、**零新依赖**、复用既有 job 菜单（id=1031，route=/job/list），不新种子。

## 2. 使用方式（运维/管理员视角）

### 2.1 配置前置依赖

在「定时任务 → 新建/编辑」对话框中，新增「前置依赖」多选框：

- 列出当前全部任务（排除自身，防止自依赖）。
- 可多选；保存后经后端校验：自依赖拒绝、依赖的任务不存在拒绝、**形成环拒绝**。
- 留空表示无依赖（与原行为一致，可立即启动）。

### 2.2 WAITING（等待依赖）状态

- 当任务配置了前置依赖但依赖尚未全部满足时，`启动` 操作不会注册调度，而是将其置为 `WAITING` 挂起态（列表状态标签显示「等待依赖」）。
- 上游任务成功执行后，由 `JobDagService.onJobSuccess` 自动检测并即时触发所有就绪（依赖满足）的 `WAITING` 下游——复用上游任务自身的触发类型（CRON/SIMPLE 注册周期；ONE_TIME 注册「now+2s」一次性）。

### 2.3 触发下游（手动）

当某任务被其他任务依赖时，其操作列出现「触发下游」按钮：

- 立即驱动所有依赖本任务且已就绪的 `WAITING` 下游，返回实际触发数量。
- 用于上游已成功、但希望立即验证下游触发的场景（无需等待下一次事件驱动）。

### 2.4 依赖可视化

列表新增「依赖编排」列：

- **依赖**：本任务的前置依赖任务（按名称展示）。
- **下游**：依赖本任务的其他任务（按名称展示）。
- 名称经 `jobPage` 全量清单反查（ID→名称）。

## 3. 后端架构

```
                ┌─────────────────────────────────────────────┐
                │              JobController                   │
                │  POST /api/jobs/{id}/trigger-downstream      │
                └───────────────┬─────────────────────────────┘
                                │
                ┌───────────────▼─────────────────────────────┐
                │              JobDagService                   │
                │  onJobSuccess / triggerDownstream /          │
                │  isReady / validateDependencyEdit /          │
                │  assertNoDependents / dependentsMap          │
                └───┬───────────────────────┬─────────────────┘
                    │                        │
      ┌─────────────▼─────────┐   ┌─────────▼──────────────────┐
      │     JobDagRules        │   │      JobDagPort (端口)      │
      │ (纯函数: 解析/校验/     │   │  JooqJobDagAdapter (适配)   │
      │  环检测/拓扑/去重)      │   │  → JooqWriters(sys_job)    │
      └────────────────────────┘   └────────────────────────────┘
                    │
      ┌─────────────▼─────────┐
      │   JobSchedulerPort     │  (JobRunr 实现：scheduleCron/
      │                       │   scheduleInterval/scheduleOneTime)
      └────────────────────────┘
```

- **纯函数层 `JobDagRules`**：`parseDependsOn` / `serializeDependsOn`（JSON 数组文本 ↔ 列表，去重保序、容错）/ `validateDependencies`（自依赖 + 存在性）/ `hasCycle`（DFS 回边）/ `topologicalOrder`（Kahn）/ `dedupe`。与 `JobValidator` 同范式（`static`、抛 `BusinessException.badRequest`、显式传参）。
- **防腐层端口 `JobDagPort` / `JooqJobDagAdapter`**：隔离 `sys_job` 读取与状态写回；每次调用全量加载 `sys_job` 至内存并构建反向依赖索引；`setRunning`/`setStatus` 仅写目标列，避免 `new SysJobDO()` 默认值污染。
- **编排 `JobDagService`**：`onJobSuccess` 被 `JobExecutor` 成功路径钩子调用；`triggerReady` 复用 `JobSchedulerPort` 按上游触发类型注册调度并精确写回 `RUNNING` 态与 `next_fire_time`。
- **执行器钩子 `JobExecutor`**：`recordResult` 成功后 `try/catch` 调 `jobDagService.onJobSuccess`（失败仅告警，不影响本次执行结果）；状态门控允许 `WAITING`。

### 3.1 依赖就绪判定（isReady）

| 场景 | 判定 |
|---|---|
| 无依赖 | 就绪（可立即启动） |
| 上游 `COMPLETED` 或 `success_runs > 0` | 满足 |
| 上游未成功（`PAUSED`/`RUNNING` 且 `success_runs=0`） | 未满足 |
| 上游已删除（`findById` 空） | 视为满足（避免悬空边永久阻塞） |

### 3.2 删除守卫

删除任务前经 `assertNoDependents` 校验：若存在其他任务依赖本任务，拒绝删除并提示解除依赖。避免悬空依赖。

## 4. API 契约

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/jobs/{id}/trigger-downstream` | 触发本任务的就绪下游，返回实际触发数（int） | `job:manage` |

`JobVO` 新增字段：

- `dependsOn: number[]`（前置依赖任务 ID）
- `dependents: string[]`（下游任务 ID，雪花 ID 字符串化防前端精度丢失）
- `waitForDeps: boolean`（是否正处于等待依赖态，即 `WAITING`）

`JobCreateDTO` / `JobUpdateDTO` 新增 `dependsOn: number[]`（可选，可空）。

## 5. 数据迁移

`V59__job_dependency.sql`：通过 `information_schema.columns` 探测 `sys_job.depends_on` 是否已存在，不存在才执行 `ALTER TABLE sys_job ADD depends_on VARCHAR(1024)`——**幂等、可重跑**。配套 `JooqTables.SYS_JOB` 列定义与 `SysJobDO.dependsOn` 字段同步（先行于本模块落盘）。

## 6. 测试与门禁

新增 34 例单测（均零 Spring / 零 DB）：

- **`JobDagRulesTest` 17 例**：解析（正常/去重保序/忽略非数字/空与非法返回空）、序列化（数组/去重/空返回 null）、校验（null/空通过、自依赖抛、不存在抛、存在通过、null existingSet 跳存在性）、环检测（三节点环/二节点环/无环）、拓扑排序（依赖前置）、去重（去 null 保序）。
- **`JobDagServiceTest` 17 例**（Mockito 模拟 `JobDagPort`+`JobSchedulerPort`）：onJobSuccess 触发就绪 WAITING 下游 / 不触发 RUNNING / 不触发未就绪 / 无下游空操作；triggerDownstream 仅计就绪 WAITING；assertNoDependents 抛/通过；isReady 四态；validateDependencyEdit 拒不存在/自依赖/环、接受无环；dependentsMap 聚合。

门禁：`mvn -o clean test` **全仓 1610→1644 例全绿** + `scripts/check-conventions.py` EXIT=0 + 前端 `npx vite build` EXIT=0。

## 7. 设计约束与边界

1. **依赖「至少成功一次」**：不要求上游结束，周期任务首次成功即可驱动下游，符合「上游产出即下游输入」的实用语义。
2. **上游删除视为满足**：运维误删上游时，下游不会永久卡在 `WAITING`。
3. **幂等触发**：仅对 `WAITING` 态触发，已运行/暂停者不重复调度。
4. **编辑安全**：自依赖、不存在的依赖、成环均被拦截；新受控资源零前端硬编码。
5. **不引入新调度语义**：复用既有 `JobSchedulerPort`，DAG 只是「是否注册调度 + 何时触发」的编排层，调度本身仍由 JobRunr 负责。
