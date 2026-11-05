package com.acme.scaffold.system.notification.config;

import com.acme.scaffold.common.resilience.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知投递韧性配置（P1-6）。
 *
 * <p>为 {@link com.acme.scaffold.system.notification.service.NotificationService#dispatchAsync}
 * 提供下游熔断器：下游通道/网关持续失败时进入 OPEN 并快速失败，冷却后半开试探一次。
 * 阈值当前为合理默认值，后续可下放到 {@link NotificationRetryProperties} 配置化。</p>
 */
@Configuration
public class NotificationResilienceConfig {

    /** 连续失败达到该次数即熔断。 */
    private static final int FAILURE_THRESHOLD = 5;
    /** OPEN 状态冷却时长（毫秒），结束后进入 HALF_OPEN 放行一次试探。 */
    private static final long OPEN_MS = 30_000L;

    @Bean
    public CircuitBreaker notificationDispatchBreaker() {
        return new CircuitBreaker(FAILURE_THRESHOLD, OPEN_MS);
    }
}
