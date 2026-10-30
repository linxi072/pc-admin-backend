package com.acme.scaffold.workbench.report.service;

import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportServiceSeriesTest {

    @Test
    void fillHourly_produces24BucketsWithZeroPadding() {
        Map<Integer, long[]> agg = new HashMap<>();
        agg.put(8, new long[]{5, 2});
        agg.put(12, new long[]{3, 1});

        List<ReportSeriesPointVO> series = ReportService.fillHourly(agg);
        assertEquals(24, series.size());
        assertEquals("00:00", series.get(0).bucket());
        assertEquals(0L, series.get(0).visits());
        assertEquals("08:00", series.get(8).bucket());
        assertEquals(5L, series.get(8).visits());
        assertEquals(2L, series.get(8).activeUsers());
        assertEquals("12:00", series.get(12).bucket());
        assertEquals(3L, series.get(12).visits());
        assertEquals(1L, series.get(12).activeUsers());
        assertEquals("23:00", series.get(23).bucket());
    }

    @Test
    void fillDaily_producesContinuousDatesWithZeroPadding() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 3);
        Map<LocalDate, long[]> agg = new HashMap<>();
        agg.put(from, new long[]{10, 4});
        // 10-02 缺失 → 应补零
        agg.put(to, new long[]{2, 1});

        List<ReportSeriesPointVO> series = ReportService.fillDaily(from, to, agg);
        assertEquals(3, series.size());
        assertEquals("10-01", series.get(0).bucket());
        assertEquals(10L, series.get(0).visits());
        assertEquals(4L, series.get(0).activeUsers());
        assertEquals("10-02", series.get(1).bucket());
        assertEquals(0L, series.get(1).visits());
        assertEquals(0L, series.get(1).activeUsers());
        assertEquals("10-03", series.get(2).bucket());
        assertEquals(2L, series.get(2).visits());
    }

    @Test
    void fillDaily_singleDayWindow() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        List<ReportSeriesPointVO> series = ReportService.fillDaily(d, d, Map.of());
        assertEquals(1, series.size());
        assertEquals("10-01", series.get(0).bucket());
        assertNotNull(series.get(0));
    }
}
