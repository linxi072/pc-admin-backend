package com.acme.scaffold.common.audit;

import java.time.LocalDateTime;

/**
 * 审计留存纯函数规则（M20 增强）。
 *
 * <p>与项目既有 {@code *Rules} 纯函数范式一致：无状态、易单测、被 Service/Job 调用。
 * 留存口径为「发生时间早于 cutoff 的记录视为过期可清理」。</p>
 */
public final class AuditRetentionRules {

    private AuditRetentionRules() {
    }

    /**
     * 计算留存截止时间：当前时间往前推 {@code retentionDays} 天。
     *
     * @param retentionDays 留存天数；≤0 表示不清理，返回 {@code null}
     * @return 截止时间；{@code null} 表示禁用清理
     */
    public static LocalDateTime cutoff(int retentionDays) {
        if (retentionDays <= 0) {
            return null;
        }
        return LocalDateTime.now().minusDays(retentionDays);
    }

    /**
     * 判断某条审计记录是否过期（发生时间早于 cutoff）。
     */
    public static boolean isExpired(LocalDateTime occurredAt, LocalDateTime cutoff) {
        return occurredAt != null && cutoff != null && occurredAt.isBefore(cutoff);
    }
}
