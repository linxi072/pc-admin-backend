package com.acme.scaffold.monitor.detection;

import com.acme.scaffold.mybatis.dsl.ExecuteContext;
import com.acme.scaffold.mybatis.dsl.ExecuteListener;
import lombok.extern.slf4j.Slf4j;

/**
 * N+1 查询告警监听器（MyBatis 兼容层版）。
 * 同构 SQL 在单请求内重复执行超阈值即告警，并向疑似事件留存登记。
 */
@Slf4j
public class NPlusOneListener implements ExecuteListener {

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
                log.debug("N+1 疑似事件登记失败: {}", ex.toString());
            }
            if (probe.isFailMode()) {
                throw new NPlusOneThresholdExceededException(probe.threshold(), NPlusOneProbe.normalize(sql));
            }
        }
    }
}
