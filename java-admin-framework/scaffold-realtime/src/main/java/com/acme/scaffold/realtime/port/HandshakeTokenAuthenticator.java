package com.acme.scaffold.realtime.port;

/**
 * 握手令牌鉴权端口（防腐层 / SPI）。
 *
 * <p>WebSocket 握手无法使用 Authorization 头（浏览器 WS API 不支持自定义头），令牌只能走
 * query 参数，因此握手鉴权由本端口承接。realtime 模块只依赖本端口，<b>不依赖具体安全实现</b>；
 * JWT 解析由认证模块（{@code com.acme.scaffold.security.jwt}）以适配器形式注入，
 * 从而保持 realtime → security 的单向依赖（避免与 system 模块成环）。
 */
public interface HandshakeTokenAuthenticator {

    /**
     * 解析握手令牌并返回用户 ID。
     *
     * @param token 握手 query 中的原始令牌；允许为 {@code null}
     * @return 用户 ID；令牌缺失、无效或已过期时返回 {@code null}（调用方据此拒绝握手）
     */
    Long resolveUserId(String token);
}
