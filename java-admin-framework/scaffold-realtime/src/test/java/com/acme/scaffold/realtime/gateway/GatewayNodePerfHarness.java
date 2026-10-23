package com.acme.scaffold.realtime.gateway;

import com.acme.scaffold.common.messaging.bus.BusMetrics;
import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.codec.FrameCodec;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.Tier;
import com.acme.scaffold.realtime.gateway.auth.AllowAllAuthenticator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 实时网关核心性能与并发压测 harness（联机测试任务 #620）。
 *
 * <p>定位：{@link GatewayNode} 节点内核的<b>可重复量化基准</b>，覆盖任务要求的
 * 握手 / 上行吞吐、下行投递延迟、广播扇出、心跳 tick、多线程并发 hammer
 * （验证 {@link ConnectionRegistry} 与 {@link Session} 线程安全）与慢消费者分级处置。
 *
 * <p>设计约定：
 * <ul>
 *   <li>本类不是 JUnit 用例（不含 Test 后缀），surefire/failsafe 均不会自动执行，
 *       按需手工运行，避免把与机器相关的耗时基准纳入回归门禁；</li>
 *   <li>总线以 {@link NoopBus} 打桩（仅原子计数），隔离节点内核开销，
 *       避免存储保留成本扭曲吞吐数字；慢消费者分级的真实处置路径不受影响；</li>
 *   <li>每个阶段独立建节点与配置，计数器断言互不干扰；每阶段先预热再计量；</li>
 *   <li>全部阶段内置不变量断言（状态码 / 连接数 / seq 不重不漏 / 路由零误摘），
 *       任一失败即以非零退出码终止，防止「跑出数字但行为已错」的假绿。</li>
 * </ul>
 *
 * <p>运行方式（在 scaffold-realtime 模块目录下，JDK 21）：
 * <pre>
 *   java -cp "target/test-classes:target/classes:../scaffold-common/target/classes" \
 *       com.acme.scaffold.realtime.gateway.GatewayNodePerfHarness
 * </pre>
 * 报告同时打印到标准输出并尽力写入 {@code target/gateway-perf-report.txt}。
 */
public final class GatewayNodePerfHarness {

    private static final long TS = 1_700_000_000_000L;
    private static final byte[] PAYLOAD = "perf-payload-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static final List<String> report = new ArrayList<>();
    private static boolean failed = false;

    private GatewayNodePerfHarness() {
    }

    public static void main(String[] args) {
        printEnv();
        try {
            benchHandshake();
            benchUpstream();
            benchDeliverLatency();
            benchBroadcast();
            benchHeartbeatTick();
            benchConcurrentHammer();
            benchSlowConsumer();
        } catch (Throwable t) {
            failed = true;
            out("[FATAL] 压测中断: " + t);
            t.printStackTrace();
        }
        writeReport();
        out(failed ? "HARNESS RESULT: FAILED" : "HARNESS RESULT: PASSED");
        if (failed) {
            System.exit(1);
        }
    }

    // ================================================================= 阶段 1：握手吞吐

