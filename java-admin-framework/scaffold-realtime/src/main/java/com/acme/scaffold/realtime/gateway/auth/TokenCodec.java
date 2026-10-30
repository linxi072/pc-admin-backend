package com.acme.scaffold.realtime.gateway.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Optional;

/**
 * 短期令牌编解码（演示级实现，对应设计稿 §3.2「短期令牌 + 防重放」）。
 *
 * <p>令牌格式（点分五段，非标准 JWT——避免为演示引入完整 JWT 库）：
 * <pre>
 *   v1.&lt;userId&gt;.&lt;expMillis&gt;.&lt;iatMillis&gt;.&lt;base64url(HmacSHA256(secret, 前三段))&gt;
 * </pre>
 *
 * <p>与生产 JWT 的对应关系：exp/iat 校验语义一致；生产环境应替换为标准 JWT 库，
 * 本类保持接口不变即可替换。
 */
public final class TokenCodec {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private TokenCodec() {
    }

    /** 解析结果。 */
    public record ParsedToken(String userId, long exp, long iat, String signature) {
    }

    /** 签发令牌。 */
    public static String mint(String secret, String userId, long expMillis, long iatMillis) {
        String body = VERSION + "." + userId + "." + expMillis + "." + iatMillis;
        return body + "." + sign(secret, body);
    }

    /** 解析令牌（不校验签名与时效）。 */
    public static Optional<ParsedToken> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ParsedToken(parts[1], Long.parseLong(parts[2]),
                    Long.parseLong(parts[3]), parts[4]));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** 验签（恒定时间比较，防时序侧信道）。 */
    public static boolean verify(String secret, String token) {
        Optional<ParsedToken> parsed = parse(token);
        if (parsed.isEmpty()) {
            return false;
        }
        ParsedToken body = parsed.get();
        String expected = sign(secret, VERSION + "." + body.userId() + "." + body.exp() + "." + body.iat());
        return constantTimeEquals(expected, body.signature());
    }

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 不可用", ex);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
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
