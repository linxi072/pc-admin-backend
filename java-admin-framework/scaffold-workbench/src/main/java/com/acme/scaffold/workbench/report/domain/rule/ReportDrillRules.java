package com.acme.scaffold.workbench.report.domain.rule;

import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.SqlTables.TableRef;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.workbench.report.dto.DrillDimension;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportType;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;

/**
 * 报表下钻纯函数规则（无副作用、可离线单测）。
 *
 * <p>只负责下钻过滤谓词构建、维度适用性判断、父级日期解析与标题拼装等确定性逻辑，
 * 不触碰数据库 / {@code DSLContext} / Spring 上下文，与 {@code report.domain.rule.ReportStats}
 * 同款纯函数范式（{@link ReportService} 的 {@code drill} 统一委托本类，避免私有方法无法单测）。</p>
 */
public final class ReportDrillRules {

    /** 定时任务日志状态：成功 / 失败（下钻过滤专用副本，避免与统计视图常量耦合）。 */
    private static final String JOB_STATUS_SUCCESS = "SUCCESS";
    private static final String JOB_STATUS_FAILED = "FAILED";
    /** 登录方式中文标签（其余取值原样展示）。 */
    private static final String LOGIN_TYPE_PASSWORD_LABEL = "密码登录";

    private ReportDrillRules() {
    }

    /**
     * 维度适用性约束：避免对不匹配的表引用字段导致跨表 SQL 错误。
     *
     * @return {@code true} 表示该维度可作用于该报表类型（否则下钻退化为整窗序列）。
     */
    public static boolean dimensionApplies(ReportType type, DrillDimension dimension) {
        return switch (dimension) {
            case TIME_DAY, DURATION -> true;
            case MODULE, STATUS, REQUEST_PATH ->
                    type == ReportType.USAGE || type == ReportType.CATEGORY
                            || type == ReportType.STATUS || type == ReportType.API_CALL;
            case LOGIN_TYPE -> type == ReportType.LOGIN;
        };
    }

    /**
     * 父级维度过滤谓词：在既有聚合条件之上叠加一个等值 / 范围谓词。
     * 不匹配报表类型的维度或空父级值退化为 {@code noCondition}（不下钻）。
     */
    public static Condition parentCondition(ReportType type, DrillDimension dimension, String parent) {
        if (parent == null || parent.isBlank()) {
            return DSL.noCondition();
        }
        if (!dimensionApplies(type, dimension)) {
            return DSL.noCondition();
        }
        return switch (dimension) {
            case TIME_DAY -> DSL.noCondition();
            case MODULE -> SqlTables.SYS_OPERATION_LOG.field("module_code", String.class).eq(parent);
            case STATUS -> type == ReportType.JOB_EXEC
                    ? SqlTables.SYS_JOB_LOG.field("status", String.class)
                        .eq("成功".equals(parent) ? JOB_STATUS_SUCCESS : JOB_STATUS_FAILED)
                    : SqlTables.SYS_OPERATION_LOG.field("success", Integer.class)
                        .eq("成功".equals(parent) ? 1 : 0);
            case LOGIN_TYPE -> SqlTables.SYS_LOGIN_LOG.field("login_type", String.class)
                    .eq(LOGIN_TYPE_PASSWORD_LABEL.equals(parent) ? "PASSWORD" : parent);
            case REQUEST_PATH -> SqlTables.SYS_OPERATION_LOG.field("request_path", String.class).eq(parent);
            case DURATION -> {
                TableRef t = switch (type) {
                    case JOB_EXEC -> SqlTables.SYS_JOB_LOG;
                    case API_CALL -> SqlTables.SYS_OPERATION_LOG;
                    default -> null;
                };
                yield t == null ? DSL.noCondition() : durationBucketCondition(parent, t);
            }
        };
    }

    /**
     * 解析父级日期：优先 {@code yyyy-MM-dd}；其次在窗口内匹配 {@code MM-dd}（如 {@code "10-12"}）；
     * 均失败回退到今日（保证下钻序列非空且有意义）。
     */
    public static LocalDate resolveDay(String parent, ReportRange.ResolvedWindow w) {
        if (parent == null || parent.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(parent, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception ignored) {
            // fall through
        }
        try {
            MonthDay md = MonthDay.parse(parent, DateTimeFormatter.ofPattern("MM-dd"));
            for (LocalDate d = w.from().toLocalDate(); !d.isAfter(w.to().toLocalDate()); d = d.plusDays(1)) {
                if (d.getMonthValue() == md.getMonthValue() && d.getDayOfMonth() == md.getDayOfMonth()) {
                    return d;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return LocalDate.now();
    }

    /** 耗时分桶谓词：按标签索引映射到与 {@link ReportStats#DURATION_LABELS} 一致的区间。 */
    public static Condition durationBucketCondition(String label, TableRef t) {
        String[] labels = ReportStats.DURATION_LABELS;
        int idx = -1;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(label)) {
                idx = i;
                break;
            }
        }
        Field<Long> d = t.field("duration_ms", Long.class);
        return switch (idx) {
            case 0 -> d.isNull();
            case 1 -> d.isNotNull().and(d.lessThan(100L));
            case 2 -> d.greaterOrEqual(100L).and(d.lessThan(500L));
            case 3 -> d.greaterOrEqual(500L).and(d.lessThan(1000L));
            case 4 -> d.greaterOrEqual(1000L).and(d.lessThan(5000L));
            case 5 -> d.greaterOrEqual(5000L);
            default -> DSL.noCondition();
        };
    }

    /** 下钻标题拼装（纯字符串，便于单测断言）。 */
    public static String drillTitle(ReportType type, DrillDimension dimension, String parent) {
        return switch (dimension) {
            case TIME_DAY -> type.getLabel() + " · " + str(parent) + " 逐小时分布";
            case MODULE -> "模块「" + str(parent) + "」趋势";
            case STATUS -> "状态「" + str(parent) + "」趋势";
            case LOGIN_TYPE -> "登录方式「" + str(parent) + "」趋势";
            case REQUEST_PATH -> "接口「" + str(parent) + "」趋势";
            case DURATION -> "耗时「" + str(parent) + "」明细";
        };
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
