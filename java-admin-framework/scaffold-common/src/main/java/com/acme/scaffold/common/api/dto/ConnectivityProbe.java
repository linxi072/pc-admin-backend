package com.acme.scaffold.common.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 联通探针响应载体：供前端探测后端存活、时钟与多语言协商结果。
 *
 * <p>字段约定（与前端 {@code src/api/connectivity.js} 严格对齐）：
 * <ul>
 *   <li>{@code serverTime}：后端 UTC 时钟，前端用于时钟漂移粗校验；</li>
 *   <li>{@code receivedLocale}：后端经 {@code LocaleContextHolder} 解析到的语言标签，前端用于确认多语言协商生效；</li>
 *   <li>{@code nonce}：每次请求随机串，前端可用于判别是否为同一响应（防缓存误判联通）；</li>
 *   <li>{@code framework}：后端框架标识，联通测试断言固定为 {@code java-admin-framework}。</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectivityProbe(
        Instant serverTime,
        String receivedLocale,
        String nonce,
        String framework) {
}
