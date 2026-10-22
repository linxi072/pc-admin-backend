package com.acme.scaffold.common.messaging.bus;

import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.Tier;

/**
 * 消息总线端口（设计稿 §2.3 分级投递的抽象）。
 *
 * <p>端口化目的是让「有无 Kafka broker」成为部署细节而非代码细节：
 * <ul>
 *   <li>生产实现：{@code scaffold-kafka} 的 KafkaBus（真实 KafkaProducer，按通道分级参数）；</li>
 *   <li>演示实现：内存环形缓冲，无 broker 可运行可观测；</li>
 *   <li>测试实现：各模块手写 RecordingBus（断言 topic/分区/键值选择）。</li>
 * </ul>
 *
 * <p>双写策略（§2.3）由调用方显式发起：同一条消息分别
 * {@code produce(e, RT)} 与 {@code produce(e, DURABLE)}，非事务，两通道独立 SLA。
 */
public interface MessageBus {

    /**
     * 向指定通道生产一条消息。
     *
     * @param envelope 消息信封（key 决定分区，§4.1）
     * @param tier     目标通道
     * @return 生产结果；总线自身故障时返回 rejected 而非抛出（热路径不因总线故障崩溃）
     */
    ProduceResult produce(Envelope envelope, Tier tier);

    /** 同步刷盘（优雅退出第 6 步，§3.6；超时后强制退出）。 */
    void flush(long timeoutMs);

    /** 指标快照（§9）。 */
    BusMetrics metrics();

    /** 关闭总线（幂等）。 */
    void close();
}
