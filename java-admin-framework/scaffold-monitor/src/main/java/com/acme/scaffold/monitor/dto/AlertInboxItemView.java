package com.acme.scaffold.monitor.dto;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.rules.AlertInboxRules;

import java.time.Instant;

/**
 * 告警收件箱条目视图（管理端只读投影）。
 *
 * <p>由 {@link #of(AlertInboxEntry)} 从内存条目投影：{@code receivedAt} 转为
 * epoch 毫秒便于前端直接格式化；{@code summary} 按
 * {@link AlertInboxRules#MAX_SUMMARY_LEN} 截断，避免超长告警描述撑爆响应。
 * 不含敏感字段（Alertmanager webhook 负载本就只含标签与注解）。</p>
 *
 * @param id                   收件箱条目 ID（入箱时生成的 UUID）
 * @param receivedAtEpochMillis 接收时间（epoch 毫秒）
 * @param status               告警状态：firing / resolved
 * @param name                 告警名（labels.alertname）
 * @param severity             严重级别（labels.severity）
 * @param summary              摘要（annotations.summary，截断后）
 * @param instance             告警对象（labels.instance）
 * @param startsAt             告警生效时间（Alertmanager 原始字符串）
 * @param endsAt               告警消除时间（Alertmanager 原始字符串，firing 时通常为空）
 */
public record AlertInboxItemView(String id, long receivedAtEpochMillis, String status, String name,
                                 String severity, String summary, String instance, String startsAt,
                                 String endsAt) {

    public static AlertInboxItemView of(AlertInboxEntry entry) {
        Instant receivedAt = entry.getReceivedAt();
        return new AlertInboxItemView(
                entry.getId(),
                receivedAt == null ? 0L : receivedAt.toEpochMilli(),
                entry.getStatus(),
                entry.getName(),
                entry.getSeverity(),
                AlertInboxRules.truncate(entry.getSummary(), AlertInboxRules.MAX_SUMMARY_LEN),
                entry.getInstance(),
                entry.getStartsAt(),
                entry.getEndsAt());
    }
}
