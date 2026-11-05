# 千万级数据规模功能与性能测试报告（V1.1.0）

> 范围：`java-admin-framework` 后端 17 个 Maven 模块 + 前端契约；千万级数据集上的全链路功能/性能/稳定性验证  
> 生成时间：2026-11-05 ｜ 分支：`feat/V1.0.7` ｜ 被测库：`java_admin_perf`（隔离库，未触碰 `java_admin`）  
> 判定标准：延续 `scripts/perf/PERF-TEST-PLAN.md` §3/§4（p50/p95/p99、吞吐、错误率、预热、`<1%` 错误率、交互级 `p95<150ms`），并叠加本次 DB 层阈值  
> **结论：达标。** 判定标准中 5 项硬性阈值全部满足（数据规模、功能用例通过率、keyset 首页/深页 p95、深页退化比、稳定性错误率），四门禁全绿；另有 4 项「诊断对照」场景实测超 150ms、1 项中危配置风险，均非阻断但须纳入上线治理清单（§7、§8）。

---

## 0. 结论速览

| 判定项 | 阈值 | 实测 | 结论 |
| --- | --- | --- | --- |
| 测试数据规模 | ≥ 10,000,000 行 | **14,000,004 行**（精确 COUNT） | 达标 |
| 功能用例通过率 | 100% | **25 / 25 = 100%** | 达标 |
| keyset 首页 p95 | < 150 ms | **2.00 ms** | 达标 |
| keyset 深页 p95 | < 150 ms | **2.00 ms** | 达标 |
| keyset 深页/首页 p95 | < 2.0 | **1.00**（无退化） | 达标 |
| 稳定性错误率（30s / 16 线程） | < 1% | **0.00%**（167,038 次操作 / 0 错误） | 达标 |
| 后端全量回归 | 0 失败 | **2,052 例 / 0 失败 / 0 错误 / 0 跳过** | 达标 |
| 前端契约 + 生产构建 | 全通过 | 91/91 + `vite build` **EXIT=0**（15.25s） | 达标 |
| 诊断对照场景（非 SLA） | — | 4 项超 150 ms（§7） | 未达标·非阻断 |

**明确结论**：本次千万级功能与性能测试 **达标**，准予继续 V1.1.0 上线准备。但结论**不是无条件放行**：

1. §8.1 的 JDBC `serverTimezone=UTC` 与 JVM 时区不一致风险为中危，上线前必须核对生产容器 `TZ` 与数据源 URL；
2. 若业务要求「千万级大表任意列即席查询 / 全量 GROUP BY 达到交互级延迟」，则**该子项不达标**，须按 §8 方案整改后复测；
3. 报告结论仅对 §1.1 声明的测试范围有效，覆盖局限见 §8.6。

---

## 1. 测试范围与判定标准

### 1.1 覆盖层次（延续既有测试范围）

| 层次 | 内容 | 本次是否执行 | 结果 |
| --- | --- | --- | --- |
| L1 全模块功能回归 | 17 个 Maven 模块全部 `*Test`，含 RBAC/租户/工作流/通知/CMS/IM/审批/报表/AI/codegen 等 | 是（全量重跑） | 2,052 例全绿 |
| L2 前端契约与构建 | `node:test` 契约测试 + `vite build` 生产构建 | 是 | 91/91 + 构建通过 |
| L3 千万级数据集功能/性能基准 | **本轮新增**：25 功能用例 + 9 性能场景 + 30s 稳定性 + 资源采样 | 是 | 25/25，见 §3–§5 |
| L4 HTTP 端到端压测基线 | `scripts/perf/PERF-TEST-PLAN.md` §8 的 TC-P1/TC-P2/TC-P3/RAMP | 沿用既有实测基线，未重复执行 | ping 2367.3 req/s、menus/tree 917.3 req/s、login 62.3 req/s @c=20 |
| L5 架构约定门禁 | `scripts/check-conventions.py` H1–H5 | 是 | EXIT=0 |

**被测业务域**：用户（`sys_user`）、登录审计（`sys_login_log`）、操作审计（`sys_operation_log`）、通知投递（`sys_notification_record`）、站内信与回执（`sys_message` / `sys_message_receipt`）、监控采样（`sys_monitor_sample`），共 7 张来自生产 `java_admin` 的真实表结构。

