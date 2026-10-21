package com.acme.scaffold.monitor.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.monitor.detection.NPlusOneProbe;
import com.acme.scaffold.monitor.detection.NPlusOneSuspect;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.monitor.dto.NPlusOneStatusView;
import com.acme.scaffold.monitor.dto.NPlusOneSuspectView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NPlusOneService} 单元测试：查询条数输入校验、视图映射、状态聚合与清空委托。
 */
@DisplayName("N+1 探测运维服务测试")
class NPlusOneServiceTest {

    private final NPlusOneProbe probe = mock(NPlusOneProbe.class);
    private final NPlusOneSuspectRegistry registry = mock(NPlusOneSuspectRegistry.class);
    private final NPlusOneService service = new NPlusOneService(probe, registry);

    @Test
    @DisplayName("合法 limit 透传检索并映射为视图")
    void recentSuspectsMapsViews() {
        NPlusOneSuspect suspect = new NPlusOneSuspect(
                "select * from sys_user where id=?", "select * from sys_user where id = 7", 12, "http-nio-1", 1_700_000_000_000L);
        when(registry.recent(50)).thenReturn(List.of(suspect));
        List<NPlusOneSuspectView> views = service.recentSuspects(50);
        assertEquals(1, views.size());
        NPlusOneSuspectView view = views.get(0);
        assertEquals(suspect.fingerprint(), view.fingerprint());
        assertEquals(suspect.sampleSql(), view.sampleSql());
        assertEquals(12, view.count());
        assertEquals("http-nio-1", view.threadName());
        assertEquals(1_700_000_000_000L, view.timestampMillis());
    }

    @Test
    @DisplayName("limit=0 越界 → BusinessException(VALIDATION_ERROR)，不触达留存")
    void limitZeroRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.recentSuspects(0));
        assertEquals("COMMON_001", ex.getErrorCode().code());
        verify(registry, org.mockito.Mockito.never()).recent(org.mockito.Mockito.anyInt());
    }

    @Test
    @DisplayName("limit 超过上限 → BusinessException(VALIDATION_ERROR)")
    void limitAboveMaxRejected() {
        assertThrows(BusinessException.class,
                () -> service.recentSuspects(NPlusOneService.MAX_LIMIT + 1));
    }

    @Test
    @DisplayName("limit 为负 → BusinessException(VALIDATION_ERROR)")
    void negativeLimitRejected() {
        assertThrows(BusinessException.class, () -> service.recentSuspects(-1));
    }

    @Test
    @DisplayName("状态视图聚合阈值/容量/存量/累计口径")
    void statusAggregates() {
        when(probe.threshold()).thenReturn(10);
        when(registry.capacity()).thenReturn(200);
        when(registry.size()).thenReturn(3);
        when(registry.total()).thenReturn(42L);
        when(registry.lastBreachAt()).thenReturn(1_700_000_000_123L);
        NPlusOneStatusView status = service.status();
        assertEquals(10, status.threshold());
        assertEquals(200, status.capacity());
        assertEquals(3, status.registrySize());
        assertEquals(42L, status.totalBreaches());
        assertEquals(1_700_000_000_123L, status.lastBreachAtEpochMillis());
    }

    @Test
    @DisplayName("clear 委托留存清空并返回 true")
    void clearDelegates() {
        assertTrue(service.clear());
        verify(registry).clear();
    }
}
