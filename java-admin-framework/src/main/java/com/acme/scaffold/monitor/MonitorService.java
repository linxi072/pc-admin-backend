package com.acme.scaffold.monitor;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.audit.SysOperationLogDO;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.token.SysRefreshTokenDO;
import com.acme.scaffold.system.entity.SysMonitorSampleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

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

    private final DSLContext dsl;
    private final RuntimeMetricsCollector collector;

    /** 实时运行指标（CPU/内存/磁盘/JVM）。 */
    public ServerMetrics currentMetrics() {
        return collector.snapshot();
    }

    /**
     * 在线会话列表。
     *
     * @param keyword 用户名/姓名模糊筛选，为空则不过滤
     * @param limit   返回条数上限，防止会话过多拖慢响应
     */
    public List<OnlineSessionView> onlineSessions(String keyword, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", LocalDateTime.class).isNull());
        conds.add(JooqTables.SYS_REFRESH_TOKEN.field("expires_at", LocalDateTime.class).gt(now));

        List<SysRefreshTokenDO> tokens = JooqWriters.fetchList(dsl, JooqTables.SYS_REFRESH_TOKEN,
                SysRefreshTokenDO.class, DSL.and(conds),
                JooqTables.SYS_REFRESH_TOKEN.field("last_used_at", LocalDateTime.class).desc().nullsLast());

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
            JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                            JooqTables.SYS_USER.field("id", Long.class).in(userIds))
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
                JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", LocalDateTime.class).isNull(),
                JooqTables.SYS_REFRESH_TOKEN.field("expires_at", LocalDateTime.class).gt(now));

        Integer tokens = dsl.selectCount().from(JooqTables.SYS_REFRESH_TOKEN.table())
                .where(cond).fetchOne(0, Integer.class);
        Integer sessions = dsl.selectDistinct(JooqTables.SYS_REFRESH_TOKEN.field("session_id", String.class))
                .from(JooqTables.SYS_REFRESH_TOKEN.table()).where(cond)
                .fetch().size();
        Integer users = dsl.selectDistinct(JooqTables.SYS_REFRESH_TOKEN.field("user_id", Long.class))
                .from(JooqTables.SYS_REFRESH_TOKEN.table()).where(cond)
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
     */
    public PageResult<SysOperationLogDO> errorLogs(int page, int size, LocalDateTime from,
                                                   LocalDateTime to, String module, String keyword) {
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

        return JooqWriters.page(dsl, JooqTables.SYS_OPERATION_LOG, SysOperationLogDO.class,
                DSL.and(conds), PageQuery.of(page, size),
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).desc());
    }

    /** 按时间范围查询历史采样（用于趋势图）。 */
    public List<SysMonitorSampleDO> samples(LocalDateTime from, LocalDateTime to, int limit) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime begin = from == null ? end.minusHours(24) : from;
        List<Condition> conds = new ArrayList<>();
        conds.add(DSL.and(
                JooqTables.SYS_MONITOR_SAMPLE.field("sampled_at", LocalDateTime.class).ge(begin),
                JooqTables.SYS_MONITOR_SAMPLE.field("sampled_at", LocalDateTime.class).le(end)));

        return JooqWriters.fetchList(dsl, JooqTables.SYS_MONITOR_SAMPLE, SysMonitorSampleDO.class,
                DSL.and(conds),
                JooqTables.SYS_MONITOR_SAMPLE.field("sampled_at", LocalDateTime.class).desc())
                .stream().limit(limit <= 0 ? 200 : Math.min(limit, 1000))
                .collect(Collectors.toList());
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
