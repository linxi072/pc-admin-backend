package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditLogQuery} 单元测试：查询条件的合法性在构造期收口，
 * 避免非法区间被翻译成「永远查不到」的 SQL 后静默返回空结果。
 */
class AuditLogQueryTest {

    private static AuditLogQuery empty() {
        return new AuditLogQuery(0, 0, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void pageAndSizeAreClampedByPageQuery() {
        var q = empty().toPageQuery();
        // PageQuery 构造期把 page 钳到 >=1、size 钳到 [1,100]
        assertEquals(1, q.page());
        assertEquals(1, q.size());

        var q2 = new AuditLogQuery(3, 500, null, null, null, null, null, null, null, null, null, null,
                null, null).toPageQuery();
        assertEquals(3, q2.page());
        assertEquals(100, q2.size());
    }

    @Test
    void blankFiltersAreNormalizedToNull() {
        AuditLogQuery q = new AuditLogQuery(1, 20, "  system ", "\t", null, "  ", null, " ", "  ", null,
                null, null, null, null);
        assertEquals("system", q.moduleCode());
        assertNull(q.operationType());
        assertNull(q.operatorName());
        assertNull(q.traceId());
        assertNull(q.keyword());
        // moduleCode="system" 是有效过滤条件
        assertTrue(q.hasFilter());
    }

    @Test
    void allBlankMeansNoFilter() {
        AuditLogQuery q = new AuditLogQuery(1, 20, " ", "", null, "   ", null, "", null, null, null, null,
                null, null);
        assertNull(q.moduleCode());
        assertFalse(q.hasFilter());
    }

    @Test
    void rejectsInvertedTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new AuditLogQuery(1, 20, null, null, null, null, null, null, null, null,
                        now, now.minusDays(1), null, null));
        assertTrue(ex.getMessage().contains("开始时间不能晚于结束时间"), ex.getMessage());
    }

    @Test
    void acceptsEqualOrOrderedTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        // 起止相同视为「同一时刻」的合法查询
        assertEquals(now, new AuditLogQuery(1, 20, null, null, null, null, null, null, null, null,
                now, now, null, null).startTime());
    }

    @Test
    void rejectsNegativeMinDuration() {
        assertThrows(BusinessException.class,
                () -> new AuditLogQuery(1, 20, null, null, null, null, null, null, null, -1L,
                        null, null, null, null));
    }

    @Test
    void hasFilterReflectsAnyCondition() {
        assertTrue(new AuditLogQuery(1, 20, null, null, 7L, null, null, null, null, null, null, null,
                null, null).hasFilter());
        assertTrue(new AuditLogQuery(1, 20, null, null, null, null, Boolean.FALSE, null, null, null,
                null, null, null, null).hasFilter());
        assertFalse(empty().hasFilter());
    }
}
