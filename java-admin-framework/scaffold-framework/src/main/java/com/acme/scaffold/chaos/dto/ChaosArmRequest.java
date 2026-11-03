package com.acme.scaffold.chaos.dto;

import com.acme.scaffold.chaos.model.ChaosFaultType;

/**
 * 故障注入请求（REST 入参）。字段语义与 {@code ChaosFault} 对齐，id 由服务端生成。
 *
 * @param type          故障类型（必填）
 * @param pathPattern   路径匹配（必填，Ant 风格）
 * @param latencyMillis LATENCY 时延（毫秒，>=0，缺省 0）
 * @param errorStatus   ERROR 返回状态码（缺省 503）
 * @param probability   触发概率（0~1，缺省 1.0 必触发）
 * @param description   可读说明（可选）
 */
public record ChaosArmRequest(ChaosFaultType type, String pathPattern, Integer latencyMillis,
                             Integer errorStatus, Double probability, String description) {

    public int latencyMillisOrDefault() {
        return latencyMillis == null ? 0 : latencyMillis;
    }

    public int errorStatusOrDefault() {
        return errorStatus == null ? 503 : errorStatus;
    }

    public double probabilityOrDefault() {
        return probability == null ? 1.0 : probability;
    }
}
