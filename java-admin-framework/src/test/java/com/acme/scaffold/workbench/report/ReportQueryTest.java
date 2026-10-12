package com.acme.scaffold.workbench.report;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportQueryTest {

    @Test
    void defaults_appliedWhenNull() {
        ReportQuery q = new ReportQuery(null, ReportRange.LAST_7D, null, null, null, null, null, null, null);
        assertEquals(ReportType.USAGE, q.type());
        assertEquals(SortField.COUNT, q.sortBy());
        assertEquals(SortOrder.DESC, q.order());
    }

    @Test
    void custom_withoutBounds_throws() {
        assertThrows(BusinessException.class,
                () -> new ReportQuery(ReportType.USAGE, ReportRange.CUSTOM, null, null, null, null, null, null, null));
    }

    @Test
    void custom_validBounds_ok() {
        ReportQuery q = new ReportQuery(ReportType.STATUS, ReportRange.CUSTOM,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "user", true, "kw", SortField.NAME, SortOrder.ASC);
        assertEquals(ReportType.STATUS, q.type());
        assertEquals("user", q.module());
        assertEquals(Boolean.TRUE, q.success());
        assertEquals("kw", q.keyword());
        assertEquals(SortField.NAME, q.sortBy());
        assertEquals(SortOrder.ASC, q.order());
    }
}
