package com.acme.scaffold.integration.perf;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 千万级数据下的 L4 全链路 HTTP 压测（补齐 {@code QA-TEST-REPORT-TEN-MILLION-V1.1.0.md} §8.6 覆盖局限）。
 *
 * <p>复用 {@link AbstractHttpIntegrationTest} 的真实 Spring 上下文（integration profile +
 * 随机端口 + 本机 MySQL），{@code TestRestTemplate} 经真实 socket 打到内嵌 Tomcat，覆盖
 * 「HTTP 连接器 → 过滤器链（含鉴权/审计）→ 业务 → 千万级 DB」完整路径。场景特意选取
 * 会真实触达 1280 万行 {@code sys_operation_log} 的端点，而非只打 ping/menus/login 这些
 * 与大表无关的接口。</p>
 *
 * <p><b>全量时间窗口</b>：{@code /error-logs} 与 {@code /error-summary} 默认只查「最近 24 小时
 * 且 success=0」，而基准数据集的 occurred_at 集中在 2025 年，默认窗口命中 0 行会让深页
 * OFFSET 与全表 GROUP BY 退化为空转。因此这两个端点显式传
 * {@code from=1970-01-01T00:00:00&to=2099-12-31T23:59:59}，使 SQL 真实扫描千万级主表
 * （实测命中 256,000 行 success=0），并在用例前置断言该行数不低于阈值。</p>
 *
 * <p><b>增量落盘</b>：重查询单次可达 20s，长跑若被外部中断，一次性 flush 会丢失全部结果。
 * 因此每个场景完成即追加写 {@code /tmp/ten-million-http-perf-results.txt} 并同步打印日志，
 * 任何时点中断都保留已完成场景的完整指标；场景开始/结束各写一条 SCENARIO 便于定位中断点。</p>
 *
 * <p><b>命名与触发</b>：以 {@code Perf} 结尾，不匹配 surefire 默认 {@code *Test} 与 failsafe
 * 默认 {@code *IT}，因此不进常规 2,062 例门禁、也不进 CI 的 106 个 IT；须显式执行：</p>
 * <pre>
 * TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/java_admin_perf_l4?useSSL=false&amp;serverTimezone=UTC&amp;allowPublicKeyRetrieval=true&amp;characterEncoding=utf8' \
 * TZ=UTC JAVA_HOME=$(/usr/libexec/java_home -v 21) \
 * mvn -o -f pom.xml -pl scaffold-admin -am test \
 *   -Dtest=TenMillionHttpPerf -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>压测是「容量探索」而非硬 SLA 卡点：仅设宽松护栏（框架基线错误率、鉴权接口须有 2xx、
 * 吞吐为正），环境相关的延迟阈值写入报告而非断言，避免 CI 抖动误报。</p>
 */
class TenMillionHttpPerf extends AbstractHttpIntegrationTest {

    private static final String OUT = "/tmp/ten-million-http-perf-results.txt";
    private static final long MIN_MAIN_ROWS = 10_000_000L;
    /** error-logs/error-summary 默认只查最近 24h；显式放大到全量，确保真实扫描千万级主表。 */
    private static final String FULL_RANGE = "from=1970-01-01T00:00:00&to=2099-12-31T23:59:59";
    /** 全量窗口下 success=0 的实测行数下限，用于防止「跑了却测不到大表」的假闭环。 */
    private static final long MIN_ERROR_ROWS = 200_000L;
    private static final String USER = "admin";
    private static final String PASS = "admin123";

    @Autowired
    private DSLContext dsl;

    private record Scenario(String name, String path, boolean auth,
                            int total, int concurrency, int warmup) {
    }

    private static final class Metric {
        String name;
        int total;
        int concurrency;
        double elapsedSec;
        double throughput;
        double errorRate;
        long p50;
        long p95;
        long p99;
        long avg;
        long min;
        long max;
        int ok2xx;
        String statusDist;
    }

