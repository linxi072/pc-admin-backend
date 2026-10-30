package com.acme.scaffold.system.notification.rules;

import com.acme.scaffold.common.exception.BusinessException;

/**
 * 投递重试纯函数规则（M16）：可重试判定、退避计算、次数钳制。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link com.acme.scaffold.system.notification.service.NotificationService}
 * 的重试循环复用，保证「什么值得重试、等多久、最多几次」三处口径同源。</p>
 */
public final class DeliveryRetryRules {

    private DeliveryRetryRules() {
    }

    /**
     * 是否值得重试：{@code BusinessException} 一律不重试。
     *
     * <p>业务异常是<b>确定性拒绝</b>——渠道未启用、收件人非法、规则拦截——
     * 同样的输入再试必然同样失败，重试只放大延迟；其余运行时异常
     * （网关 IO、连接超时、SMTP/HTTP 传输失败）是<b>瞬时故障</b>，
     * 短间隔重试的救回概率显著，值得重试。</p>
     */
    public static boolean isRetryable(RuntimeException ex) {
        return !(ex instanceof BusinessException);
    }

    /**
     * 第 {@code attempt} 次重试前的退避时长（线性）：{@code attempt × baseMs}。
     *
     * <p>attempt 从 1 起（首次重试等 1×base，第二次重试等 2×base）。
     * 线性而非指数：通知投递对延迟敏感，指数退避在 {@code maxAttempts} 有界
     * （默认 3）的前提下收益不明显，反而把最坏延迟推向秒级。总等待有硬上界
     * {@code (maxAttempts-1) × baseMs}，调用方超时预算可精确覆盖。</p>
     */
    public static long backoffMs(int attempt, long baseMs) {
        return Math.max(0, baseMs) * Math.max(1, attempt);
    }

    /** 尝试次数钳制：配置小于 1 按 1 处理（至少原样投递一次，不静默跳过）。 */
    public static int clampAttempts(int maxAttempts) {
        return Math.max(1, maxAttempts);
    }
}
