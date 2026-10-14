package com.acme.scaffold.monitor;

/**
 * 数据库连接池统计（V29 连接池监控页数据源）。
 *
 * <p>数据源为 HikariCP 的 {@code HikariPoolMXBean}（spring-boot-starter-jdbc 默认连接池，
 * actuator 亦自动暴露其 micrometer 指标）。若运行环境切换到其他 DataSource 实现，
 * {@code MonitorService#datasourceStats()} 回退为「仅配置项」快照，counts 各字段为 -1。
 *
 * @param poolName       连接池名称
 * @param activeCount    活跃连接数（-1 表示不可用）
 * @param idleCount      空闲连接数（-1 表示不可用）
 * @param totalCount     active + idle（-1 表示不可用）
 * @param threadsAwaiting 等待连接的线程数（-1 表示不可用）
 * @param usageRate      使用率 active / maximumPoolSize（-1 表示不可用）
 * @param maximumPoolSize 配置的最大连接数
 * @param minimumIdle    配置的最小空闲连接数
 * @param connectionTimeoutMillis 获取连接超时（毫秒）
 * @param validationTimeoutMillis 连接活性校验超时（毫秒）
 * @param idleTimeoutMillis       空闲连接回收超时（毫秒）
 * @param maxLifetimeMillis       连接最大存活时长（毫秒）
 * @param jdbcUrl         连接地址（展示用）
 */
public record PoolStatView(
        String poolName,
        long activeCount,
        long idleCount,
        long totalCount,
        long threadsAwaiting,
        double usageRate,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMillis,
        long validationTimeoutMillis,
        long idleTimeoutMillis,
        long maxLifetimeMillis,
        String jdbcUrl) {

    /** 计数不可用时的哨兵值（非 Hikari 数据源回退场景）。 */
    public static final long UNAVAILABLE = -1L;

    public static PoolStatView configOnly(String poolName, int maximumPoolSize, int minimumIdle,
                                          long connectionTimeoutMillis, long validationTimeoutMillis,
                                          long idleTimeoutMillis, long maxLifetimeMillis,
                                          String jdbcUrl) {
        return new PoolStatView(poolName, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE,
                UNAVAILABLE, maximumPoolSize, minimumIdle, connectionTimeoutMillis,
                validationTimeoutMillis, idleTimeoutMillis, maxLifetimeMillis, jdbcUrl);
    }
}
