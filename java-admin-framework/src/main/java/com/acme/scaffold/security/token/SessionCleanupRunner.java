package com.acme.scaffold.security.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后执行一次会话回收，用于<b>进程崩溃恢复</b>：
 * 上一进程异常退出时未来得及回收的会话（last_used_at 停滞），在新进程启动时被统一回收。
 */
@Slf4j
@Component
@Order(100)
public class SessionCleanupRunner implements ApplicationRunner {

    private final SessionCleanupService sessionCleanupService;

    public SessionCleanupRunner(SessionCleanupService sessionCleanupService) {
        this.sessionCleanupService = sessionCleanupService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            sessionCleanupService.runCleanup();
        } catch (Exception e) {
            log.warn("启动会话回收执行异常（不影响启动）", e);
        }
    }
}
