package com.acme.scaffold.monitor.detection;

import com.acme.scaffold.mybatis.dsl.ExecuteContext;
import com.acme.scaffold.mybatis.dsl.ExecuteListener;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 慢 SQL 监控监听器（MyBatis 兼容层版）。
 * 记录超过阈值的 SQL 及耗时，仅供观测，不改变 SQL 执行。
 */
@Slf4j
public class SlowQueryListener implements ExecuteListener {

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
