# 千万级数据规模功能与性能测试报告（V1.1.0）

> 范围：`java-admin-framework` 后端 17 个 Maven 模块 + 前端契约；千万级数据集上的 **L3 数据访问层 + L4 Spring Boot HTTP 全链路** 功能/性能/稳定性验证  
> 生成时间：2026-11-05 ｜ 分支：`feat/V1.0.7` ｜ 被测库：`java_admin_perf`（L3，7 表 14,000,004 行）与 `java_admin_perf_l4`（L4，110 表 + 12,800,004 行主表），均为隔离库，未触碰 `java_admin`  
> 判定标准：延续 `scripts/perf/PERF-TEST-PLAN.md` §3/§4（p50/p95/p99、吞吐、错误率、预热、`<1%` 错误率、交互级 `p95<150ms`），并叠加本次 DB 层阈值  
> **结论：有条件达标。** L3 的 5 项硬性阈值全部满足，四门禁全绿；L4 千万级 HTTP 全链路功能正确性达标（5 场景 0.00% 错误率），但 **4 个遗留大表端点 p95 达 3.0~21.5s，按交互级 `<150ms` 口径不达标**，须按 §8.7 治理后方可在千万级数据下开放；1 项中危时区配置风险已闭环（§8.1）；L4 首次拉起完整 Spring 上下文还暴露并修复了 1 项 P7 装配缺陷（§8.8）。

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
| L4 HTTP 全链路功能正确性 | 5/5 场景完成、错误率 < 1% | **5/5、错误率 0.00%**（200+8+3+8+3 请求全 2xx） | 达标 |
| L4 遗留大表端点交互级延迟 | p95 < 150 ms | **p95 3,024 ~ 21,505 ms**（§4.1 / §8.7） | **不达标·须治理** |
| L3 诊断对照场景（非 SLA） | — | 4 项超 150 ms（§7） | 未达标·非阻断 |

> 注：上表四门禁为千万级 L3 基准执行时点实测（2,052 例）；§8.1 时区闭环新增 10 例后复验为 **2,062 例 / 0 失败**；§8.8 的 P7 装配契约再新增 5 例，最终四门禁为 **2,067 例 / 0 失败**（详见 §9）。

**明确结论**：本次千万级功能与性能测试为 **有条件达标**——功能正确性、稳定性与 L3 推荐路径（keyset）性能达标；**L4 暴露的 4 个遗留大表 HTTP 端点性能不达标**，构成上线前必须处置的阻断项。结论的适用边界如下：

1. §8.1 的 JDBC `serverTimezone=UTC` 与 JVM 时区不一致风险原为中危，**已闭环**：容器/CI 统一 UTC、生产启动 fail-fast 校验、10 例契约回归断言（见 §8.1）；不使用本仓库镜像/compose 的自管部署仍需按同一口径核对。
2. §8.8 的 P7 熔断器 Bean 装配歧义是 L4 首次拉起完整 Spring 上下文才暴露的**真实启动缺陷**，已用逐点 `@Qualifier` 修复并补 5 例纯 JVM 装配契约回归；修复后 L4 上下文正常启动、压测全绿。
3. **上线前条件（§8.7）**：千万级数据下不得对 `error-logs`（含深页）、`error-summary`、`dashboard/overview` 提供在线同步全量查询；须改走 keyset 分页 / 预聚合 / 异步报表，或对这些端点加超时、并发隔离与运营时段限制，并在治理后按 §4.1 复测。未治理时，这些端点在 1280 万行主表上 p95 为 3~21.5s、吞吐 0.07~1.34 req/s，会拖垮 Tomcat 线程池。
4. 若业务要求「千万级大表任意列即席查询 / 全量 GROUP BY 达到交互级延迟」，则**该子项不达标**，须按 §8.3 方案整改后复测。
5. 报告结论仅对 §1.1 声明的测试范围有效，剩余覆盖局限见 §8.6。

---

## 1. 测试范围与判定标准

### 1.1 覆盖层次（延续既有测试范围）

