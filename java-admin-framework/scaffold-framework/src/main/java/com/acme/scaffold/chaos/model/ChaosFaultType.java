package com.acme.scaffold.chaos.model;

/**
 * 混沌故障类型。
 *
 * <ul>
 *   <li>{@code LATENCY}：对匹配的请求注入固定时延（模拟下游慢调用）。</li>
 *   <li>{@code ERROR}：对匹配的请求直接返回指定 HTTP 错误状态码（模拟依赖故障）。</li>
 *   <li>{@code EXCEPTION}：对匹配的请求抛未捕获异常（模拟进程内异常，容器映射为 500）。</li>
 * </ul>
 */
public enum ChaosFaultType {
    LATENCY,
    ERROR,
    EXCEPTION
}
