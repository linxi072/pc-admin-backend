package com.acme.scaffold.common.messaging.domain;

/**
 * 消息通道分级（对应设计稿 §2.3「Topic 分级设计」）。
 *
 * <p>分级是整套系统吞吐/SLA 取舍的核心：同一业务消息在网关本地<b>双写</b> RT + DURABLE
 * 两个通道（非事务），用约 1.6× 写入量换取两个通道完全独立的 SLA：
 * <ul>
 *   <li>{@link #RT}：实时计算通道，acks=1 换低延迟；</li>
 *   <li>{@link #DURABLE}：持久化/审计通道，acks=all 保数据；</li>
 *   <li>{@link #FANOUT}：跨网关下行扇出，按分片投递；</li>
 *   <li>{@link #DLQ}：死信，重试耗尽或反序列化失败的消息归宿。</li>
 * </ul>
 */
public enum Tier {

    /** 实时计算通道（msg.raw.rt）：acks=1、linger 5ms、保留 6h。 */
    RT("实时计算通道"),

    /** 持久化/审计通道（msg.raw.durable）：acks=all、linger 20ms、保留 72h。 */
    DURABLE("持久化审计通道"),

    /** 跨网关扇出通道（msg.fanout.&lt;shard&gt;）：acks=1、保留 2h。 */
    FANOUT("跨网关扇出通道"),

    /** 死信通道（msg.dlq）：acks=all、保留 7d。 */
    DLQ("死信通道");

    private final String description;

    Tier(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** 是否参与网关入口的 RT+DURABLE 双写（扇出/死信由其他路径产生，不双写）。 */
    public boolean isDualWrittenAtIngest() {
        return this == RT || this == DURABLE;
    }
}
