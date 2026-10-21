package com.acme.scaffold.monitor.health;

/**
 * 探针状态。{@code UP} 表示健康；{@code DOWN} 表示不健康
 * （liveness 返回 DOWN 将触发容器重启，readiness 返回 DOWN 将把实例从负载均衡摘除）。
 */
public enum ProbeStatus {
    UP,
    DOWN
}
