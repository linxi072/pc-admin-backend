package com.acme.scaffold.system.notification.rules;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Webhook 外发 HMAC 签名规则（M18）：对「外发报文 + 时间戳 + 一次性 nonce」做 HMAC-SHA256 签名，
 * 由 {@code RestTemplateWebhookClient} 注入请求头、由接收方（或本框架校验器）验签，
 * 取代原先「明文 secret 透传报文」的不安全做法。
 *
 * <p><b>签名输入</b>（点分三段，与 {@code UnsubscribeTokenRules} 同范式，避免引入额外依赖）：
 * <pre>
 *   sign = base64url(HmacSHA256(key, timestampMillis + "." + nonce + "." + rawBody))
 * </pre>
 * 签名覆盖<b>原始报文字节</b>（即实际发出的 JSON 字符串），而非重新序列化的规范形——
 * 规避「JSON 重排/空格差异导致验签不一致」的经典陷阱；接收方对收到的原始 body 直接验签即可。</p>
 *
 * <p><b>防重放</b>：
 * <ul>
 *   <li>{@code timestamp}（毫秒）+ 时间戳新鲜度窗口（默认建议 5 分钟）：拦截过期重放与未来时钟漂移；</li>
 *   <li>{@code nonce}（每次请求唯一随机串）：配合接收方 nonce 去重表可彻底杜绝窗口内重放——
 *       去重表属接收方职责（本仓仅提供签名/验签原语，不在发送侧维护全局状态）。</li>
 * </ul>
 * </p>
 *
 * <p><b>请求头契约</b>（接收方据此取参）：
 * <ul>
 *   <li>{@link #SIGNATURE_HEADER} = {@code "sha256=<base64url>"}；</li>
 *   <li>{@link #TIMESTAMP_HEADER}、{@link #NONCE_HEADER} 分别承载时间戳与 nonce，
 *       验签时须原样回传同一笔报文对应的两个值。</li>
 * </ul>
 * </p>
 *
 * <p><b>安全边界</b>：验签恒定时间比较（防时序侧信道）；密钥为空/参数缺失/形态非法一律按无效处理；
 * 无副作用、不依赖 Spring 上下文与数据库，可直接离线单测（与 M15 退订令牌规则同范式）。</p>
 */
public final class WebhookSignatureRules {

    /** 签名头：{@code sha256=<base64url>}。 */
    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    /** 时间戳头（毫秒，字符串）。 */
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    /** 一次性 nonce 头。 */
    public static final String NONCE_HEADER = "X-Webhook-Nonce";
    /** 签名方案前缀。 */
    public static final String SCHEME = "sha256=";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private WebhookSignatureRules() {
    }

    /**
     * 计算报文签名。
     *
     * @param key        HMAC 密钥（调用方保证非空白）
     * @param timestamp  时间戳（毫秒）
     * @param nonce      一次性随机串（调用方保证非空白）
     * @param rawBody    原始报文（即实际发出的 JSON 字符串，不可再序列化）
     * @return base64url 编码的 HMAC 摘要
     */
    public static String sign(String key, long timestamp, String nonce, String rawBody) {
        String signed = timestamp + "." + nonce + "." + rawBody;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 不可用", ex);
        }
    }

    /**
     * 将原始签名封装为请求头值（带方案前缀）。
     *
     * @param signature {@link #sign} 的返回值
     */
    public static String buildHeader(String signature) {
        return SCHEME + signature;
    }

    /**
     * 剥去方案前缀，取纯签名串（头部形态不合法按空白处理）。
     */
    public static String stripScheme(String headerValue) {
        if (headerValue == null) {
            return "";
        }
        if (headerValue.startsWith(SCHEME)) {
            return headerValue.substring(SCHEME.length());
        }
        return headerValue;
    }

    /**
     * 生成一次性 nonce（12 字节随机，base64url 无填充，URL 安全）。
     */
    public static String newNonce() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * 验签：密钥匹配 + 签名一致 + 时间戳新鲜。
     *
     * @param key               HMAC 密钥（空白按无效处理：未配置时任何签名都不可用）
     * @param timestampHeader   请求头时间戳（毫秒字符串）
     * @param nonceHeader       请求头 nonce
     * @param signatureHeader   请求头签名（{@code sha256=<sig>} 或裸 sig）
     * @param rawBody           接收方拿到的原始报文（与签名时同一字节）
     * @param maxAgeMillis      时间戳新鲜度窗口（{@code nowMillis - ts > maxAgeMillis} 即过期）
     * @param nowMillis         当前时间（毫秒，测试可注入）
     * @return 全部通过返回 {@code true}；任一不符返回 {@code false}
     */
    public static boolean verify(String key, String timestampHeader, String nonceHeader,
                                 String signatureHeader, String rawBody, long maxAgeMillis, long nowMillis) {
        if (key == null || key.isBlank() || timestampHeader == null || timestampHeader.isBlank()
                || nonceHeader == null || nonceHeader.isBlank()
                || signatureHeader == null || signatureHeader.isBlank()
                || rawBody == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        // 新鲜度：过期（或被显著预置的未来时钟）一律拒绝；允许 5s 正向漂移容错
        if (nowMillis - ts > maxAgeMillis || ts - nowMillis > 5_000L) {
            return false;
        }
        String expected = sign(key, ts, nonceHeader, rawBody);
        String provided = stripScheme(signatureHeader.trim());
        return constantTimeEquals(expected, provided);
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
