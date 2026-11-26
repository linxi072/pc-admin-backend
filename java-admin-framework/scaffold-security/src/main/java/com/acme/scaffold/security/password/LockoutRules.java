package com.acme.scaffold.security.password;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 登录失败渐进式锁定纯规则（无 Spring / 无 DB 依赖，便于离线单测）。
 *
 * <p>设计文档 §5.3：「登录失败 5 次锁定 15 分钟；继续失败采用渐进式锁定」。
 * 锁定时长随连续失败次数递增（15m → 30m → 1h → 2h → 4h → 8h → 16h → 封顶 maxDuration），
 * 避免固定时长锁定在持续爆破下形同虚设，同时避免无限膨胀。
 */
public final class LockoutRules {

    private LockoutRules() {
    }

    /** 是否达到锁定阈值（连续失败次数 ≥ 阈值）。 */
    public static boolean shouldLock(int consecutiveFailures, int maxFailures) {
        return consecutiveFailures >= maxFailures;
    }

    /**
     * 当前时刻是否仍处于锁定态。
     *
     * <p>{@code status=LOCKED} 有两种来源：带 {@code lockedUntil} 的渐进式临时锁，以及不带到期时间的
     * 人工长期锁。临时锁到期后必须放行，否则账号会永久无法登录；人工锁仍需持续阻断。</p>
     */
    public static boolean isLocked(String status, LocalDateTime lockedUntil, LocalDateTime now) {
        if ("LOCKED".equals(status)) {
            return lockedUntil == null || lockedUntil.isAfter(now);
        }
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 计算锁定持续时间（渐进式）。
     *
     * @param consecutiveFailures 当前连续失败次数（含本次）
     * @param maxFailures         触发锁定的阈值（如 5）
     * @param baseDuration        首次锁定基准时长（如 15 分钟）
     * @param maxDuration         锁定时长上限（如 24 小时），防止无限膨胀
     * @return 本次应锁定的时长；未达阈值返回 {@link Duration#ZERO}
     */
    public static Duration computeDuration(int consecutiveFailures, int maxFailures,
                                           Duration baseDuration, Duration maxDuration) {
        if (consecutiveFailures < maxFailures) {
            return Duration.ZERO;
        }
        // 第 1 次达到阈值锁 base；此后每再多 maxFailures 次升一级（时长翻倍）
        int tier = (consecutiveFailures - maxFailures) / maxFailures + 1;
        long maxSeconds = maxDuration.toSeconds();
        long seconds = Math.min(baseDuration.toSeconds(), maxSeconds);
        // 先钳制上界再翻倍：maxSeconds 极大时 seconds*2 可能 long 溢出为负，
        // 且一旦触顶无需继续循环（否则极大失败次数下空转数百次）。
        for (int i = 1; i < tier && seconds < maxSeconds; i++) {
            seconds = seconds > maxSeconds / 2 ? maxSeconds : seconds * 2;
        }
        return Duration.ofSeconds(seconds);
    }
}