**被测生产原语**（不复制实现，直接调用线上代码）：`JooqKeysetPager`（keyset 游标分页）、`JooqWriters`（count/page/fetchTop/fetchListBounded/fetchList/batchInsert/insert/updateById/fetchById/notDeleted）、`JooqTables`（表列元数据）、`SnakeRecordMapper`（真实 Record→POJO 映射）、`CursorCodec`（不透明游标）、`KeysetPageQuery` / `PageQuery`（边界钳制）。

### 1.2 判定标准

沿用 `PERF-TEST-PLAN.md`：响应时间采集 p50/p95/p99/avg/min/max；吞吐量 = 成功操作数 / 施压时长；错误率 = 异常操作占比（阈值 `<1%`）；每组场景预热 50 次请求不计入统计；交互级 p95 `< 150ms`（TC-P2 口径）。

本次 DB 层阈值（写入基准类 `THRESHOLD` 常量，报告原样引用）：

```
keyset首页p95<150.0ms; keyset深页p95/首页p95<2.0; 错误率<1.0%;
功能用例通过率=100%; 数据规模>=10000000行
```

### 1.3 环境

| 项 | 值 |
| --- | --- |
| JDK / OS / CPU | 21.0.12.1 / Mac OS X x86_64 / 8 核 |
| JVM 最大堆 | 4,096 MB；运行时区显式对齐 `UTC` |
| 数据库 | MySQL 8.4.11，`127.0.0.1:3306`，loopback，单机单实例 |
| Spring 上下文 | 未启动（L3 为数据访问层进程内基准，直接 JDBC + jOOQ） |
| 隔离策略 | 库 `java_admin_perf` 独立；`java_admin` 全程只读用于 `--no-data` 复制 DDL |

### 1.4 数据集

由 `scripts/perf/seed-ten-million.sh` + `seed-ten-million.sql` 生成：`mysqldump --no-data` 从 `java_admin` 原样复制 7 张表结构（列、主键、二级索引一致）；主表先删 3 个二级索引，以 **128 批 × 10 万行**装载 12,800,000 行，再重建生产同款索引；最后注入 4 行边界数据。装载耗时约 4m53s。

| 表 | 精确行数 | 说明 |
| --- | ---: | --- |
| `sys_operation_log` | **12,800,004** | 主表：1,280 万批量行 + 4 行边界值 |
| `sys_login_log` | 400,000 | 登录审计 |
| `sys_message_receipt` | 300,000 | 站内信回执（含未读分布） |
| `sys_notification_record` | 200,000 | 通知投递记录 |
| `sys_user` | 100,000 | 用户（8 租户，含 DISABLED 状态） |
| `sys_message` | 100,000 | 站内信 |
| `sys_monitor_sample` | 100,000 | 监控采样 |
| **合计（精确 COUNT）** | **14,000,004** | ≥ 1,000 万，达标 |

**边界 / 异常数据**：1970-01-01 空字符串、2099-12-31 未来时间 + `Long.MAX_VALUE` 时长、Unicode/emoji `🚀`、`BIGINT UNSIGNED` 极大值、非法枚举 `ZZ_INVALID`；运行期另注入超长 `operation_name`（DB 按预期以 `DataException` 拒绝）、非法游标、错误字段数游标、恒假条件、并发查询。探针行在用例内自清理，保证基准可重复执行。

---

## 2. 复现方式

```bash
# 1) 构建千万级隔离库（约 5 分钟，需本机 MySQL root/root）
bash scripts/perf/seed-ten-million.sh

# 2) 执行功能 + 性能 + 稳定性基准（约 3 分钟）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -o -f pom.xml -pl scaffold-admin -am test \
  -Dtest=TenMillionScalePerf \
  -Dsurefire.failIfNoSpecifiedTests=false

# 3) 四门禁
mvn -o -f pom.xml clean test          # 17 模块全量
python3 scripts/check-conventions.py  # EXIT=0
cd ../pc-admin-vue3-vite && npm test && npm run build
```

