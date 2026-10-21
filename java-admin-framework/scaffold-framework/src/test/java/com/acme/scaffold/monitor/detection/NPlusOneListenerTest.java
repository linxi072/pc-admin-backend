package com.acme.scaffold.monitor.detection;

import org.jooq.ExecuteContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link NPlusOneListener} 单元测试：阈值越限时向 {@link NPlusOneSuspectRegistry} 登记事件，
 * 未越限 / 空 SQL 不登记，同一线程同一指纹每轮至多登记一次。
 */
@DisplayName("N+1 告警监听器测试")
class NPlusOneListenerTest {

    private static ExecuteContext ctx(String sql) {
        ExecuteContext context = mock(ExecuteContext.class);
        when(context.sql()).thenReturn(sql);
        return context;
    }

    @Test
    @DisplayName("越阈值时登记疑似事件（指纹/样本/计数正确）")
    void breachRegistersSuspect() {
        NPlusOneProbe probe = new NPlusOneProbe(2);
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(10);
        NPlusOneListener listener = new NPlusOneListener(probe, registry);
        String sql = "select * from sys_user where id = 1";

        listener.executeEnd(ctx(sql)); // 1：未越限
        assertTrue(registry.recent(10).isEmpty());
        listener.executeEnd(ctx(sql)); // 2：越限 → 登记

        List<NPlusOneSuspect> recent = registry.recent(10);
        assertEquals(1, recent.size());
        NPlusOneSuspect suspect = recent.get(0);
        assertEquals(NPlusOneProbe.normalize(sql), suspect.fingerprint());
        assertEquals(sql, suspect.sampleSql());
        assertEquals(2, suspect.count());
        assertEquals(1L, registry.total());
    }

    @Test
    @DisplayName("未达阈值不登记")
    void belowThresholdNoRegistration() {
        NPlusOneProbe probe = new NPlusOneProbe(5);
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(10);
        NPlusOneListener listener = new NPlusOneListener(probe, registry);
        String sql = "select * from sys_user where id = 1";
        for (int i = 0; i < 4; i++) {
            listener.executeEnd(ctx(sql));
        }
        assertEquals(0, registry.size());
        assertEquals(0L, registry.total());
    }

    @Test
    @DisplayName("同一线程同一指纹每轮至多登记一次（不刷屏）")
    void singleEventPerCycle() {
        NPlusOneProbe probe = new NPlusOneProbe(2);
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(10);
        NPlusOneListener listener = new NPlusOneListener(probe, registry);
        String sql = "select * from sys_user where id = 1";
        for (int i = 0; i < 6; i++) {
            listener.executeEnd(ctx(sql));
        }
        assertEquals(1, registry.size());
        assertEquals(1L, registry.total());
    }

    @Test
    @DisplayName("SQL 为 null 时静默跳过（不登记不抛错）")
    void nullSqlSafe() {
        NPlusOneProbe probe = new NPlusOneProbe(2);
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(10);
        NPlusOneListener listener = new NPlusOneListener(probe, registry);
        listener.executeEnd(ctx(null));
        listener.executeEnd(ctx(null));
        assertEquals(0, registry.size());
    }
}
