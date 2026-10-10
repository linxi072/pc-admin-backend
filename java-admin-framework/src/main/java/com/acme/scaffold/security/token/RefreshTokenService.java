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

    /**
     * 吊销某用户在指定客户端（设备）上的全部刷新令牌，返回受影响行数。
     * <p>用于「账号安全 - 登录设备」的单个设备下线。
     * <p>安全说明：只能由服务端在鉴权后调用，且必须同时限定 userId，
     * 防止用户 A 通过伪造 clientId 吊销用户 B 的会话。
     *
     * @return 受影响的会话行数；0 表示该设备不存在或已失效
     */
    int revokeByUserAndClient(Long userId, String clientId);

    /**
     * 按会话精确吊销单个登录会话，返回受影响行数。
     * <p>用于「账号安全 - 登录设备」下线某一次登录。
     *
     * <p><b>为何不用 clientId</b>：clientId 表示客户端类型（如 web），同一浏览器多次登录
     * 会共享同一个 clientId，按它吊销会误伤其他会话；sessionId 才是「一次登录」的准确标识。
     *
     * @param userId 当前用户，限定范围防止越权吊销他人会话
     * @param sessionId 目标会话标识
     * @return 受影响行数；0 表示会话不存在或已失效
     */
    int revokeBySession(Long userId, String sessionId);

    /**
     * 统计某用户当前活跃会话数（未吊销且未过期）。用于多端登录并发限制。
     *
     * @param userId 当前用户
     * @return 活跃会话数（每个活跃刷新令牌代表一个在线设备/会话）
     */
    int countActiveSessions(Long userId);

    /**
     * 吊销某用户最旧的若干会话，仅保留最近使用的 {@code keepCount} 个。
     * 用于多端登录限制策略 evict_oldest：保留 {@code keepCount} 个最新会话，其余予以吊销。
     *
     * <p>排序依据：先按 {@code last_used_at} 降序（越近使用的越保留），
     * 缺失时回退到 {@code issued_at} 降序；仅作用于未吊销且未过期的会话。
     *
     * @param userId    当前用户
     * @param keepCount 保留的最近使用会话数（自动收敛为不小于 0）
     */
    void revokeOldestSessions(Long userId, int keepCount);
}
