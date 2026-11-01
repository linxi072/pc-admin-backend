package com.acme.scaffold.monitor.dto;

import com.acme.scaffold.monitor.entity.AlertRuleDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 告警规则视图（管理端只读投影）。
 *
 * <p>由 {@link #of(AlertRuleDO)} 从持久化对象投影：id 转为字符串避免前端对雪花 ID 的精度丢失；
 * createdAt / updatedAt 转为 epoch 毫秒便于前端直接格式化与排序。</p>
 *
 * @param id                 规则 ID（字符串）
 * @param ruleName           规则名称
 * @param metricKey          指标键（AlertRuleRules.MetricKey 枚举名）
 * @param comparator         比较符（AlertRuleRules.Comparator 枚举名）
 * @param threshold          阈值
 * @param severity           严重度（AlertRuleRules.Severity 枚举名）
 * @param forDurationSec     持续时长（秒）
 * @param consecutiveCount   连续触发次数
 * @param channelType        渠道类型（AlertRuleRules.ChannelType 枚举名）
 * @param channelConfig      渠道配置（WEBHOOK 时为回调地址）
 * @param enabled            是否启用
 * @param remark             备注
 * @param createdAtEpochMillis 创建时间（epoch 毫秒）
 * @param updatedAtEpochMillis 更新时间（epoch 毫秒）
 */
public record AlertRuleView(String id, String ruleName, String metricKey, String comparator,
                           BigDecimal threshold, String severity, int forDurationSec,
                           int consecutiveCount, String channelType, String channelConfig,
                           boolean enabled, String remark, long createdAtEpochMillis,
                           long updatedAtEpochMillis) {

    public static AlertRuleView of(AlertRuleDO d) {
        return new AlertRuleView(
                String.valueOf(d.getId()),
                d.getRuleName(),
                d.getMetricKey(),
                d.getComparator(),
                d.getThreshold(),
                d.getSeverity(),
                d.getForDurationSec() == null ? 0 : d.getForDurationSec(),
                d.getConsecutiveCount() == null ? 1 : d.getConsecutiveCount(),
                d.getChannelType(),
                d.getChannelConfig(),
                Boolean.TRUE.equals(d.getEnabled()),
                d.getRemark(),
                toEpoch(d.getCreatedAt()),
                toEpoch(d.getUpdatedAt()));
    }

    private static long toEpoch(LocalDateTime t) {
        return t == null ? 0L : t.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
    }
}
