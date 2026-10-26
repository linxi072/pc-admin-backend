package com.acme.scaffold.system.dashboard;

/**
 * 运营总览快照（M27 报表大屏 / 后端第一刀）。
 *
 * <p>聚合系统域核心计数（租户/用户/角色/菜单/在线会话/幂等记录/操作日志/今日登录），
 * 所有计数为非负长整型；{@code generatedAt} 为生成时刻（epoch 毫秒），便于前端展示「数据时间」。
 * N+1 疑似 / 链路追踪等来自 scaffold-framework 的指标，将在引入跨模块统计端口后补齐。</p>
 *
 * @param tenantCount            租户总数
 * @param userCount              用户总数（不含已删除）
 * @param roleCount              角色总数
 * @param menuCount              菜单总数
 * @param onlineSessionCount    当前在线会话数（status=ONLINE）
 * @param idempotencyRecordCount 幂等记录总数
 * @param operationLogCount      操作日志（审计）总数
 * @param loginLogTodayCount     今日登录日志数
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
        long generatedAt) {
}
