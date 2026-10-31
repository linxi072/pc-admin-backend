package com.acme.scaffold.monitor.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.config.cache.CacheStats;
import com.acme.scaffold.config.cache.StatisticsCacheManager;
import com.acme.scaffold.config.datasource.DataSourceRegistry;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.monitor.dto.CacheStatView;
import com.acme.scaffold.monitor.dto.OnlineSessionView;
import com.acme.scaffold.monitor.dto.PoolStatView;
import com.acme.scaffold.monitor.domain.ServerMetrics;
import com.acme.scaffold.security.entity.SysRefreshTokenDO;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.entity.SysMonitorSampleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统监控服务：运行指标、在线会话、异常日志、历史采样查询。
 *
 * <p>在线用户/会话口径：{@code sys_refresh_token} 中「未吊销(revoked_at is null)且未过期(expires_at > now)」
 * 的记录，按 session_id 去重后即为活跃会话；对应 user_id 去重即为在线用户数。
 * 该口径不依赖内存态，重启后依然可追溯。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {
    private static final JooqTables.TableRef USER = JooqTables.SYS_USER;

    private static final JooqTables.TableRef RT =
            JooqTables.SYS_REFRESH_TOKEN;

    /** 异常日志可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> ERROR_LOG_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "moduleCode", "module_code",
            "operationName", "operation_name",
            "operatorName", "operator_name",
            "durationMs", "duration_ms",
            "occurredAt", "occurred_at");

    private final DSLContext dsl;
    private final RuntimeMetricsCollector collector;
    private final TokenVersionService tokenVersionService;
    private final DataSource dataSource;
    /** 多数据源注册表（路由数据源后注入的 DataSource 不再是 Hikari 本体，需经此解包主库）。 */
    private final ObjectProvider<DataSourceRegistry> dataSourceRegistryProvider;
    /** Spring Cache 管理器（带统计装饰）；ObjectProvider 容忍无缓存bean的裁剪上下文（如单测）。 */
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    /** 实时运行指标（CPU/内存/磁盘/JVM）。 */
    public ServerMetrics currentMetrics() {
        return collector.snapshot();
    }

    // ---------------- 缓存监控（V29） ----------------

    /**
     * 缓存统计列表（缓存监控页）：进程内 Token 版本缓存 + Spring Cache 各缓存。
     *
     * <p>Spring Cache 部分来自 {@link StatisticsCacheManager} 的进程内记账，
     * 仅包含本实例实际使用过的缓存（未使用的不占进程内存也无统计意义）；
     * 无 CacheManager bean 时（裁剪上下文）仅返回 Token 版本一项。
     */
    public List<CacheStatView> cacheStats() {
        long[] stats = tokenVersionService.cacheStats();
        long hit = stats[0];
        long miss = stats[1];
        long evict = stats[2];
        List<CacheStatView> views = new ArrayList<>();
        views.add(new CacheStatView(
                "token-version",
                "CONCURRENT_HASH_MAP",
                tokenVersionService.cacheSize(),
                hit,
                miss,
                evict,
                CacheStatView.hitRateOf(hit, miss),
                tokenVersionService.cacheTtlMillis(),
                "Token 版本进程内缓存（权限变更后旧凭证失效判定，TTL 30s）"));
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager instanceof StatisticsCacheManager statistics) {
            for (CacheStats s : statistics.stats()) {
                views.add(new CacheStatView(
                        s.name(),
                        s.type(),
                        s.entries(),
                        s.hitCount(),
                        s.missCount(),
                        s.evictCount(),
                        CacheStatView.hitRateOf(s.hitCount(), s.missCount()),
                        null,
                        "Spring Cache（@Cacheable，TTL 见 app.cache.ttl）"));
            }
        }
        return views;
    }

    /**
     * 清理指定名称的缓存。
     *
     * <p>Spring Cache 仅可清理本实例已使用过的缓存（{@link StatisticsCacheManager#clearCache}）；
     * 未使用过的名称返回 false，由调用方输出 404 语义，避免把「不存在」误报为已清理。
     *
     * @return true=已清理；false=缓存名不存在（调用方据此返回 404 语义）
     */
    public boolean clearCache(String name) {
        if ("token-version".equals(name)) {
            tokenVersionService.clearCache();
            return true;
        }
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager instanceof StatisticsCacheManager statistics) {
            return statistics.clearCache(name);
        }
        return false;
    }

    // ---------------- 连接池监控（V29） ----------------

    /**
     * 数据库连接池统计（V29 连接池监控页）。
     *
     * <p>优先取 HikariCP 的 {@code HikariPoolMXBean} 实时计数；非 Hikari 数据源时
     * 回退为配置项快照（计数为 -1），保证页面在任何数据源实现下都可展示。
     *
     * <p>多数据源装配后注入的 {@link DataSource} 为 {@code RoutingDataSource}
     * （{@code DataSourceConfig} 的 @Primary Bean），不再直接
     * {@code instanceof HikariDataSource}——曾致统计整体回退为 -1、监控页失真，
     * 现经 {@link DataSourceRegistry} 解包主库 Hikari 数据源。
     */
    public PoolStatView datasourceStats() {
        HikariDataSource hikari = resolveHikari();
        if (hikari != null) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            int max = hikari.getMaximumPoolSize();
            long active = pool == null ? PoolStatView.UNAVAILABLE : pool.getActiveConnections();
            long idle = pool == null ? PoolStatView.UNAVAILABLE : pool.getIdleConnections();
            long total = pool == null ? PoolStatView.UNAVAILABLE : pool.getTotalConnections();
            long waiting = pool == null ? PoolStatView.UNAVAILABLE : pool.getThreadsAwaitingConnection();
            double usage = (pool != null && max > 0)
                    ? (double) pool.getActiveConnections() / max
                    : PoolStatView.UNAVAILABLE;
            return new PoolStatView(
                    hikari.getPoolName(),
                    active, idle, total, waiting, usage,
                    max, hikari.getMinimumIdle(),
                    hikari.getConnectionTimeout(), hikari.getValidationTimeout(),
                    hikari.getIdleTimeout(), hikari.getMaxLifetime(),
                    hikari.getJdbcUrl());
        }
        return PoolStatView.configOnly("unknown", -1, -1,
                PoolStatView.UNAVAILABLE, PoolStatView.UNAVAILABLE,
                PoolStatView.UNAVAILABLE, PoolStatView.UNAVAILABLE,
                null);
    }

    /** 解析主库 Hikari 数据源：直连形态直接返回；路由数据源经注册表取主库。 */
    private HikariDataSource resolveHikari() {
        if (dataSource instanceof HikariDataSource direct) {
            return direct;
        }
        DataSourceRegistry registry = dataSourceRegistryProvider.getIfAvailable();
        if (registry != null && registry.primary() instanceof HikariDataSource hikari) {
            return hikari;
        }
        return null;
    }

    /**
     * 在线会话列表。
     *
     * @param keyword 用户名/姓名模糊筛选，为空则不过滤
     * @param limit   返回条数上限，防止会话过多拖慢响应
     */
    public List<OnlineSessionView> onlineSessions(String keyword, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<Condition> conds = new ArrayList<>();
        conds.add(RT.field("revoked_at", LocalDateTime.class).isNull());
        conds.add(RT.field("expires_at", LocalDateTime.class).gt(now));

        // limit 下推至 SQL（取宽松上限后仍在内存按会话去重 + 关键字过滤 + limit 截断）：
        // 避免活跃会话量大时把全部刷新令牌拉进堆内。按 last_used_at 倒序取最近窗口，
        // 语义上等价于「最近活跃的会话」。
        int sqlCap = Math.min(Math.max(limit * 10, 500), 5000);
        List<SysRefreshTokenDO> tokens = JooqWriters.fetchTop(dsl, RT,
                SysRefreshTokenDO.class, DSL.and(conds), sqlCap,
                RT.field("last_used_at", LocalDateTime.class).desc().nullsLast());

        // 按 sessionId 去重，保留最近活跃的一条
        Map<String, SysRefreshTokenDO> bySession = new HashMap<>();
        for (SysRefreshTokenDO t : tokens) {
            bySession.putIfAbsent(t.getSessionId(), t);
        }
        List<SysRefreshTokenDO> dedup = new ArrayList<>(bySession.values());

        Set<Long> userIds = dedup.stream().map(SysRefreshTokenDO::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUserDO> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            JooqWriters.fetchList(dsl, USER, SysUserDO.class,
                            USER.field("id", Long.class).in(userIds))
                    .forEach(u -> users.put(u.getId(), u));
        }

        List<OnlineSessionView> views = new ArrayList<>();
        for (SysRefreshTokenDO t : dedup) {
            SysUserDO u = users.get(t.getUserId());
            String username = u == null ? null : u.getUsername();
            String displayName = u == null ? null : u.getDisplayName();
            if (keyword != null && !keyword.isBlank()) {
                boolean hit = (username != null && username.contains(keyword))
                        || (displayName != null && displayName.contains(keyword));
                if (!hit) {
                    continue;
                }
            }
            long remaining = t.getExpiresAt() == null ? 0
                    : Math.max(0, Duration.between(now, t.getExpiresAt()).toMinutes());
            views.add(new OnlineSessionView(t.getUserId(), username, displayName, t.getSessionId(),
                    t.getClientId(), t.getIpAddress(), t.getUserAgent(),
                    t.getIssuedAt(), t.getLastUsedAt(), t.getExpiresAt(), remaining));
            if (views.size() >= limit) {
                break;
            }
        }
        return views;
    }

    /** 在线用户数（去重）与活跃会话数。 */
    public Map<String, Object> onlineSummary() {
        LocalDateTime now = LocalDateTime.now();
        Condition cond = DSL.and(
                RT.field("revoked_at", LocalDateTime.class).isNull(),
                RT.field("expires_at", LocalDateTime.class).gt(now));

        Integer tokens = dsl.selectCount().from(RT.table())
                .where(cond).fetchOne(0, Integer.class);
        Integer sessions = dsl.selectDistinct(RT.field("session_id", String.class))
                .from(RT.table()).where(cond)
                .fetch().size();
        Integer users = dsl.selectDistinct(RT.field("user_id", Long.class))
                .from(RT.table()).where(cond)
                .fetch().size();

        return Map.of(
                "activeTokens", tokens == null ? 0 : tokens,
                "activeSessions", sessions,
                "onlineUsers", users == null ? 0 : users);
    }

    /**
     * 异常日志分页（关键接口 / 任务执行失败记录）。
     * 数据来源为审计日志中 success=0 的记录（GlobalExceptionHandler 与 AuditAspect 已写入）。
     *
     * @param from     起始时间（含），为空默认 24 小时前
     * @param to       结束时间（含），为空默认当前
     * @param module   模块编码过滤，为空不过滤
     * @param keyword  请求路径/操作名/操作人模糊筛选
     * @param sortField     排序字段（服务端白名单映射，白名单外忽略）
     * @param sortDirection 排序方向：ASC / DESC，为空按 ASC
     */
    public PageResult<SysOperationLogDO> errorLogs(int page, int size, LocalDateTime from,
                                                   LocalDateTime to, String module, String keyword,
                                                   String sortField, String sortDirection) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime begin = from == null ? end.minusHours(24) : from;
        if (begin.isAfter(end)) {
            begin = end;
        }

        List<Condition> conds = new ArrayList<>();
        conds.add(JooqTables.SYS_OPERATION_LOG.field("success", Integer.class).eq(0));
        conds.add(DSL.and(
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).ge(begin),
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).le(end)));
        if (module != null && !module.isBlank()) {
            conds.add(JooqTables.SYS_OPERATION_LOG.field("module_code", String.class).eq(module));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_OPERATION_LOG.field("request_path", String.class).like(kw),
                    JooqTables.SYS_OPERATION_LOG.field("operation_name", String.class).like(kw),
                    JooqTables.SYS_OPERATION_LOG.field("operator_name", String.class).like(kw),
                    JooqTables.SYS_OPERATION_LOG.field("trace_id", String.class).like(kw)));
        }

        var pageQuery = PageQuery.of(page, size, sortField, sortDirection);
        return JooqWriters.page(dsl, JooqTables.SYS_OPERATION_LOG, SysOperationLogDO.class,
                DSL.and(conds), pageQuery,
                JooqSorts.resolve(JooqTables.SYS_OPERATION_LOG, pageQuery, ERROR_LOG_SORT_FIELDS,
                        JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).desc()));
    }

    /** 按时间范围查询历史采样（用于趋势图）。limit 下推至 SQL，防止超大时间窗口全量拉取。 */
    public List<SysMonitorSampleDO> samples(LocalDateTime from, LocalDateTime to, int limit) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime begin = from == null ? end.minusHours(24) : from;
        List<Condition> conds = new ArrayList<>();
        conds.add(DSL.and(
                JooqTables.SYS_MONITOR_SAMPLE.field("sampled_at", LocalDateTime.class).ge(begin),
                JooqTables.SYS_MONITOR_SAMPLE.field("sampled_at", LocalDateTime.class).le(end)));

        return JooqWriters.fetchTop(dsl, JooqTables.SYS_MONITOR_SAMPLE, SysMonitorSampleDO.class,
                DSL.and(conds), limit <= 0 ? 200 : limit,
                JooqTables.SYS_MONITOR_SAMPLE.field("sampled_at", LocalDateTime.class).desc());
    }

    /**
     * 采集一次并落库（由 JobRunr 周期任务调用）。
     *
     * @return 采样条数（0 或 1）
     */
    public int sampleOnce() {
        try {
            ServerMetrics m = collector.snapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> online = (Map<String, Object>) onlineSummary();

            SysMonitorSampleDO s = new SysMonitorSampleDO();
            s.setCpuUsage(m.getCpuUsage());
            s.setMemoryUsage(m.getMemoryUsage());
            s.setSystemMemoryUsage(m.getSystemMemoryUsage());
            s.setDiskUsage(m.getDiskUsage());
            s.setUsedHeapBytes(m.getUsedHeapBytes());
            s.setMaxHeapBytes(m.getMaxHeapBytes());
            s.setUsedMemoryBytes(m.getUsedMemoryBytes());
            s.setTotalMemoryBytes(m.getTotalMemoryBytes());
            s.setOnlineUsers(((Number) online.get("onlineUsers")).intValue());
            s.setActiveSessions(((Number) online.get("activeSessions")).intValue());
            s.setThreadCount(m.getThreadCount());
            s.setSampledAt(LocalDateTime.now());
            JooqWriters.insert(dsl, JooqTables.SYS_MONITOR_SAMPLE, s);
            return 1;
        } catch (Exception e) {
            log.warn("监控指标采样失败，跳过本次采样", e);
            return 0;
        }
    }

    /** 异常日志按模块/错误码聚合统计（用于概览卡片）。 */
    public List<Map<String, Object>> errorSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime begin = from == null ? end.minusHours(24) : from;
        Condition timeRange = DSL.and(
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).ge(begin),
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).le(end));

        List<org.jooq.Record3<String, String, Integer>> rows = dsl.select(
                        JooqTables.SYS_OPERATION_LOG.field("module_code", String.class),
                        JooqTables.SYS_OPERATION_LOG.field("result_code", String.class),
                        DSL.count().as("cnt"))
                .from(JooqTables.SYS_OPERATION_LOG.table())
                .where(JooqTables.SYS_OPERATION_LOG.field("success", Integer.class).eq(0)
                        .and(timeRange))
                .groupBy(JooqTables.SYS_OPERATION_LOG.field("module_code", String.class),
                        JooqTables.SYS_OPERATION_LOG.field("result_code", String.class))
                .fetch();

        return rows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("moduleCode", r.get(0, String.class));
            m.put("resultCode", r.get(1, String.class));
            Integer cnt = r.get(2, Integer.class);
            m.put("count", cnt == null ? 0L : cnt.longValue());
            return m;
        }).collect(Collectors.toList());
    }
}
