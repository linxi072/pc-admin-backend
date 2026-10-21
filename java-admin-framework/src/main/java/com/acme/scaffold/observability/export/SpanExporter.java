package com.acme.scaffold.observability.export;

import java.util.List;

/**
 * span 导出抽象。解耦过滤器与具体导出实现，便于单测中使用内存记录器替换真实 HTTP 导出器。
 */
public interface SpanExporter {

    /** 是否已启用导出。未启用时调用方不应收集/传递 span。 */
    boolean isEnabled();

    /** 导出一批 span。实现需保证不抛出受检/非受检异常影响主请求链路（失败仅记录日志）。 */
    void export(List<OtlpSpan> spans);
}
