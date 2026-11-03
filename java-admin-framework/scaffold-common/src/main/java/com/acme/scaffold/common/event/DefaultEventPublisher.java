package com.acme.scaffold.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认事件发布实现（ROADMAP R2）。
 *
 * <p><b>注册</b>：Spring 环境下一经声明为 {@code @Component}，构造期即收集容器内所有
 * {@link DomainEventListener} Bean，无需手动登记。</p>
 *
 * <p><b>分派</b>：依据订阅者声明的 {@link DomainEventListener#eventType()} 做类型过滤，仅向匹配订阅者投递。</p>
 *
 * <p><b>错误隔离</b>：任一订阅者抛出异常均被捕获并记录，<b>不会</b>中断发布方、也不会影响其他订阅者——
 * 这是「发布即忘记」语义与系统韧性的关键保证（一个下游故障不应拖垮上游写操作）。</p>
 *
 * <p><b>可选桥接</b>：若存在 {@link EventBridge} Bean（如 Kafka 实现），则同步桥接转发用于异步 / 跨进程投递；
 * 缺省为 {@link NoOpEventBridge}（无操作）。桥接失败同样被隔离。</p>
 */
@Slf4j
@Component
public class DefaultEventPublisher implements EventPublisher {

    private final List<DomainEventListener<?>> listeners;
    private final EventBridge bridge;

    public DefaultEventPublisher(List<DomainEventListener<?>> listeners,
                                ObjectProvider<EventBridge> bridgeProvider) {
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        this.bridge = bridgeProvider.getIfAvailable(NoOpEventBridge::new);
    }

    /**
     * 运行时注册订阅者（非 Spring 环境或动态扩展场景；Spring 环境优先用构造期自动收集）。
     *
     * @param listener 订阅者
     */
    public void register(DomainEventListener<?> listener) {
        listeners.add(listener);
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        for (DomainEventListener<?> listener : listeners) {
            if (!listener.eventType().isInstance(event)) {
                continue;
            }
            try {
                dispatch(listener, event);
            } catch (Exception ex) {
                // 错误隔离：单个订阅者失败不影响发布方与其他订阅者
                log.error("领域事件订阅者处理失败 eventType={} eventId={} listener={}",
                        event.eventType(), event.getEventId(), listener.getClass().getName(), ex);
            }
        }
        try {
            bridge.bridge(event);
        } catch (Exception ex) {
            log.error("领域事件异步桥接失败 eventType={} eventId={}",
                    event.eventType(), event.getEventId(), ex);
        }
    }

    @Override
    public void publishAll(List<? extends DomainEvent> events) {
        if (events == null) {
            return;
        }
        for (DomainEvent event : events) {
            publish(event);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dispatch(DomainEventListener listener, DomainEvent event) {
        listener.onEvent(event);
    }
}
