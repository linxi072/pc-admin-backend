package com.acme.scaffold.realtime.gateway;

/**
 * 慢消费者三级处置（对应设计稿 §3.5）。
 *
 * <p>判定为纯函数：输入（本连接队列深度、是否实时类消息），输出处置级别。
 * 策略与执行分离，便于单测覆盖全部分支。
 */
public final class SlowConsumerPolicy {

    /**
     * 处置级别（§3.5 表）。
     */
    public enum SlowLevel {
        /** 正常。 */
        NONE,
        /** L1：实时类消息丢弃最旧 + dropped 计数。 */
        L1_DROP_OLDEST,
        /** L2：关键类消息断链（4029），客户端重连带 last_seq 补洞。 */
        L2_CLOSE_4029,
        /** L3：节点级熔断，拒绝新慢连接。 */
        L3_CIRCUIT_BREAKER
    }

    /** 单连接发送队列深度上限（队列 > 1000 条触发 L1/L2）。 */
    public static final int DEFAULT_QUEUE_LIMIT = 1000;

    /** 节点级发送队列高水位比例（> 70% 触发 L3）。 */
    public static final double DEFAULT_NODE_HIGH_WATER = 0.70;

    private final int queueLimit;
    private final double nodeHighWater;

    public SlowConsumerPolicy(int queueLimit, double nodeHighWater) {
        if (queueLimit <= 0) {
            throw new IllegalArgumentException("队列上限必须为正: " + queueLimit);
        }
        if (nodeHighWater <= 0 || nodeHighWater > 1) {
            throw new IllegalArgumentException("节点高水位必须在 (0,1]: " + nodeHighWater);
        }
        this.queueLimit = queueLimit;
        this.nodeHighWater = nodeHighWater;
    }

    public static SlowConsumerPolicy designDefault() {
        return new SlowConsumerPolicy(DEFAULT_QUEUE_LIMIT, DEFAULT_NODE_HIGH_WATER);
    }

    /**
     * 单连接处置判定。
     *
     * @param connQueueDepth 本连接待发送队列深度
     * @param realtime       是否实时类消息（遥测/行情 → 可丢最旧）
     */
    public SlowLevel classify(int connQueueDepth, boolean realtime) {
        if (connQueueDepth <= queueLimit) {
            return SlowLevel.NONE;
        }
        return realtime ? SlowLevel.L1_DROP_OLDEST : SlowLevel.L2_CLOSE_4029;
    }

    /**
     * 节点级熔断判定（L3：总水位 > 70% 时拒绝新连接，保已有连接）。
     *
     * @param nodeQueueRatio 节点发送队列总水位（0..1）
     */
    public boolean shouldCircuitBreak(double nodeQueueRatio) {
        return nodeQueueRatio > nodeHighWater;
    }

    public int queueLimit() {
        return queueLimit;
    }

    public double nodeHighWater() {
        return nodeHighWater;
    }
}
