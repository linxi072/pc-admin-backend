package com.acme.scaffold.security.password;

import java.time.Duration;

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
        long seconds = baseDuration.toSeconds();
        for (int i = 1; i < tier; i++) {
            seconds = Math.min(seconds * 2, maxDuration.toSeconds());
        }
        return Duration.ofSeconds(Math.min(seconds, maxDuration.toSeconds()));
    }
}
