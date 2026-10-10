package com.acme.scaffold.security.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会话空闲回收定时任务：按 {@code app.security.session-cleanup-interval-ms} 周期执行，
 * 回收异常退出后残留的刷新令牌。禁用时任务直接跳过（见 {@link SessionCleanupService#runCleanup()}）。
 */
@Slf4j
@Component
public class SessionCleanupJob {

    private final SessionCleanupService sessionCleanupService;

    public SessionCleanupJob(SessionCleanupService sessionCleanupService) {
        this.sessionCleanupService = sessionCleanupService;
    }

    @Scheduled(fixedDelayString = "${app.security.session-cleanup-interval-ms:300000}")
    public void run() {
        try {
            sessionCleanupService.runCleanup();
        } catch (Exception e) {
            log.warn("会话空闲回收任务执行异常", e);
        }
    }
}
