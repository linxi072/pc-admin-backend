package com.acme.scaffold.security.token;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多端登录限制纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p>决策逻辑：
 * <ul>
 *   <li>{@code maxSessions <= 0} 表示不限制，恒返回 {@link Decision#ALLOW}；</li>
 *   <li>当前活跃会话数 {@code activeSessions < maxSessions} 时允许新登录；</li>
 *   <li>已达上限时按策略处理：{@code REJECT} 拒绝新登录，{@code EVICT_OLDEST} 踢掉最早登录的会话以腾出名额。</li>
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
        /** 踢掉最早登录的会话，为新登录腾出名额（会话挤占）。 */
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

    /** 一次登录会话的轻量视图，用于纯函数挑选待淘汰会话。 */
    public record SessionInfo(Long id, LocalDateTime issuedAt, LocalDateTime lastUsedAt) {
    }

    /**
     * 从活跃会话中挑选需要淘汰的会话（纯函数，便于单测）。
     *
     * <p>规则：按<b>登录时间（issuedAt）升序</b>排序，最早登录的排在最前、最先被淘汰；
     * 登录时间相同时按最近使用时间（lastUsedAt）升序兜底。保留 {@code keepCount} 个<b>最晚登录</b>的会话，
     * 其余（更早上登录的）返回其 id 列表。
     *
     * <p>该语义对应「会话挤占」：新登录占用名额，最早上线的会话被挤出。
     *
     * @param sessions  当前活跃会话（未吊销且未过期）
     * @param keepCount 保留的最晚登录会话数（自动收敛为不小于 0）
     * @return 需要淘汰（吊销）的会话 id 列表，按登录时间升序
     */
    public static List<Long> selectToEvict(List<SessionInfo> sessions, int keepCount) {
        if (keepCount < 0) {
            keepCount = 0;
        }
        if (sessions == null || sessions.size() <= keepCount) {
            return List.of();
        }
        List<SessionInfo> sorted = new ArrayList<>(sessions);
        Comparator<SessionInfo> byIssued = Comparator.comparing(
                SessionInfo::issuedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<SessionInfo> byUsed = Comparator.comparing(
                SessionInfo::lastUsedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        sorted.sort(byIssued.thenComparing(byUsed));
        int evictCount = sorted.size() - keepCount;
        return sorted.subList(0, evictCount).stream().map(SessionInfo::id).toList();
    }
}
