package com.acme.scaffold.cms.article.domain.rule;

import java.time.LocalDateTime;
import java.util.UUID;

/** 草稿预览令牌规则（C6，纯函数）：令牌格式、默认有效期、过期判定。 */
public final class CmsPreviewTokenRules {

    /** 令牌长度（无连字符 UUID，32 位十六进制）。 */
    public static final int TOKEN_LENGTH = 32;

    /** 默认有效期（小时）。 */
    public static final int DEFAULT_TTL_HOURS = 24;

    private CmsPreviewTokenRules() {
    }

    /** 生成随机令牌（去连字符 UUID）。 */
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 令牌格式是否合法（32 位十六进制）。 */
    public static boolean validFormat(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    /** 计算过期时间：now + TTL 小时。 */
    public static LocalDateTime expiryAfter(LocalDateTime now, int ttlHours) {
        int hours = ttlHours <= 0 ? DEFAULT_TTL_HOURS : ttlHours;
        return now.plusHours(hours);
    }

    /** 是否已过期：expiresAt 早于 now（含等于视为未过期）。 */
    public static boolean isExpired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
