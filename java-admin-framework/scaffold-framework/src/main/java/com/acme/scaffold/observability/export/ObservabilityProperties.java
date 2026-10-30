package com.acme.scaffold.observability.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可观测性导出配置。前缀 {@code observability}。
 *
 * <p>本项目离线构建未引入 {@code opentelemetry-sdk}（离线仓库未收录相关构件），
 * 故采用零依赖的 OTLP/HTTP 自研导出器（{@link OtlpSpanExporter}），
 * 与既有 W3C {@code traceparent} / MDC {@code traceId} 对齐。
 * 在具备 Collector（Jaeger / Tempo / OTel Collector）的环境中，将 {@code otlp.enabled} 置 true 即可导出。
 */
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private final Otlp otlp = new Otlp();
    private final Recorder recorder = new Recorder();

    public Otlp getOtlp() {
        return otlp;
    }

    public Recorder getRecorder() {
        return recorder;
    }

    public static class Otlp {
        /** 是否启用 OTLP/HTTP span 导出。默认关闭，需显式开启并配置 collector 端点。 */
        private boolean enabled = false;
        /** OTLP/HTTP traces 端点（Collector 接收地址，如 http://localhost:4318/v1/traces）。 */
        private String endpoint = "http://localhost:4318/v1/traces";
        /** 资源属性 service.name。 */
        private String serviceName = "java-admin-framework";
        /** 导出 HTTP 超时（毫秒）。 */
        private int timeoutMillis = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public int getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }
    }

    public static class Recorder {
        /** 是否启用了本地 span 记录（内存有界缓冲）。与 OTLP 导出开关正交，两者可独立开启。 */
        private boolean enabled = true;
        /** 本地留存的最大 span 条数，超出后按入队序淘汰最旧记录。 */
        private int capacity = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }
}
