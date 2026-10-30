package com.acme.scaffold.common.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志留存策略视图（M20 深化）。
 *
 * <p>返回当前生效的留存天数与清理截止时间（截止时间之前的数据由 {@code AuditRetentionJob} 清理）。
 * 纯配置读取、无数据库访问；{@code retentionDays ≤ 0} 时 {@code cutoff} 为 {@code null}，表示禁用清理。
 * cutoff 以 ISO-8601 字符串透出，前端可直接展示、无需额外解析。</p>
 */
public record AuditRetentionView(int retentionDays, String cutoff) {

    /**
     * 基于留存天数构造视图（cutoff 由 {@link AuditRetentionRules#cutoff} 计算后格式化）。
     *
     * @param retentionDays 留存天数；≤0 表示不清理
     */
    public static AuditRetentionView of(int retentionDays) {
        LocalDateTime cutoff = AuditRetentionRules.cutoff(retentionDays);
        String cutoffStr = cutoff == null ? null : cutoff.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new AuditRetentionView(retentionDays, cutoffStr);
    }
}
