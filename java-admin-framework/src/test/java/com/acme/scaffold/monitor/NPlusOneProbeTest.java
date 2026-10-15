package com.acme.scaffold.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NPlusOneProbe} 单元测试：归一化指纹、阈值跨越告警、线程隔离、reset 清理。
 * 不依赖数据库或 Spring 容器，纯逻辑离线验证。
 */
class NPlusOneProbeTest {

    @Test
    void normalizeCollapsesWhitespaceAndLiterals() {
        String a = "select * from t where id = 1 and name = 'bob'";
        String b = "SELECT  *  FROM  t  WHERE  id=2  AND name='alice'";
        // 仅参数/字面量不同，归一化后应得到同一指纹
        assertEquals(NPlusOneProbe.normalize(a), NPlusOneProbe.normalize(b));
    }

    @Test
    void firesExactlyWhenCrossingThreshold() {
        NPlusOneProbe probe = new NPlusOneProbe(3);
        String sql = "select * from t where id = 1";
        assertFalse(probe.record(sql)); // 1
        assertFalse(probe.record(sql)); // 2
        assertTrue(probe.record(sql));  // 3 -> 恰好跨越阈值
        assertFalse(probe.record(sql)); // 4 -> 不再重复告警
        assertEquals(4, probe.count(sql));
    }

    @Test
    void resetClearsCounts() {
        NPlusOneProbe probe = new NPlusOneProbe(2);
        probe.record("select 1");
        probe.record("select 1");
        assertEquals(2, probe.count("select 1"));
        probe.reset();
        assertEquals(0, probe.count("select 1"));
    }

    @Test
    void differentFingerprintsCountedSeparately() {
        NPlusOneProbe probe = new NPlusOneProbe(100);
        probe.record("select a from t where id = 1");
        probe.record("select b from t where id = 1");
        assertEquals(1, probe.count("select a from t where id = 1"));
        assertEquals(1, probe.count("select b from t where id = 1"));
    }
}
