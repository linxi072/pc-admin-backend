package com.acme.scaffold.observability.dto;

/**
 * 可观测性运行态摘要：本地 span 记录器与 OTLP 导出器双链路开关及留存统计。
 *
 * @param recorderEnabled 本地记录器是否启用
 * @param capacity        本地缓冲容量上限
 * @param retained        当前留存 span 条数
 * @param okCount         历史累计 OK 条数（含已被淘汰的）
 * @param errorCount      历史累计 ERROR 条数（含已被淘汰的）
 * @param unsetCount      历史累计 UNSET 条数（含已被淘汰的）
 * @param exporterEnabled OTLP Collector 导出是否启用
 */
public record TraceSummaryView(boolean recorderEnabled, int capacity, int retained,
                               long okCount, long errorCount, long unsetCount,
                               boolean exporterEnabled) {
}
