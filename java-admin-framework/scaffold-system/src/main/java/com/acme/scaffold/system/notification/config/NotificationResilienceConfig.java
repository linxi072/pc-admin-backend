package com.acme.scaffold.system.notification.config;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.resilience.CircuitBreaker;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

/**
 * 通知投递韧性配置（P1-6 / P7）。
 *
 * <p>为 {@link com.acme.scaffold.system.notification.application.NotificationService#dispatchAsync}
 * 提供下游熔断器：下游通道/网关持续失败时进入 OPEN 并快速失败，冷却后半开试探一次。
 * 阈值当前为合理默认值，后续可下放到 {@link NotificationRetryProperties} 配置化。</p>
 *
 * <p>P7 扩展：为每个<b>外部</b>投递通道（短信 / 邮件 / Webhook / 企业微信 / 钉钉）配置独立熔断器，
 * 与既有 {@code notificationDispatchBreaker}（异步派发总闸）形成「总闸 + 分闸」双层防护，
 * 单一下游故障被隔离，避免级联拖垮全部通知链路。</p>
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

    @Bean
    public CircuitBreaker smsDispatchBreaker() {
        return new CircuitBreaker(FAILURE_THRESHOLD, OPEN_MS);
    }

    @Bean
    public CircuitBreaker emailDispatchBreaker() {
        return new CircuitBreaker(FAILURE_THRESHOLD, OPEN_MS);
    }

    @Bean
    public CircuitBreaker webhookDispatchBreaker() {
        return new CircuitBreaker(FAILURE_THRESHOLD, OPEN_MS);
    }

    @Bean
    public CircuitBreaker wecomDispatchBreaker() {
        return new CircuitBreaker(FAILURE_THRESHOLD, OPEN_MS);
    }

    @Bean
    public CircuitBreaker dingtalkDispatchBreaker() {
        return new CircuitBreaker(FAILURE_THRESHOLD, OPEN_MS);
    }

    /**
     * 按渠道路由的熔断器映射，供 {@code NotificationService} 在适配器投递层按渠道包裹。
     * 仅覆盖外部通道；站内信 / WebSocket 等进程内通道不在此列（无下游故障面）。
     *
     * <p>每个参数显式 {@code @Qualifier}：容器中同名类型 Bean 共 6 个，若依赖方法参数名
     * 隐式解析，会受编译器 {@code -parameters} 开关影响；显式限定保证任意构建配置下装配稳定。</p>
     */
    @Bean
    public Map<Channel, CircuitBreaker> externalChannelBreakers(
            @Qualifier("smsDispatchBreaker") CircuitBreaker smsDispatchBreaker,
            @Qualifier("emailDispatchBreaker") CircuitBreaker emailDispatchBreaker,
            @Qualifier("webhookDispatchBreaker") CircuitBreaker webhookDispatchBreaker,
            @Qualifier("wecomDispatchBreaker") CircuitBreaker wecomDispatchBreaker,
            @Qualifier("dingtalkDispatchBreaker") CircuitBreaker dingtalkDispatchBreaker) {
        return Map.of(
                Channel.SMS, smsDispatchBreaker,
                Channel.EMAIL, emailDispatchBreaker,
                Channel.WEBHOOK, webhookDispatchBreaker,
                Channel.WECOM, wecomDispatchBreaker,
                Channel.DINGTALK, dingtalkDispatchBreaker);
    }
}
