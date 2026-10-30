package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.notification.config.SmsProperties;
import com.acme.scaffold.system.notification.port.SmsGateway;
import com.acme.scaffold.system.notification.rules.SmsRules;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 HTTP 短信网关：POST JSON 到 {@code app.notifications.sms.gateway-url}。
 * 报文为厂商无关最小契约（appId / appKey / mobiles / content / timestamp），
 * 连接 / 读超时由 {@code connect-timeout-ms} / {@code timeout-ms} 控制。
 *
 * <p>对接具体厂商（阿里云 / 腾讯云等）时：若厂商 API 与最小契约兼容可直接配置地址使用；
 * 否则以本类为模板新增一个 {@link SmsGateway} 实现并扩展 provider 选择，
 * 规则层（SmsRules）与编排层（NotificationService）无需改动。
 */
@Component
@ConditionalOnProperty(name = "app.notifications.sms.provider", havingValue = "HTTP")
public class HttpSmsGateway implements SmsGateway {

    private final RestTemplate restTemplate;
    private final SmsProperties properties;

    public HttpSmsGateway(RestTemplateBuilder builder, SmsProperties properties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        this.properties = properties;
    }

    @Override
    public void send(SmsRules.SmsDraft draft) {
        if (properties.getGatewayUrl() == null || properties.getGatewayUrl().isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "短信网关地址未配置（app.notifications.sms.gateway-url）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", properties.getAppId());
        body.put("appKey", properties.getAppKey());
        body.put("mobiles", draft.mobiles());
        body.put("content", draft.content());
        body.put("timestamp", System.currentTimeMillis());
        restTemplate.postForEntity(properties.getGatewayUrl(), body, Void.class);
    }
}