| 层次 | 内容 | 本次是否执行 | 结果 |
| --- | --- | --- | --- |
| L1 全模块功能回归 | 17 个 Maven 模块全部 `*Test`，含 RBAC/租户/工作流/通知/CMS/IM/审批/报表/AI/codegen 等 | 是（全量重跑） | 2,052 例全绿 |
| L2 前端契约与构建 | `node:test` 契约测试 + `vite build` 生产构建 | 是 | 91/91 + 构建通过 |
| L3 千万级数据集功能/性能基准 | **本轮新增**：25 功能用例 + 9 性能场景 + 30s 稳定性 + 资源采样 | 是 | 25/25，见 §3–§5 |
| L4 HTTP 端到端压测（既有小数据基线） | `scripts/perf/PERF-TEST-PLAN.md` §8 的 TC-P1/TC-P2/TC-P3/RAMP | 沿用既有实测基线，未重复执行 | ping 2367.3 req/s、menus/tree 917.3 req/s、login 62.3 req/s @c=20 |
| **L4 HTTP 端到端压测（千万级库·本轮补齐）** | 真实 Spring Boot 上下文 + 随机端口 Tomcat + `TestRestTemplate` 经 socket 打 1280 万行主表；5 场景覆盖框架基线、`COUNT+OFFSET`、深页、全表 `COUNT`、全表 `GROUP BY` | **是（本轮新增实跑，§4.1）** | **5/5 完成、错误率 0.00%**；大表端点 p95 3,024~21,505 ms（不达标，§8.7） |
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
| Spring 上下文 | L3 未启动（数据访问层进程内基准，直接 JDBC + jOOQ）；**L4 已启动**完整 Spring Boot 上下文 + 内嵌 Tomcat（`RANDOM_PORT`） |
| L4 被测库 | `java_admin_perf_l4` = `java_admin` 完整 110 表结构与种子（含 admin 账号/菜单/权限/Flyway/Flowable）+ `java_admin_perf` 的 12,800,004 行 `sys_operation_log`；装载后 `BIT_XOR(CRC32(id))=2,953,533,379`，与源库完全一致、4 条边界行齐全 |
| 隔离策略 | 两库均独立；`java_admin` 全程只读取 DDL/种子，其主表仍为 32,854 行未变；`java_admin_perf` 亦未被修改 |

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

# 3) 构建 L4 隔离库（复制 110 表结构+种子，再分批装载 1280 万行主表）
bash scripts/perf/seed-l4-db.sh

# 4) 千万级 L4 HTTP 全链路压测（真实 Boot 上下文，约 3 分钟）
export TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/java_admin_perf_l4?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8'
mvn -o -f pom.xml -pl scaffold-admin -am test \
  -Dtest=TenMillionHttpPerf \
  -Dsurefire.failIfNoSpecifiedTests=false

