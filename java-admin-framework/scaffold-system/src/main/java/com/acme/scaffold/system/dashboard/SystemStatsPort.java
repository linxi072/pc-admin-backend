package com.acme.scaffold.system.dashboard;

import java.util.List;

/**
 * 系统统计数据源端口（M27）：屏蔽底层 jOOQ 细节，便于离线单测以假实现替换。
 *
 * <p>计数类方法返回非负行数（底层查询异常由适配器兜底为 0，统计类查询失败不应阻断看板）；
 * 趋势类方法返回稀疏趋势点（仅含有数据的日期），缺数据日的连续补齐由 {@link DashboardRules} 负责。
 * 当前实现见 {@code JooqSystemStatsAdapter}（基于手写 jOOQ DSL）。</p>
 */
public interface SystemStatsPort {

    /** 租户总数。 */
    long countTenants();

    /** 用户总数（不含已删除）。 */
    long countUsers();

    /** 角色总数。 */
    long countRoles();

    /** 菜单总数。 */
    long countMenus();

    /** 当前在线会话数（status=ONLINE）。 */
    long countOnlineSessions();

    /** 幂等记录总数。 */
    long countIdempotencyRecords();

    /** 操作日志（审计）总数。 */
    long countOperationLogs();

    /** 今日登录日志数（occurred_at >= 当日 00:00）。 */
    long countLoginLogsToday();

    /** 近 days 日每日新增用户数（按 created_at 按天聚合，仅含有数据的日期）。 */
    List<TrendPoint> dailyNewUsers(int days);

    /** 近 days 日每日新增租户数（按 created_at 按天聚合，仅含有数据的日期）。 */
    List<TrendPoint> dailyNewTenants(int days);

    /** 近 days 日每日活跃租户数（按 occurred_at 去重 tenant_id 按天聚合，仅含有数据的日期）。 */
    List<TrendPoint> dailyActiveTenants(int days);

    /** 近 days 日每日登录日志数（按 occurred_at 按天聚合，仅含有数据的日期）。 */
    List<TrendPoint> dailyLoginLogs(int days);

    /** 近 days 日每日操作日志（审计）数（按 occurred_at 按天聚合，仅含有数据的日期）。 */
    List<TrendPoint> dailyOperationLogs(int days);

    /** 近 days 日每日通知发送数（按 sent_at 按天聚合，仅含有数据的日期）。 */
    List<TrendPoint> dailyNotifications(int days);
}
