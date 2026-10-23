package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知投递重试配置（M16）：{@code app.notifications.retry.*}。
 *
 * <ul>
 *   <li>{@code enabled}：是否启用适配器级有界重试（默认 true——瞬时故障是生产常态，
 *       网关抖动/连接超时重试一次即可救回，收益明显大于风险）；</li>
 *   <li>{@code max-attempts}：单渠道最大尝试次数（含首次，默认 3——即最多额外重试 2 次；
 *       小于 1 按 1 处理）；</li>
 *   <li>{@code backoff-ms}：重试基准退避（默认 200ms，第 n 次重试前等待 {@code n × backoff-ms}，
 *       线性增长且有上界（{@code (maxAttempts-1) × backoff-ms}），不会指数爆炸）。</li>
 * </ul>
 *
 * <p><b>重试范围</b>：仅对<b>非</b> {@code BusinessException} 的运行时异常重试——
 * 业务确定性拒绝（渠道未启用、报文被规则拦截）重试无益且会放大每次校验失败的延迟；
 * 网关 IO 类运行时异常（邮件/短信/webhook 传输失败）才是重试对象。判定见
 * {@code DeliveryRetryRules.isRetryable}。</p>
 *
 * <p><b>代价</b>：重试在调用线程内同步等待，最坏情况为首次 + 2 次重试共约
 * {@code backoff + 2×backoff} 毫秒额外延迟；对异步/批量发送方建议配合调用侧超时评估。</p>
 */
@ConfigurationProperties(prefix = "app.notifications.retry")
public class NotificationRetryProperties {

    private boolean enabled = true;
    private int maxAttempts = 3;
    private long backoffMs = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBackoffMs() {
        return backoffMs;
    }

    public void setBackoffMs(long backoffMs) {
        this.backoffMs = backoffMs;
    }
}
