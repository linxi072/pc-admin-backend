package com.acme.scaffold.common.event;

import org.springframework.stereotype.Component;

/**
 * 事件桥接的缺省无操作实现（ROADMAP R2）。
 *
 * <p>未配置消息中间件时兜底：本地同步订阅仍生效，仅不产生异步跨进程投递。
 * 由 {@link DefaultEventPublisher} 在容器中无 {@link EventBridge} Bean 时自动选用。</p>
 */
@Component
public class NoOpEventBridge implements EventBridge {

    @Override
    public void bridge(DomainEvent event) {
        // 默认不桥接：保持纯本地事件语义
    }
}
