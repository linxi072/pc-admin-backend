# N+1 查询探测与可观测现状说明

> 适用分支：`feat/V1.0.4`（方案 C·纯手写 DSL，已移除 jOOQ 代码生成）
> 关联能力：jOOQ 执行链路观测（`NPlusOne*` 四件套 + `SlowQueryListener`）
> 文档性质：运维/排查参考，非用户手册

---

## 1. 背景与目标

深翻页与循环内逐行查询是后台列表接口最常见的两类性能反模式：

- **深翻页（offset 分页）**：`LIMIT 100 OFFSET 100000` 随偏移量增大，MySQL 仍需扫描并丢弃前 N 行，响应时间线性退化；
- **N+1 查询**：一次请求内对同一「结构相同、仅参数不同」的 SQL 反复执行（典型如循环里按 id 逐条 `SELECT`），单次都不慢，叠加后撑爆连接池与 DB QPS。

本项目采用**两条互补的防线**：

| 反模式 | 防线 | 落地方式 |
| --- | --- | --- |
| 深翻页 | keyset 游标分页 | `JooqKeysetPager`（登录日志 / 审计日志 / 文件分页已试点） |
| N+1 查询 | 同构 SQL 计数探测 | 本文档 `NPlusOne*` 四件套（常驻、零成本、可复盘） |

> 深翻页与 N+1 是不同问题：本说明聚焦 N+1 探测，keyset 游标分页的推广见各 Service 的 `keysetPage` 端点与《方案 C 门禁复验与健康报告》。

---

## 2. 四件套架构

N+1 探测由四个职责单一的组件构成，全部**无 Spring 强依赖**（纯 POJO / `DefaultExecuteListener`），便于离线单测与跨模块复用。

```
                    ┌───────────────────────────── 一次 HTTP 请求（同一线程）────────────────────────────┐
                    │                                                                                    │
  jOOQ 执行链路     │   executeEnd(ctx.sql())                                                            │
        │          │        │                                                                            │
        ▼          ▼        ▼                                                                            │
  ┌─────────────────────────── NPlusOneListener（DefaultExecuteListener，只读观测）──────────────────┐    │
  │   probe.record(sql) ── 跨越阈值？── 否 ─┐                                                            │    │
  │        │ 是                          │                                                              │    │
  │        ▼                             ▼                                                              │    │
  │   log.warn(同构SQL达N次)        （不登记，避免刷屏）                                                │    │
  │        │                                                                                            │    │
  │        ▼ registry.record(fingerprint, sampleSql, count)                                            │    │
  └────────┼─────────────────────────────────────────────────────────────────────────────────────────┘    │
           ▼                                                                                             │
  ┌────────────────── NPlusOneProbe（ThreadLocal 同构计数）──────────────────┐                            │
  │ · normalize(sql): 折叠空白+小写+字面量/数字→? + 压缩 `=` 两侧空白           │                            │
  │ · record(sql) → 仅「恰好等于阈值」那次返回 true（避免反复刷屏）            │                            │
  │ · reset() 由 NPlusOneResetFilter 在请求结束时清理（防线程池串台）          │                            │
  └─────────────────────────────────────────────────────────────────────────┘                            │
           ▼                                                                                             │
  ┌────────────────── NPlusOneSuspectRegistry（有界环形缓冲留存）──────────────────┐                     │
  │ · 容量默认 200，超出淘汰最旧；内存占用恒定                                    │                     │
  │ · total() 自进程启动单调累计；clear() 仅清列表，不清累计口径                  │                     │
  │ · recent(limit) 倒序返回（最新在前）                                         │                     │
  └───────────────────────────────────────────────────────────────────────────────┘                     │
           │                                                                                             │
           ▼ REST（需 system:monitor:read）                                                              │
  ┌────────────────── NPlusOneService / NPlusOneController ──────────────────┐                         │
  │ GET  /suspects       最近疑似事件（最新在前）                              │                         │
  │ GET  /status         运行态（阈值/容量/当前条数/累计越限/最近越限时间）     │                         │
  │ DELETE /suspects     清空留存（累计计数不清零）                            │                         │
  └────────────────────────────────────────────────────────────────────────────┘                         │
```

### 2.1 同构指纹算法（`NPlusOneProbe.normalize`）

判定「两条 SQL 是否同构」是关键：

1. `trim()` + `toLowerCase(Locale.ROOT)` + 折叠连续空白为单空格；
2. 将字面量 `'...'` 与数字 `\b\d+(?:\.\d+)?\b` 替换为 `?` 占位符——于是 `where id = 1` 与 `where id = 2` 归为同一指纹；
3. 压缩 `=` 两侧空白（`id = ?` 与 `id=?` 同指纹）。

> 该归一化**不解析 SQL 语义**，仅做结构近似。极少数「结构相似但语义不同」的语句可能被误聚合，但作为 N+1 初筛足够——最终以登记的 `sampleSql` 样本人工复核。

### 2.2 阈值触发语义

`record(sql)` 仅在「该指纹计数**恰好等于**阈值」的那一次返回 `true` 并触发告警+登记。这意味着：

- 同一 N+1 在**单个请求内**只登记 1 条疑似事件（不刷屏）；
- 跨请求（不同线程）的同一指纹会各自计数、各自越限，留存中可见多条——这正是「该接口被反复触发」的信号。

