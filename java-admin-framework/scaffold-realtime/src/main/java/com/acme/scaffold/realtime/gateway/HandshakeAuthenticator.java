package com.acme.scaffold.realtime.gateway;

/**
 * 握手鉴权端口（对应设计稿 §3.2：短期令牌 + 防重放）。
 *
 * <p>生产实现：{@code auth.HmacTokenAuthenticator}（HMAC 短期令牌，校验 exp/iat）；
 * 框架默认实现：{@code SpringHandshakeAuthenticator}（复用既有 JWT 解析端口）。
 * 演示/测试实现：{@code auth.AllowAllAuthenticator}（放行匿名连接）。
 */
public interface HandshakeAuthenticator {

    /**
     * 鉴权。
     *
     * @param token 握手携带的令牌（query 参数或子协议头）
     * @param now   当前时间（防重放与过期判定的时钟源）
     */
    AuthResult authenticate(String token, long now);

    /** 鉴权结果。 */
    record AuthResult(boolean ok, String userId, String reason) {

        public static AuthResult ok(String userId) {
            return new AuthResult(true, userId, null);
        }

        public static AuthResult reject(String reason) {
            return new AuthResult(false, null, reason);
        }
    }
}
