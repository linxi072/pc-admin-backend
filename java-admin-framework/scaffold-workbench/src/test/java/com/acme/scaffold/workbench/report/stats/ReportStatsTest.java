package com.acme.scaffold.workbench.report.stats;

import com.acme.scaffold.workbench.report.dto.ReportDistributionVO;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReportStats} 纯函数离线单测（无 DB 依赖）：
 * 分布换算 {@link ReportStats#toDistribution}、P95 {@link ReportStats#p95Of}、
 * 连续序列补零 {@link ReportStats#fillHourly}/{@link ReportStats#fillDaily}。
 *
 * <p>这些原属 {@code ReportService} 的纯函数已抽离至本类（重构计划 C1 余量）。
 */
class ReportStatsTest {

    // ---------------- 分布换算 ----------------

    @Test
    void toDistribution_labelsAndRatios() {
        // 桶顺序与 durationCounts 的 SQL 聚合顺序严格一致：
        // [无耗时记录(NULL), <100ms, 100-500ms, 500ms-1s, 1s-5s, >5s]
        long[] counts = {0, 4, 3, 2, 0, 1};
        List<ReportDistributionVO> dist = ReportStats.toDistribution(counts);

        assertEquals(6, dist.size());
        assertEquals("无耗时记录", dist.get(0).label());
        assertEquals(0L, dist.get(0).count());
        assertEquals("<100ms", dist.get(1).label());
        assertEquals(4L, dist.get(1).count());
        assertEquals(40.0, dist.get(1).ratio());
        assertEquals("100-500ms", dist.get(2).label());
        assertEquals(30.0, dist.get(2).ratio());
        assertEquals(">5s", dist.get(5).label());
        assertEquals(1L, dist.get(5).count());
        assertEquals(10.0, dist.get(5).ratio());

        long sum = dist.stream().mapToLong(ReportDistributionVO::count).sum();
        assertEquals(10L, sum);
        double ratioSum = dist.stream().mapToDouble(ReportDistributionVO::ratio).sum();
        assertTrue(Math.abs(ratioSum - 100.0) < 1e-6, "占比之和应约为 100，实际=" + ratioSum);
    }

    @Test
    void toDistribution_zeroTotalAllRatiosZero() {
        long[] counts = {0, 0, 0, 0, 0, 0};
        List<ReportDistributionVO> dist = ReportStats.toDistribution(counts);
        assertTrue(dist.stream().allMatch(d -> d.ratio() == 0.0), "总量为 0 时所有占比应为 0");
    }

    // ---------------- P95 ----------------

    @Test
    void p95Of_recentRankMethod() {
        // [10,20,...,100] → n=10, ceil(0.95*10)=10 → 第 10 小 = 100
        List<Long> xs = java.util.List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        assertEquals(100L, ReportStats.p95Of(xs));
    }

    @Test
    void p95Of_ignoresNullAndEmpty() {
        assertEquals(0L, ReportStats.p95Of(null));
        assertEquals(0L, ReportStats.p95Of(java.util.List.of()));
        // List.of 拒绝 null 元素，故用 Arrays.asList 构造含 null 的样本验证忽略逻辑
        assertEquals(5L, ReportStats.p95Of(java.util.Arrays.asList(5L, null, 1L)));
    }

    // ---------------- 连续序列补零 ----------------

    @Test
    void fillHourly_fillsAll24Buckets() {
        Map<Integer, long[]> agg = Map.of(
                8, new long[]{10, 3, 1},
                9, new long[]{20, 5});
        List<ReportSeriesPointVO> series = ReportStats.fillHourly(agg);
        assertEquals(24, series.size());
        assertEquals("00:00", series.get(0).bucket());
        assertEquals(0L, series.get(0).visits());
        assertEquals("08:00", series.get(8).bucket());
        assertEquals(10L, series.get(8).visits());
        assertEquals(3L, series.get(8).activeUsers());
        assertEquals(1L, series.get(8).failed());
        // 缺省桶失败数补 0
        assertEquals(0L, series.get(0).failed());
    }

    @Test
    void fillDaily_fillsContinuousDates() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 3);
        Map<LocalDate, long[]> agg = Map.of(
                from.plusDays(1), new long[]{7, 2});
        List<ReportSeriesPointVO> series = ReportStats.fillDaily(from, to, agg);
        assertEquals(3, series.size());
        assertEquals("01-01", series.get(0).bucket());
        assertEquals(0L, series.get(0).visits());
        assertEquals("01-02", series.get(1).bucket());
        assertEquals(7L, series.get(1).visits());
        assertEquals("01-03", series.get(2).bucket());
        assertEquals(0L, series.get(2).visits());
    }
}
