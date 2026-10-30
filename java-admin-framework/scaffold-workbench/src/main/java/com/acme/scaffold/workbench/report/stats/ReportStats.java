package com.acme.scaffold.workbench.report.stats;

import com.acme.scaffold.workbench.report.dto.ReportDistributionVO;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 报表统计纯函数集合（从 {@code ReportService} 抽离，对应重构计划 C1 余量）。
 *
 * <p>这些方法不含任何数据库 / Spring 依赖，仅做数值与序列换算，便于离线单测与跨报表复用。
 * 覆盖：连续序列补零（{@link #fillHourly}/{@link #fillDaily}）、分布换算（{@link #toDistribution}）、
 * P95 估算（{@link #p95Of}）。
 */
public final class ReportStats {

    private ReportStats() {
    }

    /** 趋势序列空桶占位（[主指标, 次指标]；第三元素失败数缺省为 0）。 */
    static final long[] ZERO = {0L, 0L};
    static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");
    /**
     * 耗时分布分桶标签（与 {@code durationCounts} SQL 聚合顺序严格一致）。
     *
     * <p>SQL 侧按「NULL 桶优先」聚合（见持久层 {@code durationCounts}），故此处首元素必须是「无耗时记录」，
     * 否则整体标签错位一位（历史缺陷：标签曾按「<100ms 打头」排列，导致 NULL 桶被标成 <100ms）。</p>
     */
    static final String[] DURATION_LABELS =
            {"无耗时记录", "<100ms", "100-500ms", "500ms-1s", "1s-5s", ">5s"};

    /**
     * 分布换算（纯函数）：计数 → 标签 + 占比（保留 1 位小数，总量 0 时全记 0.0）。
     *
     * @param counts 与 {@link #DURATION_LABELS} 顺序一致的 6 个分桶计数
     */
    public static List<ReportDistributionVO> toDistribution(long[] counts) {
        long total = 0L;
        for (long c : counts) {
            total += c;
        }
        List<ReportDistributionVO> out = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            out.add(new ReportDistributionVO(DURATION_LABELS[i], counts[i], ratio(counts[i], total)));
        }
        return out;
    }

    /**
     * P95 计算（纯函数，最近秩法）：第 {@code ceil(0.95*n)} 个最小值（1-based）。
     * 忽略 null 样本；空样本返回 0。
     */
    public static long p95Of(List<Long> durations) {
        if (durations == null || durations.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(durations.size());
        for (Long d : durations) {
            if (d != null) {
                sorted.add(d);
            }
        }
        if (sorted.isEmpty()) {
            return 0L;
        }
        Collections.sort(sorted);
        int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sorted.size()) {
            idx = sorted.size() - 1;
        }
        return sorted.get(idx);
    }

    /** 按 0–23 整点补零生成连续序列（纯函数）。失败数取第三元素，缺省 0。 */
    public static List<ReportSeriesPointVO> fillHourly(Map<Integer, long[]> agg) {
        List<ReportSeriesPointVO> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            long[] v = agg.getOrDefault(h, ZERO);
            long failed = v.length > 2 ? v[2] : 0L;
            out.add(new ReportSeriesPointVO(String.format("%02d:00", h), v[0], v[1], failed));
        }
        return out;
    }

    /** 按 [from, to] 连续日期补零生成序列（纯函数）。失败数取第三元素，缺省 0。 */
    public static List<ReportSeriesPointVO> fillDaily(LocalDate from, LocalDate to, Map<LocalDate, long[]> agg) {
        List<ReportSeriesPointVO> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long[] v = agg.getOrDefault(d, ZERO);
            long failed = v.length > 2 ? v[2] : 0L;
            out.add(new ReportSeriesPointVO(d.format(DAY_FMT), v[0], v[1], failed));
        }
        return out;
    }

    static double ratio(long count, long total) {
        return total == 0 ? 0.0 : Math.round(count * 1000.0 / total) / 10.0;
    }
}
