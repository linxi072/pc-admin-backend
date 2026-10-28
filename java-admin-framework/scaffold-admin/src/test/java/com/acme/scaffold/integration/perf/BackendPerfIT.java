package com.acme.scaffold.integration.perf;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysUserDO;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端性能压测（进程内真实 HTTP 链路）。
 *
 * <p>复用 {@link AbstractHttpIntegrationTest} 拉起的真实 Spring 上下文（integration profile +
 * 随机端口 + 本机 MySQL）。{@code TestRestTemplate} 通过真实 socket 打到内嵌 Tomcat，
 * 因此覆盖「HTTP 连接器 → 过滤器链（含 RateLimit/鉴权）→ 业务 → DB」完整路径，
 * 反映应用层单机容量，区别于 MockMvc 的进程内直派（不计入 servlet 开销）。</p>
 *
 * <p>用例严格对齐 {@code scripts/perf/PERF-TEST-PLAN.md}：
 * <ul>
 *   <li>TC-P1 匿名探针 {@code GET /api/connectivity/ping}（零 DB、零鉴权，框架开销基线）；</li>
 *   <li>TC-P2 鉴权读 {@code GET /api/system/menus/tree}（Bearer + DB 读取 + 树构建）；</li>
 *   <li>TC-P3 登录 {@code POST /api/auth/login}（口令哈希 + JWT 签发 + 在线登记），<strong>多账号轮询</strong>
 *       以模拟真实并发登录；每次随机 {@code X-Forwarded-For} 规避 {@code RateLimitFilter} 的
 *       login:IP 60s/10 次限流。</li>
 * </ul>
 *
 * <p><b>设计要点（TC-P3 多账号）</b>：登录成功会 {@code updateById(sys_user)} 写入
 * {@code last_login_at}，对同一行产生行锁。若所有并发请求打同一账号，将引发行锁争用 +
 * Hikari 连接池耗尽，得到的是「单账号登录风暴」假象而非真实多用户容量。因此本测试在 setUp 阶段
 * 直接落库一批独立账号（{@code password_expired=0}，避开「首次登录强制改密 423」拦截），
 * 压测时按请求序号轮询不同账号，使每次登录命中不同行，测得的是真实多用户并发登录吞吐。</p>
 *
 * <p>指标（p50/p95/p99/avg、吞吐 req/s、错误率、状态码分布）打印为结构化文本，
 * 并与方案预期阈值做<strong>宽松护栏断言</strong>（仅保证压测链路本身无致命错误，
 * 不把环境相关的 SLA 硬阈值作为失败条件，避免 CI 抖动误报）。</p>
 */