    @Test
    void tenMillionHttpProfile() throws Exception {
        reset();
        long rows = dsl.selectCount().from(DSL.table("sys_operation_log")).fetchOne(0, long.class);
        Assumptions.assumeTrue(rows >= MIN_MAIN_ROWS,
                "L4 压测需指向千万级隔离库 java_admin_perf_l4，当前主表行数=" + rows);
        // 硬断言：全量时间窗口下确实存在足量 success=0 数据，防止端点退化成空结果集的假压测
        long errorRows = dsl.selectCount().from(DSL.table("sys_operation_log"))
                .where(DSL.field("success", Integer.class).eq(0))
                .fetchOne(0, long.class);
        assertTrue(errorRows >= MIN_ERROR_ROWS,
                "千万级主表在全量窗口下应命中 >= " + MIN_ERROR_ROWS + " 行错误日志，实际 " + errorRows);

        String token = login(USER, PASS);
        // 重查询单次耗时 3~20s（实测 COUNT 4.9s / 深页 6.3s / 全表 GROUP BY 9.3s，
        // 并发争抢下深页 OFFSET 可达 20s），故总量与并发按「容量探索」收敛到分钟级。
        List<Scenario> scenarios = List.of(
                new Scenario("TC-L4-1 ping(匿名·框架基线)",
                        "/api/connectivity/ping", false, 200, 20, 30),
                new Scenario("TC-L4-2 error-logs 首页(全量窗口·千万级 COUNT+分页)",
                        "/api/system/monitor/error-logs?page=1&size=20&" + FULL_RANGE, true, 8, 4, 1),
                new Scenario("TC-L4-3 error-logs 深页(offset=19980·全量窗口)",
                        "/api/system/monitor/error-logs?page=1000&size=20&" + FULL_RANGE, true, 3, 2, 0),
                new Scenario("TC-L4-4 dashboard overview(全表 COUNT 千万级主表)",
                        "/api/system/dashboard/overview", true, 8, 4, 1),
                new Scenario("TC-L4-5 error-summary 全表 GROUP BY(全量窗口)",
                        "/api/system/monitor/error-summary?" + FULL_RANGE, true, 3, 2, 0));

        // 前置 smoke：确认 ISO 时间窗口参数被 @DateTimeFormat 正确解析、鉴权与序列化链路通畅，
        // 避免整轮压测跑完才发现全是 400/401。
        ResponseEntity<Result> smoke = rest.exchange(
                baseUrl() + scenarios.get(1).path(), HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Result.class);
        assertTrue(smoke.getStatusCode().is2xxSuccessful(),
                "全量时间窗口的 error-logs 请求须返回 2xx，实际 " + smoke.getStatusCode());

        emit("ENV|jdk=" + System.getProperty("java.version")
                + "|os=" + System.getProperty("os.name")
                + "|cpu=" + Runtime.getRuntime().availableProcessors()
                + "|jvmTimezone=" + java.util.TimeZone.getDefault().getID()
                + "|mainTableRows=" + rows
                + "|errorRowsInFullRange=" + errorRows
                + "|timeWindow=1970-01-01..2099-12-31"
                + "|protocol=HTTP/1.1 over loopback Tomcat(RANDOM_PORT)");

        List<Metric> metrics = new ArrayList<>();
        for (Scenario s : scenarios) {
            emit("SCENARIO|name=" + s.name() + "|phase=start|ts=" + Instant.now());
            Metric m = run(s, token);
            metrics.add(m);
            emit("METRIC|" + m.name
                    + "|total=" + m.total + "|c=" + m.concurrency
                    + "|elapsedSec=" + String.format("%.2f", m.elapsedSec)
                    + "|throughput=" + String.format("%.2f", m.throughput)
                    + "|p50=" + m.p50 + "|p95=" + m.p95 + "|p99=" + m.p99
                    + "|avg=" + m.avg + "|min=" + m.min + "|max=" + m.max
                    + "|errorRate=" + String.format("%.2f%%", m.errorRate * 100)
                    + "|2xx=" + m.ok2xx + "|status=" + m.statusDist);
            emit("RESOURCE|after=" + m.name + "|" + resourceLine());
            emit("SCENARIO|name=" + s.name() + "|phase=done|ts=" + Instant.now());
        }
        // error-logs 带 @AuditOperation，压测请求本身会经 saveAsync 向主表写入审计行；
        // 记录增量以区分「被测数据规模」与「压测自身产生的写入」，不把两者混为一谈。
        long rowsAfter = dsl.selectCount().from(DSL.table("sys_operation_log")).fetchOne(0, long.class);
        emit("AUDIT|rowsBefore=" + rows + "|rowsAfter=" + rowsAfter
                + "|auditRowsWritten=" + (rowsAfter - rows));
        emit("SUMMARY|scenarios=" + metrics.size()
                + "|allThroughputPositive=" + metrics.stream().allMatch(m -> m.throughput > 0)
                + "|authScenarios2xx=" + metrics.stream().skip(1).allMatch(m -> m.ok2xx > 0));

        Metric ping = metrics.get(0);
        assertTrue(ping.errorRate < 0.05, "TC-L4-1 框架基线错误率应 < 5%，实际 " + ping.errorRate);
        assertTrue(ping.ok2xx > 0, "TC-L4-1 须有 2xx 响应");
        for (int i = 1; i < metrics.size(); i++) {
            Metric m = metrics.get(i);
            assertTrue(m.ok2xx > 0, m.name + " 须至少有 2xx 响应（排除 401/403/500 全失败）: " + m.statusDist);
            assertTrue(m.throughput > 0, m.name + " 吞吐应大于 0");
        }
        assertEquals(scenarios.size(), metrics.size());
        emit("VERDICT|result=PASS|detail=5 场景均完成，护栏断言全部通过");
    }

