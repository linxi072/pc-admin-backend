package com.acme.scaffold.observability.dto;

import java.util.Map;

/**
 * 指标快照视图（来自 Micrometer MeterRegistry 的当前注册 meter）。
 *
 * @param name       指标名（如 app.security.login.success）
 * @param type       meter 类型（COUNTER / GAUGE / TIMER / DISTRIBUTION_SUMMARY 等）
 * @param description 指标描述（HELP 文本，可为空）
 * @param baseUnit   基本单位（如 seconds / bytes，可为空）
 * @param tags       标签键值对
 * @param value      当前采样值（counter 累计值 / gauge 瞬时值 / timer 计数等，无可用测量时为 null）
 */
public record MetricView(String name, String type, String description, String baseUnit,
                         Map<String, String> tags, Double value) {
}
