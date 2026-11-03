package com.acme.scaffold.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类（ROADMAP R2 · 领域事件总线）。
 *
 * <p>跨域副作用的载体：当某模块的写操作引发「其他模块的后续动作」（如用户被禁用 → 踢下线 / 发通知 /
 * 清缓存）时，发布方只负责发出事件，订阅方自行响应，从而避免模块间 Service 直连与隐式耦合。</p>
 *
 * <p><b>语义约定</b>：
 * <ul>
 *   <li>默认<b>同步</b>发布（发布即在当前线程送达所有匹配的订阅者），与本地事务同生命周期，便于测试与排障；</li>
 *   <li>需要异步 / 跨进程投递时，由 {@link EventBridge} 可选桥接（如经 Kafka），不影响本地订阅语义；</li>
 *   <li>订阅者异常被<b>错误隔离</b>：单个订阅者失败不影响其他订阅者与发布方（见 {@link DefaultEventPublisher}）。</li>
 * </ul>
 */
public abstract class DomainEvent {

    /** 事件唯一标识（幂等 / 去重 / 链路追踪用）。 */
    private final String eventId;

    /** 发生时间（UTC，毫秒精度）。 */
    private final Instant occurredAt;

    /** 租户隔离上下文（可为 null 表示平台级事件）。 */
    private final Long tenantId;

    /** 发布方模块标识（如 "system" / "security"），便于审计与排查。 */
    private final String source;

    protected DomainEvent(Long tenantId, String source) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.tenantId = tenantId;
        this.source = source;
    }

    /**
     * 事件类型名（路由 / 审计 / 幂等键）。默认取简单类名，子类可覆写为稳定的业务常量。
     *
     * @return 事件类型标识
     */
    public String eventType() {
        return getClass().getSimpleName();
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getSource() {
        return source;
    }
}