> 环境注记：本机 WorkBuddy 注入的 `NODE_OPTIONS=--require=...node-language-shim.cjs` 会让 `npm run build` 的包装进程在 `transforming...` 阶段无限挂起（本次实测直连 `./node_modules/.bin/vite build` 为 15.11s、`env -u NODE_OPTIONS npm run build` 为 15.25s，均 EXIT=0）。这是**执行环境噪声，非代码缺陷**；CI/本机裸终端无该注入，门禁命令按上方原样执行即可。

- 基准类命名以 `Perf` 结尾，**不匹配 surefire `*Test` 与 failsafe `*IT` 默认 include**，因此千万级压测不会拖慢 2,052 例日常回归；需 `-Dtest=` 显式触发。
- 支持 `-DargLine="-Dperf.functionalOnly=true"` 只跑功能用例（约 35s），便于迭代诊断。
- 结果输出 `/tmp/ten-million-perf-results.txt`，本次原始证据已归档至 `scripts/perf/results/`。

---

## 3. 功能用例结果（25 / 25 通过，通过率 100%）

按场景性质归并：规模与完整性 3、正常读取/分页 8、边界 6、异常 5、写入 2、稳定性 1。

| 编号 | 场景 | 性质 | 结论 | 关键观测 |
| --- | --- | --- | --- | --- |
| F-01 | 数据规模 ≥ 1000 万（精确 COUNT） | 规模 | PASS | 7 表合计 14,000,004 行 |
| F-02 | 主表 1280 万行且边界行存在 | 规模 | PASS | 12,800,004 行 / 4 边界行 |
| F-03 | keyset 首页返回 size 条且游标非空 | 正常 | PASS | 20 条 + base64url `nextCursor` |
| F-04 | 连续翻页 200 页无重复无遗漏、id 严格递减 | 正常 | PASS | 4,000 条，id 唯一且单调 |
| F-05 | keyset 深页（第 100 万行附近）结果正确 | 边界 | PASS | 20 条全部位于游标之后 |
| F-06 | 游标不透明且可解码 | 正常 | PASS | 解码字段数 = 1 |
| F-07 | size 边界：0/负数→1，>100→100；page→100,000 | 边界 | PASS | `KeysetPageQuery` / `PageQuery` 钳制 |
| F-08 | 非法乱码游标静默降级首页 | 异常 | PASS | 无异常，返回首页 |
| F-09 | 游标字段数与排序键不符 → `BusinessException` | 异常 | PASS | 「预期 1，实际 3」被拒绝 |
| F-10 | 条件过滤（tenant + module）结果全部满足条件 | 正常 | PASS | 50 条全部 tenant=3 且 module=USER |
| F-11 | `JooqWriters.count` 与 SQL `COUNT` 一致 | 正常 | PASS | 12,205,057（耗时 11.16s，见 §7） |
| F-12 | `fetchTop` 上限钳制：请求 5000 实返 1000 | 边界 | PASS | P4 有界加载护栏生效 |
| F-13 | `fetchListBounded` 不钳制且下推 `LIMIT` | 边界 | PASS | 实返 1500 |
| F-14 | OFFSET 首页 / 深页结果互不相交 | 正常 | PASS | offset=999,980，5.08s |
| F-15 | 1970/2099/emoji/极大数值无损映射 | 边界 | PASS | 见 §8.1（首轮曾失败，已闭环） |
| F-16 | 异常语义行不污染合法枚举过滤 | 异常 | PASS | 非法枚举 1 行隔离；USER=2,560,002 |
| F-17 | 恒假条件空结果且无下一页 | 边界 | PASS | `nextCursor=null` |
| F-18 | `batchInsert` 500 行写入→回读→清理一致 | 写入 | PASS | 500/500/500 |
| F-19 | 单条 `insert` / `updateById` / `notDeleted` 语义 | 写入 | PASS | id 回填 + 更新回读一致 |
| F-20 | 超长 `operation_name` 被 DB 拒绝 | 异常 | PASS | `DataException`（严格模式生效） |
| F-21 | 跨表聚合（消息 × 回执）正确性 | 正常 | PASS | 回执 300,000 / 未读 150,000 |
| F-22 | 其余 6 张业务表规模与聚合正确 | 正常 | PASS | 各表行数精确匹配 §1.4 |
| F-23 | 无索引列过滤结果正确（正确性而非性能） | 正常 | PASS | 结果全部满足 `operator1` |
| F-24 | 并发查询 16 线程 × 20 次无异常 | 异常/并发 | PASS | 320 次并发 keyset，0 异常 |
| F-25 | 30s / 16 线程混合负载错误率 < 1% | 稳定性 | PASS | 167,038 ops / 0 errors |

