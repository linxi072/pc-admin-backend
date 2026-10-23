package com.acme.scaffold.realtime.gateway;

/**
 * 网关节点配置（对应设计稿 §3.2/§3.3/§3.5/§3.6/§3.7 的节点级参数汇总）。
 *
 * <p>全部参数集中在单一不可变记录中：装配层（{@code GatewayNodeConfig}）从属性映射，
 * 测试层按需定制，杜绝散落的魔法数。
 */
public record GatewaySettings(
        int maxConnectionsPerNode,
        HeartbeatPolicy heartbeat,
        double ratePerConnPerSec,
        double burstPerConn,
        SlowConsumerPolicy slowConsumer,
        long drainInflightTimeoutMs,
        long drainFlushTimeoutMs,
        int closeCodeAuthFailed,
        int closeCodeProtocol,
        int closeCodeRateLimited,
        int closeCodeSlowConsumer,
        int closeCodeIdleTimeout,
        int closeCodeDraining,
        int closeCodeCapacity,
        int closeCodeSessionReplaced) {

    /** 设计稿默认值：20 万连接/节点（§3.7）、100/s 突发 500（§3.2）等。 */
    public static GatewaySettings designDefault() {
        return new GatewaySettings(
                200_000,
                HeartbeatPolicy.designDefault(),
                100.0,
                500.0,
                SlowConsumerPolicy.designDefault(),
                DrainState.DEFAULT_INFLIGHT_TIMEOUT_MS,
                DrainState.DEFAULT_FLUSH_TIMEOUT_MS,
                4001, // 鉴权失败
                4003, // 协议错误
                4028, // 限流（§3.2）
                4029, // 慢消费者断链（§3.5）
                4030, // 空闲超时
                1001, // 优雅退出（§3.6）
                4027, // 节点容量耗尽
                4009  // 同节点重连，旧会话被顶替
        );
    }

    public GatewaySettings {
        if (maxConnectionsPerNode <= 0) {
            throw new IllegalArgumentException("单节点连接上限必须为正: " + maxConnectionsPerNode);
        }
        if (ratePerConnPerSec <= 0 || burstPerConn <= 0) {
            throw new IllegalArgumentException("限流参数必须为正");
        }
        if (drainInflightTimeoutMs <= 0 || drainFlushTimeoutMs <= 0) {
            throw new IllegalArgumentException("排空超时必须为正");
        }
    }
}
