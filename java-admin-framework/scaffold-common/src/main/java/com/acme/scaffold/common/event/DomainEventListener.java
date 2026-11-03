package com.acme.scaffold.common.event;

/**
 * 领域事件订阅者（ROADMAP R2）。
 *
 * <p>Typed 订阅：实现类声明其关心的事件类型 {@link #eventType()}，总线仅将匹配事件分派给该订阅者。
 * 发布方与订阅方经事件契约解耦，互不持有对方引用。</p>
 *
 * <p>典型用法（Spring 环境中声明为 {@code @Component} 即可被 {@link DefaultEventPublisher} 自动收集）：
 * <pre>{@code
 * @Component
 * public class UserDisabledSessionRevoker implements DomainEventListener<UserDisabledEvent> {
 *     public Class<UserDisabledEvent> eventType() { return UserDisabledEvent.class; }
 *     public void onEvent(UserDisabledEvent e) { ... }
 * }
 * }</pre>
 *
 * @param <T> 该订阅者处理的事件类型
 */
public interface DomainEventListener<T extends DomainEvent> {

    /**
     * 该订阅者订阅的事件类型。总线据此过滤分派。
     *
     * @return 事件类型 Class
     */
    Class<T> eventType();

    /**
     * 处理匹配的事件。实现须自行处理异常，避免向外抛出（总线已做隔离，但良好的订阅者应自愈）。
     *
     * @param event 已匹配的事件实例
     */
    void onEvent(T event);
}