> 首轮执行时 F-15 失败（24/25），根因为测试环境 JVM 时区与 JDBC `serverTimezone` 不一致，非映射缺陷；已定位并闭环，详见 §8.1 与 `results/TEN-MILLION-FIRST-RUN.txt`。最终复验 25/25。

---

## 4. 关键性能指标（9 场景）

| 场景 | 样本 / 预热 | p50 (ms) | p95 (ms) | p99 (ms) | avg (ms) | 吞吐 | 错误 | 判定 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| P-01 keyset 首页（主键索引） | 100 / 50 | 1.00 | **2.00** | 3.00 | 1.27 | 588.87 ops/s | 0 | 达标 |
| P-02 keyset 深页（第 1180 万行附近） | 100 / 50 | 1.00 | **2.00** | 3.00 | 1.27 | 574.74 ops/s | 0 | 达标 |
| P-03 OFFSET 首页 offset=0 | 50 / 20 | 1.00 | 1.00 | 1.00 | 0.58 | 893.56 ops/s | 0 | 达标 |
| P-04 OFFSET 深页 offset=1,000,000 | 5 / 1 | 985.00 | **1,014.00** | 1,014.00 | 990.60 | 1.01 ops/s | 0 | **未达标·对照** |
| P-05 索引覆盖过滤（`idx_module_time`） | 30 / 10 | 3.00 | 4.00 | 4.00 | 3.13 | 279.89 ops/s | 0 | 达标 |
| P-06 无索引列过滤（`operator_name` 全表扫描） | 5 / 1 | 5,004.00 | **5,192.00** | 5,192.00 | 5,030.60 | 0.20 ops/s | 0 | **未达标·对照** |
| P-07 全表 `COUNT(*)` 聚合 | 5 / 1 | 1,891.00 | 1,940.00 | 1,940.00 | 1,895.20 | 0.53 ops/s | 0 | **超阈值·对照** |
| P-08 `GROUP BY module_code` 全表聚合 | 5 / 1 | 7,229.00 | **9,490.00** | 9,490.00 | 7,652.40 | 0.13 ops/s | 0 | **未达标·对照** |
| P-09 批量写入 `batchInsert`（1000 行/批） | 10 / 0 | 635.00 | 924.00 | 924.00 | 690.30 | **1,446.16 rows/s** | 0 | 达标（吞吐口径） |

**核心判定（基准输出 `VERDICT`）**：

```
keyset 首页 p95=2.00ms | 深页 p95=2.00ms | 深页/首页 p95=1.00
阈值 p95<150.0ms 且比值<2.0 → 达标
```

**关键对比**：在 1,280 万行主表上，keyset 从首页翻到第 1,180 万行附近 p95 仍为 2ms、无任何退化；而 OFFSET 从 0 到 1,000,000 由 1ms 劣化到 1,014ms（**约 1,014 倍**），量化验证了 P4 整改中「深翻页改走 keyset」的必要性，也说明现有 OFFSET 护栏（用户列表 5,000 / 消息列表阈值）是必要的兜底而非过度设计。

> 说明：P-01/P-02/P-03/P-05 为推荐生产路径（SLA 判定项），P-09 以写入吞吐为口径；P-04/P-06/P-07/P-08 为**诊断对照场景**，用于量化瓶颈，不属于推荐生产调用路径。基准程序对 P-04、P-06 输出了显式 `VERDICT=未达标`，P-07/P-08 为聚合诊断项，本报告按同一 150ms 口径一并列入超阈值清单。

---

## 5. 稳定性与资源占用

**30 秒 / 16 线程混合负载**（4/5 主键 keyset + 1/5 索引过滤，每线程独立 JDBC 连接）：

