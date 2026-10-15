package com.acme.scaffold.monitor;

import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.impl.DefaultExecuteListener;

/**
 * N+1 查询告警监听器：借助 {@link NPlusOneProbe} 在同一线程内累计同构 SQL 执行次数，
 * 超过阈值即输出告警（含指纹与阈值），便于定位循环内逐条查询问题。
 *
 * <p>监听器本身不在并发执行间共享可变状态（状态存于 probe 的线程局部变量），
 * 与 {@link SlowQueryListener} 同为只读观测，不改变 SQL 执行。
 */
@Slf4j
public class NPlusOneListener extends DefaultExecuteListener {

    private final NPlusOneProbe probe;

    public NPlusOneListener(NPlusOneProbe probe) {
        this.probe = probe;
    }

    @Override
    public void executeEnd(ExecuteContext ctx) {
        String sql = ctx.sql();
        if (probe.record(sql)) {
            log.warn("N+1 查询告警: 同构 SQL 在单请求内执行达 {} 次 | sql={}",
                    probe.threshold(), sql);
        }
    }
}
