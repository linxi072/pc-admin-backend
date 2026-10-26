package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;

/**
 * 运营总览快照（M27 报表大屏 / 后端）。
 *
 * <p>聚合系统域核心计数（租户/用户/角色/菜单/在线会话/幂等记录/操作日志/今日登录），
 * 以及来自 {@code scaffold-framework} 的跨模块可观测指标（N+1 疑似）。
 * 所有计数为非负长整型；{@code generatedAt} 为生成时刻（epoch 毫秒），便于前端展示「数据时间」。
 * 跨模块指标通过 {@link ObservabilitySnapshot} 解耦获取，缺源时回落全零，不影响系统域统计。</p>
 *
 * @param tenantCount            租户总数
 * @param userCount              用户总数（不含已删除）
 * @param roleCount              角色总数
 * @param menuCount              菜单总数
 * @param onlineSessionCount    当前在线会话数（status=ONLINE）
 * @param idempotencyRecordCount 幂等记录总数
 * @param operationLogCount      操作日志（审计）总数
 * @param loginLogTodayCount     今日登录日志数
 * @param nPlusOneSuspectTotal   N+1 疑似累计越限次数（跨模块指标，缺源为 0）
 * @param nPlusOneSuspectRetained N+1 疑似当前留存条数（跨模块指标，缺源为 0）
 * @param lastBreachAtMillis     最近一次 N+1 越限时间（epoch 毫秒，缺源/未越限为 0）
 * @param generatedAt            快照生成时刻（epoch 毫秒）
 */
public record DashboardOverview(
        long tenantCount,
        long userCount,
        long roleCount,
        long menuCount,
        long onlineSessionCount,
        long idempotencyRecordCount,
        long operationLogCount,
        long loginLogTodayCount,
        long nPlusOneSuspectTotal,
        long nPlusOneSuspectRetained,
        long lastBreachAtMillis,
        long generatedAt) {
}
