package com.acme.scaffold.system.log.application;

import com.acme.scaffold.common.audit.AuditRetentionRules;
import com.acme.scaffold.system.log.config.LoginLogProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

/**
 * 登录日志留存清理定时任务（对应 operations §27.6「后续方向」，与 {@code AuditRetentionJob} 同范式）。
 *
 * <p>按 {@code app.login-log.retention-days} 清理过期登录日志，避免登录表无限膨胀。
 * 阈值计算复用通用纯函数 {@link AuditRetentionRules#cutoff(int)}（与审计留存一致口径）；
 * 清理逻辑由 {@link LoginLogService#purgeBefore(LocalDateTime)} 在事务内执行物理删除。
 * 留存天数 ≤0 视为禁用清理；任务异常被捕获并记录，不影响下次调度。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogRetentionJob {

    private final LoginLogService loginLogService;
    private final LoginLogProperties loginLogProperties;

    @Scheduled(cron = "${app.login-log.retention-cron:0 30 3 * * ?}")
    public void purgeExpired() {
        try {
            int retentionDays = loginLogProperties.getRetentionDays();
            LocalDateTime cutoff = AuditRetentionRules.cutoff(retentionDays);
            if (cutoff == null) {
                log.debug("登录日志留存清理已禁用（app.login-log.retention-days<=0），跳过");
                return;
            }
            int removed = loginLogService.purgeBefore(cutoff);
            if (removed > 0) {
                log.info("清理过期登录日志 {} 条（留存 {} 天）", removed, retentionDays);
            }
        } catch (Exception e) {
            log.warn("登录日志留存清理执行异常", e);
        }
    }
}
