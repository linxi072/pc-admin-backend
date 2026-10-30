package com.acme.scaffold.workbench.report.service;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqTables.TableRef;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.workbench.report.csv.ReportCsvWriter;
import com.acme.scaffold.workbench.report.dto.ReportCategoryStatVO;
import com.acme.scaffold.workbench.report.stats.ReportStats;
import com.acme.scaffold.workbench.report.dto.ReportCsvVO;
import com.acme.scaffold.workbench.report.dto.ReportDetailTableVO;
import com.acme.scaffold.workbench.report.dto.ReportDistributionVO;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import com.acme.scaffold.workbench.report.dto.ReportStatusStatVO;
import com.acme.scaffold.workbench.report.dto.ReportSummaryVO;
import com.acme.scaffold.workbench.report.dto.ReportType;
import com.acme.scaffold.workbench.report.dto.ReportVO;
import com.acme.scaffold.workbench.report.dto.SortField;
import com.acme.scaffold.workbench.report.dto.SortOrder;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 报表管理聚合：不引入独立统计表，直接对业务日志做聚合查询。
 *
 * <p>数据源与报表类型映射：
 * <ul>
 *   <li>{@link ReportType#USAGE} / {@link #CATEGORY} / {@link #STATUS}：{@code sys_operation_log}（审计日志）——
 *       已覆盖「谁、在何时、对哪个模块、做了什么、成功与否」，足以支撑访问量 / 使用频次 / 活跃用户 / 成败分布；</li>
 *   <li>{@link ReportType#JOB_EXEC}：{@code sys_job_log}——执行成功率 / 失败率 / 耗时分布 / 最近执行明细；</li>
 *   <li>{@link ReportType#LOGIN}：{@code sys_login_log}——登录次数 / 活跃用户趋势 / 异常登录记录；</li>
 *   <li>{@link ReportType#API_CALL}：{@code sys_operation_log}——调用量 / 响应时间 / 错误率。</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>连续序列补零</b>：趋势按天/小时分桶后，对窗口内所有桶位（含无数据的桶）统一补零，
 *       前端可直接渲染折线/面积图，无需二次补点；</li>
 *   <li><b>关键字筛选</b>：JOB_EXEC→任务名、LOGIN→用户名、API_CALL→请求路径，含 SQL 通配符转义；</li>
 *   <li><b>纯函数可测</b>：时间窗解析见 {@link ReportRange}；统计纯函数（连续补零 {@code fillHourly/fillDaily}、
 *       分布换算 {@code toDistribution}、P95 {@code p95Of}）已抽离至 {@link ReportStats}，便于离线单测；
 *       关键字条件见 {@link #keywordCondition}；</li>
 *   <li><b>P95 策略</b>：样本量 ≤ {@value #P95_MEMORY_LIMIT} 时内存精确计算（最近秩法），
 *       超过则退化为最大值，避免大窗口全量排序拖垮数据库；</li>
 *   <li><b>单租户口径</b>：当前 scaffold 为单租户，统一按 {@code tenant_id = 0} 收敛。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DSLContext dsl;

    /** 趋势序列空桶占位转由 {@link ReportStats} 承载（见 {@code ReportStats#ZERO}）。 */
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DETAIL_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 明细表默认行数上限（最近执行 / 异常登录 / Top 接口）。 */
    private static final int DETAIL_LIMIT = 10;
    /** P95 内存精确计算的最大样本量（超过则退化为 max，避免大窗口全量排序）。 */
    private static final int P95_MEMORY_LIMIT = 5000;
    /**
     * 耗时分布分桶标签（与持久层 {@code durationCounts} SQL 聚合顺序严格一致）已迁移至
     * {@link ReportStats#DURATION_LABELS}，本类不再持有常量。
     */
    /** 定时任务日志状态：成功。 */
    private static final String JOB_STATUS_SUCCESS = "SUCCESS";
    /** 定时任务日志状态：失败。 */
    private static final String JOB_STATUS_FAILED = "FAILED";
    /** 登录日志结果：成功 / 失败。 */
    private static final String LOGIN_RESULT_SUCCESS = "SUCCESS";
    private static final String LOGIN_RESULT_FAILURE = "FAILURE";
    /** 登录方式中文标签（其余取值原样展示）。 */
    private static final String LOGIN_TYPE_PASSWORD_LABEL = "密码登录";

    // ==================================================================
    // 入口：按报表类型路由
    // ==================================================================

    /** 生成完整报表（汇总 / 趋势 / 分布 / 明细，按类型组装）。 */
    public ReportVO report(ReportQuery query) {
        ReportRange.ResolvedWindow w =
                query.rangeType().resolve(LocalDateTime.now(), query.start(), query.end());
        return switch (query.type()) {
            case USAGE, CATEGORY, STATUS -> buildUsageView(query, w);
            case JOB_EXEC -> buildJobExecView(query, w);
            case LOGIN -> buildLoginView(query, w);
            case API_CALL -> buildApiCallView(query, w);
        };
    }

    // ==================================================================
    // 使用视角（USAGE / CATEGORY / STATUS）：基于 sys_operation_log
    // ==================================================================

    private ReportVO buildUsageView(ReportQuery query, ReportRange.ResolvedWindow w) {
        ReportType type = query.type();
        Condition cond = baseCondition(w.from(), w.to(), query.module(), query.success());

        ReportSummaryVO summary = buildSummary(cond);
        List<ReportSeriesPointVO> trend = buildTrend(w, cond);
        List<ReportCategoryStatVO> categoryStats =
                buildCategoryStats(cond, summary.totalVisits(), query.sortBy(), query.order());
        List<ReportStatusStatVO> statusStats = buildStatusStats(cond, summary.totalVisits());

        return new ReportVO(
                type.name(), type.getLabel(), query.rangeType().name(),
                w.from().format(WINDOW_FMT), w.to().format(WINDOW_FMT),
                summary, trend, categoryStats, statusStats, List.of(), emptyDetails());
    }

    private ReportSummaryVO buildSummary(Condition cond) {
        var t = JooqTables.SYS_OPERATION_LOG;
        long total = JooqWriters.count(dsl, t, cond);
        if (total == 0) {
            return new ReportSummaryVO(0L, 0L, 0.0, 0L, 0L, 0L, 0L, 0L);
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
        long maxMs = maxDuration(cond, t);
        long p95Ms = p95Duration(cond, t, total);
        return new ReportSummaryVO(total, active, rate, modules, avgMs,
                total - success, maxMs, p95Ms);
    }

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

    // ==================================================================
    // 定时任务执行统计（JOB_EXEC）：基于 sys_job_log
    // ==================================================================

    private ReportVO buildJobExecView(ReportQuery query, ReportRange.ResolvedWindow w) {
        var t = JooqTables.SYS_JOB_LOG;
        Condition cond = statCondition(t, "fire_time", w.from(), w.to(),
                keywordCondition(query.type(), query.keyword()));

        long total = JooqWriters.count(dsl, t, cond);
        ReportSummaryVO summary = jobExecSummary(cond, total);
        List<ReportSeriesPointVO> trend = buildStatTrend(w, cond, t, "fire_time",
                t.field("job_id", Long.class), jobFailedCond(t));
        List<ReportDistributionVO> distribution = durationDistribution(cond, t, total);
        ReportDetailTableVO details = recentJobExecDetails(cond);
        List<ReportStatusStatVO> statusStats = statusStatsOf(
                JooqWriters.count(dsl, t, DSL.and(cond, t.field("status", String.class).eq(JOB_STATUS_SUCCESS))),
                total - JooqWriters.count(dsl, t, DSL.and(cond, t.field("status", String.class).eq(JOB_STATUS_SUCCESS))));

        return new ReportVO(ReportType.JOB_EXEC.name(), ReportType.JOB_EXEC.getLabel(),
                query.rangeType().name(), w.from().format(WINDOW_FMT), w.to().format(WINDOW_FMT),
                summary, trend, List.of(), statusStats, distribution, details);
    }

    private ReportSummaryVO jobExecSummary(Condition cond, long total) {
        var t = JooqTables.SYS_JOB_LOG;
        if (total == 0) {
            return new ReportSummaryVO(0L, 0L, 0.0, 0L, 0L, 0L, 0L, 0L);
        }
        long success = JooqWriters.count(dsl, t,
                DSL.and(cond, t.field("status", String.class).eq(JOB_STATUS_SUCCESS)));
        long failed = total - success;
        long jobs = Optional.ofNullable(dsl
                        .select(DSL.countDistinct(t.field("job_id", Long.class)))
                        .from(t.table()).where(cond)
                        .fetchOne(0, Long.class))
                .orElse(0L);
        double rate = Math.round(success * 10000.0 / total) / 100.0;
        return new ReportSummaryVO(total, jobs, rate, 0L,
                avgDuration(cond, t), failed, maxDuration(cond, t), p95Duration(cond, t, total));
    }

    /** 最近执行明细：按触发时间倒序取 {@value #DETAIL_LIMIT} 条。 */
    private ReportDetailTableVO recentJobExecDetails(Condition cond) {
        var t = JooqTables.SYS_JOB_LOG;
        var rs = dsl.select(
                        t.field("job_name", String.class),
                        t.field("task_key", String.class),
                        t.field("fire_time", LocalDateTime.class),
                        t.field("duration_ms", Long.class),
                        t.field("status", String.class),
                        t.field("error_message", String.class))
                .from(t.table()).where(cond)
                .orderBy(t.field("fire_time", LocalDateTime.class).desc())
                .limit(DETAIL_LIMIT)
                .fetch();
        List<ReportDetailTableVO.Column> columns = List.of(
                col("jobName", "任务名称"), col("taskKey", "任务标识"), col("fireTime", "触发时间"),
                col("durationMs", "耗时(ms)"), col("status", "执行结果"), col("errorMessage", "错误信息"));
        List<Map<String, String>> rows = new ArrayList<>(rs.size());
        for (var r : rs) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("jobName", str(r.value1()));
            row.put("taskKey", str(r.value2()));
            row.put("fireTime", fmtTime(r.value3()));
            row.put("durationMs", r.value4() == null ? "" : String.valueOf(r.value4()));
            row.put("status", JOB_STATUS_SUCCESS.equals(r.value5()) ? "成功" : "失败");
            row.put("errorMessage", str(r.value6()));
            rows.add(row);
        }
        return new ReportDetailTableVO(columns, rows);
    }

    // ==================================================================
    // 系统登录统计（LOGIN）：基于 sys_login_log
    // ==================================================================

    private ReportVO buildLoginView(ReportQuery query, ReportRange.ResolvedWindow w) {
        var t = JooqTables.SYS_LOGIN_LOG;
        Condition cond = statCondition(t, "occurred_at", w.from(), w.to(),
                keywordCondition(query.type(), query.keyword()));

        long total = JooqWriters.count(dsl, t, cond);
        ReportSummaryVO summary = loginSummary(cond, total);
        List<ReportSeriesPointVO> trend = buildStatTrend(w, cond, t, "occurred_at",
                t.field("user_id", Long.class), loginFailedCond(t));
        List<ReportDistributionVO> distribution = loginTypeDistribution(cond);
        ReportDetailTableVO details = abnormalLoginDetails(cond);
        List<ReportStatusStatVO> statusStats = statusStatsOf(
                JooqWriters.count(dsl, t, DSL.and(cond, t.field("result", String.class).eq(LOGIN_RESULT_SUCCESS))),
                total - JooqWriters.count(dsl, t, DSL.and(cond, t.field("result", String.class).eq(LOGIN_RESULT_SUCCESS))));

        return new ReportVO(ReportType.LOGIN.name(), ReportType.LOGIN.getLabel(),
                query.rangeType().name(), w.from().format(WINDOW_FMT), w.to().format(WINDOW_FMT),
                summary, trend, List.of(), statusStats, distribution, details);
    }

    private ReportSummaryVO loginSummary(Condition cond, long total) {
        var t = JooqTables.SYS_LOGIN_LOG;
        if (total == 0) {
            return new ReportSummaryVO(0L, 0L, 0.0, 0L, 0L, 0L, 0L, 0L);
        }
        long success = JooqWriters.count(dsl, t,
                DSL.and(cond, t.field("result", String.class).eq(LOGIN_RESULT_SUCCESS)));
        long active = Optional.ofNullable(dsl
                        .select(DSL.countDistinct(t.field("user_id", Long.class)))
                        .from(t.table()).where(cond)
                        .fetchOne(0, Long.class))
                .orElse(0L);
        double rate = Math.round(success * 10000.0 / total) / 100.0;
        // 登录无耗时口径，avg/max/p95 记 0
        return new ReportSummaryVO(total, active, rate, 0L, 0L, total - success, 0L, 0L);
    }

    /** 登录类型分布（PASSWORD → 密码登录，其余原样）。 */
    private List<ReportDistributionVO> loginTypeDistribution(Condition cond) {
        var t = JooqTables.SYS_LOGIN_LOG;
        Field<String> loginType = t.field("login_type", String.class);
        Result<Record2<String, Integer>> rs = dsl
                .select(loginType, DSL.count())
                .from(t.table()).where(cond)
                .groupBy(loginType)
                .orderBy(DSL.count().desc())
                .fetch();
        long total = rs.stream().mapToLong(r -> r.value2().longValue()).sum();
        List<ReportDistributionVO> out = new ArrayList<>(rs.size());
        for (Record2<String, Integer> r : rs) {
            String type = r.value1();
            String label = LOGIN_TYPE_PASSWORD_LABEL.equals(type) || "PASSWORD".equals(type)
                    ? LOGIN_TYPE_PASSWORD_LABEL : type;
            out.add(new ReportDistributionVO(label, r.value2().longValue(), ratio(r.value2(), total)));
        }
        return out;
    }

    /** 异常登录记录：失败登录按时间倒序取 {@value #DETAIL_LIMIT} 条。 */
    private ReportDetailTableVO abnormalLoginDetails(Condition cond) {
        var t = JooqTables.SYS_LOGIN_LOG;
        Condition failed = DSL.and(cond, loginFailedCond(t));
        var rs = dsl.select(
                        t.field("username", String.class),
                        t.field("failure_code", String.class),
                        t.field("login_type", String.class),
                        t.field("ip_address", String.class),
                        t.field("occurred_at", LocalDateTime.class))
                .from(t.table()).where(failed)
                .orderBy(t.field("occurred_at", LocalDateTime.class).desc())
                .limit(DETAIL_LIMIT)
                .fetch();
        List<ReportDetailTableVO.Column> columns = List.of(
                col("username", "用户名"), col("failureCode", "失败码"), col("loginType", "登录方式"),
                col("ip", "IP 地址"), col("occurredAt", "发生时间"));
        List<Map<String, String>> rows = new ArrayList<>(rs.size());
        for (var r : rs) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("username", str(r.value1()));
            row.put("failureCode", str(r.value2()));
            row.put("loginType", LOGIN_TYPE_PASSWORD_LABEL.equals(r.value3()) || "PASSWORD".equals(r.value3())
                    ? LOGIN_TYPE_PASSWORD_LABEL : str(r.value3()));
            row.put("ip", str(r.value4()));
            row.put("occurredAt", fmtTime(r.value5()));
            rows.add(row);
        }
        return new ReportDetailTableVO(columns, rows);
    }

    // ==================================================================
    // 接口调用统计（API_CALL）：基于 sys_operation_log
    // ==================================================================

    private ReportVO buildApiCallView(ReportQuery query, ReportRange.ResolvedWindow w) {
        var t = JooqTables.SYS_OPERATION_LOG;
        Condition cond = statCondition(t, "occurred_at", w.from(), w.to(),
                keywordCondition(query.type(), query.keyword()));

        long total = JooqWriters.count(dsl, t, cond);
        ReportSummaryVO summary = apiCallSummary(cond, total);
        List<ReportSeriesPointVO> trend = buildStatTrend(w, cond, t, "occurred_at",
                t.field("request_path", String.class), apiFailedCond(t));
        List<ReportDistributionVO> distribution = durationDistribution(cond, t, total);
        ReportDetailTableVO details = topApiDetails(cond);
        List<ReportStatusStatVO> statusStats = statusStatsOf(
                JooqWriters.count(dsl, t, DSL.and(cond, t.field("success", Integer.class).eq(1))),
                total - JooqWriters.count(dsl, t, DSL.and(cond, t.field("success", Integer.class).eq(1))));

        return new ReportVO(ReportType.API_CALL.name(), ReportType.API_CALL.getLabel(),
                query.rangeType().name(), w.from().format(WINDOW_FMT), w.to().format(WINDOW_FMT),
                summary, trend, List.of(), statusStats, distribution, details);
    }

    private ReportSummaryVO apiCallSummary(Condition cond, long total) {
        var t = JooqTables.SYS_OPERATION_LOG;
        if (total == 0) {
            return new ReportSummaryVO(0L, 0L, 0.0, 0L, 0L, 0L, 0L, 0L);
        }
        long success = JooqWriters.count(dsl, t,
                DSL.and(cond, t.field("success", Integer.class).eq(1)));
        long endpoints = Optional.ofNullable(dsl
                        .select(DSL.countDistinct(t.field("request_path", String.class)))
                        .from(t.table()).where(cond)
                        .fetchOne(0, Long.class))
                .orElse(0L);
        double rate = Math.round(success * 10000.0 / total) / 100.0;
        return new ReportSummaryVO(total, endpoints, rate, 0L,
                avgDuration(cond, t), total - success, maxDuration(cond, t), p95Duration(cond, t, total));
    }

    /** Top 接口明细：按请求路径+方法分组，按调用量倒序取 {@value #DETAIL_LIMIT} 条。 */
    private ReportDetailTableVO topApiDetails(Condition cond) {
        var t = JooqTables.SYS_OPERATION_LOG;
        Field<String> path = t.field("request_path", String.class);
        Field<String> method = t.field("request_method", String.class);
        Field<Integer> calls = DSL.count();
        Field<BigDecimal> errors = DSL.sum(DSL.when(apiFailedCond(t), 1).otherwise(0));
        Field<BigDecimal> avgMs = DSL.avg(t.field("duration_ms", Long.class));
        var rs = dsl.select(path, method, calls, errors, avgMs)
                .from(t.table()).where(cond)
                .groupBy(path, method)
                .orderBy(calls.desc())
                .limit(DETAIL_LIMIT)
                .fetch();
        List<ReportDetailTableVO.Column> columns = List.of(
                col("path", "接口路径"), col("method", "方法"), col("calls", "调用量"),
                col("errors", "错误数"), col("avgMs", "平均耗时(ms)"));
        List<Map<String, String>> rows = new ArrayList<>(rs.size());
        for (var r : rs) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("path", str(r.value1()));
            row.put("method", str(r.value2()));
            row.put("calls", String.valueOf(r.value3()));
            row.put("errors", String.valueOf(n(r.value4())));
            row.put("avgMs", r.value5() == null ? "0" : String.valueOf(Math.round(r.value5().doubleValue())));
            rows.add(row);
        }
        return new ReportDetailTableVO(columns, rows);
    }

    // ==================================================================
    // 统计视角公共构件
    // ==================================================================

    /** 统计视角基础条件：单租户 + 发生时间落在 [from, to] + 可选关键字。 */
    private Condition statCondition(TableRef t, String timeField, LocalDateTime from, LocalDateTime to,
                                    Condition keywordCond) {
        Condition c = DSL.and(
                t.field("tenant_id", Long.class).eq(0L),
                t.field(timeField, LocalDateTime.class).ge(from),
                t.field(timeField, LocalDateTime.class).le(to));
        if (keywordCond != null) {
            c = DSL.and(c, keywordCond);
        }
        return c;
    }

    /**
     * 关键字筛选条件（纯函数，无 DB 依赖，便于单测）。
     *
     * <p>按报表类型作用于不同字段：JOB_EXEC→任务名、LOGIN→用户名、API_CALL→请求路径；
     * 使用视角（USAGE/CATEGORY/STATUS）无关键字口径，返回 null。SQL 通配符 {@code %} / {@code _}
     * 做转义，避免用户输入改变匹配语义。</p>
     */
    static Condition keywordCondition(ReportType type, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return switch (type) {
            case JOB_EXEC -> JooqTables.SYS_JOB_LOG.field("job_name", String.class).like(pattern, '\\');
            case LOGIN -> JooqTables.SYS_LOGIN_LOG.field("username", String.class).like(pattern, '\\');
            case API_CALL -> JooqTables.SYS_OPERATION_LOG.field("request_path", String.class).like(pattern, '\\');
            default -> null;
        };
    }

    /**
     * 统计视角趋势（连续序列补零）：每个桶输出 [主指标, 次指标, 失败数]。
     *
     * @param distinctField 次指标的去重字段（任务数 / 活跃用户 / 端点数）
     * @param failedCond    失败口径条件
     */
    private List<ReportSeriesPointVO> buildStatTrend(ReportRange.ResolvedWindow w, Condition cond,
                                                     TableRef t, String timeField,
                                                     Field<?> distinctField, Condition failedCond) {
        Field<LocalDateTime> occurred = t.field(timeField, LocalDateTime.class);
        Field<BigDecimal> failedCnt = DSL.sum(DSL.when(failedCond, 1).otherwise(0));
        if (w.bucketMode() == ReportRange.BucketMode.HOUR) {
            Field<Integer> bucket = DSL.field("HOUR({0})", Integer.class, occurred);
            Result<Record4<Integer, Integer, Integer, BigDecimal>> rs = dsl
                    .select(bucket, DSL.count(), DSL.countDistinct(distinctField), failedCnt)
                    .from(t.table()).where(cond).groupBy(bucket).fetch();
            Map<Integer, long[]> map = new HashMap<>();
            for (Record4<Integer, Integer, Integer, BigDecimal> r : rs) {
                map.put(r.value1(), new long[]{
                        r.value2().longValue(), r.value3().longValue(), n(r.value4())});
            }
            return fillHourly(map);
        }
        Field<LocalDate> bucket = DSL.field("DATE({0})", LocalDate.class, occurred);
        Result<Record4<LocalDate, Integer, Integer, BigDecimal>> rs = dsl
                .select(bucket, DSL.count(), DSL.countDistinct(distinctField), failedCnt)
                .from(t.table()).where(cond).groupBy(bucket).fetch();
        Map<LocalDate, long[]> map = new HashMap<>();
        for (Record4<LocalDate, Integer, Integer, BigDecimal> r : rs) {
            map.put(r.value1(), new long[]{
                    r.value2().longValue(), r.value3().longValue(), n(r.value4())});
        }
        return fillDaily(w.from().toLocalDate(), w.to().toLocalDate(), map);
    }

    /** 成功 / 失败分布（统计视角复用）。 */
    private List<ReportStatusStatVO> statusStatsOf(long success, long failure) {
        long total = success + failure;
        double sr = total == 0 ? 0.0 : Math.round(success * 1000.0 / total) / 10.0;
        double fr = total == 0 ? 0.0 : Math.round(failure * 1000.0 / total) / 10.0;
        return List.of(new ReportStatusStatVO("成功", success, sr),
                new ReportStatusStatVO("失败", failure, fr));
    }

    private static Condition jobFailedCond(TableRef t) {
        return t.field("status", String.class).eq(JOB_STATUS_FAILED);
    }

    private static Condition loginFailedCond(TableRef t) {
        return t.field("result", String.class).eq(LOGIN_RESULT_FAILURE);
    }

    private static Condition apiFailedCond(TableRef t) {
        return t.field("success", Integer.class).eq(0);
    }

    // ------------------------------------------------------------------
    // 耗时分布 / 平均 / 最大 / P95
    // ------------------------------------------------------------------

    /** 耗时分布：SQL 侧 CASE 聚合（避免大窗口全量拉取），标签见 {@link ReportStats#DURATION_LABELS}。 */
    private List<ReportDistributionVO> durationDistribution(Condition cond, TableRef t, long total) {
        return toDistribution(durationCounts(cond, t));
    }

    private long[] durationCounts(Condition cond, TableRef t) {
        Field<Long> d = t.field("duration_ms", Long.class);
        List<Field<BigDecimal>> sums = List.of(
                DSL.sum(DSL.when(d.isNull(), 1).otherwise(0)),
                DSL.sum(DSL.when(d.isNotNull().and(d.lessThan(100L)), 1).otherwise(0)),
                DSL.sum(DSL.when(d.isNotNull().and(d.greaterOrEqual(100L).and(d.lessThan(500L))), 1).otherwise(0)),
                DSL.sum(DSL.when(d.isNotNull().and(d.greaterOrEqual(500L).and(d.lessThan(1000L))), 1).otherwise(0)),
                DSL.sum(DSL.when(d.isNotNull().and(d.greaterOrEqual(1000L).and(d.lessThan(5000L))), 1).otherwise(0)),
                DSL.sum(DSL.when(d.isNotNull().and(d.greaterOrEqual(5000L)), 1).otherwise(0)));
        var rec = dsl.select(sums).from(t.table()).where(cond).fetchOne();
        long[] counts = new long[6];
        if (rec != null) {
            for (int i = 0; i < 6; i++) {
                BigDecimal v = rec.get(i, BigDecimal.class);
                counts[i] = v == null ? 0L : v.longValue();
            }
        }
        return counts;
    }

    /**
     * 分布换算：委托 {@link ReportStats#toDistribution}（纯函数已抽离至 {@code report.stats}）。
     *
     * @param counts 与 {@link ReportStats#DURATION_LABELS} 顺序一致的 6 个分桶计数
     */
    static List<ReportDistributionVO> toDistribution(long[] counts) {
        return ReportStats.toDistribution(counts);
    }

    private long avgDuration(Condition cond, TableRef t) {
        BigDecimal avg = dsl.select(DSL.avg(t.field("duration_ms", Long.class)))
                .from(t.table()).where(cond)
                .fetchOne(0, BigDecimal.class);
        return avg == null ? 0L : Math.round(avg.doubleValue());
    }

    private long maxDuration(Condition cond, TableRef t) {
        Integer max = dsl.select(DSL.max(t.field("duration_ms", Long.class)))
                .from(t.table()).where(cond)
                .fetchOne(0, Integer.class);
        return max == null ? 0L : max.longValue();
    }

    /**
     * P95 耗时：样本 ≤ {@value #P95_MEMORY_LIMIT} 内存精确（最近秩法），否则退化为 max。
     * LOGIN 等无耗时字段的报表不调用本方法。
     */
    private long p95Duration(Condition cond, TableRef t, long total) {
        if (total == 0L) {
            return 0L;
        }
        if (total > P95_MEMORY_LIMIT) {
            return maxDuration(cond, t);
        }
        List<Long> durations = dsl.select(t.field("duration_ms", Long.class))
                .from(t.table()).where(cond)
                .fetch(r -> r.get(0, Long.class));
        return p95Of(durations);
    }

    /**
     * P95 计算：委托 {@link ReportStats#p95Of}（纯函数已抽离至 {@code report.stats}）。
     */
    static long p95Of(List<Long> durations) {
        return ReportStats.p95Of(durations);
    }

    // ==================================================================
    // 类别（模块）维度 Top10 + 排序（使用视角）
    // ==================================================================

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

    // ==================================================================
    // 状态维度（成功 / 失败，使用视角）
    // ==================================================================

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

    /** 时间窗基础条件：单租户 + 发生时间落在 [from, to]，可选类别/状态筛选（使用视角）。 */
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

    // ==================================================================
    // 连续序列补零（纯函数）
    // ==================================================================

    /** 按 0–23 整点补零生成连续序列：委托 {@link ReportStats#fillHourly}。 */
    static List<ReportSeriesPointVO> fillHourly(Map<Integer, long[]> agg) {
        return ReportStats.fillHourly(agg);
    }

    /** 按 [from, to] 连续日期补零生成序列：委托 {@link ReportStats#fillDaily}。 */
    static List<ReportSeriesPointVO> fillDaily(LocalDate from, LocalDate to, Map<LocalDate, long[]> agg) {
        return ReportStats.fillDaily(from, to, agg);
    }

    // ==================================================================
    // 导出 CSV（行模型与序列化已抽至 report/export/ReportCsvWriter，
    // 转义/BOM/行尾统一走 common/export/CsvExporter；本类只做编排）
    // ==================================================================

    /** 导出 CSV：使用视角输出汇总+类别+状态+趋势四段；统计视角输出汇总+分布+明细+趋势四段。UTF-8 含 BOM。 */
    public ReportCsvVO exportCsv(ReportQuery query) {
        ReportVO vo = report(query);
        return new ReportCsvVO(ReportCsvWriter.fileName(query, vo),
                ReportCsvWriter.toCsvBytes(ReportCsvWriter.lines(query, vo)));
    }

    // ==================================================================
    // 内部小工具
    // ==================================================================

    private static ReportDetailTableVO emptyDetails() {
        return new ReportDetailTableVO(List.of(), List.of());
    }

    private static ReportDetailTableVO.Column col(String key, String label) {
        return new ReportDetailTableVO.Column(key, label);
    }

    private static double ratio(Number count, long total) {
        return total == 0 ? 0.0 : Math.round(count.longValue() * 1000.0 / total) / 10.0;
    }

    private static long n(Number v) {
        return v == null ? 0L : v.longValue();
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static String fmtTime(LocalDateTime t) {
        return t == null ? "" : t.format(DETAIL_TIME_FMT);
    }
}
