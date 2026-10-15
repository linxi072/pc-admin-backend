package com.acme.scaffold.workbench.report.service;

import com.acme.scaffold.workbench.report.dto.ReportDistributionVO;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import com.acme.scaffold.workbench.report.dto.ReportType;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报表统计纯函数离线单测（无 DB 依赖）：
 * 分布换算 {@link ReportService#toDistribution}、P95 {@link ReportService#p95Of}、
 * 连续序列失败数兼容 {@link ReportService#fillHourly}/{@link ReportService#fillDaily}、
 * 关键字条件 {@link ReportService#keywordCondition}。
 */
class ReportServiceStatsTest {

    // ---------------- 分布换算 ----------------

    @Test
    void toDistribution_labelsAndRatios() {
        // 10 条样本：<100ms×4、100-500ms×3、500ms-1s×2、>5s×1，其余桶 0
        long[] counts = {4, 3, 2, 0, 1, 0};
        List<ReportDistributionVO> dist = ReportService.toDistribution(counts);

        assertEquals(6, dist.size());
        assertEquals("<100ms", dist.get(0).label());
        assertEquals(4L, dist.get(0).count());
        assertEquals(40.0, dist.get(0).ratio());
        assertEquals("100-500ms", dist.get(1).label());
        assertEquals(30.0, dist.get(1).ratio());
        assertEquals("500ms-1s", dist.get(2).label());
        assertEquals(20.0, dist.get(2).ratio());
        assertEquals("1s-5s", dist.get(3).label());
        assertEquals(0L, dist.get(3).count());
        assertEquals(">5s", dist.get(4).label());
        assertEquals(1L, dist.get(4).count());
        assertEquals(10.0, dist.get(4).ratio());
        assertEquals("无耗时记录", dist.get(5).label());

        // 汇总守恒：计数之和 = 样本总量
        long sum = dist.stream().mapToLong(ReportDistributionVO::count).sum();
        assertEquals(10L, sum);
        // 占比之和 = 100（允许 1 位小数舍入误差）
        double ratioSum = dist.stream().mapToDouble(ReportDistributionVO::ratio).sum();
        assertEquals(100.0, ratioSum, 0.05);
    }

    @Test
    void toDistribution_empty_countsAllZero() {
        List<ReportDistributionVO> dist = ReportService.toDistribution(new long[6]);
        assertEquals(6, dist.size());
        assertTrue(dist.stream().allMatch(d -> d.count() == 0L && d.ratio() == 0.0));
    }

    // ---------------- P95 ----------------

    @Test
    void p95Of_emptyAndNullSamples() {
        assertEquals(0L, ReportService.p95Of(List.of()));
        assertEquals(0L, ReportService.p95Of(null));
        assertEquals(0L, ReportService.p95Of(Arrays.asList(null, null)));
    }

    @Test
    void p95Of_singleValue() {
        assertEquals(42L, ReportService.p95Of(List.of(42L)));
    }

    @Test
    void p95Of_1to100_returns95() {
        List<Long> durations = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            durations.add(i);
        }
        // 最近秩法：ceil(0.95 * 100) = 95 → 第 95 小 = 95
        assertEquals(95L, ReportService.p95Of(durations));
    }

    @Test
    void p95Of_20Samples_returns19th() {
        List<Long> durations = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            durations.add(i);
        }
        // ceil(0.95 * 20) = 19 → 第 19 小 = 19
        assertEquals(19L, ReportService.p95Of(durations));
    }

    @Test
    void p95Of_ignoresNulls() {
        List<Long> durations = new ArrayList<>(Arrays.asList(10L, null, 20L, null, 30L));
        // 有效样本 3 个：ceil(0.95*3)=3 → 第 3 小 = 30
        assertEquals(30L, ReportService.p95Of(durations));
    }

    // ---------------- 连续序列失败数兼容 ----------------

    @Test
    void fillHourly_thirdElementIsFailedCount() {
        Map<Integer, long[]> agg = new HashMap<>();
        agg.put(8, new long[]{10, 3, 2});   // [主指标, 次指标, 失败数]
        agg.put(12, new long[]{5, 2});      // 缺省第三元素 → 失败数 0（向后兼容）

        List<ReportSeriesPointVO> series = ReportService.fillHourly(agg);
        assertEquals(24, series.size());
        assertEquals(10L, series.get(8).visits());
        assertEquals(3L, series.get(8).activeUsers());
        assertEquals(2L, series.get(8).failed());
        assertEquals(5L, series.get(12).visits());
        assertEquals(2L, series.get(12).activeUsers());
        assertEquals(0L, series.get(12).failed());
        // 空桶补零且 failed=0
        assertEquals(0L, series.get(0).failed());
    }

    @Test
    void fillDaily_thirdElementIsFailedCount() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 2);
        Map<LocalDate, long[]> agg = new HashMap<>();
        agg.put(from, new long[]{7, 2, 1});

        List<ReportSeriesPointVO> series = ReportService.fillDaily(from, to, agg);
        assertEquals(2, series.size());
        assertEquals(1L, series.get(0).failed());
        assertEquals(0L, series.get(1).failed());
    }

    // ---------------- 关键字条件 ----------------

    @Test
    void keywordCondition_nullOrBlankIgnored() {
        assertNull(ReportService.keywordCondition(ReportType.JOB_EXEC, null));
        assertNull(ReportService.keywordCondition(ReportType.JOB_EXEC, ""));
        assertNull(ReportService.keywordCondition(ReportType.JOB_EXEC, "   "));
    }

    @Test
    void keywordCondition_usageViewHasNoKeywordDimension() {
        assertNull(ReportService.keywordCondition(ReportType.USAGE, "admin"));
        assertNull(ReportService.keywordCondition(ReportType.CATEGORY, "admin"));
        assertNull(ReportService.keywordCondition(ReportType.STATUS, "admin"));
    }

    @Test
    void keywordCondition_mapsToTypeSpecificField() {
        // MySQL 方言渲染列名带反引号：`job_name` like '%daily%' escape '\\'
        String jobSql = render(ReportService.keywordCondition(ReportType.JOB_EXEC, "daily"));
        assertTrue(jobSql.contains("job_name"), jobSql);
        assertTrue(jobSql.contains("like"), jobSql);
        assertFalse(jobSql.contains("request_path"));

        String loginSQL = render(ReportService.keywordCondition(ReportType.LOGIN, "admin"));
        assertTrue(loginSQL.contains("username"), loginSQL);

        String apiSQL = render(ReportService.keywordCondition(ReportType.API_CALL, "/api/user"));
        assertTrue(apiSQL.contains("request_path"), apiSQL);
    }

    @Test
    void keywordCondition_escapesSqlWildcards() {
        // 用户输入 % 与 _ 应被转义为字面量，不作通配符
        String sql = render(ReportService.keywordCondition(ReportType.JOB_EXEC, "100%_x"));
        assertTrue(sql.contains("\\%"), sql);
        assertTrue(sql.contains("\\_"), sql);
    }

    private static String render(org.jooq.Condition cond) {
        return DSL.using(SQLDialect.MYSQL).renderInlined(cond);
    }
}
