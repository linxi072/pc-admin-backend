package com.acme.scaffold.common.event.replication;

import com.acme.scaffold.common.event.DomainEvent;
import java.util.List;

/**
 * 跨区域复制信封（E7）：把本地领域事件包装为可跨部署单元复制的消息。
 *
 * <p>与 {@link DomainEvent} 正交：事件描述「发生了什么」，信封描述「要复制到哪里、以什么顺序」。
 * 信封不可变，便于跨线程/跨进程传递与幂等去重。</p>
 *
 * @param eventId      来源事件 ID（幂等键，与 DomainEvent.eventId 一致）
 * @param eventType    事件类型名（路由键）
 * @param sourceUnit   源部署单元（如 region-a）
 * @param targetUnits  目标部署单元列表（由拓扑计算，已排除源单元）
 * @param tenantId     租户 ID（可为 null 表示平台级事件）
 * @param version      逻辑版本号（单调递增，用于乱序兜底）
 * @param payload      事件负载摘要（类注释/关键字段，不携带敏感原文）
 */
public record ReplicationEnvelope(
        String eventId,
        String eventType,
        String sourceUnit,
        List<String> targetUnits,
        Long tenantId,
        long version,
        String payload) {

    public ReplicationEnvelope {
        targetUnits = targetUnits == null ? List.of() : List.copyOf(targetUnits);
    }

    /** 由领域事件与拓扑路由构造信封。 */
    public static ReplicationEnvelope of(DomainEvent event, String sourceUnit,
                                         List<String> targetUnits, long version) {
        return new ReplicationEnvelope(
                event.getEventId(), event.eventType(), sourceUnit, targetUnits,
                event.getTenantId(), version,
                event.getClass().getSimpleName() + "@" + event.getOccurredAt().toEpochMilli());
    }
}
