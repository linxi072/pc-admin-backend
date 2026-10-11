package com.acme.scaffold.workbench.report;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 报表管理聚合：基于 {@code sys_operation_log}（审计日志）聚合并以纯 jOOQ 聚合查询完成，
 * 不引入独立统计表——审计日志已覆盖「谁、在何时、对哪个模块、做了什么、成功与否」，足以支撑
 * 访问量 / 功能使用频次 / 活跃用户数 / 成功失败分布等指标。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>连续序列补零</b>：趋势按天/小时分桶后，对窗口内所有桶位（含无数据的桶）统一补零，
 *       前端可直接渲染折线/面积图，无需二次补点；</li>
 *   <li><b>多维度</b>：支持按时间（趋势）、类别（module_code Top10）、状态（成功/失败）三类维度汇总，
 *       并提供类别筛选与排序；</li>
 *   <li><b>纯函数可测</b>：时间窗解析见 {@link ReportRange}，连续补零见本类 {@code fillHourly/fillDaily}，
 *       均以静态方法暴露，便于离线单测；</li>
 *   <li><b>单租户口径</b>：当前 scaffold 为单租户，统一按 {@code tenant_id = 0} 收敛。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DSLContext dsl;

    /** 趋势序列空桶占位（[访问量, 活跃用户数]）。 */
    private static final long[] ZERO = {0L, 0L};
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 生成完整报表（含汇总 / 趋势 / 类别 / 状态四个维度）。 */
    public ReportVO report(ReportQuery query) {
        ReportType type = query.type();
        ReportRange.ResolvedWindow w =
                query.rangeType().resolve(LocalDateTime.now(), query.start(), query.end());
        Condition cond = baseCondition(w.from(), w.to(), query.module(), query.success());

        ReportSummaryVO summary = buildSummary(cond);
        List<ReportSeriesPointVO> trend = buildTrend(w, cond);
        List<ReportCategoryStatVO> categoryStats =
                buildCategoryStats(cond, summary.totalVisits(), query.sortBy(), query.order());
        List<ReportStatusStatVO> statusStats = buildStatusStats(cond, summary.totalVisits());

        return new ReportVO(
                type.name(), type.getLabel(), query.rangeType().name(),
                w.from().format(WINDOW_FMT), w.to().format(WINDOW_FMT),
                summary, trend, categoryStats, statusStats);
    }

    /** 导出 CSV：汇总 + 类别 + 状态 + 趋势四段，UTF-8 含 BOM。 */
    public ReportCsvExport exportCsv(ReportQuery query) {
        ReportVO vo = report(query);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("报表管理导出,,\n");
        sb.append("统计窗口,").append(vo.start()).append(" ~ ").append(vo.end()).append('\n');
        sb.append("报表类型,").append(vo.title()).append('\n');
        sb.append('\n');
        sb.append("汇总指标,数值,\n");
        sb.append("访问量,").append(vo.summary().totalVisits()).append(",\n");
        sb.append("活跃用户数,").append(vo.summary().activeUsers()).append(",\n");
        sb.append("成功率(%),").append(vo.summary().successRate()).append(",\n");
        sb.append("涉及模块数,").append(vo.summary().moduleCount()).append(",\n");
        sb.append("平均耗时(ms),").append(vo.summary().avgDurationMs()).append(",\n");
        sb.append('\n');
        sb.append("类别分布,操作次数,占比(%)\n");
        for (ReportCategoryStatVO c : vo.categoryStats()) {
            sb.append(escapeCsv(c.moduleCode())).append(',')
                    .append(c.count()).append(',')
                    .append(c.ratio()).append('\n');
        }
        sb.append('\n');
        sb.append("状态分布,操作次数,占比(%)\n");
        for (ReportStatusStatVO s : vo.statusStats()) {
            sb.append(escapeCsv(s.statusLabel())).append(',')
                    .append(s.count()).append(',')
                    .append(s.ratio()).append('\n');
        }
        sb.append('\n');
        sb.append("时间,访问量,活跃用户数\n");
        for (ReportSeriesPointVO p : vo.trend()) {
            sb.append(escapeCsv(p.bucket())).append(',')
                    .append(p.visits()).append(',')
                    .append(p.activeUsers()).append('\n');
        }
        String filename = String.format("报表管理_%s_%s_%s.csv",
                query.rangeType().name(), vo.start().substring(0, 10), vo.end().substring(0, 10));
        return new ReportCsvExport(filename, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ------------------------------------------------------------------
    // 汇总
    // ------------------------------------------------------------------

    private ReportSummaryVO buildSummary(Condition cond) {
        var t = JooqTables.SYS_OPERATION_LOG;
        long total = JooqWriters.count(dsl, t, cond);
        if (total == 0) {
            return new ReportSummaryVO(0L, 0L, 0.0, 0L, 0L);
        }
        long active = Optional.ofNullable(dsl
                        .select(DSL.countDistinct(t.field("operator_id", Long.class)))
                        .from(t.table()).where(cond)
                        .fetchOne(0, Long.class))
                .orElse(0L);
        long success = JooqWriters.count(dsl, t,
                DSL.and(cond, t.field("success", Integer.class).eq(1)));
        double rate = Math.round(success * 10000.0 / total) / 100.0;
        long modules = Optional.ofNullable(dsl
                        .select(DSL.countDistinct(t.field("module_code", String.class)))
                        .from(t.table()).where(cond)
                        .fetchOne(0, Long.class))
                .orElse(0L);
        BigDecimal avg = dsl.select(DSL.avg(t.field("duration_ms", Long.class)))
                .from(t.table()).where(cond)
                .fetchOne(0, BigDecimal.class);
        long avgMs = avg == null ? 0L : Math.round(avg.doubleValue());
        return new ReportSummaryVO(total, active, rate, modules, avgMs);
    }

    // ------------------------------------------------------------------
    // 趋势（连续序列补零）
    // ------------------------------------------------------------------

    private List<ReportSeriesPointVO> buildTrend(ReportRange.ResolvedWindow w, Condition cond) {
        var t = JooqTables.SYS_OPERATION_LOG;
        Field<LocalDateTime> occurred = t.field("occurred_at", LocalDateTime.class);
        Field<Long> operatorId = t.field("operator_id", Long.class);

        if (w.bucketMode() == ReportRange.BucketMode.HOUR) {
            Field<Integer> bucket = DSL.field("HOUR({0})", Integer.class, occurred);
            Result<Record3<Integer, Integer, Integer>> rs = dsl
                    .select(bucket, DSL.count(), DSL.countDistinct(operatorId))
                    .from(t.table()).where(cond).groupBy(bucket).fetch();
            Map<Integer, long[]> map = new HashMap<>();
            for (Record3<Integer, Integer, Integer> r : rs) {
                map.put(r.value1(), new long[]{r.value2().longValue(), r.value3().longValue()});
            }
            return fillHourly(map);
        }
        // 按天分桶：MySQL DATE() 取时间戳的日期部分（项目目标库为 MySQL，使用方言函数模板）
        Field<LocalDate> bucket = DSL.field("DATE({0})", LocalDate.class, occurred);
        Result<Record3<LocalDate, Integer, Integer>> rs = dsl
                .select(bucket, DSL.count(), DSL.countDistinct(operatorId))
                .from(t.table()).where(cond).groupBy(bucket).fetch();
        Map<LocalDate, long[]> map = new HashMap<>();
        for (Record3<LocalDate, Integer, Integer> r : rs) {
            map.put(r.value1(), new long[]{r.value2().longValue(), r.value3().longValue()});
        }
        return fillDaily(w.from().toLocalDate(), w.to().toLocalDate(), map);
    }

    /** 按 0–23 整点补零生成连续序列（纯函数，便于单测）。 */
    static List<ReportSeriesPointVO> fillHourly(Map<Integer, long[]> agg) {
        List<ReportSeriesPointVO> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            long[] v = agg.getOrDefault(h, ZERO);
            out.add(new ReportSeriesPointVO(String.format("%02d:00", h), v[0], v[1]));
        }
        return out;
    }

    /** 按 [from, to] 连续日期补零生成序列（纯函数，便于单测）。 */
    static List<ReportSeriesPointVO> fillDaily(LocalDate from, LocalDate to, Map<LocalDate, long[]> agg) {
        List<ReportSeriesPointVO> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long[] v = agg.getOrDefault(d, ZERO);
            out.add(new ReportSeriesPointVO(d.format(DAY_FMT), v[0], v[1]));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 类别（模块）维度 Top10 + 排序
    // ------------------------------------------------------------------

    private List<ReportCategoryStatVO> buildCategoryStats(Condition cond, long total,
                                                          SortField sortBy, SortOrder order) {
        var t = JooqTables.SYS_OPERATION_LOG;
        Field<String> moduleCoalesced = DSL.coalesce(t.field("module_code", String.class), DSL.val("未知"));
        Result<Record2<String, Integer>> rs = dsl
                .select(moduleCoalesced, DSL.count())
                .from(t.table()).where(cond)
                .groupBy(moduleCoalesced)
                .fetch();
        List<ReportCategoryStatVO> list = new ArrayList<>(rs.size());
        for (Record2<String, Integer> r : rs) {
            long c = r.value2().longValue();
            double ratio = total == 0 ? 0.0 : Math.round(c * 1000.0 / total) / 10.0;
            list.add(new ReportCategoryStatVO(r.value1(), c, ratio));
        }
        Comparator<ReportCategoryStatVO> cmp = switch (sortBy) {
            case COUNT -> Comparator.comparingLong(ReportCategoryStatVO::count);
            case RATIO -> Comparator.comparingDouble(ReportCategoryStatVO::ratio);
            case NAME -> Comparator.comparing(ReportCategoryStatVO::moduleCode, String.CASE_INSENSITIVE_ORDER);
        };
        if (order == SortOrder.DESC) {
            cmp = cmp.reversed();
        }
        list.sort(cmp);
        return list.stream().limit(10).toList();
    }

    // ------------------------------------------------------------------
    // 状态维度（成功 / 失败）
    // ------------------------------------------------------------------

    private List<ReportStatusStatVO> buildStatusStats(Condition cond, long total) {
        var t = JooqTables.SYS_OPERATION_LOG;
        long success = JooqWriters.count(dsl, t,
                DSL.and(cond, t.field("success", Integer.class).eq(1)));
        long failure = JooqWriters.count(dsl, t,
                DSL.and(cond, t.field("success", Integer.class).eq(0)));
        double sr = total == 0 ? 0.0 : Math.round(success * 1000.0 / total) / 10.0;
        double fr = total == 0 ? 0.0 : Math.round(failure * 1000.0 / total) / 10.0;
        List<ReportStatusStatVO> list = new ArrayList<>(2);
        list.add(new ReportStatusStatVO("成功", success, sr));
        list.add(new ReportStatusStatVO("失败", failure, fr));
        return list;
    }

    /** 时间窗基础条件：单租户 + 发生时间落在 [from, to]，可选类别/状态筛选。 */
    private Condition baseCondition(LocalDateTime from, LocalDateTime to, String module, Boolean success) {
        var t = JooqTables.SYS_OPERATION_LOG;
        Condition c = DSL.and(
                t.field("tenant_id", Long.class).eq(0L),
                t.field("occurred_at", LocalDateTime.class).ge(from),
                t.field("occurred_at", LocalDateTime.class).le(to));
        if (module != null && !module.isBlank()) {
            c = DSL.and(c, t.field("module_code", String.class).eq(module));
        }
        if (success != null) {
            c = DSL.and(c, t.field("success", Integer.class).eq(success ? 1 : 0));
        }
        return c;
    }
}
