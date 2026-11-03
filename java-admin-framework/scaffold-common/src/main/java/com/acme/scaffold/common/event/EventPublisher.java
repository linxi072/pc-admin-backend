package com.acme.scaffold.common.event;

import java.util.List;

/**
 * 领域事件发布端口（ROADMAP R2）。
 *
 * <p>默认语义为<b>同步本地发布</b>：事件在当前线程即时送达所有匹配的 {@link DomainEventListener}。
 * 跨进程 / 异步投递由 {@link EventBridge} 负责，与本地订阅解耦，发布方无需感知。</p>
 *
 * <p>实现须保证：发布自身<b>不抛出</b>订阅者异常（由 {@link DefaultEventPublisher} 做错误隔离），
 * 发布方调用 {@code publish} 永不因订阅者故障而中断。</p>
 */
public interface EventPublisher {

    /**
     * 发布单个事件。事件为 null 时静默忽略。
     *
     * @param event 领域事件（非空）
     */
    void publish(DomainEvent event);

    /**
     * 批量发布。逐条调用 {@link #publish}，单条失败不影响其余。
     *
     * @param events 事件集合（可空）
     */
    void publishAll(List<? extends DomainEvent> events);
}
