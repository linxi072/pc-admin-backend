package com.acme.scaffold.monitor.alert;

import java.util.List;
import java.util.Map;

/**
 * Alertmanager webhook 推送体（兼容 v3 告警格式）。
 *
 * <p>仅声明实际使用的字段；Alertmanager 发送的其它字段（externalURL、commonLabels 等）
 * 由 Jackson 默认忽略，不影响反序列化。详见
 * https://prometheus.io/docs/alerting/latest/configuration/#webhook_config
 */
public class AlertPayload {

    public String version;
    public String groupKey;
    public String status;       // "firing" | "resolved"
    public String receiver;
    public List<Alert> alerts;

    public static class Alert {
        public String status;
        public Map<String, String> labels;
        public Map<String, String> annotations;
        public String startsAt;
        public String endsAt;
        public String generatorURL;
        public String fingerprint;
    }
}
