package com.acme.scaffold.observability.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 零依赖 OTLP/HTTP span 导出器（不使用官方 opentelemetry-sdk，以兼容离线构建）。
 *
 * <p>通过 JDK 内置 {@link HttpClient} 将 span 以 OTLP/HTTP JSON 推送到 Collector
 * （Jaeger / Tempo / OTel Collector）。默认关闭（{@code observability.otlp.enabled=false}）；
 * 开启后需配置 Collector 端点。导出失败仅记录日志，绝不抛异常影响主请求链路。
 */
public class OtlpSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(OtlpSpanExporter.class);

    private final boolean enabled;
    private final String endpoint;
    private final String serviceName;
    private final int timeoutMillis;
    private final HttpClient httpClient;

    public OtlpSpanExporter(boolean enabled, String endpoint, String serviceName, int timeoutMillis) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.serviceName = serviceName;
        this.timeoutMillis = timeoutMillis;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void export(List<OtlpSpan> spans) {
        if (!enabled || spans == null || spans.isEmpty()) {
            return;
        }
        try {
            String body = OtlpSpanSerializer.toJson(spans, serviceName);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("OTLP span 导出返回非 2xx 状态：{}（endpoint={}）", resp.statusCode(), endpoint);
            }
        } catch (Exception e) {
            log.warn("OTLP span 导出失败（endpoint={}）：{}", endpoint, e.getMessage());
        }
    }
}
