package com.acme.scaffold.common;

/**
 * 轻量雪花 ID 生成器，替代 MyBatis-Plus 的 ASSIGN_ID。
 * 单机/小集群场景足够；若需更强一致性可替换为号段或 Leaf。
 */
public final class IdGenerator {

    private static final long EPOCH = 1700000000000L; // 2023-11-14，自定义基准时间
    private static final long WORKER_ID_BITS = 10L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static long workerId = 1L;
    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private IdGenerator() {
    }

    public static void setWorkerId(long id) {
        if (id < 0 || id > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 超出范围 [0, " + MAX_WORKER_ID + "]");
        }
        workerId = id;
    }

    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & (~(-1L << SEQUENCE_BITS));
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private static long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