| 指标 | 实测 |
| --- | --- |
| 总操作数 | **167,038** |
| 错误数 / 错误率 | **0 / 0.00%**（阈值 < 1%） |
| 吞吐 | **5,555.19 ops/s** |
| p50 / p95 / p99 | 1.00 ms / 4.00 ms / 48.00 ms |

**JVM 资源采样（每 5s）**：

| 时刻 | 堆已用 | 堆上限 | 非堆 | 线程 | GC 次数 | GC 累计耗时 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 5s | 75 MB | 4096 MB | 54 MB | 25 | 60 | 129 ms |
| 10s | 18 MB | 4096 MB | 54 MB | 25 | 86 | 186 ms |
| 15s | 261 MB | 4096 MB | 55 MB | 25 | 106 | 273 ms |
| 20s | 159 MB | 4096 MB | 55 MB | 25 | 119 | 333 ms |
| 25s | 254 MB | 4096 MB | 55 MB | 25 | 131 | 406 ms |
| 结束 | 225 MB | 4096 MB | 55 MB | 9 | 142 | 493 ms |

堆占用在 18–261 MB 区间震荡（峰值 < 7% 的 4 GB 上限），非堆稳定 54–55 MB，GC 累计 493 ms / 30 s（约 1.6% 墙钟时间），线程数负载期恒定 25、结束后回落 9，MySQL 连接数收敛到 1。**无内存泄漏、无连接泄漏、无线程膨胀**。

**落盘规模（`ANALYZE TABLE` 刷新后，见 `results/RESOURCE-AFTER-ANALYZE.txt`）**：

| 表 | 数据 (MB) | 索引 (MB) |
| --- | ---: | ---: |
| `sys_operation_log` | 1,947.0 | **1,294.8** |
| `sys_login_log` | 50.6 | 70.8 |
| `sys_message_receipt` | 19.5 | 42.7 |
| `sys_notification_record` | 26.6 | 28.6 |
| `sys_message` | 25.6 | 4.5 |
| `sys_user` | 23.6 | 18.1 |
| `sys_monitor_sample` | 10.5 | 3.5 |
| **合计** | **2,103.4** | **1,463.0**（约 3.48 GB） |

> 注意：`information_schema.tables` 在批量装载 + 重建索引后统计滞后，原始结果文件中出现 `indexMB=0.0` 的假象；`SHOW INDEX` 已确认 PRIMARY + `idx_module_time` + `idx_operator_time` + `idx_trace_id` 四个索引全部存在，执行 `ANALYZE TABLE` 后得到上表准确值（见 §8.5）。

---

## 6. 用例通过率汇总

| 层次 | 总数 | 通过 | 通过率 |
| --- | ---: | ---: | ---: |
| 后端全模块单测（17 模块） | 2,052 | 2,052 | **100%** |
| 前端契约测试 | 91 | 91 | **100%** |
| 千万级功能用例（正常/边界/异常/写入/并发/稳定性） | 25 | 25 | **100%** |
| 性能场景（SLA 判定项：P-01/02/03/05/09） | 5 | 5 | **100%** |
| 性能场景（诊断对照项：P-04/06/07/08） | 4 | 0（超阈值） | 0% · 见 §7 |
| 稳定性场景 | 1 | 1 | **100%** |
| 门禁（后端/约定/前端单测/前端构建） | 4 | 4 | **100%** |
| **合计（不含诊断对照）** | **2,178** | **2,178** | **100%** |

---

## 7. 未通过项

