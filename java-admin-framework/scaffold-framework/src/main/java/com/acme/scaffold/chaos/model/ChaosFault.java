package com.acme.scaffold.chaos.model;

/**
 * 混沌故障规则（不可变快照）。由 {@code ChaosService} 在 arm 时生成 id 后登记到 {@code ChaosRegistry}。
 *
 * @param id            故障唯一标识（arm 时生成，用于 disarm）
 * @param type          故障类型（LATENCY / ERROR / EXCEPTION）
 * @param pathPattern   Ant 风格路径匹配（如 {@code /api/pay/**}、`/api/users/*`）
 * @param latencyMillis LATENCY 类型注入的时延（毫秒，>=0）
 * @param errorStatus   ERROR 类型返回的 HTTP 状态码（如 503）
 * @param probability   触发概率（0~1，1 表示必定触发）
 * @param description   可读说明（便于回溯该故障的注入意图）
 */
public record ChaosFault(String id, ChaosFaultType type, String pathPattern,
                        int latencyMillis, int errorStatus, double probability,
                        String description) {

    /** 当前故障是否应触发（按概率抽样）。 */
    public boolean shouldTrigger() {
        return Math.random() < probability;
    }
}
