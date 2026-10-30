package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.system.notification.config.WebhookProperties;
import com.acme.scaffold.system.notification.rules.WebhookSignatureRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RestTemplateWebhookClient} 测试：签名密钥缺失时不挂签名头（向下兼容）；
 * 签名密钥配置时挂 {@code X-Webhook-Signature/Timestamp/Nonce} 三头，且签名可经
 * {@link WebhookSignatureRules#verify} 用「捕获的原始报文 + 头部」还原验签通过。
 *
 * <p>用 Mockito 接管 RestTemplateBuilder/RestTemplate（netty/spring-test 的
 * MockRestServiceServer 在本版本对 mock factory 设超时会失败），仅依赖 JUnit + Mockito。</p>
 */
class RestTemplateWebhookClientTest {

    private WebhookProperties props;
    private ObjectMapper mapper;
    private RestTemplate restTemplate;
    private RestTemplateBuilder builder;
    private RestTemplateWebhookClient client;
    private HttpEntity<String> captured;

    @BeforeEach
    void setUp() {
        props = new WebhookProperties();
        props.setTimeoutMs(5000);
        props.setConnectTimeoutMs(2000);
        mapper = new ObjectMapper();
        restTemplate = mock(RestTemplate.class);
        builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);
        client = new RestTemplateWebhookClient(builder, props, mapper);
    }

    @Test
    void postsWithoutSignatureHeadersWhenSigningKeyAbsent() {
        capturePost();
        client.post("https://hook.example.com/x", Map.of("title", "t"));
        assertNull(captured.getHeaders().getFirst(WebhookSignatureRules.SIGNATURE_HEADER));
        assertNull(captured.getHeaders().getFirst(WebhookSignatureRules.TIMESTAMP_HEADER));
        assertNull(captured.getHeaders().getFirst(WebhookSignatureRules.NONCE_HEADER));
    }

    @Test
    void postsWithValidSignatureHeadersWhenSigningKeySet() {
        props.setSigningKey("test-key");
        capturePost();
        Map<String, Object> body = Map.of("title", "t", "content", "c", "msgType", "ALERT");
        client.post("https://hook.example.com/x", body);

        HttpHeaders headers = captured.getHeaders();
        String sigHeader = headers.getFirst(WebhookSignatureRules.SIGNATURE_HEADER);
        String tsHeader = headers.getFirst(WebhookSignatureRules.TIMESTAMP_HEADER);
        String nonceHeader = headers.getFirst(WebhookSignatureRules.NONCE_HEADER);
        assertNotNull(sigHeader);
        assertTrue(sigHeader.startsWith(WebhookSignatureRules.SCHEME));
        assertNotNull(tsHeader);
        assertNotNull(nonceHeader);
        assertTrue(MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(
                headers.getFirst(HttpHeaders.CONTENT_TYPE)), "报文应为 application/json");

        // 验签：用捕获的原始报文 + 头部还原，验证签名确实覆盖实际发出的字节
        assertTrue(WebhookSignatureRules.verify("test-key", tsHeader, nonceHeader, sigHeader,
                captured.getBody(), 60_000, System.currentTimeMillis()));
    }

    @SuppressWarnings("unchecked")
    private void capturePost() {
        when(restTemplate.postForEntity(eq("https://hook.example.com/x"), any(HttpEntity.class), eq(Void.class)))
                .thenAnswer(invocation -> {
                    captured = (HttpEntity<String>) invocation.getArgument(1);
                    return new ResponseEntity<>(HttpStatus.OK);
                });
    }
}
