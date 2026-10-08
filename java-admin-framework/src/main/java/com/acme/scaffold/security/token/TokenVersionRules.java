package com.acme.scaffold.security.token;

/**
 * Token 版本纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p><b>为什么用 {@code claim >= stored} 而不是 {@code ==}</b>：
 * 运维可能手工回退版本号来解锁误操作；用「大于等于」既能在版本递增后判定失效，
 * 又不会因为人为下调把用户永久锁死。判定失效的唯一条件是「库里版本已超前于手中凭证」。
 */
public final class TokenVersionRules {

    /** 历史 token 无该声明时的默认版本，与 V1 建表的 DEFAULT 1 保持一致。 */
    public static final int DEFAULT_VERSION = 1;

    private TokenVersionRules() {
    }

    /** 声明缺失时按 1 处理，兼容改造前签发的 token。 */
    public static int normalizeClaim(Integer claimVersion) {
        return claimVersion == null ? DEFAULT_VERSION : claimVersion;
    }

    /**
     * 凭证是否仍然有效。
     *
     * @param claimVersion  JWT 中声明的版本
     * @param storedVersion 库中当前版本，{@code null} 表示用户不存在（已删除）→ 一律失效
     */
    public static boolean isValid(Integer claimVersion, Integer storedVersion) {
        if (storedVersion == null) {
            return false;
        }
        return normalizeClaim(claimVersion) >= storedVersion;
    }

    /** 缓存条目是否已过期。 */
    public static boolean isExpired(long nowMillis, long loadedAtMillis, long ttlMillis) {
        return nowMillis - loadedAtMillis >= ttlMillis;
    }
}
