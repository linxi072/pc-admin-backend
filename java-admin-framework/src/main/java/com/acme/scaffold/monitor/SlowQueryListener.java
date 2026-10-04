package com.acme.scaffold.monitor;

import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.impl.DefaultExecuteListener;

import java.util.concurrent.TimeUnit;

/**
 * 慢 SQL 监控监听器（jOOQ 版，替代原 MyBatis-Plus 的 InnerInterceptor）。
 * <p>
 * 记录超过阈值的 SQL 及耗时，仅供观测，不改变 SQL 执行。
 * 计时数据存放于 {@link ExecuteContext}，避免监听器实例在并发执行间共享状态。
 */
@Slf4j
public class SlowQueryListener extends DefaultExecuteListener {

    private static final String START_NANOS = "slowQuery.startNanos";

    private final long thresholdMillis;

    public SlowQueryListener(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    @Override
    public void executeStart(ExecuteContext ctx) {
        ctx.data(START_NANOS, System.nanoTime());
    }

    @Override
    public void executeEnd(ExecuteContext ctx) {
        Object start = ctx.data(START_NANOS);
        if (!(start instanceof Long startNanos)) {
            return;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (elapsedMs >= thresholdMillis) {
            log.warn("慢 SQL 检测: {} ms | sql={}", elapsedMs, ctx.sql());
        }
    }
}
