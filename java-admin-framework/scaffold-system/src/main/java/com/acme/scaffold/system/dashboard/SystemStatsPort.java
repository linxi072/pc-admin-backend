package com.acme.scaffold.system.dashboard;

/**
 * 系统统计数据源端口（M27）：屏蔽底层 jOOQ 细节，便于离线单测以假实现替换。
 *
 * <p>各方法返回非负行数（底层查询异常由适配器兜底为 0，统计类查询失败不应阻断看板）。
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

    /** 今日登录日志数（login_at >= 当日 00:00）。 */
    long countLoginLogsToday();
}
