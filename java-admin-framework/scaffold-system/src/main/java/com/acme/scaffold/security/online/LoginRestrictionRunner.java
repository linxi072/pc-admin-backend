package com.acme.scaffold.security.online;

import com.acme.scaffold.system.service.OnlineUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后执行一次登录限制到期解除：进程停机期间到期的限制记录，
 * 在重启后立即扫出，与 {@link LoginRestrictionJob} 的周期扫描互补。
 */
@Slf4j
@Component
@Order(101)
public class LoginRestrictionRunner implements ApplicationRunner {

    private final OnlineUserService onlineUserService;

    public LoginRestrictionRunner(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int released = onlineUserService.releaseExpired();
            if (released > 0) {
                log.info("启动时解除到期登录限制 {} 条", released);
            }
        } catch (Exception e) {
            log.warn("启动时登录限制到期解除异常（不影响启动）", e);
        }
    }
}
