package com.acme.scaffold.monitor.health;

import java.util.Map;

/**
 * 探针结果。统一结构，便于监控平台解析：
 * <pre>{@code {"status":"UP","message":"ok","details":{...}}}</pre>
 */
public record ProbeResult(ProbeStatus status, String message, Map<String, Object> details) {

    public static ProbeResult up(Map<String, Object> details) {
        return new ProbeResult(ProbeStatus.UP, "ok", details);
    }

    public static ProbeResult down(String message, Map<String, Object> details) {
        return new ProbeResult(ProbeStatus.DOWN, message, details);
    }
}
