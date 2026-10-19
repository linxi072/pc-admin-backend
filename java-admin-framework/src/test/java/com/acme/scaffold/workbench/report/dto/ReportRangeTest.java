package com.acme.scaffold.workbench.report.dto;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRangeTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 10, 15, 14, 30, 0);

    @Test
    void today_resolvesToHourBucketsFromMidnightToNow() {
        ReportRange.ResolvedWindow w = ReportRange.TODAY.resolve(FIXED_NOW, null, null);
        assertEquals(LocalDate.of(2026, 10, 15).atStartOfDay(), w.from());
        assertEquals(FIXED_NOW, w.to());
        assertEquals(ReportRange.BucketMode.HOUR, w.bucketMode());
    }

    @Test
    void last7d_spansInclusive7DaysWithDayBuckets() {
        ReportRange.ResolvedWindow w = ReportRange.LAST_7D.resolve(FIXED_NOW, null, null);
        assertEquals(LocalDate.of(2026, 10, 9).atStartOfDay(), w.from());
        assertEquals(FIXED_NOW, w.to());
        assertEquals(ReportRange.BucketMode.DAY, w.bucketMode());
    }

    @Test
    void last30d_spansInclusive30DaysWithDayBuckets() {
        ReportRange.ResolvedWindow w = ReportRange.LAST_30D.resolve(FIXED_NOW, null, null);
        assertEquals(LocalDate.of(2026, 9, 16).atStartOfDay(), w.from());
        assertEquals(FIXED_NOW, w.to());
        assertEquals(ReportRange.BucketMode.DAY, w.bucketMode());
    }

    @Test
    void custom_resolvesInclusiveEndOfDayWithDayBuckets() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 10);
        ReportRange.ResolvedWindow w = ReportRange.CUSTOM.resolve(FIXED_NOW, start, end);
        assertEquals(start.atStartOfDay(), w.from());
        assertEquals(end.atTime(23, 59, 59, 999_000_000), w.to());
        assertEquals(ReportRange.BucketMode.DAY, w.bucketMode());
    }

    @Test
    void custom_missingStart_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReportRange.CUSTOM.resolve(FIXED_NOW, null, LocalDate.of(2026, 10, 10)));
        assertTrue(ex.getMessage().contains("开始"));
    }

    @Test
    void custom_startAfterEnd_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReportRange.CUSTOM.resolve(FIXED_NOW,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1)));
        assertTrue(ex.getMessage().contains("不能晚于"));
    }

    @Test
    void custom_spanAtMaxIsAccepted() {
        // 恰好 366 天（2025-10-15 ~ 2026-10-15）为合法上限
        LocalDate start = LocalDate.of(2025, 10, 15);
        LocalDate end = LocalDate.of(2026, 10, 15);
        ReportRange.ResolvedWindow w = ReportRange.CUSTOM.resolve(FIXED_NOW, start, end);
        assertEquals(start.atStartOfDay(), w.from());
    }

    @Test
    void custom_spanOverMax_throws() {
        // 367 天：按天补零会生成 367 个序列点，超出上限直接拒绝，防止响应体爆炸
        LocalDate start = LocalDate.of(2025, 10, 14);
        LocalDate end = LocalDate.of(2026, 10, 15);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReportRange.CUSTOM.resolve(FIXED_NOW, start, end));
        assertTrue(ex.getMessage().contains("跨度"));
    }

    @Test
    void nonCustom_ignoresStartEndArguments() {
        // LAST_7D 不依赖传入的 start/end，不应抛错
        ReportRange.ResolvedWindow w = ReportRange.LAST_7D.resolve(FIXED_NOW,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));
        assertEquals(LocalDate.of(2026, 10, 9).atStartOfDay(), w.from());
    }
}