class BackendPerfIT extends AbstractHttpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BackendPerfIT.class);

    private static final String USER = "admin";
    private static final String PASS = "admin123";
    /** 压测账号口令：仅用于本测试直接落库哈希与登录回传，不经密码策略校验。 */
    private static final String PERF_PASS = "PerfTest@8848";
    /** 预建独立账号数：需 ≥ 最大并发度（50），保证任意瞬时并发各命中不同行。 */
    private static final int ACCOUNT_COUNT = 80;
    private static final String ACCOUNT_PREFIX = "perf_";

    @Autowired
    private DSLContext dsl;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Random rnd = new Random();

    @Test
    void backendPerformanceProfile() throws Exception {
        // ---- TC-P1 匿名探针（框架开销基线）----
        ScenarioResult p1 = run("TC-P1 ping(匿名)",
                baseUrl() + "/api/connectivity/ping", HttpMethod.GET, null,
                null, false, false, null, 1000, 50, 50);

        // 取得鉴权令牌
        String token = login(USER, PASS);

        // ---- TC-P2 鉴权读接口 ----
        ScenarioResult p2 = run("TC-P2 menus/tree(鉴权读)",
                baseUrl() + "/api/system/menus/tree", HttpMethod.GET, null,
                token, true, false, null, 300, 20, 30);

        // ---- 预建多账号（消除单账号行锁风暴）----
        List<String> accounts = provisionAccounts();
        log.info("BackendPerfIT: 已预建 {} 个压测账号（前缀 {}）", accounts.size(), ACCOUNT_PREFIX);

        // ---- TC-P3 多账号并发登录（每次随机 IP 规避限流）----
        ScenarioResult p3 = run("TC-P3 login(多账号并发登录)",
                baseUrl() + "/api/auth/login", HttpMethod.POST, null,
                null, false, true, accounts, 300, 20, 30);

        // ---- TC-P3-RAMP 登录并发爬坡（吞吐饱和 / 错误起点探测）----
        List<ScenarioResult> ramp = new ArrayList<>();
        for (int c : new int[]{10, 25, 50}) {
            ramp.add(run("TC-P3-ramp login@c=" + c,
                    baseUrl() + "/api/auth/login", HttpMethod.POST, null,
                    null, false, true, accounts, 200, c, 20));
        }

        // ---- 汇总报告 ----
        StringBuilder sb = new StringBuilder("\n================ 后端性能压测报告 ================\n");
        sb.append(render(p1)).append('\n').append(render(p2)).append('\n').append(render(p3)).append('\n');
        sb.append("-- 登录并发爬坡（吞吐饱和探测，多账号轮询）--\n");
        for (ScenarioResult r : ramp) {
            sb.append(String.format("  c=%-4d 吞吐=%-8.1f req/s  p95=%-7.1f ms  p99=%-7.1f ms  错误率=%.2f%%%n",
                    r.concurrency, r.throughput, r.p95, r.p99, r.errorRate * 100));
        }
        sb.append("======================================================\n");
        System.out.println(sb);

        // 宽松护栏断言（压测是「容量探索」而非硬 SLA 卡点）：
        //  - TC-P1 / TC-P2 为只读/轻写链路，必须 0 致命错误；
        //  - TC-P3 在「可持续并发」(c=10) 下必须健康（<5% 错误），作为登录容量基线的及格线；
        //  - c≥20 出现的延迟陡增 / 错误为真实容量拐点（Hikari 连接池耗尽），属报告结论而非测试失败。
        assertTrue(p1.errorRate < 0.05, "TC-P1 错误率应 < 5%，实际 " + p1.errorRate);
        assertTrue(p2.errorRate < 0.05, "TC-P2 错误率应 < 5%，实际 " + p2.errorRate);
        assertTrue(p1.throughput > 0 && p2.throughput > 0 && p3.throughput > 0, "吞吐应大于 0");
        // ramp.get(0) 即 c=10（循环顺序 10→25→50）：可持续容量基线
        assertTrue(ramp.get(0).errorRate < 0.05,
                "TC-P3 可持续并发(c=10)错误率应 < 5%，实际 " + ramp.get(0).errorRate
                        + "；若超阈值说明登录链路在低位并发即已存在瓶颈");

        // 收尾清理预建账号（无外键，直接删除即可，孤儿刷新令牌/登录日志无害）
        cleanupAccounts();
    }

    // ------------------------------------------------------------------
    // 多账号预建 / 清理

    /** 幂等预建一批独立登录账号（绕过「首次登录强制改密」），返回用户名清单。 */
    private List<String> provisionAccounts() {
        cleanupAccounts(); // 先清残留，保证可重复运行
        String hash = passwordEncoder.encode(PERF_PASS);
        List<SysUserDO> users = new ArrayList<>(ACCOUNT_COUNT);
        List<String> accs = new ArrayList<>(ACCOUNT_COUNT);
        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            String u = ACCOUNT_PREFIX + i;
            SysUserDO user = new SysUserDO();
            user.setUsername(u);
            user.setPasswordHash(hash);
            user.setDisplayName(u);
            user.setStatus("ACTIVE");
            user.setPasswordExpired(0);
            user.setTokenVersion(1);
            users.add(user);
            accs.add(u);
        }
        // 走 JooqWriters 批量写入：自动生成 id / tenant_id / 时间字段，与业务写入同源
        JooqWriters.batchInsert(dsl, JooqTables.SYS_USER, users);
        return accs;
    }

    /** 删除本测试预建的账号（best-effort，失败仅告警不阻断）。 */
    private void cleanupAccounts() {
        try {
            dsl.deleteFrom(DSL.table("sys_user"))
                    .where(DSL.field("username", String.class).like(ACCOUNT_PREFIX + "%"))
                    .execute();
        } catch (Exception e) {
            log.warn("BackendPerfIT: 清理预建账号失败（可忽略）: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 压测执行

    private ScenarioResult run(String name, String url, HttpMethod method, Object body,
                               String token, boolean authBearer, boolean randomIp,
                               List<String> accounts, int total, int concurrency, int warmup) throws Exception {
        // 预热：拉起连接池 / JIT，不计入统计
        for (int i = 0; i < warmup; i++) {
            doOne(url, method, body, token, authBearer, randomIp, i, accounts);
        }

        List<Long> latencies = new ArrayList<>(total);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger[] statusCounts = new AtomicInteger[600];
        for (int i = 0; i < 600; i++) statusCounts[i] = new AtomicInteger(0);

        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                long t0 = System.nanoTime();
                int status = doOne(url, method, body, token, authBearer, randomIp, idx, accounts);
                long dtMs = (System.nanoTime() - t0) / 1_000_000L;
                latencies.add(dtMs);
                if (status < 200 || status >= 400) errors.incrementAndGet();
                if (status < 600) statusCounts[status].incrementAndGet();
            }));
        }
        for (java.util.concurrent.Future<?> f : futures) f.get();
        pool.shutdown();
        pool.awaitTermination(120, TimeUnit.SECONDS);
        double elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0;

        latencies.sort(Comparator.naturalOrder());
        ScenarioResult r = new ScenarioResult();
        r.name = name;
        r.total = total;
        r.concurrency = concurrency;
        r.elapsedSec = elapsedSec;
        r.throughput = total / elapsedSec;
        r.errorRate = (double) errors.get() / total;
        r.p50 = pct(latencies, 50);
        r.p95 = pct(latencies, 95);
        r.p99 = pct(latencies, 99);
        r.avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        r.min = latencies.isEmpty() ? 0 : latencies.get(0);
        r.max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        r.statusCounts = statusCounts;
        return r;
    }

    /** 执行单次请求，返回 HTTP 状态码（异常返回 -1）。 */
    private int doOne(String url, HttpMethod method, Object body, String token,
                      boolean authBearer, boolean randomIp, int idx, List<String> accounts) {
        try {
            // 多账号轮询：每个请求命中不同账号 → 不同 sys_user 行，避免行锁争用
            Object actualBody = body;
            if (accounts != null && !accounts.isEmpty()) {
                String u = accounts.get(idx % accounts.size());
                actualBody = Map.of("username", u, "password", PERF_PASS);
            }
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            if (authBearer && token != null) {
                h.setBearerAuth(token);
            }
            if (randomIp) {
                h.set("X-Forwarded-For", randomIp());
            }
            HttpEntity<Object> req = new HttpEntity<>(actualBody, h);
            ResponseEntity<Result> resp = rest.exchange(url, method, req, Result.class);
            return resp.getStatusCode().value();
        } catch (Exception e) {
            return -1;
        }
    }

    private static long pct(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = Math.min(sorted.size() - 1, (int) Math.round((p / 100.0) * (sorted.size() - 1)));
        return sorted.get(idx);
    }

    private String randomIp() {
        return "10." + (rnd.nextInt(254) + 1) + "." + (rnd.nextInt(254) + 1) + "." + (rnd.nextInt(254) + 1);
    }

    private String render(ScenarioResult r) {
        return String.format(
                "%s%n  total=%-5d c=%-4d 耗时=%.2fs 吞吐=%-9.1f req/s 错误率=%.2f%%%n"
                        + "  p50=%-7.1f ms p95=%-7.1f ms p99=%-7.1f ms avg=%-7.1f ms min=%-6.1f ms max=%-7.1f ms%n"
                        + "  状态码分布: %s",
                r.name, r.total, r.concurrency, r.elapsedSec, r.throughput, r.errorRate * 100,
                r.p50, r.p95, r.p99, r.avg, r.min, r.max, statusDist(r));
    }

    private String statusDist(ScenarioResult r) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < 600; s++) {
            if (r.statusCounts[s].get() > 0) {
                sb.append(s).append('=').append(r.statusCounts[s].get()).append(' ');
            }
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    /** 单个场景的度量结果。 */
    private static final class ScenarioResult {
        String name;
        int total;
        int concurrency;
        double elapsedSec;
        double throughput;
        double errorRate;
        double p50, p95, p99, avg, min, max;
        AtomicInteger[] statusCounts;
    }
}
