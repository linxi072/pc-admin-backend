package com.acme.scaffold.system.notification;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * 基于 Spring {@link RestTemplate} 的 Webhook 实现（POST JSON）。
 * 连接 / 读超时由 {@code app.notifications.webhook.timeout-ms} 与
 * {@code connect-timeout-ms} 控制（默认 5000 / 2000ms）。
 */
@Component
public class RestTemplateWebhookClient implements WebhookClient {

    private final RestTemplate restTemplate;

    public RestTemplateWebhookClient(RestTemplateBuilder builder, WebhookProperties properties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    @Override
    public void post(String url, Map<String, Object> body) {
        restTemplate.postForEntity(url, body, Void.class);
    }
}
