package com.acme.scaffold.security.token;

/**
 * 刷新令牌服务：使用不透明令牌 + 哈希存储 + 家族轮换（refresh token rotation），
 * 一旦检测到已吊销令牌被再次使用，即吊销整个家族以阻止令牌盗用。
 */
public interface RefreshTokenService {

    /** 签发新刷新令牌，返回原始令牌（仅此一次返回给客户端）。 */
    String issue(Long userId, String clientId, String deviceId, String userAgent, String ip);

    /** 校验刷新令牌，返回对应用户 ID；无效/过期/已吊销则抛异常。 */
    Long verify(String rawToken);

    /** 轮换：校验旧令牌后签发新令牌并令旧令牌失效，返回新原始令牌。 */
    String rotate(String rawToken);

    /** 吊销某用户所有刷新令牌（登出/改密时使用）。 */
    void revokeByUser(Long userId);
}
