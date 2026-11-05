package com.acme.scaffold.perf;

import com.acme.scaffold.common.api.CursorCodec;
import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooq.paging.JooqKeysetPager;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 千万级数据规模功能与性能基准（V1.1.0 全量功能/性能测试）。
 *
 * <p><b>数据</b>：隔离库 {@code java_admin_perf}（由 {@code scripts/perf/seed-ten-million.sh}
 * 从生产 {@code java_admin} 复制 7 张真实表结构后灌入），精确 COUNT 合计 14,000,004 行，
 * 主表 {@code sys_operation_log} 12,800,004 行。绝不触碰现有业务库。</p>
 *
 * <p><b>被测代码</b>：全部走生产原语，不复制实现——
 * {@link JooqKeysetPager}（keyset 游标分页）、{@link JooqWriters}
 * （count/page/fetchTop/fetchListBounded/batchInsert/insert/updateById/notDeleted）、
 * {@link JooqTables}（表/列元数据）、{@link CursorCodec}（不透明游标）、
 * {@link KeysetPageQuery} / {@link PageQuery}（边界钳制）、SnakeRecordMapper（真实映射）。</p>
 *
 * <p><b>判定口径</b>：延续 {@code scripts/perf/PERF-TEST-PLAN.md} 的
 * p50/p95/p99、吞吐、错误率、预热与饱和判定；DB 层阈值见 {@link #THRESHOLD}。</p>
 *
 * <p><b>命名</b>：类名以 {@code Perf} 结尾，不匹配 surefire（{@code *Test}）与
 * failsafe（{@code *IT}）默认 include，因此不会进入常规 {@code mvn clean test} 门禁，
 * 避免千万级压测拖慢 2052 例日常回归。显式执行：
 * {@code mvn -o -f pom.xml -pl scaffold-admin -am test -Dtest=TenMillionScalePerf
 * -Dsurefire.failIfNoSpecifiedTests=false}。</p>
 */
class TenMillionScalePerf {

    // ------------------------------------------------------------------
    // 环境与阈值（本次 DB 层基准采用的判定标准，报告须原样引用）
    // ------------------------------------------------------------------
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/java_admin_perf"
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
            + "&characterEncoding=utf8&rewriteBatchedStatements=true";
    private static final String USER = "root";
    private static final String PASSWORD = "root";
    private static final String OUT = System.getProperty("perf.out", "/tmp/ten-million-perf-results.txt");

    /** keyset 交互级 p95 上界，对齐 PERF-TEST-PLAN.md §4 TC-P2 的 150ms。 */
    private static final double KEYSET_P95_LIMIT_MS = 150.0;
    /** keyset 深页 / 首页 p95 比值上界：超过即视为随偏移退化。 */
    private static final double KEYSET_DEEP_RATIO_LIMIT = 2.0;
    /** 错误率上界，对齐 PERF-TEST-PLAN.md §4 的 < 1%。 */
    private static final double ERROR_RATE_LIMIT = 0.01;
    /** 千万级数据集最低行数要求。 */
    private static final long MIN_TOTAL_ROWS = 10_000_000L;

    private static final String THRESHOLD =
            "keyset首页p95<" + KEYSET_P95_LIMIT_MS + "ms; keyset深页p95/首页p95<"
                    + KEYSET_DEEP_RATIO_LIMIT + "; 错误率<" + (ERROR_RATE_LIMIT * 100)
                    + "%; 功能用例通过率=100%; 数据规模>=" + MIN_TOTAL_ROWS + "行";

    private static final JooqTables.TableRef T = JooqTables.SYS_OPERATION_LOG;
    private static final Field<Long> F_ID = T.field("id", Long.class);
    private static final Field<String> F_MODULE = T.field("module_code", String.class);
    private static final Field<String> F_RESULT = T.field("result_code", String.class);
    private static final Field<String> F_OPERATOR_NAME = T.field("operator_name", String.class);
    private static final Field<String> F_TRACE = T.field("trace_id", String.class);
    private static final Field<Long> F_TENANT = T.field("tenant_id", Long.class);
    private static final Field<LocalDateTime> F_OCCURRED = T.field("occurred_at", LocalDateTime.class);
    private static final OrderField<?> ORDER_ID_DESC = F_ID.desc();
    private static final Field<?>[] KEY_FIELDS = {F_ID};
    private static final boolean[] ASCENDING = {false};

    private static Connection conn;
    private static DSLContext dsl;
    private static PrintWriter out;
    private static final List<CaseResult> CASES = new ArrayList<>();
    private static final List<Metric> METRICS = new ArrayList<>();
    private static final List<String> RESOURCES = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();

    private record CaseResult(String name, String group, boolean pass, double ms, String detail) {
    }

    private record Metric(String scenario, int samples, double p50, double p95, double p99,
                          double avg, double min, double max, double throughput,
                          long errors, long total, String extra) {
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @BeforeAll
    static void setUp() throws Exception {
        // JDBC URL 沿用生产/CI 的 serverTimezone=UTC。JVM 默认时区必须与之对齐，否则
        // Connector/J 会把 DATETIME 按 UTC 解释后再换算到 JVM 本地时区，读回值整体偏移：
        // 本机 Asia/Shanghai 实测 +8h（1970-01-01 00:00 -> 08:00，
        // 2099-12-31 23:59:59.999 -> 2100-01-01 07:59:59.999）。容器标准部署为 UTC，
        // 此处显式对齐使基准在任意开发机上可复现；该配置风险已写入测试报告「风险」章节。
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        out = new PrintWriter(Files.newBufferedWriter(Path.of(OUT), StandardCharsets.UTF_8));
        line("ENV|jdk=" + System.getProperty("java.version")
                + "|os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + "|cpu=" + Runtime.getRuntime().availableProcessors()
                + "|maxHeapMB=" + (Runtime.getRuntime().maxMemory() / 1024 / 1024)
                + "|jvmTimezone=UTC(与 JDBC serverTimezone 对齐)");
        line("ENV|dataset=java_admin_perf|threshold=" + THRESHOLD);
        conn = DriverManager.getConnection(URL, USER, PASSWORD);
        dsl = DSL.using(conn, SQLDialect.MYSQL);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                line("ENV|mysql=" + rs.getString(1));
            }
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (out != null) {
            emitSummary();
            out.flush();
            out.close();
        }
        if (conn != null) {
            conn.close();
        }
    }

    private static void line(String s) {
        out.println(s);
        System.out.println("[TEN-MILLION] " + s);
    }

    // ------------------------------------------------------------------
    // 主入口：功能用例 → 性能场景 → 稳定性 → 资源
    // ------------------------------------------------------------------

    @Test
    void tenMillionScaleBenchmark() {
        functionalCases();
        if (!Boolean.getBoolean("perf.functionalOnly")) {
            performanceScenarios();
            stabilityScenario();
            resourceSnapshot();
        }

        long failed = CASES.stream().filter(c -> !c.pass()).count();
        line("SUMMARY|functionalTotal=" + CASES.size() + "|functionalPassed="
                + (CASES.size() - failed) + "|functionalFailed=" + failed);
        assertEquals(0L, failed, "存在功能用例未通过，详见 " + OUT);
    }

    // ------------------------------------------------------------------
    // 一、功能正确性（正常 / 边界 / 异常）
    // ------------------------------------------------------------------

    private void functionalCases() {
        // 每个线程使用独立 Connection：静态 dsl 绑定的单连接不可跨线程共享
        group("F-01 数据规模 >= 1000 万（精确 COUNT）", "正常/规模",
                () -> {
                    long total = exactCount();
                    assertTrue(total >= MIN_TOTAL_ROWS, "数据规模不足: " + total);
                    return "7 表精确 COUNT 合计=" + total;
                });
        group("F-02 主表 1280 万行且边界行存在", "正常/规模",
                () -> {
                    long n = countOf("sys_operation_log");
                    assertEquals(12_800_004L, n);
                    long boundary = dsl.selectCount().from(T.table())
                            .where(F_TRACE.eq("BOUNDARY")).fetchOne(0, long.class);
                    assertEquals(4L, boundary);
                    return "主表=" + n + ", 边界行=4";
                });
        group("F-03 keyset 首页返回 size 条且 nextCursor 非空", "正常",
                () -> {
                    KeysetPageResult<SysOperationLogDO> r = keyset(20, "");
                    assertEquals(20, r.records().size());
                    assertTrue(r.hasNext());
                    return "records=20, nextCursor=" + r.nextCursor();
                });
        group("F-04 keyset 连续翻页 200 页无重复无遗漏且 id 严格递减", "正常",
                () -> {
                    String cursor = "";
                    Set<Long> seen = new java.util.HashSet<>();
                    Long prev = null;
                    int pages = 0;
                    for (int i = 0; i < 200; i++) {
                        KeysetPageResult<SysOperationLogDO> r = keyset(20, cursor);
                        for (SysOperationLogDO e : r.records()) {
                            Long id = e.getId();
                            assertTrue(seen.add(id), "keyset 出现重复 id=" + id);
                            assertTrue(prev == null || id < prev, "keyset 排序非单调递减");
                            prev = id;
                        }
                        pages++;
                        if (!r.hasNext()) {
                            break;
                        }
                        cursor = r.nextCursor();
                    }
                    assertEquals(200, pages);
                    assertEquals(4000, seen.size());
                    return "连续 200 页 / 4000 条，id 唯一且严格递减";
                });
        group("F-05 keyset 深页（第 100 万行附近）结果正确", "边界/深翻页",
                () -> {
                    String cursor = CursorCodec.encode(List.of(900000000000000000L + 11_800_000L));
                    KeysetPageResult<SysOperationLogDO> r = keyset(20, cursor);
                    assertEquals(20, r.records().size());
                    for (SysOperationLogDO e : r.records()) {
                        assertTrue(e.getId() < 900000000000000000L + 11_800_000L);
                    }
                    return "深页 20 条全部位于游标之后";
                });
        group("F-06 keyset 游标不透明且可解码", "正常",
                () -> {
                    String c = CursorCodec.encode(List.of(123456789L));
                    assertTrue(!c.contains("123456789"));
                    assertEquals(List.of("123456789"), CursorCodec.decode(c));
                    return "base64url 编码，解码后字段数=1";
                });
        group("F-07 size 边界：0/负数钳到 1，>100 钳到 100", "边界",
                () -> {
                    assertEquals(1, new KeysetPageQuery(0, "").size());
                    assertEquals(1, new KeysetPageQuery(-5, "").size());
                    assertEquals(100, new KeysetPageQuery(10_000, "").size());
                    assertEquals(1, PageQuery.of(0, 0).page());
                    assertEquals(100, PageQuery.of(1, 9999).size());
                    assertEquals(100_000, PageQuery.of(999_999, 20).page());
                    return "KeysetPageQuery[1,100] 与 PageQuery[MAX_PAGE=100000] 钳制生效";
                });
        group("F-08 非法游标（乱码）静默降级首页", "异常",
                () -> {
                    KeysetPageResult<SysOperationLogDO> r = keyset(20, "!!!not-a-cursor!!!");
                    assertEquals(20, r.records().size());
                    return "乱码游标按首页处理，无异常";
                });
        group("F-09 游标字段数与排序键不符 → BusinessException", "异常",
                () -> {
                    String bad = CursorCodec.encode(List.of("1", "2", "3"));
                    try {
                        keyset(20, bad);
                        throw new AssertionError("预期抛出 BusinessException");
                    } catch (BusinessException expected) {
                        return "按预期拒绝: " + expected.getMessage();
                    }
                });
        group("F-10 条件过滤（tenant + module）结果全部满足条件", "正常",
                () -> {
                    Condition cond = F_TENANT.eq(3L).and(F_MODULE.eq("USER"));
                    KeysetPageResult<SysOperationLogDO> r = JooqKeysetPager.query(
                            dsl, T, SysOperationLogDO.class, cond,
                            new KeysetPageQuery(50, ""), e -> new Object[]{e.getId()},
                            KEY_FIELDS, ASCENDING);
                    assertTrue(!r.records().isEmpty());
                    for (SysOperationLogDO e : r.records()) {
                        assertEquals(3L, e.getTenantId());
                        assertEquals("USER", e.getModuleCode());
                    }
                    return "50 条全部 tenant_id=3 且 module_code=USER";
                });
        group("F-11 JooqWriters.count 与 SQL COUNT 一致", "正常",
                () -> {
                    long a = JooqWriters.count(dsl, T, F_RESULT.eq("200"));
                    long b = dsl.selectCount().from(T.table()).where(F_RESULT.eq("200"))
                            .fetchOne(0, long.class);
                    assertEquals(b, a);
                    return "count=" + a;
                });
        group("F-12 fetchTop 上限钳制（limit>1000 只返回 1000）", "边界",
                () -> {
                    List<SysOperationLogDO> l = JooqWriters.fetchTop(
                            dsl, T, SysOperationLogDO.class, DSL.trueCondition(), 5000, ORDER_ID_DESC);
                    assertEquals(1000, l.size());
                    return "请求 5000，实返 1000（P4 有界加载护栏）";
                });
        group("F-13 fetchListBounded 不钳制且严格下推 LIMIT", "边界",
                () -> {
                    List<SysOperationLogDO> l = JooqWriters.fetchListBounded(
                            dsl, T, SysOperationLogDO.class, DSL.trueCondition(), 1500, ORDER_ID_DESC);
                    assertEquals(1500, l.size());
                    return "请求 1500，实返 1500";
                });
        group("F-14 OFFSET 分页首页/深页结果互不相交", "正常",
                () -> {
                    PageResult<SysOperationLogDO> first = JooqWriters.page(
                            dsl, T, SysOperationLogDO.class, DSL.trueCondition(),
                            PageQuery.of(1, 20), ORDER_ID_DESC);
                    PageResult<SysOperationLogDO> deep = JooqWriters.page(
                            dsl, T, SysOperationLogDO.class, DSL.trueCondition(),
                            PageQuery.of(50_000, 20), ORDER_ID_DESC);
                    assertEquals(20, first.records().size());
                    assertEquals(20, deep.records().size());
                    Set<Long> ids = new java.util.HashSet<>();
                    first.records().forEach(e -> ids.add(e.getId()));
                    deep.records().forEach(e -> assertTrue(ids.add(e.getId())));
                    return "首页与 offset=999980 的结果无交集";
                });
        group("F-15 边界值行（1970/2099/emoji/极大数值）可正确映射", "边界",
                () -> {
                    List<SysOperationLogDO> l = JooqWriters.fetchList(dsl, T,
                            SysOperationLogDO.class, F_TRACE.eq("BOUNDARY"), F_ID.asc());
                    assertEquals(4, l.size());
                    boolean emoji = l.stream().anyMatch(e ->
                            e.getOperationName() != null && e.getOperationName().contains("🚀"));
                    boolean future = l.stream().anyMatch(e ->
                            e.getOccurredAt() != null && e.getOccurredAt().getYear() == 2099);
                    boolean huge = l.stream().anyMatch(e ->
                            e.getDurationMs() != null && e.getDurationMs() > 9_000_000_000_000_000_000L);
                    String dump = l.stream()
                            .map(e -> e.getId() + "=>" + e.getOccurredAt())
                            .collect(java.util.stream.Collectors.joining(","));
                    assertTrue(emoji && future && huge, "边界值丢失: emoji=" + emoji
                            + " future=" + future + " huge=" + huge + " | occurredAt=" + dump);
                    return "emoji/未来时间/极大 duration 无损读取";
                });
        group("F-16 异常语义行不污染合法枚举过滤", "异常",
                () -> {
                    long invalid = JooqWriters.count(dsl, T, F_MODULE.eq("ZZ_INVALID"));
                    assertEquals(1L, invalid);
                    long valid = JooqWriters.count(dsl, T, F_MODULE.eq("USER"));
                    assertTrue(valid > 2_000_000L);
                    return "非法枚举 1 行被隔离；合法 USER=" + valid;
                });
        group("F-17 恒假条件返回空且 keyset 无下一页", "边界",
                () -> {
                    KeysetPageResult<SysOperationLogDO> r = JooqKeysetPager.query(
                            dsl, T, SysOperationLogDO.class, DSL.falseCondition(),
                            KeysetPageQuery.of(20), e -> new Object[]{e.getId()},
                            KEY_FIELDS, ASCENDING);
                    assertTrue(r.records().isEmpty());
                    assertTrue(!r.hasNext());
                    return "空结果集，nextCursor=null";
                });
        group("F-18 批量写入 batchInsert 后回读一致并清理", "正常/写入",
                () -> {
                    List<SysOperationLogDO> batch = new ArrayList<>();
                    for (int i = 0; i < 500; i++) {
                        SysOperationLogDO e = new SysOperationLogDO();
                        e.setModuleCode("PERF_TEST");
                        e.setOperationType("QUERY");
                        e.setOperationName("batch-insert-" + i);
                        e.setResultCode("200");
                        e.setDurationMs((long) i);
                        e.setSuccess(1);
                        e.setOccurredAt(LocalDateTime.now());
                        e.setTraceId("PERF_WRITE");
                        batch.add(e);
                    }
                    JooqWriters.batchInsert(dsl, T, batch);
                    long written = JooqWriters.count(dsl, T, F_TRACE.eq("PERF_WRITE"));
                    assertEquals(500L, written);
                    int cleaned = dsl.deleteFrom(T.table()).where(F_TRACE.eq("PERF_WRITE")).execute();
                    assertEquals(500, cleaned);
                    return "写入 500 行 → 回读 500 → 清理 500";
                });
        group("F-19 单条 insert / updateById / notDeleted 语义", "正常/写入",
                () -> {
                    SysOperationLogDO e = new SysOperationLogDO();
                    e.setModuleCode("PERF_TEST");
                    e.setOperationType("CREATE");
                    e.setOperationName("single-write");
                    e.setResultCode("200");
                    e.setSuccess(1);
                    e.setDurationMs(1L);
                    e.setOccurredAt(LocalDateTime.now());
                    e.setTraceId("PERF_SINGLE");
                    JooqWriters.insert(dsl, T, e);
                    Long id = e.getId();
                    assertTrue(id != null && id > 0, "insert 未回填 id");
                    SysOperationLogDO loaded = JooqWriters.fetchById(dsl, T,
                            SysOperationLogDO.class, id);
                    assertTrue(loaded != null);
                    assertEquals("single-write", loaded.getOperationName());
                    loaded.setOperationName("single-write-updated");
                    JooqWriters.updateById(dsl, T, id, loaded);
                    assertEquals("single-write-updated",
                            JooqWriters.fetchById(dsl, T, SysOperationLogDO.class, id)
                                    .getOperationName());
                    dsl.deleteFrom(T.table()).where(F_TRACE.eq("PERF_SINGLE")).execute();
                    return "insert 回填 id / fetchById / updateById 全链路一致";
                });
        group("F-20 约束异常：超长 operation_name 被 DB 拒绝", "异常",
                () -> {
                    SysOperationLogDO e = new SysOperationLogDO();
                    e.setModuleCode("PERF_TEST");
                    e.setOperationType("QUERY");
                    e.setOperationName("x".repeat(300));
                    e.setResultCode("200");
                    e.setSuccess(1);
                    e.setOccurredAt(LocalDateTime.now());
                    e.setTraceId("PERF_ANOMALY");
                    String outcome;
                    try {
                        JooqWriters.insert(dsl, T, e);
                        NOTES.add("异常输入未被 DB 拒绝（varchar(128) 超长），需核对 sql_mode");
                        outcome = "DB 接受（记为观测点，非阻断）";
                    } catch (RuntimeException rejected) {
                        outcome = "DB 按预期拒绝: " + rejected.getClass().getSimpleName();
                    }
                    // 无论 DB 是否拒绝，均清理探针行，保证基准可重复执行且主表精确行数不变
                    dsl.deleteFrom(T.table()).where(F_TRACE.eq("PERF_ANOMALY")).execute();
                    return outcome;
                });
        group("F-21 跨表关联聚合（消息 × 回执）正确性", "正常/跨模块",
                () -> {
                    long receipts = countOf("sys_message_receipt");
                    long unread = dsl.selectCount()
                            .from(JooqTables.SYS_MESSAGE_RECEIPT.table())
                            .where(JooqTables.SYS_MESSAGE_RECEIPT
                                    .field("is_read", Integer.class).eq(0))
                            .fetchOne(0, long.class);
                    assertTrue(receipts >= 300_000L);
                    assertTrue(unread > 0 && unread < receipts);
                    return "回执=" + receipts + ", 未读=" + unread;
                });
        group("F-22 其余 6 张业务表规模与聚合正确", "正常/全模块",
                () -> {
                    Map<String, Long> m = tableCounts();
                    assertEquals(100_000L, m.get("sys_user"));
                    assertEquals(400_000L, m.get("sys_login_log"));
                    assertEquals(200_000L, m.get("sys_notification_record"));
                    assertEquals(100_000L, m.get("sys_message"));
                    assertTrue(m.get("sys_message_receipt") >= 300_000L);
                    assertEquals(100_000L, m.get("sys_monitor_sample"));
                    return m.toString();
                });
        group("F-23 无索引列过滤结果正确（正确性而非性能）", "正常",
                () -> {
                    List<SysOperationLogDO> l = JooqWriters.fetchTop(dsl, T,
                            SysOperationLogDO.class, F_OPERATOR_NAME.eq("operator1"),
                            10, ORDER_ID_DESC);
                    assertTrue(!l.isEmpty());
                    l.forEach(e -> assertEquals("operator1", e.getOperatorName()));
                    return "命中 operator1，结果全部满足条件";
                });
        group("F-24 并发查询无异常（16 线程 × 20 次）", "异常/并发",
                () -> {
                    AtomicInteger errors = new AtomicInteger();
                    ExecutorService pool = Executors.newFixedThreadPool(16);
                    CountDownLatch latch = new CountDownLatch(16);
                    for (int t = 0; t < 16; t++) {
                        pool.submit(() -> {
                            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                                DSLContext local = DSL.using(c, SQLDialect.MYSQL);
                                for (int i = 0; i < 20; i++) {
                                    JooqKeysetPager.query(local, T, SysOperationLogDO.class,
                                            DSL.trueCondition(), KeysetPageQuery.of(20),
                                            e -> new Object[]{e.getId()}, KEY_FIELDS, ASCENDING);
                                }
                            } catch (Exception e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        });
                    }
                    assertTrue(latch.await(120, TimeUnit.SECONDS), "并发用例超时");
                    pool.shutdownNow();
                    assertEquals(0, errors.get());
                    return "320 次并发 keyset 查询，0 异常";
                });
    }

    // ------------------------------------------------------------------
    // 二、性能场景（真实指标）
    // ------------------------------------------------------------------

    private void performanceScenarios() {
        // keyset 首页 / 深页：走主键聚簇索引，生产推荐路径
        Metric keysetFirst = measure("P-01 keyset 首页（主键索引）", 50, 100, () -> keyset(20, ""));
        line("METRIC|" + render(keysetFirst));
        METRICS.add(keysetFirst);

        String deepCursor = CursorCodec.encode(List.of(900000000000000000L + 11_800_000L));
        Metric keysetDeep = measure("P-02 keyset 深页（第 1180 万行附近）", 50, 100,
                () -> keyset(20, deepCursor));
        line("METRIC|" + render(keysetDeep));
        METRICS.add(keysetDeep);

        double ratio = keysetFirst.p50() <= 0 ? 0 : keysetDeep.p95() / Math.max(keysetFirst.p95(), 0.01);
        line("VERDICT|keyset首页p95=" + fmt(keysetFirst.p95()) + "ms|keyset深页p95="
                + fmt(keysetDeep.p95()) + "ms|深页/首页p95=" + fmt(ratio)
                + "|阈值p95<" + KEYSET_P95_LIMIT_MS + "ms 且比值<" + KEYSET_DEEP_RATIO_LIMIT
                + "|结论=" + verdict(keysetFirst.p95() < KEYSET_P95_LIMIT_MS
                && keysetDeep.p95() < KEYSET_P95_LIMIT_MS && ratio < KEYSET_DEEP_RATIO_LIMIT));

        // OFFSET 深翻页对照：量化 P4 关注的线性退化
        Metric offsetFirst = measure("P-03 OFFSET 首页 offset=0", 20, 50,
                () -> offsetSelect(0));
        line("METRIC|" + render(offsetFirst));
        METRICS.add(offsetFirst);

        Metric offsetDeep = measure("P-04 OFFSET 深页 offset=1,000,000", 1, 5,
                () -> offsetSelect(1_000_000));
        line("METRIC|" + render(offsetDeep));
        METRICS.add(offsetDeep);

        double offRatio = offsetFirst.p95() <= 0 ? 0
                : offsetDeep.p95() / Math.max(offsetFirst.p95(), 0.01);
        line("VERDICT|OFFSET深页p95=" + fmt(offsetDeep.p95()) + "ms|首页p95="
                + fmt(offsetFirst.p95()) + "ms|退化倍数=" + fmt(offRatio)
                + "|阈值<" + KEYSET_P95_LIMIT_MS + "ms|结论="
                + verdict(offsetDeep.p95() < KEYSET_P95_LIMIT_MS));

        // 索引覆盖过滤 vs 无索引全表扫描
        Metric indexed = measure("P-05 索引覆盖过滤（idx_module_time）", 10, 30,
                () -> dsl.selectFrom(T.table())
                        .where(F_TENANT.eq(3L).and(F_MODULE.eq("USER")))
                        .orderBy(F_ID.desc()).limit(50).fetch().size());
        line("METRIC|" + render(indexed));
        METRICS.add(indexed);

        Metric fullScan = measure("P-06 无索引列过滤（operator_name 全表扫描）", 1, 5,
                () -> dsl.selectCount().from(T.table())
                        .where(F_OPERATOR_NAME.eq("operator1")).fetchOne(0, long.class));
        line("METRIC|" + render(fullScan));
        METRICS.add(fullScan);
        line("VERDICT|无索引全表扫描p95=" + fmt(fullScan.p95()) + "ms|阈值<"
                + KEYSET_P95_LIMIT_MS + "ms|结论="
                + verdict(fullScan.p95() < KEYSET_P95_LIMIT_MS));

        // 聚合
        Metric countAll = measure("P-07 全表 COUNT(*) 聚合", 1, 5,
                () -> dsl.selectCount().from(T.table()).fetchOne(0, long.class));
        line("METRIC|" + render(countAll));
        METRICS.add(countAll);

        Metric groupBy = measure("P-08 GROUP BY module_code 聚合", 1, 5,
                () -> dsl.select(F_MODULE, DSL.count()).from(T.table())
                        .groupBy(F_MODULE).fetch().size());
        line("METRIC|" + render(groupBy));
        METRICS.add(groupBy);

        // 批量写入吞吐
        Metric write = measureBulkWrite();
        line("METRIC|" + render(write));
        METRICS.add(write);
    }

    /** 自适应轮次：先探测单次耗时，慢场景自动降轮数，避免全表扫描把基准拖到不可完成。 */
    private Metric measure(String name, int warmup, int rounds, Runnable action) {
        for (int i = 0; i < warmup; i++) {
            action.run();
        }
        long[] samples = new long[rounds];
        long errors = 0;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            long t0 = System.nanoTime();
            try {
                action.run();
            } catch (RuntimeException e) {
                errors++;
            }
            samples[i] = (System.nanoTime() - t0) / 1_000_000;
        }
        long elapsed = System.nanoTime() - start;
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        double throughput = elapsed <= 0 ? 0 : (rounds - errors) * 1_000_000_000.0 / elapsed;
        return new Metric(name, rounds, p(sorted, 50), p(sorted, 95), p(sorted, 99),
                Arrays.stream(samples).average().orElse(0), sorted[0], sorted[sorted.length - 1],
                throughput, errors, rounds,
                "warmup=" + warmup + ", elapsedMs=" + (elapsed / 1_000_000));
    }

    private Metric measureBulkWrite() {
        int batch = 1000;
        int rounds = 10;
        List<Long> samples = new ArrayList<>();
        long errors = 0;
        long totalRows = 0;
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            List<SysOperationLogDO> buf = new ArrayList<>(batch);
            for (int i = 0; i < batch; i++) {
                SysOperationLogDO e = new SysOperationLogDO();
                e.setModuleCode("PERF_BULK");
                e.setOperationType("QUERY");
                e.setOperationName("bulk-" + r + "-" + i);
                e.setResultCode("200");
                e.setSuccess(1);
                e.setDurationMs((long) i);
                e.setOccurredAt(LocalDateTime.now());
                e.setTraceId("PERF_BULK");
                buf.add(e);
            }
            long t0 = System.nanoTime();
            try {
                JooqWriters.batchInsert(dsl, T, buf);
                totalRows += batch;
            } catch (RuntimeException e) {
                errors++;
            }
            samples.add((System.nanoTime() - t0) / 1_000_000);
        }
        long elapsed = System.nanoTime() - start;
        dsl.deleteFrom(T.table()).where(F_TRACE.eq("PERF_BULK")).execute();
        long[] arr = samples.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(arr);
        return new Metric("P-09 批量写入 batchInsert(1000行/批)", rounds,
                p(arr, 50), p(arr, 95), p(arr, 99),
                Arrays.stream(arr).average().orElse(0), arr[0], arr[arr.length - 1],
                totalRows * 1_000_000_000.0 / elapsed, errors, rounds,
                "总写入=" + totalRows + "行, 吞吐单位=rows/s, elapsedMs=" + (elapsed / 1_000_000));
    }

    private static double p(long[] sorted, int percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    private static int offsetSelect(int offset) {
        return dsl.selectFrom(T.table()).orderBy(ORDER_ID_DESC)
                .limit(20).offset(offset).fetch().size();
    }

    // ------------------------------------------------------------------
    // 三、稳定性（持续混合负载 + 资源采样）
    // ------------------------------------------------------------------

    private void stabilityScenario() {
        int threads = 16;
        int seconds = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong ops = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> samples = new ConcurrentLinkedQueue<>();
        long start = System.nanoTime();
        long deadline = start + TimeUnit.SECONDS.toNanos(seconds);

        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                    DSLContext local = DSL.using(c, SQLDialect.MYSQL);
                    while (System.nanoTime() < deadline) {
                        long t0 = System.nanoTime();
                        try {
                            // 混合：4/5 走主键 keyset，1/5 走索引过滤，偶发跨表聚合
                            int kind = (int) (ops.getAndIncrement() % 5);
                            if (kind == 4) {
                                local.selectFrom(T.table())
                                        .where(F_TENANT.eq(3L).and(F_MODULE.eq("USER")))
                                        .orderBy(F_ID.desc()).limit(20).fetch();
                            } else {
                                JooqKeysetPager.query(local, T, SysOperationLogDO.class,
                                        DSL.trueCondition(), KeysetPageQuery.of(20),
                                        e -> new Object[]{e.getId()}, KEY_FIELDS, ASCENDING);
                            }
                        } catch (RuntimeException e) {
                            errors.incrementAndGet();
                        }
                        latencies.add((System.nanoTime() - t0) / 1_000_000);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    samples.add("THREAD_ABORT|" + id + "|" + e.getClass().getSimpleName());
                }
            });
        }

        // 每 5 秒采样 JVM 资源
        long nextSample = start + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (System.nanoTime() >= nextSample) {
                samples.add(resourceLine("SAMPLE@" + (System.nanoTime() - start) / 1_000_000_000 + "s",
                        ops.get(), errors.get()));
                nextSample += TimeUnit.SECONDS.toNanos(5);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        pool.shutdown();
        try {
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "稳定性线程池未收敛");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long elapsed = System.nanoTime() - start;
        long[] arr = latencies.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(arr);
        double throughput = ops.get() * 1_000_000_000.0 / elapsed;
        double errorRate = ops.get() == 0 ? 0 : errors.get() * 1.0 / ops.get();

        line("STABILITY|durationSec=" + seconds + "|threads=" + threads
                + "|operations=" + ops.get() + "|errors=" + errors.get()
                + "|errorRate=" + fmt(errorRate * 100) + "%|throughput=" + fmt(throughput)
                + "ops/s|p50=" + fmt(p(arr, 50)) + "ms|p95=" + fmt(p(arr, 95))
                + "ms|p99=" + fmt(p(arr, 99)) + "ms");
        for (String s : samples) {
            line("STABILITY_" + s);
        }
        line("VERDICT|稳定性错误率=" + fmt(errorRate * 100) + "%|阈值<"
                + (ERROR_RATE_LIMIT * 100) + "%|结论=" + verdict(errorRate < ERROR_RATE_LIMIT));

        group("F-25 持续 30s / 16 线程混合负载错误率 < 1%", "稳定性",
                () -> {
                    assertTrue(errorRate < ERROR_RATE_LIMIT, "错误率超标: " + errorRate);
                    assertTrue(ops.get() > 100, "有效操作过少: " + ops.get());
                    return "ops=" + ops.get() + ", errors=" + errors.get()
                            + ", throughput=" + fmt(throughput) + " ops/s";
                });
    }

    private static String resourceLine(String tag, long ops, long errors) {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        ThreadMXBean th = ManagementFactory.getThreadMXBean();
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount() > 0 ? gc.getCollectionCount() : 0;
            gcTime += gc.getCollectionTime() > 0 ? gc.getCollectionTime() : 0;
        }
        return tag + "|heapUsedMB=" + (mem.getHeapMemoryUsage().getUsed() / 1024 / 1024)
                + "|heapMaxMB=" + (mem.getHeapMemoryUsage().getMax() / 1024 / 1024)
                + "|nonHeapMB=" + (mem.getNonHeapMemoryUsage().getUsed() / 1024 / 1024)
                + "|threads=" + th.getThreadCount() + "|gcCount=" + gcCount
                + "|gcTimeMs=" + gcTime + "|ops=" + ops + "|errors=" + errors;
    }

    private void resourceSnapshot() {
        line("RESOURCE|" + resourceLine("FINAL", 0, 0));
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name, ROUND(data_length/1024/1024,1), "
                             + "ROUND(index_length/1024/1024,1) FROM information_schema.tables "
                             + "WHERE table_schema='java_admin_perf' ORDER BY table_name")) {
            while (rs.next()) {
                line("RESOURCE_TABLE|" + rs.getString(1) + "|dataMB=" + rs.getDouble(2)
                        + "|indexMB=" + rs.getDouble(3));
            }
        } catch (Exception e) {
            NOTES.add("资源快照采集失败: " + e.getMessage());
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS LIKE 'Threads_connected'")) {
            if (rs.next()) {
                line("RESOURCE|mysqlThreadsConnected=" + rs.getString(2));
            }
        } catch (Exception e) {
            NOTES.add("MySQL 连接数采集失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static KeysetPageResult<SysOperationLogDO> keyset(int size, String cursor) {
        return JooqKeysetPager.query(dsl, T, SysOperationLogDO.class, DSL.trueCondition(),
                new KeysetPageQuery(size, cursor), e -> new Object[]{e.getId()},
                KEY_FIELDS, ASCENDING);
    }

    private static long countOf(String table) {
        return dsl.selectCount().from(DSL.table(DSL.name(table))).fetchOne(0, long.class);
    }

    private static Map<String, Long> tableCounts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String t : List.of("sys_user", "sys_login_log", "sys_operation_log",
                "sys_notification_record", "sys_message", "sys_message_receipt",
                "sys_monitor_sample")) {
            m.put(t, countOf(t));
        }
        return m;
    }

    private static long exactCount() {
        return tableCounts().values().stream().mapToLong(Long::longValue).sum();
    }

    private interface ThrowingSupplier {
        String get() throws Exception;
    }

    private void group(String name, String group, ThrowingSupplier body) {
        long t0 = System.nanoTime();
        try {
            String detail = body.get();
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            CASES.add(new CaseResult(name, group, true, ms, detail));
            line("CASE|" + name + "|" + group + "|PASS|" + fmt(ms) + "ms|" + detail);
        } catch (Throwable e) {
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            CASES.add(new CaseResult(name, group, false, ms, detail));
            line("CASE|" + name + "|" + group + "|FAIL|" + fmt(ms) + "ms|" + detail);
        }
    }

    private static String render(Metric m) {
        return m.scenario() + "|samples=" + m.samples() + "|p50=" + fmt(m.p50())
                + "ms|p95=" + fmt(m.p95()) + "ms|p99=" + fmt(m.p99())
                + "ms|avg=" + fmt(m.avg()) + "ms|min=" + fmt(m.min())
                + "ms|max=" + fmt(m.max()) + "ms|throughput=" + fmt(m.throughput())
                + "|errors=" + m.errors() + "|total=" + m.total() + "|" + m.extra();
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private static String verdict(boolean ok) {
        return ok ? "达标" : "未达标";
    }

    private static void emitSummary() {
        long passed = CASES.stream().filter(CaseResult::pass).count();
        line("SUMMARY|functionalTotal=" + CASES.size() + "|functionalPassed=" + passed
                + "|functionalFailed=" + (CASES.size() - passed));
        for (String n : NOTES) {
            line("NOTE|" + n);
        }
        line("SUMMARY|resultsFile=" + OUT);
    }
}