    private static void benchHandshake() {
        int n = 50_000;
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(GatewaySettings.designDefault(), clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();

        for (int i = 0; i < 2_000; i++) { // 预热
            node.onHandshake("warm-" + i, auth, new CountingConnChannel("wc-" + i, "wu-" + i), now);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            GatewayNode.HandshakeOutcome outcome =
                    node.onHandshake("u-" + i, auth, new CountingConnChannel("c-" + i, "u-" + i), now);
            check(outcome.status() == GatewayNode.HandshakeStatus.OK, "握手应成功: " + outcome.status());
        }
        double elapsed = secondsSince(t0);
        // 预热连接不摘除，注册表总数 = 预热 2000 + 计量 50000
        check(node.registry().userCount() == n + 2_000,
                "注册表用户数应为 " + (n + 2_000) + "，实际 " + node.registry().userCount());
        phase("1", "握手吞吐", n, opsPerSec(n, elapsed));
    }

    // ================================================================= 阶段 2：上行吞吐

    private static void benchUpstream() {
        // 设计默认限流（100/s、突发 500）：每批用全新会话消耗满burst，模拟「新连接突发上行」。
        int batch = 500;
        int batches = 200;
        int total = batch * batches;
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(GatewaySettings.designDefault(), clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();
        byte[] frame = FrameCodec.encode(Envelope.upstream("m-up", "k-up", PAYLOAD, TS, "trace-perf"));

        for (int b = 0; b < 2; b++) { // 预热
            ingestBatch(node, auth, "uw-" + b, "cuw-" + b, frame, batch, now);
        }
        long t0 = System.nanoTime();
        for (int b = 0; b < batches; b++) {
            ingestBatch(node, auth, "u-" + b, "c-" + b, frame, batch, now);
        }
        double elapsed = secondsSince(t0);
        // 预热批次连接不摘除，注册表总数 = 预热 2 批 + 计量 200 批
        check(node.registry().userCount() == batches + 2,
                "注册表用户数应为 " + (batches + 2) + "，实际 " + node.registry().userCount());
        phase("2", "上行吞吐（解码+限流+双写）", total, opsPerSec(total, elapsed));
    }

    private static void ingestBatch(GatewayNode node, AllowAllAuthenticator auth,
                                    String user, String connId, byte[] frame, int count, long now) {
        GatewayNode.HandshakeOutcome hs = node.onHandshake(user, auth, new CountingConnChannel(connId, user), now);
        check(hs.status() == GatewayNode.HandshakeStatus.OK, "批处理握手应成功");
        Session session = hs.session();
        for (int i = 0; i < count; i++) {
            GatewayNode.IngestOutcome out = node.onFrame(session, frame, now);
            check(out.status() == GatewayNode.IngestStatus.OK,
                    "上行帧应被接受（突发 " + count + " 内）: " + out.status() + " / " + out.reason());
        }
    }

    // ================================================================= 阶段 3：下行投递延迟

    private static void benchDeliverLatency() {
        int n = 20_000;
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(GatewaySettings.designDefault(), clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();
        GatewayNode.HandshakeOutcome hs =
                node.onHandshake("lat-user", auth, new CountingConnChannel("lat-conn", "lat-user"), now);
        check(hs.status() == GatewayNode.HandshakeStatus.OK, "延迟基准握手应成功");
        Envelope env = Envelope.downstream(0, "m-lat", "k-lat", PAYLOAD, TS, "trace-perf");

        for (int i = 0; i < 2_000; i++) { // 预热
            node.deliver("lat-user", env, false, now);
        }
        long[] latencies = new long[n];
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            long s = System.nanoTime();
            GatewayNode.DeliverOutcome out = node.deliver("lat-user", env, false, now);
            latencies[i] = System.nanoTime() - s;
            check(out.status() == GatewayNode.DeliverStatus.DELIVERED, "投递应成功: " + out.status());
        }
        double elapsed = secondsSince(t0);
        java.util.Arrays.sort(latencies);
        long sum = 0;
        for (long v : latencies) {
            sum += v;
        }
        out("[3] 下行投递延迟  n=" + n
                + "  p50=" + us(percentile(latencies, 50)) + "µs"
                + "  p90=" + us(percentile(latencies, 90)) + "µs"
                + "  p99=" + us(percentile(latencies, 99)) + "µs"
                + "  p999=" + us(percentile(latencies, 99.9)) + "µs"
                + "  max=" + us(latencies[n - 1]) + "µs"
                + "  mean=" + us(sum / n) + "µs"
                + "  吞吐=" + fmt(opsPerSec(n, elapsed)) + " ops/s");
    }

    // ================================================================= 阶段 4：广播扇出

    private static void benchBroadcast() {
        int users = 2_000;
        int rounds = 200;
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(GatewaySettings.designDefault(), clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();
        for (int i = 0; i < users; i++) {
            GatewayNode.HandshakeOutcome hs =
                    node.onHandshake("b-" + i, auth, new CountingConnChannel("bc-" + i, "b-" + i), now);
            check(hs.status() == GatewayNode.HandshakeStatus.OK, "广播基准握手应成功");
        }
        Envelope env = Envelope.downstream(0, "m-bc", "k-bc", PAYLOAD, TS, "trace-perf");

        for (int r = 0; r < 5; r++) { // 预热
            node.broadcast(env, false, now);
        }
        long delivered = 0;
        long t0 = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            List<GatewayNode.DeliverOutcome> outcomes = node.broadcast(env, false, now);
            check(outcomes.size() == users, "广播应覆盖全部 " + users + " 连接，实际 " + outcomes.size());
            for (GatewayNode.DeliverOutcome o : outcomes) {
                check(o.status() == GatewayNode.DeliverStatus.DELIVERED, "广播投递应成功: " + o.status());
                delivered++;
            }
        }
        double elapsed = secondsSince(t0);
        check(delivered == (long) users * rounds, "广播投递总数不匹配: " + delivered);
        phase("4", "广播扇出（" + users + " 连接 × " + rounds + " 轮）", delivered, opsPerSec(delivered, elapsed));
    }

    // ================================================================= 阶段 5：心跳 tick

    private static void benchHeartbeatTick() {
        int users = 2_000;
        int ticks = 100;
        // 定制心跳：ping 30s，空闲/路由 TTL 放大到 1h，隔离「ping 发放吞吐」这一被测目标，
        // 避免设计默认 90s 空闲超时在 100 个 tick（50 分钟模拟时间）中途断开连接。
        HeartbeatPolicy heartbeat = new HeartbeatPolicy(30_000L, 3_600_000L, 3_600_000L);
        GatewaySettings settings = new GatewaySettings(
                200_000, heartbeat, 100.0, 500.0, SlowConsumerPolicy.designDefault(),
                DrainState.DEFAULT_INFLIGHT_TIMEOUT_MS, DrainState.DEFAULT_FLUSH_TIMEOUT_MS,
                4001, 4003, 4028, 4029, 4030, 1001, 4027, 4009);
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(settings, clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();
        for (int i = 0; i < users; i++) {
            GatewayNode.HandshakeOutcome hs =
                    node.onHandshake("h-" + i, auth, new CountingConnChannel("hc-" + i, "h-" + i), now);
            check(hs.status() == GatewayNode.HandshakeStatus.OK, "心跳基准握手应成功");
        }

        long t0 = System.nanoTime();
        long pings = 0;
        long expired = 0;
        long idleClosed = 0;
        for (int t = 0; t < ticks; t++) {
            clock.advanceBy(30_001L); // 越过 ping 周期，保证每个 tick 全部待发
            GatewayNode.HeartbeatTickResult result = node.onTick(clock.nowMillis());
            pings += result.pingsSent().size();
            expired += result.routesExpired().size();
            idleClosed += result.idleClosed().size();
        }
        double elapsed = secondsSince(t0);
        check(pings == (long) users * ticks, "ping 总数应为 " + ((long) users * ticks) + "，实际 " + pings);
        check(expired == 0, "活跃通道路由不应被 TTL 误摘（touch-on-ping 回归），实际误摘 " + expired);
        check(idleClosed == 0, "放大空闲超时后不应有空闲断开，实际 " + idleClosed);
        check(node.registry().size() == users, "tick 后连接数应保持不变，实际 " + node.registry().size());
        phase("5", "心跳 tick（" + ticks + " tick × " + users + " 连接）", pings, opsPerSec(pings, elapsed));
    }

    // ================================================================= 阶段 6：并发 hammer

    private static void benchConcurrentHammer() {
        int pool = 2_000;   // 随机投递寻路的注册表规模
        int threads = Math.min(8, Runtime.getRuntime().availableProcessors());
        int warmupOps = 5_000;
        int opsPerThread = 25_000;

        // 提高限流上限（10M/s）：hammer 目标是注册表/会话/编解码的并发安全与竞争吞吐，
        // 单连接限流行为由 RateLimiterTest 与 GatewayNodeTest 覆盖，不在此重复计量。
        GatewaySettings settings = new GatewaySettings(
                200_000, HeartbeatPolicy.designDefault(), 10_000_000.0, 10_000_000.0,
                SlowConsumerPolicy.designDefault(),
                DrainState.DEFAULT_INFLIGHT_TIMEOUT_MS, DrainState.DEFAULT_FLUSH_TIMEOUT_MS,
                4001, 4003, 4028, 4029, 4030, 1001, 4027, 4009);
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(settings, clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();
        byte[] frame = FrameCodec.encode(Envelope.upstream("m-h", "k-h", PAYLOAD, TS, "trace-perf"));
        Envelope env = Envelope.downstream(0, "m-h", "k-h", PAYLOAD, TS, "trace-perf");

        String[] users = new String[pool];
        for (int i = 0; i < pool; i++) {
            users[i] = "p-" + i;
            GatewayNode.HandshakeOutcome hs =
                    node.onHandshake(users[i], auth, new CountingConnChannel("pc-" + i, users[i]), now);
            check(hs.status() == GatewayNode.HandshakeStatus.OK, "hammer 基准握手应成功");
        }
        // 热点会话：全部线程竞争同一连接的 seq 分配与路由刷新
        GatewayNode.HandshakeOutcome hot =
                node.onHandshake("hot", auth, new CountingConnChannel("hot-conn", "hot"), now);
        check(hot.status() == GatewayNode.HandshakeStatus.OK, "热点会话握手应成功");
        int expectedConns = pool + 1 + threads; // 基准 + 热点 + 每线程专属会话

        AtomicLong okOps = new AtomicLong();
        AtomicLong hotDelivers = new AtomicLong();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        runHammer(node, threads, warmupOps, users, "hot", frame, env, now,
                new Session[threads], okOps, hotDelivers, firstError, true);

        okOps.set(0);
        hotDelivers.set(0);
        firstError.set(null);
        Session[] sessions = new Session[threads];
        for (int t = 0; t < threads; t++) {
            GatewayNode.HandshakeOutcome hs = node.onHandshake(
                    "t-" + t, auth, new CountingConnChannel("tc-" + t, "t-" + t), now);
            check(hs.status() == GatewayNode.HandshakeStatus.OK, "hammer 线程会话握手应成功");
            sessions[t] = hs.session();
        }
        check(node.registry().size() == expectedConns, "hammer 前连接数应为 " + expectedConns);

        long t0 = System.nanoTime();
        runHammer(node, threads, opsPerThread, users, "hot", frame, env, now,
                sessions, okOps, hotDelivers, firstError, false);
        double elapsed = secondsSince(t0);

        Throwable error = firstError.get();
        check(error == null, "并发 hammer 出现异常: " + error);
        long total = (long) opsPerThread * threads;
        check(okOps.get() == total, "成功操作数应为 " + total + "，实际 " + okOps.get());
        check(node.registry().size() == expectedConns,
                "hammer 后连接数应保持不变（无幽灵/误摘），实际 " + node.registry().size());
        Session hotSession = node.sessionOf("hot").orElse(null);
        check(hotSession != null, "热点会话应仍在注册表中");
        long expectedNextSeq = 1 + hotDelivers.get();
        check(hotSession.peekNextSeq() == expectedNextSeq,
                "热点会话 seq 应不重不漏：期望 nextSeq=" + expectedNextSeq
                        + "（投递 " + hotDelivers.get() + " 次），实际 " + hotSession.peekNextSeq());
        check(node.totalInflight() == 0, "hammer 后 in-flight 应归零，实际 " + node.totalInflight());
        phase("6", "并发 hammer（" + threads + " 线程 × " + opsPerThread + " 混合读写）",
                total, opsPerSec(total, elapsed));
    }

    private static void runHammer(GatewayNode node, int threads, int ops, String[] users, String hotUser,
                                  byte[] frame, Envelope env, long now, Session[] sessions,
                                  AtomicLong okOps, AtomicLong hotDelivers,
                                  AtomicReference<Throwable> firstError, boolean warmup) {
        ExecutorService poolExecutor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            poolExecutor.execute(() -> {
                try {
                    start.await();
                    java.util.Random rnd = new java.util.Random(idx);
                    Session mySession = sessions[idx];
                    String myUser = "t-" + idx;
                    for (int i = 0; i < ops; i++) {
                        switch (i & 3) {
                            case 0 -> { // 上行帧（自己的会话）
                                GatewayNode.IngestOutcome o = node.onFrame(mySession, frame, now);
                                if (o.status() != GatewayNode.IngestStatus.OK) {
                                    throw new IllegalStateException("hammer 上行应被接受: " + o.status());
                                }
                            }
                            case 1 -> { // 定向投递（自己的用户）
                                GatewayNode.DeliverOutcome o = node.deliver(myUser, env, false, now);
                                if (o.status() != GatewayNode.DeliverStatus.DELIVERED) {
                                    throw new IllegalStateException("hammer 定向投递应成功: " + o.status());
                                }
                            }
                            case 2 -> { // 热点投递（竞争 seq 分配）
                                GatewayNode.DeliverOutcome o = node.deliver(hotUser, env, false, now);
                                if (o.status() != GatewayNode.DeliverStatus.DELIVERED) {
                                    throw new IllegalStateException("hammer 热点投递应成功: " + o.status());
                                }
                                hotDelivers.incrementAndGet();
                            }
                            default -> { // 随机用户投递（注册表寻路竞争）
                                GatewayNode.DeliverOutcome o =
                                        node.deliver(users[rnd.nextInt(users.length)], env, false, now);
                                if (o.status() != GatewayNode.DeliverStatus.DELIVERED) {
                                    throw new IllegalStateException("hammer 随机投递应成功: " + o.status());
                                }
                            }
                        }
                        okOps.incrementAndGet();
                    }
                } catch (Throwable ex) {
                    firstError.compareAndSet(null, ex);
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            start.countDown();
            check(done.await(5, TimeUnit.MINUTES), "hammer 线程未在超时内完成");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hammer 被中断", ex);
        } finally {
            poolExecutor.shutdownNow();
        }
        if (!warmup) {
            out("[6] hammer 线程数=" + threads + "，每线程 " + ops + " 次混合操作");
        }
    }

    // ================================================================= 阶段 7：慢消费者分级

    private static void benchSlowConsumer() {
        int iterations = 1_000;
        MutableClock clock = new MutableClock(TS);
        GatewayNode node = newNode(GatewaySettings.designDefault(), clock);
        AllowAllAuthenticator auth = new AllowAllAuthenticator();
        long now = clock.nowMillis();
        Envelope env = Envelope.downstream(0, "m-slow", "k-slow", PAYLOAD, TS, "trace-perf");
        int connsBefore = node.registry().size();

        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String user = "slow-" + i;
            CountingConnChannel channel = new CountingConnChannel("sc-" + i, user);
            GatewayNode.HandshakeOutcome hs = node.onHandshake(user, auth, channel, now);
            check(hs.status() == GatewayNode.HandshakeStatus.OK, "慢消费者基准握手应成功");
            channel.setInflight(1_500); // > 队列上限 1000，触发慢消费者判定

            // L1：实时类消息 → 丢弃最旧（不断链）
            GatewayNode.DeliverOutcome l1 = node.deliver(user, env, true, now);
            check(l1.status() == GatewayNode.DeliverStatus.DROPPED_OLDEST, "实时类应走 L1 丢最旧: " + l1.status());
            check(channel.droppedCount() == 1, "L1 应标记一次丢最旧，实际 " + channel.droppedCount());
            check(channel.isActive(), "L1 不应断链");

            // L2：关键类消息 → 断链 4029 并清理会话与路由
            GatewayNode.DeliverOutcome l2 = node.deliver(user, env, false, now);
            check(l2.status() == GatewayNode.DeliverStatus.CLOSED_SLOW_CONSUMER, "关键类应走 L2 断链: " + l2.status());
            check(channel.closeCode() == 4029, "L2 关闭码应为 4029，实际 " + channel.closeCode());
            check(!channel.isActive(), "L2 后通道应关闭");
            check(node.registry().size() == connsBefore,
                    "L2 后注册表应回收该连接，实际 " + node.registry().size());
            check(node.sessionOf(user).isEmpty(), "L2 后会话应被摘除");
        }
        double elapsed = secondsSince(t0);
        BackpressureCounter.Snapshot snapshot = node.backpressure();
        check(snapshot.droppedOldest() == iterations, "L1 计数应为 " + iterations + "，实际 " + snapshot.droppedOldest());
        check(snapshot.slowClosed() == iterations, "L2 计数应为 " + iterations + "，实际 " + snapshot.slowClosed());
        phase("7", "慢消费者分级（L1 丢最旧 / L2 断链 4029）", iterations * 2L, opsPerSec(iterations * 2L, elapsed));
    }

    // ================================================================= 基础设施

    /** 打桩总线：仅原子计数，不保留记录——隔离节点内核开销，压测数字不被存储成本扭曲。 */
    static final class NoopBus implements MessageBus {

        private final AtomicLong produced = new AtomicLong();

        @Override
        public ProduceResult produce(Envelope envelope, Tier tier) {
            produced.incrementAndGet();
            return ProduceResult.accepted("noop", 0);
        }

        @Override
        public void flush(long timeoutMs) {
            // 无需 flush
        }

        @Override
        public BusMetrics metrics() {
            return BusMetrics.zero();
        }

        @Override
        public void close() {
            // 无资源
        }

        long produced() {
            return produced.get();
        }
    }

    /** 计数假通道：不保留帧内容（避免海量投递堆积内存），支持注入 in-flight 水位。 */
    static final class CountingConnChannel implements ConnChannel {

        private final String connId;
        private final String userId;
        private final AtomicLong written = new AtomicLong();
        private final AtomicLong dropped = new AtomicLong();
        private volatile int inflightValue;
        private volatile boolean active = true;
        private volatile int closeCode = -1;

        CountingConnChannel(String connId, String userId) {
            this.connId = connId;
            this.userId = userId;
        }

        @Override
        public String connId() {
            return connId;
        }

        @Override
        public String userId() {
            return userId;
        }

        @Override
        public void writeFrame(byte[] frame) {
            written.incrementAndGet();
        }

        @Override
        public void close(int code, String reason) {
            this.closeCode = code;
            this.active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public int inflight() {
            return inflightValue;
        }

        void setInflight(int value) {
            this.inflightValue = value;
        }

        @Override
        public long droppedCount() {
            return dropped.get();
        }

        @Override
        public void markDroppedOldest() {
            dropped.incrementAndGet();
        }

        @Override
        public int closeCode() {
            return closeCode;
        }

        long writtenTotal() {
            return written.get();
        }
    }

    private static GatewayNode newNode(GatewaySettings settings, MutableClock clock) {
        return new GatewayNode(settings, new NoopBus(), clock, "perf-node");
    }

    private static double secondsSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000_000.0;
    }

    private static double opsPerSec(double count, double seconds) {
        return seconds <= 0 ? 0 : count / seconds;
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static String us(long nanos) {
        return String.format("%.1f", nanos / 1000.0);
    }

    private static String fmt(double value) {
        return String.format("%,.0f", value);
    }

    private static void phase(String no, String name, long count, double opsPerSec) {
        out("[" + no + "] " + name + "  n=" + fmt(count) + "  吞吐=" + fmt(opsPerSec) + " ops/s");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failed = true;
            throw new IllegalStateException("不变量校验失败: " + message);
        }
    }

    private static void printEnv() {
        Runtime runtime = Runtime.getRuntime();
        out("================================================================");
        out("实时网关核心性能压测报告（GatewayNodePerfHarness）");
        out("环境: " + System.getProperty("java.version")
                + " / " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + " / " + runtime.availableProcessors() + " 核"
                + " / 最大堆 " + (runtime.maxMemory() >> 20) + "MB");
        out("总线: NoopBus 打桩（隔离节点内核）；时间: MutableClock 手动驱动");
        out("================================================================");
    }

    private static void out(String line) {
        System.out.println(line);
        report.add(line);
    }

    private static void writeReport() {
        try {
            Path path = Paths.get("target", "gateway-perf-report.txt");
            Files.createDirectories(path.getParent());
            Files.write(path, String.join(System.lineSeparator(), report).getBytes(StandardCharsets.UTF_8));
            System.out.println("报告已写入: " + path.toAbsolutePath());
        } catch (IOException ex) {
            System.out.println("报告写入失败（不影响退出码语义）: " + ex.getMessage());
        }
    }
}
