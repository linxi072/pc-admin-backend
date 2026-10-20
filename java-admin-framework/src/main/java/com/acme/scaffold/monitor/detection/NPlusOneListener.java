package com.acme.scaffold.monitor.detection;

import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.impl.DefaultExecuteListener;

/**
 * N+1 查询告警监听器：借助 {@link NPlusOneProbe} 在同一线程内累计同构 SQL 执行次数，
 * 超过阈值即输出告警（含指纹与阈值），并向 {@link NPlusOneSuspectRegistry} 登记一条疑似事件，
 * 使告警从「一次性日志」升级为「可查询、可复盘」的运行态数据。
 *
 * <p>监听器本身不在并发执行间共享可变状态（计数状态存于 probe 的线程局部变量），
 * 与 {@link SlowQueryListener} 同为只读观测，不改变 SQL 执行。登记旁路带异常防护：
 * 即使留存组件自身异常也仅记 debug 日志，绝不向 jOOQ 执行链路传播。</p>
 */
@Slf4j
public class NPlusOneListener extends DefaultExecuteListener {

    private final NPlusOneProbe probe;
    private final NPlusOneSuspectRegistry registry;

    public NPlusOneListener(NPlusOneProbe probe, NPlusOneSuspectRegistry registry) {
        this.probe = probe;
        this.registry = registry;
    }

    @Override
    public void executeEnd(ExecuteContext ctx) {
        String sql = ctx.sql();
        if (probe.record(sql)) {
            log.warn("N+1 查询告警: 同构 SQL 在单请求内执行达 {} 次 | sql={}",
                    probe.threshold(), sql);
            try {
                registry.record(NPlusOneProbe.normalize(sql), sql, probe.count(sql));
            } catch (RuntimeException ex) {
                // 观测旁路绝不因自身异常影响主链路
                log.debug("N+1 疑似事件登记失败: {}", ex.toString());
            }
        }
    }
}