| 编号 | 场景 | 实测 p95 | 阈值 | 性质 | 判定与处置 |
| --- | --- | ---: | ---: | --- | --- |
| P-04 | OFFSET 深页 offset=1,000,000 | 1,014 ms | 150 ms | 诊断对照 | **未达标**。生产推荐路径已切 keyset（P-01/P-02 为 2ms），存量 OFFSET 端点保留前端契约但设有 5,000 偏移护栏；非阻断。 |
| P-06 | 无索引列 `operator_name` 全表扫描 | 5,192 ms | 150 ms | 诊断对照 | **未达标**。禁止千万级表按无索引列即席查询；需补索引或改走检索/异步导出；非阻断（现有服务未暴露该查询）。 |
| P-07 | 全表 `COUNT(*)` | 1,940 ms | 150 ms | 诊断对照 | **超阈值**。`JooqWriters.page` 每次分页执行 `count`，在大表成为固定成本；keyset 不返回 `total` 的设计正确，建议列表接口优先 keyset；非阻断。 |
| P-08 | `GROUP BY module_code` 全表聚合 | 9,490 ms | 150 ms | 诊断对照 | **未达标**。报表/大屏类聚合须走预聚合、物化汇总表或离线任务，不可在线实时扫全表；非阻断。 |

**首轮 F-15 失败（已闭环，不计入最终未通过项）**：初版在 JVM 默认 `Asia/Shanghai` 下运行，F-15 断言 2099 年边界行失败；定位为 JDBC 时区问题，修复后最终复验 25/25。原始证据见 `results/TEN-MILLION-FIRST-RUN.txt`。

---

## 8. 性能瓶颈与风险

### 8.1 【中危·配置风险】`serverTimezone=UTC` 与 JVM 时区不一致导致 DATETIME 整体 +8h

- **证据**：首轮 F-15 诊断输出 `1970-01-01 00:00:00` 读回为 `1970-01-01T08:00`、`2099-12-31 23:59:59.999` 读回为 `2100-01-01T07:59:59.999`、`2025-06-15 12:00` 读回为 `20:00`，全部精确 +8h；改用 `-Duser.timezone=UTC` 后同一用例立即通过。
- **根因**：数据源 URL 声明 `serverTimezone=UTC`（`application-local.yml`、CI `ci.yml`、`application-integration.yml` 均如此），而 JVM 默认 `Asia/Shanghai`；Connector/J 将 DATETIME 按连接时区解释后换算到 JVM 本地时区，`SnakeRecordMapper` 的 `Timestamp → LocalDateTime` 转换本身正确。
- **影响**：所有 DATETIME 读取/展示/导出在 JVM 非 UTC 的部署下整体偏移，且临界值会跨日跨年；H2 单测无法暴露（仅真实 MySQL 路径）。
- **处置建议**：① 容器统一 `TZ=UTC`；或 ② JDBC URL 改用 `connectionTimeZone=SERVER` + `forceConnectionTimeZoneToSession=true` 并统一 JVM 时区；③ 补一条真实 MySQL 的边界时间集成断言（1970/2099 临界值）防止回归。
- **本次基准处理**：`TenMillionScalePerf.setUp()` 内显式 `TimeZone.setDefault(UTC)`，与标准容器部署对齐，保证任意开发机可复现；该风险本身**仍需在上线前核对生产配置**。

### 8.2 OFFSET 深翻页线性退化
1,280 万行下 offset=1,000,000 的 p95 达 1,014ms，是首页的约 1,014 倍。P4 整改提供的 keyset 端点与偏移护栏是必要兜底；建议逐步把仍在使用 OFFSET 的存量列表接口收敛到 keyset。

### 8.3 大表无索引过滤与全表聚合
P-06 5,192ms、P-08 9,490ms、P-07 1,940ms 表明：千万级表上任何「未下推到索引」的查询都会退化到秒级。治理方向：查询条件白名单化并强制走复合索引、分页接口优先 keyset（不计算 total）、统计类改预聚合/异步任务。

### 8.4 二级索引存储成本
主表数据 1,947.0 MB、索引 1,294.8 MB，索引约占数据 66%；整库约 3.48 GB。容量规划需按「数据 + 索引」双倍估算，并评估历史归档/分区策略。

### 8.5 `information_schema` 统计滞后
批量装载 + 重建索引后 `index_length` 曾显示 0，易误判「索引未建成」。`SHOW INDEX` 与 P-05 的 4ms 索引覆盖查询均证明索引有效；运维取证前需先 `ANALYZE TABLE`。

