package com.acme.scaffold.monitor.dto;

import java.math.BigDecimal;

/**
 * 告警规则创建请求。
 *
 * <p>字段级静态校验在服务层由 {@code AlertRuleRules.validate} 统一完成，
 * 本 DTO 仅承载数据，不引入 Bean Validation 约束，便于复用同一套校验口径。</p>
 *
 * @param ruleName          规则名称（必填）
 * @param metricKey         指标键（必填，AlertRuleRules.MetricKey 枚举名）
 * @param comparator        比较符（必填，AlertRuleRules.Comparator 枚举名）
 * @param threshold         阈值（必填）
 * @param severity          严重度（必填，AlertRuleRules.Severity 枚举名）
 * @param forDurationSec    持续时长（秒，缺省 0）
 * @param consecutiveCount  连续触发次数（缺省 1）
 * @param channelType       渠道类型（必填，AlertRuleRules.ChannelType 枚举名）
 * @param channelConfig     渠道配置（WEBHOOK 时必填回调地址）
 * @param enabled           是否启用（缺省 false）
 * @param remark            备注
 */
public record AlertRuleCreateRequest(String ruleName, String metricKey, String comparator,
                                    BigDecimal threshold, String severity, Integer forDurationSec,
                                    Integer consecutiveCount, String channelType,
                                    String channelConfig, Boolean enabled, String remark) {
}
