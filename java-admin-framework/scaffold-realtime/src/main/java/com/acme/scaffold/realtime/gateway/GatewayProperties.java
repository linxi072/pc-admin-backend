package com.acme.scaffold.realtime.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实时网关节点配置属性（前缀 {@code realtime.gateway}）。
 *
 * <p>全部参数映射为 {@link GatewaySettings}（不可变记录）；默认值与设计稿一致，
 * 运维可按节点规格（连接上限、心跳、慢消费者、排空、关闭码）覆盖。
 */
@ConfigurationProperties(prefix = "realtime.gateway")
public class GatewayProperties {

    /** 是否启用实时网关节点（默认 true）。 */
    private boolean enabled = true;

    /** 单节点最大连接数（§3.7）。 */
    private int maxConnectionsPerNode = 200_000;

    /** 单连接稳态速率（令牌/秒，§3.2）。 */
    private double ratePerConnPerSec = 100.0;

    /** 单连接突发容量（令牌桶深度，§3.2）。 */
    private double burstPerConn = 500.0;

    private Heartbeat heartbeat = new Heartbeat();
    private SlowConsumer slowConsumer = new SlowConsumer();
    private Drain drain = new Drain();

    /** 各关闭码（与原 ws-kafka-gateway 保持一致的 WebSocket 关闭语义）。 */
    private int closeCodeAuthFailed = 4001;
    private int closeCodeProtocol = 4003;
    private int closeCodeRateLimited = 4028;
    private int closeCodeSlowConsumer = 4029;
    private int closeCodeIdleTimeout = 4030;
    private int closeCodeDraining = 1001;
    private int closeCodeCapacity = 4027;
    private int closeCodeSessionReplaced = 4009;

    /** 节点 ID（跨节点单播寻址；默认取本机主机名）。 */
    private String gatewayId;

    /** HMAC 短期令牌鉴权（可选，替代默认 JWT 解析路径）。 */
    private Hmac hmac = new Hmac();

    public static class Heartbeat {
        private long pingIntervalMs = 30_000L;
        private long idleTimeoutMs = 90_000L;
        private long routeTtlMs = 90_000L;

        public long getPingIntervalMs() {
            return pingIntervalMs;
        }

        public void setPingIntervalMs(long v) {
            this.pingIntervalMs = v;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long v) {
            this.idleTimeoutMs = v;
        }

        public long getRouteTtlMs() {
            return routeTtlMs;
        }

        public void setRouteTtlMs(long v) {
            this.routeTtlMs = v;
        }
    }

    public static class SlowConsumer {
        private int queueLimit = 1000;
        private double nodeHighWater = 0.70;

        public int getQueueLimit() {
            return queueLimit;
        }

        public void setQueueLimit(int v) {
            this.queueLimit = v;
        }

        public double getNodeHighWater() {
            return nodeHighWater;
        }

        public void setNodeHighWater(double v) {
            this.nodeHighWater = v;
        }
    }

    public static class Drain {
        private long inflightTimeoutMs = 30_000L;
        private long flushTimeoutMs = 5_000L;

        public long getInflightTimeoutMs() {
            return inflightTimeoutMs;
        }

        public void setInflightTimeoutMs(long v) {
            this.inflightTimeoutMs = v;
        }

        public long getFlushTimeoutMs() {
            return flushTimeoutMs;
        }

        public void setFlushTimeoutMs(long v) {
            this.flushTimeoutMs = v;
        }
    }

    public static class Hmac {
        /** 是否启用 HMAC 短期令牌鉴权（启用后替代默认 JWT 解析路径）。 */
        private boolean enabled = false;
        /** HMAC 签名密钥（生产须外部注入，禁止默认值）。 */
        private String secret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String v) {
            this.secret = v;
        }
    }

    // ---- getters / setters ----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
    }

    public int getMaxConnectionsPerNode() {
        return maxConnectionsPerNode;
    }

    public void setMaxConnectionsPerNode(int v) {
        this.maxConnectionsPerNode = v;
    }

    public double getRatePerConnPerSec() {
        return ratePerConnPerSec;
    }

    public void setRatePerConnPerSec(double v) {
        this.ratePerConnPerSec = v;
    }

    public double getBurstPerConn() {
        return burstPerConn;
    }

    public void setBurstPerConn(double v) {
        this.burstPerConn = v;
    }

    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Heartbeat v) {
        this.heartbeat = v;
    }

    public SlowConsumer getSlowConsumer() {
        return slowConsumer;
    }

    public void setSlowConsumer(SlowConsumer v) {
        this.slowConsumer = v;
    }

    public Drain getDrain() {
        return drain;
    }

    public void setDrain(Drain v) {
        this.drain = v;
    }

    public int getCloseCodeAuthFailed() {
        return closeCodeAuthFailed;
    }

    public void setCloseCodeAuthFailed(int v) {
        this.closeCodeAuthFailed = v;
    }

    public int getCloseCodeProtocol() {
        return closeCodeProtocol;
    }

    public void setCloseCodeProtocol(int v) {
        this.closeCodeProtocol = v;
    }

    public int getCloseCodeRateLimited() {
        return closeCodeRateLimited;
    }

    public void setCloseCodeRateLimited(int v) {
        this.closeCodeRateLimited = v;
    }

    public int getCloseCodeSlowConsumer() {
        return closeCodeSlowConsumer;
    }

    public void setCloseCodeSlowConsumer(int v) {
        this.closeCodeSlowConsumer = v;
    }

    public int getCloseCodeIdleTimeout() {
        return closeCodeIdleTimeout;
    }

    public void setCloseCodeIdleTimeout(int v) {
        this.closeCodeIdleTimeout = v;
    }

    public int getCloseCodeDraining() {
        return closeCodeDraining;
    }

    public void setCloseCodeDraining(int v) {
        this.closeCodeDraining = v;
    }

    public int getCloseCodeCapacity() {
        return closeCodeCapacity;
    }

    public void setCloseCodeCapacity(int v) {
        this.closeCodeCapacity = v;
    }

    public int getCloseCodeSessionReplaced() {
        return closeCodeSessionReplaced;
    }

    public void setCloseCodeSessionReplaced(int v) {
        this.closeCodeSessionReplaced = v;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String v) {
        this.gatewayId = v;
    }

    public Hmac getHmac() {
        return hmac;
    }

    public void setHmac(Hmac v) {
        this.hmac = v;
    }

    /** 解析为不可变 {@link GatewaySettings}。 */
    public GatewaySettings toGatewaySettings(String resolvedGatewayId) {
        return new GatewaySettings(
                maxConnectionsPerNode,
                new HeartbeatPolicy(heartbeat.pingIntervalMs, heartbeat.idleTimeoutMs, heartbeat.routeTtlMs),
                ratePerConnPerSec,
                burstPerConn,
                new SlowConsumerPolicy(slowConsumer.queueLimit, slowConsumer.nodeHighWater),
                drain.inflightTimeoutMs,
                drain.flushTimeoutMs,
                closeCodeAuthFailed,
                closeCodeProtocol,
                closeCodeRateLimited,
                closeCodeSlowConsumer,
                closeCodeIdleTimeout,
                closeCodeDraining,
                closeCodeCapacity,
                closeCodeSessionReplaced);
    }
}