---

## 3. 阈值与配置项

| 配置键 | 默认值 | 作用域 | 说明 |
| --- | --- | --- | --- |
| `app.jooq.nplus1-threshold` | `10` | `NPlusOneProbe` | 同一线程内同构 SQL 执行达此次数即判定 N+1，告警并登记疑似事件 |
| `app.jooq.nplus1-max-suspects` | `200` | `NPlusOneSuspectRegistry` | 疑似事件留存环形缓冲容量；超出淘汰最旧记录，内存占用恒定 |
| `app.jooq.slow-query-threshold-ms` | `500` | `SlowQueryListener` | 慢查询阈值（独立观测，非 N+1 判定口径） |

装配位置：`scaffold-framework` 的 `config.jooq.JooqConfig`（`nPlusOneProbe` / `nPlusOneSuspectRegistry` / `dslContext` 注入 `NPlusOneListener`），通过 `@Value("${...:默认值}")` 读取，**无开关语义——探测常驻**。

> **为何不提供开关**：探测本身零成本（仅同构 SQL 计数），留存容量有界（环形缓冲），与 `SlowQueryListener` 同为只读观测，不影响主链路；关闭反而会丢失排查线索。

---

## 4. REST 接口（运维侧）

统一前缀 `/api/system/monitor/n-plus-one`，均需权限 `system:monitor:read`（敏感运维数据）。

| 方法 | 路径 | 说明 | 返回 |
| --- | --- | --- | --- |
| GET | `/suspects?limit=50` | 最近疑似事件（最新在前） | `List<NPlusOneSuspectView>`（fingerprint / sampleSql / count / threadName / timestampMillis） |
| GET | `/status` | 运行态快照 | `NPlusOneStatusView`（threshold / capacity / size / total / lastBreachAt） |
| DELETE | `/suspects` | 清空留存列表（**累计计数不清零**） | `true` |

`limit` 越界（<1 或 >200）返回 `VALIDATION_ERROR` → 400。

---

## 5. 运行态现状与边界

| 维度 | 现状 |
| --- | --- |
| 是否默认开启 | **是**，随应用启动常驻，无需额外配置 |
| 性能开销 | 极低：每次 jOOQ 执行一次同构指纹计算 + 一次 `ConcurrentHashMap.merge`；`total()` 用 `LongAdder` 累加 |
| 内存占用 | 有界：`NPlusOneSuspectRegistry` 环形缓冲固定容量（默认 200，单条样本 SQL 截断至 500 字符） |
| 数据生命周期 | `Probe` 计数随**请求结束**由 `NPlusOneResetFilter` 清理；`Registry` 留存跨请求、进程级，仅 `clear()` 显式清空 |
| 累计口径 | `registry.total()` 自进程启动**单调递增**；`clear()` 仅清留存列表，不重置累计与最近越限时间——便于评估告警频率趋势 |
| 线程安全 | `Probe` 状态存 ThreadLocal（请求级隔离）；`Registry` 全读写经同一监视器锁串行化（事件生产速率低，竞争可忽略） |
| 旁路健壮性 | 登记失败/容量为 0/指纹为空均静默忽略，绝不向 jOOQ 执行链路抛异常 |
| 与慢查询关系 | `SlowQueryListener` 单独观测单条 SQL 耗时（默认 >500ms），与 N+1（同构 SQL 数量）正交——二者互补：慢查询看「单条重」，N+1 看「数量多」 |

---

## 6. 排查 N+1 的标准工作流

1. **看趋势**：`GET /status` 关注 `total`（累计越限次数）是否持续上涨，`lastBreachAt` 是否频繁——上涨即存在稳定复现的 N+1。
2. **看样本**：`GET /suspects` 取最新 `fingerprint` 与 `sampleSql`，定位「哪条 SQL 在循环」。
3. **定位代码**：用 `sampleSql` 反查业务方法（多为循环内逐条 `JooqWriters.fetchById` / `fetchOne`，或 `@OneToMany` 式逐行查询），改用批量查询或 `JooqWriters.fetchList(..., in(ids))`。
4. **验证修复**：修复后压测同一接口，`/status` 的 `total` 停止新增、`/suspects` 不再出现该 `fingerprint` 即为闭环。
5. **清理留存**：排查完毕 `DELETE /suspects` 清空列表（累计计数保留供趋势评估）。

---

## 7. 已知边界与后续方向

- **归一化近似**：不解析 SQL 语义，极少数「结构相似语义不同」的语句可能误聚合为同一指纹；以登记的 `sampleSql` 样本人工复核为准。
- **阈值调参**：默认 10 偏宽松，适合先发现「明显」N+1；若需捕捉「温和」N+1，可下调 `app.jooq.nplus1-threshold`（最低 2，构造器强制 `Math.max(2, threshold)`）。
- **未接入指标系统**：当前疑似事件仅留存于进程内存、经 REST 暴露，尚未对接 Prometheus / 告警平台；多实例部署时各实例数据独立，需逐实例排查。后续可考虑将 `registry.total()` 暴露为 Micrometer 指标。
- **与 keyset 游标分页的关系**：二者解决不同问题（N+1 vs 深翻页），互不替代；本项目同时具备两条防线。