    private Metric run(Scenario s, String token) throws Exception {
        // 预热：连接池 / JIT / 语句缓存，不计入统计
        for (int i = 0; i < s.warmup(); i++) {
            doOne(s, token, i);
        }

        // 多线程并发写入，必须用线程安全容器；此前 ArrayList.add 存在数据竞争，
        // 长跑下可能丢样本或触发数组越界，导致 p50/p95 口径失真。
        Queue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger ok2xx = new AtomicInteger();
        AtomicInteger[] statusCounts = new AtomicInteger[600];
        for (int i = 0; i < 600; i++) {
            statusCounts[i] = new AtomicInteger();
        }

        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(s.concurrency());
        List<Future<?>> futures = new ArrayList<>(s.total());
        for (int i = 0; i < s.total(); i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                long t0 = System.nanoTime();
                int status = doOne(s, token, idx);
                latencies.add((System.nanoTime() - t0) / 1_000_000L);
                if (status < 200 || status >= 400) {
                    errors.incrementAndGet();
                } else {
                    ok2xx.incrementAndGet();
                }
                if (status >= 0 && status < 600) {
                    statusCounts[status].incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        pool.awaitTermination(120, TimeUnit.SECONDS);
        double elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0;

        // 所有 future 已结束，此时再快照为可排序 List，避免对并发容器直接 sort。
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Comparator.naturalOrder());
        Metric m = new Metric();
        m.name = s.name();
        m.total = s.total();
        m.concurrency = s.concurrency();
        m.elapsedSec = elapsedSec;
        m.throughput = s.total() / Math.max(elapsedSec, 0.001);
        m.errorRate = (double) errors.get() / s.total();
        m.p50 = pct(sorted, 50);
        m.p95 = pct(sorted, 95);
        m.p99 = pct(sorted, 99);
        m.avg = (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        m.min = sorted.isEmpty() ? 0 : sorted.get(0);
        m.max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);
        m.ok2xx = ok2xx.get();
        StringBuilder dist = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            if (statusCounts[i].get() > 0) {
                dist.append(i).append('x').append(statusCounts[i].get()).append(' ');
            }
        }
        m.statusDist = dist.toString().trim();
        return m;
    }

    /** 单次真实 HTTP 请求；异常记为 -1（连接失败 / 超时）。 */
    private int doOne(Scenario s, String token, int idx) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            if (s.auth()) {
                h.setBearerAuth(token);
            }
            // 固定但按请求分散的客户端 IP，避免只读接口被限流误伤
            h.set("X-Forwarded-For", "10.88." + ((idx / 250) % 250) + "." + (idx % 250));
            ResponseEntity<Result> resp = rest.exchange(
                    baseUrl() + s.path(), HttpMethod.GET, new HttpEntity<>(h), Result.class);
            return resp.getStatusCode().value();
        } catch (Exception e) {
            return -1;
        }
    }

    private static long pct(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = Math.min(sorted.size() - 1, (int) Math.round((p / 100.0) * (sorted.size() - 1)));
        return sorted.get(idx);
    }

    private static String resourceLine() {
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024 / 1024;
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        return "heapUsedMB=" + heap + "|threads=" + threads;
    }

    /** 每轮开始清空结果文件，避免与上次运行残留混淆。 */
    private static void reset() throws Exception {
        Files.writeString(Path.of(OUT), "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 增量落盘：每次调用立即追加并关闭文件句柄，同时同步打印到 Maven 日志。
     * 长跑被外部中断时，已完成场景的指标不会丢失。
     */
    private static void emit(String text) {
        System.out.println(text);
        System.out.flush();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                Path.of(OUT), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            out.println(text);
        } catch (Exception e) {
            throw new IllegalStateException("L4 结果增量落盘失败: " + OUT, e);
        }
    }
}
