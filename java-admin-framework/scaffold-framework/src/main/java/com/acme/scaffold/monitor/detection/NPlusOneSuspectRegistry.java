package com.acme.scaffold.monitor.detection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * N+1 疑似事件留存：有界内存环形缓冲，承接 {@link NPlusOneListener} 在阈值越限时登记的事件，
 * 使「只在日志里出现过一次的 N+1 告警」变为<b>可查询、可复盘</b>的运行态数据。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>有界</b>：容量默认 200 条，超出后按入队序淘汰最旧记录，内存占用恒定，
 *       不受请求量影响（区别于 {@link NPlusOneProbe} 的线程局部计数，后者随请求结束即清理）；</li>
 *   <li><b>只增旁路</b>：登记失败/容量为 0 等异常输入均静默忽略，绝不向 jOOQ 执行链路抛异常；</li>
 *   <li><b>倒序检索</b>：{@link #recent(int)} 返回最新在前的事件列表，符合运维「先看最近」的习惯；</li>
 *   <li><b>累计计数</b>：{@link #total()} 记录自进程启动以来的越限总次数，{@link #clear()} 不清零
 *       （清空仅针对留存列表，累计口径保持单调，便于评估告警频率）。</li>
 * </ul>
 * </p>
 *
 * <p>线程安全：所有读写经同一监视器锁串行化；事件生产速率低（每请求每指纹至多 1 次越限），
 * 锁竞争可忽略。本类无 Spring 依赖，便于离线单测。</p>
 */
public class NPlusOneSuspectRegistry {

    /** 默认留存容量（条）。 */
    public static final int DEFAULT_CAPACITY = 200;

    /** 样本 SQL 最大保留长度，超出截断（防止超长 SQL 撑爆内存）。 */
    public static final int MAX_SAMPLE_SQL_LENGTH = 500;

    private final int capacity;
    private final Deque<NPlusOneSuspect> buffer = new ArrayDeque<>();
    private final Object lock = new Object();
    private final LongAdder totalBreaches = new LongAdder();
    private final AtomicLong lastBreachAt = new AtomicLong(0L);

    public NPlusOneSuspectRegistry() {
        this(DEFAULT_CAPACITY);
    }

    public NPlusOneSuspectRegistry(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * 登记一条 N+1 疑似事件。指纹为空/空白时静默忽略；样本 SQL 超长截断；
     * 计数小于 1 时按 1 计。任何输入下本方法都不抛异常。
     *
     * @param fingerprint 同构指纹（{@link NPlusOneProbe#normalize(String)} 的结果）
     * @param sampleSql   触发时的原始 SQL 样本（可为 null）
     * @param count       触发时的执行次数
     */
    public void record(String fingerprint, String sampleSql, int count) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        NPlusOneSuspect suspect = new NPlusOneSuspect(
                fingerprint,
                truncate(sampleSql),
                Math.max(1, count),
                Thread.currentThread().getName(),
                System.currentTimeMillis());
        synchronized (lock) {
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
            }
            buffer.addLast(suspect);
        }
        totalBreaches.increment();
        lastBreachAt.set(suspect.timestampMillis());
    }

    /**
     * 查询最近的疑似事件（最新在前）。
     *
     * @param limit 返回条数上限；{@code <= 0} 返回空列表，超过留存总量时返回全部
     * @return 不可变列表（可能为空）
     */
    public List<NPlusOneSuspect> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        synchronized (lock) {
            List<NPlusOneSuspect> result = new ArrayList<>(Math.min(limit, buffer.size()));
            Iterator<NPlusOneSuspect> it = buffer.descendingIterator();
            while (it.hasNext() && result.size() < limit) {
                result.add(it.next());
            }
            return List.copyOf(result);
        }
    }

    /** @return 当前留存条数 */
    public int size() {
        synchronized (lock) {
            return buffer.size();
        }
    }

    /** @return 留存容量 */
    public int capacity() {
        return capacity;
    }

    /** @return 自进程启动以来累计越限次数（单调递增，{@link #clear()} 不清零） */
    public long total() {
        return totalBreaches.sum();
    }

    /** @return 最近一次越限登记时间（epoch 毫秒）；从未越限时为 0 */
    public long lastBreachAt() {
        return lastBreachAt.get();
    }

    /** 清空留存列表（累计计数与最近登记时间保持不变）。 */
    public void clear() {
        synchronized (lock) {
            buffer.clear();
        }
    }

    private static String truncate(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.length() > MAX_SAMPLE_SQL_LENGTH
                ? sql.substring(0, MAX_SAMPLE_SQL_LENGTH) + "...[truncated]"
                : sql;
    }
}
