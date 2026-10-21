package com.acme.scaffold.security.antireplay;

/**
 * 防重放纯函数规则：与 Spring 解耦，便于离线单测。
 *
 * <p>仅负责「客户端时间戳」的合法性判定（缺失 / 非法 / 过早 / 过晚 / 通过）。
 * nonce 的查重由 {@link ReplayNonceService} 负责。
 */
public final class ReplayProtectionRules {

    public enum TimestampVerdict {
        OK,
        MISSING,
        INVALID,
        TOO_OLD,
        TOO_FUTURE
    }

    private ReplayProtectionRules() {
    }

    /**
     * 校验客户端时间戳是否在允许的时钟偏差内。
     *
     * @param clientTimestamp 客户端提交的毫秒时间戳（可能为 null）
     * @param serverNow       服务端当前毫秒时间戳
     * @param maxSkewMillis   允许的最大偏差（毫秒，取绝对值）
     * @return 判定结果
     */
    public static TimestampVerdict verifyTimestamp(Long clientTimestamp, long serverNow, long maxSkewMillis) {
        if (clientTimestamp == null) {
            return TimestampVerdict.MISSING;
        }
        if (clientTimestamp <= 0) {
            return TimestampVerdict.INVALID;
        }
        long diff = Math.abs(clientTimestamp - serverNow);
        if (diff > maxSkewMillis) {
            return clientTimestamp < serverNow ? TimestampVerdict.TOO_OLD : TimestampVerdict.TOO_FUTURE;
        }
        return TimestampVerdict.OK;
    }
}
