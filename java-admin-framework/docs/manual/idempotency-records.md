# 系统：幂等记录管理（M2 运维侧面）

> 模块：`common/idempotency`（controller / service / dto / rules）｜ 状态：已交付（2026-10-20）｜ 分支：`feat/V1.0.2`

## 1. 背景与能力

M2 幂等模块保证「同一 `Idempotency-Key` 在窗口期内只执行业务一次」：`IdempotencyService` 在请求链路上写入 `sys_idempotency_record`（acquire 登记 → markSuccess / markFailed 回写 → 系统错误时 release），`IdempotencyCleanupJob` 周期清理过期行。

但在本模块交付前，这些数据**只写不查**：

- 运维看不到当前有多少记录、三态（PROCESSING / SUCCESS / FAILED）如何分布；
- 客户端「拿到响应前断开」会残留 `PROCESSING` 僵尸行（直到过期才被清理），无查询入口即无发现手段；
- `FAILED` 行的错误码/原因是排障「为什么被幂等拦住」的第一手资料，此前只能翻数据库；
- 清理只能等 JobRunr 周期，无法在排障时手动触发。

本模块补齐管理侧面：**分页查询、统计摘要、手动清理**三条入口，全部只读或受控写（清理与周期任务同口径），不触碰核心写路径语义。

## 2. 模块结构

```
common/idempotency/
├── IdempotencyService.java            （既有：核心写路径，acquire/mark/release/purgeExpired）
├── IdempotencyStatus.java             （既有：状态机 PROCESSING/SUCCESS/FAILED）
├── domain/IdempotencyRecordDO.java    （既有：sys_idempotency_record 持久化对象）
├── controller/IdempotencyRecordController.java   新增：REST 入口（3 端点）
├── service/IdempotencyRecordService.java         新增：只读查询 + 清理委托
├── dto/IdempotencyRecordView.java                新增：记录视图（不含响应快照）
├── dto/IdempotencyStatsView.java                 新增：统计摘要视图
└── rules/IdempotencyRecordRules.java             新增：纯函数（筛选归一/时间窗/过期判定）
```

依赖方向：`controller → service → {dto, rules, IdempotencyService, jooq.dsl}`；`dto → {domain, rules}`；`rules` 零依赖。无包级循环（`check-conventions.py` H1 通过）。

## 3. 快速启用

**零配置、零迁移、零新依赖**：表（V16，后并入 V32 consolidated schema）与写路径均已存在，控制器随组件扫描注册，启动即用。无需改 `application.yml`。

## 4. 接口与权限

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/system/idempotency-records/page` | `system:idempotency:read` | 分页查询；筛选：`status` / `apiScope` / `userId` / `keyword` / `startTime` / `endTime`（ISO 日期时间）；排序：`sortField` + `sortDirection`（白名单映射） |
| GET | `/api/system/idempotency-records/summary` | `system:idempotency:read` | 统计摘要（同筛选口径）：总数 / 三态分布 / 已过期待清理 / PROCESSING 占比 / 最近登记时间 |
| POST | `/api/system/idempotency-records/purge` | `system:idempotency:manage` | 手动清理过期记录，返回删除条数（与 `IdempotencyCleanupJob` 同口径） |

```bash
# 分页（默认最近 7 天、created_at DESC）
curl -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/system/idempotency-records/page?status=PROCESSING&size=20"

# 摘要
curl -H "Authorization: Bearer $TOKEN" "$BASE/api/system/idempotency-records/summary"

# 手动清理过期记录
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/system/idempotency-records/purge"
```

权限说明：`read` / `manage` 分离便于最小授权；`ApiResourceAutoSyncRunner` 启动时扫描 `@PreAuthorize` 自动登记 `sys_api_resource` 并授予 SUPER_ADMIN，**无需手工播种**。如需菜单入口，按 V37（短信记录）模式补一条 `system:idempotency` 菜单种子即可（非阻塞）。

## 5. 关键设计点

1. **`response_snapshot` 不暴露、不检索**：该列保存首次执行的完整响应体（最大 4000 字符），可能含业务数据与 PII。重放由幂等切面自动完成，运维排障只需要 key / scope / 指纹 / 错误码，从契约上消除越权读取面。
2. **不提供单条删除**：删除一条仍处 `PROCESSING` 的记录，会让同 key 的在途请求失去「第二道防线」而重复执行业务——直接破坏 M2 核心保证。唯一清理入口是「删除已过期记录」（过期即超出幂等窗口，同 key 再现本就是全新业务操作），与周期任务调用同一 `IdempotencyService.purgeExpired()`。
3. **容错查询 posture**：与登录日志 / 短信记录页一致——非法 `status`、非数字 `userId`、空 `keyword` 一律忽略该筛选条件而非 400（管理页是排障工具，粘贴即搜）；`userId` 以 `String` 承接并在规则类归一，避免类型转换失败穿透成 500。唯一硬校验是排序方向（`PageQuery.of` → `SortItem.single`），因为静默降级成默认排序会让前端误以为排序已生效。
4. **时间窗默认收敛 7 天**（`IdempotencyRecordRules.clampWindow`）：起止皆空时只查最近一周，防止无条件全表扫描；起晚于止时钳为同日（区间为空但不报错）。
5. **`expired` 计算字段**：查询时刻统一取一次 `now` 传给视图工厂，避免逐行调用系统时钟导致同页数据口径不一致；`summary` 的 `expiredPending`（已过期待清理）是该模块的核心水位——持续增长说明清理任务未运行。
6. **`processingShare` 口径**：PROCESSING 占比保留 4 位小数，`total <= 0` 时为 `0.0`（绝不把除零 NaN 写进 JSON）；`PROCESSING` 长期不为 0 即「僵尸处理中」信号。

## 6. 测试

| 测试类 | 例数 | 覆盖 |
| --- | --- | --- |
| `e2e/IdempotencyRecordE2eTest` | 6 | ID-01~06：分页默认/筛选透传/排序方向非法→400/摘要/清理/非法状态容错 |
| `common/idempotency/rules/IdempotencyRecordRulesTest` | 12 | 三态归一（大小写不敏感）/未知值忽略/scope 截断/userId 解析/keyword 包裹/时间窗三态/过期边界/截断 |
| `common/idempotency/service/IdempotencyRecordServiceTest` | 6 | 摘要组装：三态直出/占比舍入/零记录 0.0/全处理中 1.0/脏计数防御/无处理中透传 |

E2E 采用 standalone MockMvc + 真实控制器 / `GlobalExceptionHandler` / `I18nService`，服务层 Mockito 桩接；DB 交互路径（分页/计数/删除）按项目约定由 MySQL 集成测试（`*IT`）覆盖，沙箱离线以「契约桩接 + 纯函数单测」达成等价验证。详见 `docs/test/test-case-specification.md` §10 与 `docs/test/sequence-diagrams.html` SD-09。

## 7. 离线构建与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -o clean test          # 824 例全绿（本模块贡献 24 例）
python3 scripts/check-conventions.py   # EXIT=0（H1~H4 / W1~W2）
```
