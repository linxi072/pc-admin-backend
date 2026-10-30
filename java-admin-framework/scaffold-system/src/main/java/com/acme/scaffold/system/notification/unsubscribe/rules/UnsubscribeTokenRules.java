package com.acme.scaffold.system.notification.unsubscribe.rules;

import com.acme.scaffold.common.notification.Channel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 退订令牌纯函数规则（M15）：HMAC-SHA256 签名的「免登录退订」凭证编解码。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link com.acme.scaffold.system.notification.unsubscribe.NotificationUnsubscribeService}
 * 铸造（邮件/短信正文注入）与验签（公开退订端点）复用，保证「签什么、验什么」同源。</p>
 *
 * <p><b>令牌格式</b>（点分三段，非标准 JWT——与 realtime 网关短期令牌同范式，避免为退订
 * 引入完整 JWT 库）：
 * <pre>
 *   v1.&lt;base64url(userId|channel|msgType|expMillis|nonce)&gt;.&lt;base64url(HmacSHA256(secret, 前两段))&gt;
 * </pre>
 * <ul>
 *   <li>payload 以 {@code |} 分隔五段，base64url 编码后不含点号，不会与令牌分隔符冲突；</li>
 *   <li>签名覆盖版本段与 payload 段（{@code v1.<payload>}），篡改任一段即验签失败；</li>
 *   <li>{@code nonce} 为 8 字符随机串：令同一用户的多次铸造互不相等（链接不可关联），
 *       无状态校验不记录 nonce——退订操作幂等，防重放由 HMAC 签名 + 短 TTL 承担；</li>
 *   <li>时效：{@code expMillis} 早于当前时间即过期，由调用方传入 {@code nowMillis}
 *       （便于测试注入时钟，与 MutableClock 测试范式一致）。</li>
 * </ul>
 *
 * <p><b>安全边界</b>：验签恒定时间比较（防时序侧信道）；密钥为空/令牌形态非法/数字或枚举
 * 解析失败一律按无效处理，不向调用方泄露失败细节（由服务层统一文案）。
 */
public final class UnsubscribeTokenRules {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PAYLOAD_SEPARATOR = "|";
    private static final int PAYLOAD_FIELDS = 5;
    private static final int NONCE_CHARS = 8;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private UnsubscribeTokenRules() {
    }

    /** 验签通过且未过期的退订目标。 */
    public record UnsubscribeTarget(long userId, Channel channel, String msgType, long expMillis) {
    }

    /**
     * 铸造退订令牌。
     *
     * @param secret    HMAC 密钥（调用方保证非空白）
     * @param userId    退订目标用户
     * @param channel   退订目标渠道（调用方保证在可设偏好白名单内）
     * @param msgType   退订目标消息类型（调用方保证已归一）
     * @param expMillis 过期时间戳（毫秒）
     * @return 点分三段令牌（URL 安全，可直接拼入查询串）
     */
    public static String mint(String secret, long userId, Channel channel, String msgType,
                              long expMillis) {
        String payload = userId + PAYLOAD_SEPARATOR + channel.name() + PAYLOAD_SEPARATOR
                + msgType + PAYLOAD_SEPARATOR + expMillis + PAYLOAD_SEPARATOR + newNonce();
        String body = VERSION + "." + encode(payload);
        return body + "." + sign(secret, body);
    }

    /**
     * 验签并解析退订令牌。
     *
     * @param secret     HMAC 密钥（空白按无效处理：功能未配置时任何令牌都不可用）
     * @param token      待验令牌
     * @param nowMillis 当前时间（毫秒，测试可注入）
     * @return 校验通过的目标；无效、篡改、过期均返回 {@code empty}
     */
    public static Optional<UnsubscribeTarget> verify(String secret, String token, long nowMillis) {
        if (secret == null || secret.isBlank() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        String body = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(secret, body), parts[2])) {
            return Optional.empty();
        }
        String payload = decode(parts[1]);
        if (payload == null) {
            return Optional.empty();
        }
        // -1 保留尾段：msgType 含分隔符或段数不符均按无效处理（签名只保证未被篡改，
        // 不保证 payload 语义合法——语义合法性由解析失败路径拒绝）
        String[] fields = payload.split("\\" + PAYLOAD_SEPARATOR, -1);
        if (fields.length != PAYLOAD_FIELDS) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(fields[0]);
            long exp = Long.parseLong(fields[3]);
            Channel channel = Channel.valueOf(fields[1]);
            String msgType = fields[2];
            if (msgType.isBlank() || exp <= nowMillis) {
                return Optional.empty();
            }
            return Optional.of(new UnsubscribeTarget(userId, channel, msgType, exp));
        } catch (IllegalArgumentException ex) {
            // 数字或渠道枚举解析失败：令牌被构造或为历史格式，按无效处理
            return Optional.empty();
        }
    }

    private static String newNonce() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String encode(String payload) {
        return URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        try {
            return new String(URL_DECODER.decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(digest);
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
