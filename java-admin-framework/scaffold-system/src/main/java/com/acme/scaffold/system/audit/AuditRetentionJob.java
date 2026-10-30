package com.acme.scaffold.system.audit;

import com.acme.scaffold.common.audit.AuditLogService;
import com.acme.scaffold.common.audit.AuditProperties;
import com.acme.scaffold.common.audit.AuditRetentionRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志留存清理定时任务（M20 增强）。
 *
 * <p>按 {@code app.audit.retention-days} 清理过期审计记录，避免审计表无限膨胀。
 * 阈值计算与「是否过期」判定剥离到纯函数 {@link AuditRetentionRules}，本类仅编排调度与日志；
 * 清理逻辑复用 {@link AuditLogService#purgeBefore}（与导出中心同出一处，语义一致）。
 * 留存天数 ≤0 视为禁用清理；任务异常被捕获并记录，不影响下次调度。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    private final AuditLogService auditLogService;
    private final AuditProperties auditProperties;

    @Scheduled(cron = "${app.audit.retention-cron:0 0 3 * * ?}")
    public void purgeExpired() {
        try {
            int retentionDays = auditProperties.getRetentionDays();
            LocalDateTime cutoff = AuditRetentionRules.cutoff(retentionDays);
            if (cutoff == null) {
                log.debug("审计日志留存清理已禁用（app.audit.retention-days<=0），跳过");
                return;
            }
            int removed = auditLogService.purgeBefore(cutoff);
            if (removed > 0) {
                log.info("清理过期审计日志 {} 条（留存 {} 天）", removed, retentionDays);
            }
        } catch (Exception e) {
            log.warn("审计日志留存清理执行异常", e);
        }
    }
}