# 5) 四门禁
mvn -o -f pom.xml clean test          # 17 模块全量
python3 scripts/check-conventions.py  # EXIT=0
cd ../pc-admin-vue3-vite && npm test && npm run build
```

> 环境注记：本机 WorkBuddy 注入的 `NODE_OPTIONS=--require=...node-language-shim.cjs` 会让 `npm run build` 的包装进程在 `transforming...` 阶段无限挂起（本次实测直连 `./node_modules/.bin/vite build` 为 15.11s、`env -u NODE_OPTIONS npm run build` 为 15.25s，均 EXIT=0）。这是**执行环境噪声，非代码缺陷**；CI/本机裸终端无该注入，门禁命令按上方原样执行即可。

- 两个基准类均以 `Perf` 结尾，**不匹配 surefire `*Test` 与 failsafe `*IT` 默认 include**，因此千万级压测不会拖慢 2,067 例日常回归；需 `-Dtest=` 显式触发。
- 支持 `-DargLine="-Dperf.functionalOnly=true"` 只跑 L3 功能用例（约 35s），便于迭代诊断。
- L3 结果输出 `/tmp/ten-million-perf-results.txt`；L4 结果输出 `/tmp/ten-million-http-perf-results.txt`。L4 采用**逐场景增量落盘**（场景开始/结束各写 SCENARIO 行），长跑被外部中断时已完成场景的指标不会丢失；本次原始证据已归档至 `scripts/perf/results/`。
- L4 端点默认只查最近 24h 且 `success=0`，而基准数据 `occurred_at` 集中在 2025 年；压测显式传 `from=1970-01-01T00:00:00&to=2099-12-31T23:59:59`，并前置断言 `success=0` 命中 ≥ 200,000 行，防止「跑了却扫不到大表」的假闭环。

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

### 4.1 L4 千万级 HTTP 端到端实测（本轮补齐 §8.6 覆盖局限）

真实 Spring Boot 上下文（`integration` profile、`RANDOM_PORT` 内嵌 Tomcat、本机 MySQL、`TZ=UTC`、JDK 21），`TestRestTemplate` 经 loopback socket 请求，完整穿过 HTTP 连接器 → 过滤器链（鉴权 / 限流 / `@AuditOperation`）→ Controller/Service → 1280 万行主表。原始结果见 `results/TEN-MILLION-HTTP-L4.txt`，慢 SQL 证据见 `results/TEN-MILLION-HTTP-L4-SLOWSQL.txt`。

| 场景 | 请求 / 并发 / 预热 | p50 (ms) | p95 (ms) | p99 (ms) | 吞吐 (req/s) | 错误率 | 2xx | 判定 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| TC-L4-1 `ping`（匿名·框架基线） | 200 / 20 / 30 | 11 | **109** | 133 | **671.20** | 0.00% | 200 | 达标 |
| TC-L4-2 `error-logs` 首页（全量窗口·COUNT+分页） | 8 / 4 / 1 | 20,853 | **20,855** | 20,855 | 0.19 | 0.00% | 8 | **不达标** |
| TC-L4-3 `error-logs` 深页（offset=19980·全量窗口） | 3 / 2 / 0 | 21,504 | **21,505** | 21,505 | 0.07 | 0.00% | 3 | **不达标** |
| TC-L4-4 `dashboard/overview`（全表 COUNT） | 8 / 4 / 1 | 3,023 | **3,024** | 3,024 | 1.34 | 0.00% | 8 | **不达标** |
| TC-L4-5 `error-summary`（全表 GROUP BY） | 3 / 2 / 0 | 6,251 | **6,353** | 6,353 | 0.24 | 0.00% | 3 | **不达标** |

**关键观测**：

1. **功能正确性达标**：5/5 场景完成、统计范围内 222 个请求全部 2xx、**错误率 0.00%**，`VERDICT=PASS`。说明千万级数据下鉴权、时间参数解析、分页序列化、审计写入均无功能缺陷。
2. **瓶颈定位明确**：框架基线 `ping` p95 109ms / 671 req/s，而大表端点 p95 3,024~21,505ms / 0.07~1.34 req/s，差距 28~197 倍，故慢的不是 HTTP 框架、网络或鉴权，而是**大表 SQL 本身**。日志中 41 条慢 SQL（去重 28 条）精确对应 `COUNT + ORDER BY ... LIMIT ? OFFSET ?`、`select count(*) from sys_operation_log`、`GROUP BY module_code, result_code`，与 §4 的 P-04/P-07/P-08 诊断结论在真实 HTTP 链路上相互印证。
3. **并发放大效应**：L3 单线程实测 COUNT 4.9s / 深页 6.3s / GROUP BY 9.3s；L4 并发 4 争抢同一批大表扫描时，首页 p95 放大到 20.9s。Tomcat 工作线程会长时间被占满，是比单次延迟更严重的**线程池耗尽风险**。
4. **审计写入已隔离计量**：`AUDIT|rowsBefore=12,800,004|rowsAfter=12,800,017|auditRowsWritten=13`，压测自身经 `@AuditOperation` 异步写入 13 行，未与被测数据规模混算；压测前已清理上一轮遗留的 34 行并校验 `BIT_XOR(CRC32(id))` 与源库一致。
5. **资源稳定**：各场景结束采样 `heapUsedMB` 116~156、`threads` 54~64，无堆持续增长与线程膨胀；错误率恒为 0，未出现连接池耗尽或超时熔断。
6. **口径说明**：L4 是容量探索而非硬 SLA 卡点，断言仅设宽松护栏（框架基线错误率 <5%、鉴权场景须有 2xx、吞吐 >0），环境相关延迟写入报告而非断言以免 CI 抖动误报。按 §1.2 的交互级 `p95<150ms` 口径，TC-L4-2~5 **明确不达标**，处置见 §8.7。

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

**L4 全链路资源占用**（每个场景结束后采样，完整 Boot 上下文 + Tomcat 线程池）：

| 场景后 | 堆已用 | 线程数 |
| --- | ---: | ---: |
| TC-L4-1 ping | 156 MB | 64 |
| TC-L4-2 error-logs 首页 | 116 MB | 64 |
| TC-L4-3 error-logs 深页 | 120 MB | 54 |
| TC-L4-4 dashboard overview | 126 MB | 55 |
| TC-L4-5 error-summary | 128 MB | 54 |

堆在 116–156 MB 区间震荡、线程数 54–64 无膨胀，全场景 0 错误、0 连接泄漏；即便如此，大表端点单次占用 Tomcat 工作线程 3~21s，并发 4 即可耗尽默认线程池，故**资源未泄漏不等于容量可用**，瓶颈见 §8.7。

---

## 6. 用例通过率汇总

| 层次 | 总数 | 通过 | 通过率 |
| --- | ---: | ---: | ---: |
| 后端全模块单测（17 模块，最终四门禁） | 2,067 | 2,067 | **100%** |
| 前端契约测试 | 91 | 91 | **100%** |
| 千万级 L3 功能用例（正常/边界/异常/写入/并发/稳定性） | 25 | 25 | **100%** |
| 性能场景（L3 SLA 判定项：P-01/02/03/05/09） | 5 | 5 | **100%** |
| 性能场景（L3 诊断对照项：P-04/06/07/08） | 4 | 0（超阈值） | 0% · 见 §7 |
| 稳定性场景 | 1 | 1 | **100%** |
| **L4 HTTP 场景完成与功能正确性**（5 场景 / 222 统计请求 / 0 错误） | 5 | 5 | **100%** |
| **L4 大表端点交互级性能**（TC-L4-2~5，p95<150ms 口径） | 4 | 0（超阈值） | **0% · 见 §7 / §8.7** |
| 门禁（后端/约定/前端单测/前端构建） | 4 | 4 | **100%** |
| **合计（不含 L3 诊断对照与 L4 性能不达标项）** | **2,198** | **2,198** | **100%** |

> 合计口径说明：L3 的 4 项诊断对照是刻意构造的非生产路径，L4 的 4 个大表端点则是**真实暴露的在线接口**，后者按 §0/§9 计为上线前阻断项，不计入「通过」合计但单列在 §7。

---

## 7. 未通过项

| 编号 | 场景 | 实测 p95 | 阈值 | 性质 | 判定与处置 |
| --- | --- | ---: | ---: | --- | --- |
| P-04 | OFFSET 深页 offset=1,000,000 | 1,014 ms | 150 ms | 诊断对照 | **未达标**。生产推荐路径已切 keyset（P-01/P-02 为 2ms），存量 OFFSET 端点保留前端契约但设有 5,000 偏移护栏；非阻断。 |
| P-06 | 无索引列 `operator_name` 全表扫描 | 5,192 ms | 150 ms | 诊断对照 | **未达标**。禁止千万级表按无索引列即席查询；需补索引或改走检索/异步导出；非阻断（现有服务未暴露该查询）。 |
| P-07 | 全表 `COUNT(*)` | 1,940 ms | 150 ms | 诊断对照 | **超阈值**。`JooqWriters.page` 每次分页执行 `count`，在大表成为固定成本；keyset 不返回 `total` 的设计正确，建议列表接口优先 keyset；非阻断。 |
| P-08 | `GROUP BY module_code` 全表聚合 | 9,490 ms | 150 ms | 诊断对照 | **未达标**。报表/大屏类聚合须走预聚合、物化汇总表或离线任务，不可在线实时扫全表；非阻断。 |

**L4 真实在线 HTTP 端点（本轮新增·上线前阻断项）**：与 L3 刻意构造的诊断对照不同，以下是生产已暴露的在线接口，在 1280 万行主表上实测不达标，详见 §4.1 / §8.7。

| 编号 | 端点 | 实测 p95 | 阈值 | 错误率 | 判定与处置 |
| --- | --- | ---: | ---: | ---: | --- |
| TC-L4-2 | `GET /api/system/monitor/error-logs?page=1&size=20`（全量窗口） | 20,855 ms | 150 ms | 0.00% | **不达标**。`JooqWriters.page` 每次执行 `COUNT` + `ORDER BY/LIMIT/OFFSET`；须改走 keyset（不返回 total）或强制收窄时间窗口与最大页数。 |
| TC-L4-3 | 同上 `page=1000`（offset=19980） | 21,505 ms | 150 ms | 0.00% | **不达标**。深页 OFFSET 叠加排序；P4 的 5,000 偏移护栏**未覆盖 monitor 端点**，须补齐护栏并引导 keyset。 |
| TC-L4-4 | `GET /api/system/dashboard/overview` | 3,024 ms | 150 ms | 0.00% | **不达标**。`operationCount()` 每次对整表 `fetchCount`；须改缓存/预聚合计数或异步刷新，避免每次打开总览都扫全表。 |
| TC-L4-5 | `GET /api/system/monitor/error-summary`（全量窗口） | 6,353 ms | 150 ms | 0.00% | **不达标**。全表 `GROUP BY module_code, result_code`；须改预聚合表/离线任务并限制时间窗口。 |

**首轮 F-15 失败（已闭环，不计入最终未通过项）**：初版在 JVM 默认 `Asia/Shanghai` 下运行，F-15 断言 2099 年边界行失败；定位为 JDBC 时区问题，修复后最终复验 25/25。原始证据见 `results/TEN-MILLION-FIRST-RUN.txt`。

---

## 8. 性能瓶颈与风险

### 8.1 【中危·配置风险】`serverTimezone=UTC` 与 JVM 时区不一致导致 DATETIME 整体 +8h

- **证据**：首轮 F-15 诊断输出 `1970-01-01 00:00:00` 读回为 `1970-01-01T08:00`、`2099-12-31 23:59:59.999` 读回为 `2100-01-01T07:59:59.999`、`2025-06-15 12:00` 读回为 `20:00`，全部精确 +8h；改用 `-Duser.timezone=UTC` 后同一用例立即通过。
- **根因**：数据源 URL 声明 `serverTimezone=UTC`（`application-local.yml`、CI `ci.yml`、`application-integration.yml` 均如此），而 JVM 默认 `Asia/Shanghai`；Connector/J 将 DATETIME 按连接时区解释后换算到 JVM 本地时区，`SnakeRecordMapper` 的 `Timestamp → LocalDateTime` 转换本身正确。
- **影响**：所有 DATETIME 读取/展示/导出在 JVM 非 UTC 的部署下整体偏移，且临界值会跨日跨年；H2 单测无法暴露（仅真实 MySQL 路径）。
- **处置建议**：① 容器统一 `TZ=UTC`；或 ② JDBC URL 改用 `connectionTimeZone=SERVER` + `forceConnectionTimeZoneToSession=true` 并统一 JVM 时区；③ 补一条真实 MySQL 的边界时间集成断言（1970/2099 临界值）防止回归。
- **本次基准处理**：`TenMillionScalePerf.setUp()` 内显式 `TimeZone.setDefault(UTC)`，与标准容器部署对齐，保证任意开发机可复现。
- **闭环落地（2026-11-05 追加，原 P0「上线前核对」项已完成）**：
  - **运行期显式 UTC**：`deploy/Dockerfile` 增加 `TZ=UTC` 与 `JAVA_OPTS` 的 `-Duser.timezone=UTC`；`deploy/docker-compose.yml` 的 app/MySQL 均设 `TZ: UTC`，MySQL 追加 `--default-time-zone=+00:00`（使 `NOW()`/`CURRENT_TIMESTAMP` 与 JDBC 语义一致）；`.github/workflows/ci.yml` 的单元与集成 job 均设 `TZ: UTC`。
  - **生产 fail-fast**：新增 `scaffold-admin/src/main/java/com/acme/scaffold/admin/config/DataSourceTimeZoneGuard.java`（仅 `@Profile("prod")`），启动时解析 JDBC URL 的 `serverTimezone`/`connectionTimeZone` 并与 JVM `getRawOffset()` 比对，不一致即拒绝启动并输出修复指引；未声明、声明 `SERVER` 或值无法解析时只告警、不误伤；可用 `app.timezone.guard.enabled=false` 关闭。`application-prod.yml` 显式启用，`scripts/init/env.template` 标注不可删除 `serverTimezone=UTC`。
  - **回归断言**：新增 `DataSourceTimeZoneGuardTest`（10 例，进常规 surefire）：覆盖 UTC 对齐放行、`Asia/Shanghai` 错配 fail-fast、运维注入错配、逃生开关、`spring.datasource.url` 回退、参数别名解析；并用真实 `SnakeRecordMapper` 固化「UTC 下 1970/2099 临界 DATETIME 零偏移、非 UTC 下精确 +8h」的根因语义；另设部署配置契约断言，防止 Dockerfile/compose/CI/环境模板的 UTC 配置漂移。
  - **闭环后四门禁**：后端 **2,062 例 / 0 失败 / 0 错误 / 0 跳过**、`check-conventions.py` EXIT=0、前端 91/91、`vite build` EXIT=0。
  - **边界**：自管部署（不使用本仓库镜像/compose）或外部注入 `DB_PRIMARY_URL` 时，仍须按同一口径设置容器 `TZ` 与 JVM 时区；guard 只拦截「显式且可解析」的错配声明，无法静态解析的声明仍为告警。

### 8.2 OFFSET 深翻页线性退化
1,280 万行下 offset=1,000,000 的 p95 达 1,014ms，是首页的约 1,014 倍。P4 整改提供的 keyset 端点与偏移护栏是必要兜底；建议逐步把仍在使用 OFFSET 的存量列表接口收敛到 keyset。

### 8.3 大表无索引过滤与全表聚合
P-06 5,192ms、P-08 9,490ms、P-07 1,940ms 表明：千万级表上任何「未下推到索引」的查询都会退化到秒级。治理方向：查询条件白名单化并强制走复合索引、分页接口优先 keyset（不计算 total）、统计类改预聚合/异步任务。

### 8.4 二级索引存储成本
主表数据 1,947.0 MB、索引 1,294.8 MB，索引约占数据 66%；整库约 3.48 GB。容量规划需按「数据 + 索引」双倍估算，并评估历史归档/分区策略。

### 8.5 `information_schema` 统计滞后
批量装载 + 重建索引后 `index_length` 曾显示 0，易误判「索引未建成」。`SHOW INDEX` 与 P-05 的 4ms 索引覆盖查询均证明索引有效；运维取证前需先 `ANALYZE TABLE`。

### 8.6 覆盖局限（结论边界）

- **L4 已在千万级隔离库补齐**（§4.1），原「L3 未启动 Spring Boot HTTP 全链路 / L4 未在千万级库重跑」的局限已消除；但 L4 仅覆盖 5 个场景（框架基线 + 4 个大表端点），未覆盖登录写入、通知派发、工作流、文件上传等全业务链路在千万级下的表现。
- 单机、loopback、无 TLS / 反向代理 / 网关；本机 Redis 不可用，限流回落本地计数（`RedisRateLimiter` 输出 WARN），因此**未验证 Redis 可用时的限流与缓存表现**；结果不代表生产集群或分布式部署。
- 数据集为按真实 DDL/索引生成的合成数据，分布均匀；未覆盖倾斜热键、长事务锁竞争、主从延迟、故障注入（混沌）。
- L4 重查询并发规模按「容量探索」收敛（单场景 3~8 请求），样本量不足以给出严格 p99 置信区间；p95/p99 仅作量级判断，治理后须扩大样本复测。
- C9 真实 CVE 扫描依赖联网 NVD，P4 存量 OFFSET 端点为保留前端契约的渐进收敛，均沿用 V1.1.0 整改核对说明 §10 的非阻断遗留口径。

### 8.7 【高危·性能·上线前阻断】千万级下遗留大表 HTTP 端点

- **证据**：§4.1 的 TC-L4-2~5 实测 p95 3,024 / 6,353 / 20,855 / 21,505 ms，吞吐 0.07~1.34 req/s，错误率虽为 0 但每次请求占用 Tomcat 工作线程 3~21s；日志 41 条慢 SQL（去重 28 条）与 `results/TEN-MILLION-HTTP-L4-SLOWSQL.txt` 精确对应。框架基线 ping p95 109ms / 671 req/s，排除框架与网络因素。
- **根因**：
  1. `MonitorService.errorLogs` 走 `JooqWriters.page`，每次分页固定执行 `COUNT(*)` + `ORDER BY occurred_at DESC LIMIT ? OFFSET ?`；`errorSummary` 执行全表 `GROUP BY module_code, result_code`；`JooqSystemStatsAdapter.operationCount()` 执行 `select count(*) from sys_operation_log`（由 `dashboard/overview` 触发）。
  2. 这两个监控端点默认只查「最近 24h 且 `success=0`」，在常规数据量下天然限流；一旦运营放宽时间窗口或数据持续累积到千万级，保护失效。
  3. **P4 整改的偏移护栏只覆盖 `user`/`message` 列表，未覆盖 monitor 端点**：实测 `page=1000`（offset=19,980）未被拦截，深页照常执行。
- **影响**：`dashboard/overview` 属于首页高频接口；少量并发即可长时间占满 Tomcat 工作线程池，造成同实例其他接口级联不可用。这是比单次慢查询更严重的**容量与可用性风险**，不是单纯的用户体验问题。
- **治理方案（建议顺序）**：
  1. **P0 止血**：对 4 个端点强制时间窗口上限（如 ≤31 天）、最大页数 / 最大 offset 护栏与 SQL 查询超时；对大表查询做单独限流或线程池隔离，避免拖垮公共工作线程。
  2. **P1 结构性改造**：`error-logs` 切 keyset（`JooqKeysetPager`，不返回 `total`）；`overview` 的 `operationCount` 改缓存 / 预聚合计数 / 异步刷新；`error-summary` 改预聚合表或离线报表任务。
  3. **P1 索引与归档**：为千万级主表按 `(tenant_id, occurred_at, success)` 建复合索引，并评估按时间分区与历史归档，避免任何路径全表扫描。
  4. **P2 复测**：按 §2 第 4 步命令在治理后重跑 L4，验收口径 `p95 < 150ms`、错误率 `< 1%`、吞吐较本次提升 ≥10 倍。
- **临时缓解（若上线时间紧）**：在上线配置中把这些端点标注为「运营异步查询」、限制使用时段与并发，并从默认首页移除实时调用；同时保留 §8.2/§8.3 已验证的 keyset 与预聚合替代路径。**未治理前，本报告不认可这些端点在千万级数据下可对外提供在线服务**。

### 8.8 【已修复·真实启动缺陷】P7 熔断器 Bean 装配歧义

- **发现路径**：L4 首次拉起完整 Spring Boot 上下文时失败，根因为 `NoUniqueBeanDefinitionException`——P7 整改新增 5 个独立 `CircuitBreaker` Bean 后容器中同类型 Bean 变为 6 个，而 `NotificationService.dispatchBreaker` 仍是纯按类型 `@Autowired(required=false)` 注入。
- **为何常规门禁未暴露**：2,062 例后端单测均为 mock 单测、不拉起真实 Spring 上下文；`*IT` 本地默认跳过（failsafe 在 `verify` 阶段，`mvn -o clean test` 不执行）。只有 L4 / 完整集成 IT 会触发真实装配，属于**测试层次覆盖缺口**，不是新引入的功能退化。
- **修复决策**：**不**给总闸加 `@Primary`——那会让 5 个渠道分闸参数全部误注入同一个总闸实例，直接破坏 P7「单一下游故障隔离」语义。改为**逐点精确限定**：
  - `NotificationService.dispatchBreaker` 增加 `@Qualifier("notificationDispatchBreaker")`；
  - `NotificationResilienceConfig.externalChannelBreakers(...)` 的 5 个 `CircuitBreaker` 参数各加对应 `@Qualifier`，避免依赖 `-parameters` 编译参数名解析。
- **回归断言**：新增 `NotificationResilienceWiringTest`（5 例，纯 JVM `AnnotationConfigApplicationContext`，无需 MySQL/Boot 全上下文，进常规 surefire 门禁）：覆盖 6 个 Bean 均注册且两两独立、5 个渠道映射绑定到正确分闸实例（防 `@Qualifier` 串线）、生产字段注解未被移除、与生产注入点同构的消费者在 6 候选下装配成功、以及「无 `@Qualifier` 必然失败」的负向契约。
- **修复后验证**：L4 上下文正常启动、`BUILD SUCCESS`、5/5 场景完成、`VERDICT=PASS`；修复已纳入本次交付与四门禁回归。

---

## 9. 达标结论

> **本次千万级（L3 14,000,004 行 / L4 12,800,004 行主表）功能与性能测试：有条件达标。**
> 功能正确性、稳定性与 L3 推荐路径（keyset）性能 **达标**；**L4 的 4 个遗留大表在线端点交互级性能不达标，构成上线前阻断项**（§8.7）。未治理前，不认可这些端点在千万级数据下对外提供在线服务。

1. 数据规模、功能用例通过率、keyset 首页/深页 p95、深页退化比、稳定性错误率 5 项硬性判定标准**全部满足**；25/25 功能用例覆盖正常、边界、异常、写入、并发与稳定性场景。
2. **L4 HTTP 全链路功能正确性达标**：5/5 场景完成、222 个统计请求全部 2xx、**错误率 0.00%**，鉴权、ISO 时间参数解析、分页序列化、异步审计写入在千万级数据下均无功能缺陷。
3. 四门禁全绿：后端 `mvn -o clean test` **2,067 例 / 0 失败 / 0 错误 / 0 跳过**、`check-conventions.py` **EXIT=0**、前端 `npm test` **91/91**、前端 `vite build` **EXIT=0**。
4. 30s / 16 线程混合负载 167,038 次操作 **0 错误**，吞吐 5,555 ops/s；L4 各场景堆 116~156 MB、线程 54~64，均无泄漏迹象。
5. §7 的 L3 4 项超阈值场景均为**诊断对照**（刻意构造的非生产路径），不构成上线阻断；但本轮 L4 把同样的 SQL 模式映射到了**真实在线端点**并实测 p95 3,024~21,505ms，因此 §8.7 单列为**阻断项**。两者不矛盾：前者是「不这么用」，后者是「现在真有这么用的接口」。若业务把「任意列即席查询 / 在线全量聚合」列入 SLA，该子项**不达标**，须按 §8.3 / §8.7 整改后复测。
6. §8.1 的时区配置风险已通过「部署统一 UTC + 生产 fail-fast + 10 例契约回归」闭环；§8.8 的 P7 熔断器装配歧义（L4 首次暴露的真实启动缺陷）已用逐点 `@Qualifier` 修复并补 5 例装配契约回归。两者均已闭环，不影响本次功能/稳定性达标判定；自管部署仍须按 §8.1 边界核对。
7. **放行条件**：完成 §8.7 的 P0 止血（强制时间窗口上限 / 最大 offset 护栏 / SQL 查询超时 / 大表查询线程池隔离），并按 §2 第 4 步在治理后复测 `p95<150ms`、错误率 `<1%`，方可把结论升级为「无条件达标」；P1 的 keyset / 预聚合结构性改造可随后续迭代收敛，但不得跳过 P0。

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
| `java-admin-framework/scaffold-admin/src/main/java/com/acme/scaffold/admin/config/DataSourceTimeZoneGuard.java` | §8.1 风险闭环：生产启动期时区一致性 fail-fast |
| `java-admin-framework/scaffold-admin/src/test/java/com/acme/scaffold/admin/config/DataSourceTimeZoneGuardTest.java` | §8.1 风险闭环：10 例契约与边界时间回归断言 |
| `java-admin-framework/scripts/perf/seed-l4-db.sh` | L4 隔离库：复制 110 表结构+种子，分批装载 1280 万行主表并校验 |
| `java-admin-framework/scaffold-admin/src/test/java/com/acme/scaffold/integration/perf/TenMillionHttpPerf.java` | L4 千万级 HTTP 全链路压测（5 场景、增量落盘、并发安全结果收集） |
| `java-admin-framework/scripts/perf/results/TEN-MILLION-HTTP-L4.txt` | L4 最终原始结果（ENV/METRIC/RESOURCE/AUDIT/SUMMARY/VERDICT） |
| `java-admin-framework/scripts/perf/results/TEN-MILLION-HTTP-L4-SLOWSQL.txt` | L4 慢 SQL 证据（41 条检测、去重 28 条 SQL+耗时） |
| `java-admin-framework/scaffold-system/src/main/java/com/acme/scaffold/system/notification/config/NotificationResilienceConfig.java` | §8.8 修复：5 个分闸参数逐点 `@Qualifier` |
| `java-admin-framework/scaffold-system/src/main/java/com/acme/scaffold/system/notification/service/NotificationService.java` | §8.8 修复：总闸 `dispatchBreaker` 精确限定 |
| `java-admin-framework/scaffold-system/src/test/java/com/acme/scaffold/system/notification/config/NotificationResilienceWiringTest.java` | §8.8 回归：5 例纯 JVM 装配契约（含负向契约） |

---

## 11. 后续建议（流转给负责人确认）

| 优先级 | 事项 | 建议角色 |
| --- | --- | --- |
| P0（已完成 2026-11-05） | 容器/CI 统一 UTC、生产启动 fail-fast、10 例边界时间契约断言（§8.1）；仅自管部署仍需按同一口径核对 | 已完成 |
| **P0（新增·上线前阻断）** | 按 §8.7 完成 4 个大表端点止血：强制时间窗口上限、最大 offset 护栏（补覆盖 monitor 端点）、SQL 查询超时、大表查询线程池隔离；治理后按 §2 第 4 步复测 `p95<150ms` / 错误率 `<1%` | 后端 / 测试 |
| P1 | `error-logs` 切 keyset、`overview` 改预聚合计数、`error-summary` 改离线报表（§8.7） | 后端 / 前端 |
| P1 | 报表 / 大屏全量聚合改预聚合或离线任务；禁止无索引列在线查询（§8.3） | 后端 / 数据 |
| P1 | 存量 OFFSET 列表接口继续收敛至 keyset（§8.2） | 后端 / 前端 |
| P2 | 千万级表容量规划：索引 1.29 GB、整库 3.48 GB，评估归档/分区（§8.4） | 运维 / DBA |
| P2（已完成 2026-11-05） | 在千万级库上重跑 HTTP 端到端压测，补齐 L4 覆盖局限（§4.1 / §8.6） | 已完成 |

> 建议将上述事项创建为项目事项分配给对应负责人并添加关注人，把本报告与原始结果文件上传到项目资料库，形成版本质量上下文。
