package com.acme.scaffold.system.notification.rules;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 机器人渠道（企业微信 / 钉钉）加签规则（M35）：纯函数、无 Spring 依赖、无副作用，可离线单测。
 *
 * <p>两平台签名原语同源：以 secret 为 HMAC 密钥，对 {@code timestamp + "\n" + secret} 做 HMAC-SHA256，
 * 输出 base64。差异仅在于传输形态：
 * <ul>
 *   <li>企业微信：原始 base64（不 URL 编码），作为 query 参数 {@code sign} 追加，并带 {@code timestamp}；</li>
 *   <li>钉钉：base64 再经 URL 编码，同样作为 {@code sign} query 参数追加，并带 {@code timestamp}。</li>
 * </ul>
 * 二者均不引入 {@code sha256=} 前缀（与通用 Webhook 渠道的 {@link WebhookSignatureRules} 不同），
 * 签名统一以 query 参数形式落地，从而直接复用 {@code WebhookClient.post(url, body)} 端口，零新增 HTTP 依赖。</p>
 */
public final class RobotSigningRules {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getEncoder();

    private RobotSigningRules() {
    }

    /**
     * 共享签名原语：{@code base64(HmacSHA256(secret, timestamp + "\n" + secret))}。
     *
     * @param secret    加签密钥（不可为空）
     * @param timestamp 时间戳（毫秒）
     * @return base64 编码的 HMAC 摘要
     */
    public static String sign(String secret, long timestamp) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("机器人加签密钥不可为空");
        }
        String payload = timestamp + "\n" + secret;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 不可用", ex);
        }
    }

    /** 企业微信签名：原始 base64（不 URL 编码）。 */
    public static String signWecom(String secret, long timestamp) {
        return sign(secret, timestamp);
    }

    /** 钉钉签名：base64 再经 URL 编码。 */
    public static String signDingtalk(String secret, long timestamp) {
        return URLEncoder.encode(sign(secret, timestamp), StandardCharsets.UTF_8);
    }

    /** 企业微信：在 {@code baseUrl} 上追加 {@code timestamp} 与 {@code sign} 两个 query 参数（原始 base64）。 */
    public static String buildWecomUrl(String baseUrl, String secret, long timestamp) {
        String s = signWecom(secret, timestamp);
        return append(baseUrl, "timestamp", String.valueOf(timestamp), "sign", s);
    }

    /** 钉钉：在 {@code baseUrl} 上追加 {@code timestamp} 与 URL 编码的 {@code sign} 两个 query 参数。 */
    public static String buildDingtalkUrl(String baseUrl, String secret, long timestamp) {
        String s = signDingtalk(secret, timestamp);
        return append(baseUrl, "timestamp", String.valueOf(timestamp), "sign", s);
    }

    private static String append(String baseUrl, String k1, String v1, String k2, String v2) {
        String sep = (baseUrl == null || baseUrl.indexOf('?') < 0) ? "?" : "&";
        return baseUrl + sep + k1 + "=" + v1 + "&" + k2 + "=" + v2;
    }
}
