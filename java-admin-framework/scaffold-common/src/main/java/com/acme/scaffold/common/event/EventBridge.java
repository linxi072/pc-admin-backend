package com.acme.scaffold.common.event;

/**
 * 事件异步桥接端口（ROADMAP R2 · 可选）。
 *
 * <p>将本地领域事件转发到消息中间件（如 Kafka）以实现异步 / 跨进程投递。
 * 与本地同步订阅（{@link DomainEventListener}）正交：本地订阅在同一事务内即时响应，
 * 桥接负责「跨进程最终一致」。</p>
 *
 * <p>缺省实现为 {@link NoOpEventBridge}（无操作）；当应用提供具体实现 Bean（如 KafkaBus 适配器）时，
 * {@link DefaultEventPublisher} 会自动启用桥接。发布方无需感知桥接存在。</p>
 */
public interface EventBridge {

    /**
     * 桥接转发事件（实现须保证自身故障不抛回发布方）。
     *
     * @param event 待转发的领域事件
     */
    void bridge(DomainEvent event);
}
