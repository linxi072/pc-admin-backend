package com.acme.scaffold.common.security;

import java.nio.charset.StandardCharsets;

/**
 * 恒定时间字符串比较（防时序侧信道）。
 *
 * <p>用于令牌 / HMAC 签名验签：实现不随首个不同字节提前短路，避免攻击者通过响应时间差异
 * 推断签名前缀。任一参数为 {@code null} 或两串长度不等均直接返回 {@code false}（不进入逐字节比较）。</p>
 *
 * <p>原散落于 {@code TokenCodec}（realtime）、{@code UnsubscribeTokenRules}、
 * {@code WebhookSignatureRules}（system）三处完全一致的私有实现，统一收敛到此处作为单一事实来源。</p>
 */
public final class ConstantTime {

    private ConstantTime() {
    }

    /**
     * 恒定时间比较两字符串（按 UTF-8 字节）。
     *
     * @param left  左值（可空）
     * @param right 右值（可空）
     * @return 两串非空且逐字节一致时返回 {@code true}；任一为 null 或长度不等返回 {@code false}
     */
    public static boolean equals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            diff |= leftBytes[i] ^ rightBytes[i];
        }
        return diff == 0;
    }
}
