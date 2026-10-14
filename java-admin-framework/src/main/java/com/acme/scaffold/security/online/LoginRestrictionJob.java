package com.acme.scaffold.security.online;

import com.acme.scaffold.system.service.OnlineUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 登录限制到期自动解除定时任务：按 {@code app.security.online-session-restriction-scan-interval-ms}
 * 周期扫描，把「ACTIVE 且已过到期时间」的限制记录置为 RELEASED（AUTO_EXPIRED）。
 *
 * <p>与登录链路 {@code RestrictionRules.isActive} 的「到期即不生效」判定互为兜底：
 * 任务未执行的窗口期内，过期记录在登录侧同样判为不生效，不会出现「到期仍拒登录」。
 */
@Slf4j
@Component
public class LoginRestrictionJob {

    private final OnlineUserService onlineUserService;

    public LoginRestrictionJob(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    @Scheduled(fixedDelayString = "${app.security.online-session-restriction-scan-interval-ms:60000}")
    public void run() {
        try {
            onlineUserService.releaseExpired();
        } catch (Exception e) {
            log.warn("登录限制到期解除任务执行异常", e);
        }
    }
}
