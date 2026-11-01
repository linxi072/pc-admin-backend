package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules.MetricKey;
import com.acme.scaffold.monitor.domain.ServerMetrics;
import com.acme.scaffold.monitor.service.MonitorService;
import com.acme.scaffold.monitor.service.NPlusOneService;
import com.acme.scaffold.monitor.service.RuntimeMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标采样器：将各类实时运行指标归一成 {@code Map<MetricKey.code, BigDecimal>}，供规则引擎比较。
 *
 * <p>指标来源：
 * <ul>
 *   <li>CPU / 内存 / 磁盘使用率、线程数：{@link RuntimeMetricsCollector#snapshot()}（JVM/OS MXBean，零外部依赖）；</li>
 *   <li>N+1 疑似数：{@link NPlusOneService#status()} 当前留存条数；</li>
 *   <li>在线用户数：{@link MonitorService#onlineSummary()}；</li>
 *   <li>近 10 分钟错误日志数：审计日志（success=0）计数。</li>
 * </ul>
 *
 * <p>任一指标采集失败（如容器不支持 CPU 采样返回 null）返回 {@code null}，
 * 规则引擎对 null 一律视为「不触发」，避免缺失指标误报。</p>
 */
@Slf4j
@Component
public class MetricSampler {

    /** 错误日志统计窗口（分钟）：规则命中「错误数」类指标时按此窗口计数。 */
    private static final int ERROR_LOG_WINDOW_MINUTES = 10;

    private final RuntimeMetricsCollector collector;
    private final NPlusOneService nPlusOneService;
    private final MonitorService monitorService;
    private final DSLContext dsl;

    public MetricSampler(RuntimeMetricsCollector collector, NPlusOneService nPlusOneService,
                         MonitorService monitorService, DSLContext dsl) {
        this.collector = collector;
        this.nPlusOneService = nPlusOneService;
        this.monitorService = monitorService;
        this.dsl = dsl;
    }

    /**
     * 采样单个指标当前值。
     *
     * @param key 指标键（枚举），非法值返回 null
     * @return 当前值（BigDecimal）；不支持/采集失败返回 null
     */
    public BigDecimal sample(MetricKey key) {
        if (key == null) {
            return null;
        }
        try {
            ServerMetrics m = collector.snapshot();
            return switch (key) {
                case CPU_USAGE -> m.getCpuUsage();
                case MEMORY_USAGE -> m.getMemoryUsage();
                case DISK_USAGE -> m.getDiskUsage();
                case THREAD_COUNT -> m.getThreadCount() == null ? null : BigDecimal.valueOf(m.getThreadCount());
                case NPLUS1_COUNT -> BigDecimal.valueOf(nPlusOneService.status().registrySize());
                case ONLINE_USERS -> onlineUsers();
                case ERROR_LOG_COUNT -> BigDecimal.valueOf(countErrorLogs());
            };
        } catch (Exception e) {
            log.warn("指标采样失败 key={}", key, e);
            return null;
        }
    }

    private BigDecimal onlineUsers() {
        var summary = monitorService.onlineSummary();
        Object o = summary == null ? null : summary.get("onlineUsers");
        return o instanceof Number n ? BigDecimal.valueOf(n.longValue()) : BigDecimal.ZERO;
    }

    private long countErrorLogs() {
        Long count = dsl.selectCount()
                .from(JooqTables.SYS_OPERATION_LOG.table())
                .where(JooqTables.SYS_OPERATION_LOG.field("success", Integer.class).eq(0))
                .and(JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class)
                        .ge(LocalDateTime.now().minusMinutes(ERROR_LOG_WINDOW_MINUTES)))
                .fetchOne(0, Long.class);
        return count == null ? 0L : count;
    }
}
