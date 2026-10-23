package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.system.notification.config.WebhookProperties;
import com.acme.scaffold.system.notification.port.WebhookClient;
import com.acme.scaffold.system.notification.rules.WebhookSignatureRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * 基于 Spring {@link RestTemplate} 的 Webhook 实现（POST JSON）。
 * 连接 / 读超时由 {@code app.notifications.webhook.timeout-ms} 与
 * {@code connect-timeout-ms} 控制（默认 5000 / 2000ms）。
 *
 * <p><b>M18 签名</b>：当 {@code app.notifications.webhook.signing-key} 非空白时，
 * 对实际发出的原始 JSON 报文按 {@link WebhookSignatureRules} 生成 HMAC-SHA256 签名，
 * 注入 {@code X-Webhook-Signature}/{@code X-Webhook-Timestamp}/{@code X-Webhook-Nonce}
 * 三头（防篡改 + 时间戳/nonce 防重放）；留空则不做签名（向下兼容明文透传）。</p>
 */
@Component
public class RestTemplateWebhookClient implements WebhookClient {

    private final RestTemplate restTemplate;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;

    public RestTemplateWebhookClient(RestTemplateBuilder builder, WebhookProperties properties,
                                     ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void post(String url, Map<String, Object> body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Webhook 报文序列化失败", ex);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String signingKey = properties.getSigningKey();
        if (signingKey != null && !signingKey.isBlank()) {
            long ts = System.currentTimeMillis();
            String nonce = WebhookSignatureRules.newNonce();
            String signature = WebhookSignatureRules.sign(signingKey, ts, nonce, json);
            headers.set(WebhookSignatureRules.SIGNATURE_HEADER, WebhookSignatureRules.buildHeader(signature));
            headers.set(WebhookSignatureRules.TIMESTAMP_HEADER, String.valueOf(ts));
            headers.set(WebhookSignatureRules.NONCE_HEADER, nonce);
        }
        restTemplate.postForEntity(url, new HttpEntity<>(json, headers), Void.class);
    }
}
