package com.acme.scaffold.common.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 超时探针响应载体：回显本次实际生效的延迟毫秒数。
 *
 * <p>供联通测试断言「请求确实被延迟」：探针/前端以远超自身超时阈值的延迟请求
 * {@code GET /api/connectivity/slow}，在延迟生效期间客户端即已超时断开；
 * 若未超时（阈值大于延迟），则可读到此字段核对服务端实际延迟值。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectivitySlowProbe(int delayMs) {
}