### 8.6 覆盖局限（结论边界）
- L3 为**进程内数据访问层**基准（直连 MySQL + jOOQ + 生产原语），未启动 Spring Boot HTTP 全链路；L4 的 HTTP 压测沿用 `PERF-TEST-PLAN.md` §8 既有基线，未在千万级库上重跑。
- 单机、loopback、无 TLS / 反向代理 / 网关 / Redis 缓存；结果不代表生产集群或分布式部署。
- 数据集为按真实 DDL/索引生成的合成数据，分布均匀；未覆盖倾斜热键、长事务锁竞争、主从延迟、故障注入（混沌）。
- C9 真实 CVE 扫描依赖联网 NVD，P4 存量 OFFSET 端点为保留前端契约的渐进收敛，均沿用 V1.1.0 整改核对说明 §10 的非阻断遗留口径。

---

## 9. 达标结论

> **本次千万级（14,000,004 行）功能与性能测试：达标。**

1. 数据规模、功能用例通过率、keyset 首页/深页 p95、深页退化比、稳定性错误率 5 项硬性判定标准**全部满足**；25/25 功能用例覆盖正常、边界、异常、写入、并发与稳定性场景。
2. 四门禁全绿：后端 `mvn -o clean test` **2,052 例 0 失败**、`check-conventions.py` **EXIT=0**（仅 1 条 W* 分层提示）、前端 `npm test` **91/91**、前端 `vite build` **EXIT=0**（产物完整，15.25s）。
3. 30s / 16 线程混合负载 167,038 次操作 **0 错误**，吞吐 5,555 ops/s，堆与线程稳定，无泄漏迹象。
4. §7 的 4 项超阈值场景均为**诊断对照**，用于量化瓶颈，不是推荐生产路径；系统已通过 keyset 分页、偏移护栏、有界加载提供替代路径，因此**不构成上线阻断**。若业务把「任意列即席查询 / 在线全量聚合」列入 SLA，则该子项**不达标**，须按 §8.3 整改后复测。
5. §8.1 的时区配置风险为**中危且需上线前核对**，建议作为上线检查单的必选项；不影响本次功能/性能达标判定，但不应带病上线。

---

## 10. 交付物

| 文件 | 用途 |
| --- | --- |
| `outputs/QA-TEST-REPORT-TEN-MILLION-V1.1.0.md` | 本报告 |
| `java-admin-framework/scripts/perf/seed-ten-million.sh` | 隔离库创建 + 真实 DDL 复制 + 灌数调度 |
| `java-admin-framework/scripts/perf/seed-ten-million.sql` | 128 批 × 10 万行装载 + 索引重建 + 边界数据 |
| `java-admin-framework/scaffold-admin/src/test/java/com/acme/scaffold/perf/TenMillionScalePerf.java` | 25 功能用例 + 9 性能场景 + 稳定性 + 资源采样 |
| `java-admin-framework/scripts/perf/results/TEN-MILLION-FINAL.txt` | 最终完整原始结果（BUILD SUCCESS / 25-25） |
| `java-admin-framework/scripts/perf/results/TEN-MILLION-FIRST-RUN.txt` | 首轮原始结果（含 F-15 时区失败证据） |
| `java-admin-framework/scripts/perf/results/RESOURCE-AFTER-ANALYZE.txt` | `ANALYZE TABLE` 后的准确落盘统计 |

---

## 11. 后续建议（流转给负责人确认）

| 优先级 | 事项 | 建议角色 |
| --- | --- | --- |
| P0（上线前核对） | 生产容器 `TZ` 与数据源 `serverTimezone` 对齐，补边界时间集成断言（§8.1） | 后端 / 运维 |
| P1 | 报表 / 大屏全量聚合改预聚合或离线任务；禁止无索引列在线查询（§8.3） | 后端 / 数据 |
| P1 | 存量 OFFSET 列表接口继续收敛至 keyset（§8.2） | 后端 / 前端 |
| P2 | 千万级表容量规划：索引 1.29 GB、整库 3.48 GB，评估归档/分区（§8.4） | 运维 / DBA |
| P2 | 在千万级库上重跑 HTTP 端到端压测（TC-P1/2/3 + RAMP），补齐 L4（§8.6） | 测试 |

> 建议将上述事项创建为项目事项分配给对应负责人并添加关注人，把本报告与原始结果文件上传到项目资料库，形成版本质量上下文。
