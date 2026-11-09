package com.acme.scaffold.system.export.application;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 导出任务共享的时间参数解析方法。
 *
 * <p>{@code AuditLogExportTask} 与 {@code LoginLogExportTask} 原本各自内联了一份完全相同的
 * ISO-8601 时间参数解析逻辑（空/非法回落 {@code null}，仅 warn 文案不同）；此处收敛为单一事实来源，
 * 上下文标签作为参数透传。</p>
 */
@Slf4j
final class ExportTimeSupport {

    private ExportTimeSupport() {
    }

    /** 解析 ISO-8601 时间参数；空或非法一律返回 {@code null}（视为不加时间过滤，由服务端补默认区间）。 */
    static LocalDateTime parseTime(String value, String contextLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.warn("{}：时间参数格式非法，已忽略: {}", contextLabel, value);
            return null;
        }
    }
}
