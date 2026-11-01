package com.acme.scaffold.monitor.dto;

import java.math.BigDecimal;

/**
 * 告警规则更新请求（全量字段，缺省项沿用既有值由服务层处理）。
 *
 * @param ruleName          规则名称
 * @param metricKey         指标键
 * @param comparator        比较符
 * @param threshold         阈值
 * @param severity          严重度
 * @param forDurationSec    持续时长（秒）
 * @param consecutiveCount  连续触发次数
 * @param channelType       渠道类型
 * @param channelConfig     渠道配置
 * @param enabled           是否启用
 * @param remark            备注
 */
public record AlertRuleUpdateRequest(String ruleName, String metricKey, String comparator,
                                   BigDecimal threshold, String severity, Integer forDurationSec,
                                   Integer consecutiveCount, String channelType,
                                   String channelConfig, Boolean enabled, String remark) {
}
