package com.acme.scaffold.monitor.dto;

/**
 * N+1 探测运行态视图（探测阈值、留存容量与累计口径）。
 *
 * @param threshold               同构 SQL 单线程内执行达该值即判定疑似 N+1（{@code app.jooq.nplus1-threshold}）
 * @param capacity                疑似事件留存容量（有界，{@code app.jooq.nplus1-max-suspects}）
 * @param registrySize            当前留存条数
 * @param totalBreaches           自进程启动以来累计越限次数（单调递增，清空不清零）
 * @param lastBreachAtEpochMillis 最近一次越限登记时间（epoch 毫秒）；从未越限为 0
 */
public record NPlusOneStatusView(int threshold, int capacity, int registrySize, long totalBreaches,
                                 long lastBreachAtEpochMillis) {
}
