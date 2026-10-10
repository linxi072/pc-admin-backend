package com.acme.scaffold.security.token;

/**
 * 多端登录限制纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p>决策逻辑：
 * <ul>
 *   <li>{@code maxSessions <= 0} 表示不限制，恒返回 {@link Decision#ALLOW}；</li>
 *   <li>当前活跃会话数 {@code activeSessions < maxSessions} 时允许新登录；</li>
 *   <li>已达上限时按策略处理：{@code REJECT} 拒绝新登录，{@code EVICT_OLDEST} 踢掉最久未使用的会话以腾出名额。</li>
 * </ul>
 * 计数由调用方基于刷新令牌表（每个活跃刷新令牌代表一个在线设备/会话）提供，本类只做纯决策。
 */
public final class SessionLimitRules {

    private SessionLimitRules() {
    }

    /** 超限处理策略。 */
    public enum Strategy {
        /** 拒绝新登录。 */
        REJECT,
        /** 踢掉最久未使用的会话，为新登录腾出名额。 */
        EVICT_OLDEST
    }

    /** 决策结果。 */
    public enum Decision {
        ALLOW,
        REJECT,
        EVICT_OLDEST
    }

    /**
     * 评估是否允许新登录。
     *
     * @param activeSessions 当前该账号活跃会话数（不含本次将签发的）
     * @param maxSessions    允许的最大并发会话数（<=0 表示不限制）
     * @param strategy       超限处理策略
     * @return 决策结果
     */
    public static Decision evaluate(int activeSessions, int maxSessions, Strategy strategy) {
        if (maxSessions <= 0) {
            return Decision.ALLOW;
        }
        if (activeSessions < maxSessions) {
            return Decision.ALLOW;
        }
        return strategy == Strategy.EVICT_OLDEST ? Decision.EVICT_OLDEST : Decision.REJECT;
    }
}
