package com.acme.scaffold.monitor.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警规则持久化对象（{@code sys_alert_rule} 表；V70 原名 {@code alert_rule}，V84 重命名）。
 *
 * <p>指标键 / 比较符 / 严重度 / 渠道类型均以字符串存储枚举名（由
 * {@code com.acme.scaffold.monitor.alert.rules.AlertRuleRules} 解析校验），
 * 便于在不改表结构的前提下横向扩展枚举。tenant_id 由 SqlWriters 在未启用强制隔离时统一写 0，
 * deleted / version / 时间字段由 SqlWriters 自动填充，无需在业务层赋值。</p>
 */
@Data
public class AlertRuleDO {

    private Long id;

    private Long tenantId = 0L;

    /** 规则名称（展示用，唯一性由业务层保证）。 */
    private String ruleName;

    /** 指标键（AlertRuleRules.MetricKey 枚举名，如 CPU_USAGE）。 */
    private String metricKey;

    /** 比较符（AlertRuleRules.Comparator 枚举名，如 GT）。 */
    private String comparator;

    /** 阈值（与指标同单位，如 CPU 使用率为百分比小数 0~100）。 */
    private BigDecimal threshold;

    /** 严重度（AlertRuleRules.Severity 枚举名，如 WARNING）。 */
    private String severity;

    /** 持续时长（秒）：指标连续超阈达到该时长才触发，0 表示立即触发。 */
    private Integer forDurationSec = 0;

    /** 连续触发次数：连续 N 个评估周期均超阈才触发，1 表示单次即触发。 */
    private Integer consecutiveCount = 1;

    /** 渠道类型（AlertRuleRules.ChannelType 枚举名，如 INBOX / WEBHOOK）。 */
    private String channelType;

    /** 渠道配置（JSON 字符串，WEBHOOK 时为回调地址等）。 */
    private String channelConfig;

    /** 是否启用。 */
    private Boolean enabled = false;

    /** 备注。 */
    private String remark;

    private Integer version = 0;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
