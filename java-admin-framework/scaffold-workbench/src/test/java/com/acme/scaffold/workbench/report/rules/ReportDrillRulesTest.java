package com.acme.scaffold.workbench.report.rules;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.workbench.report.dto.DrillDimension;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportType;
import org.jooq.Condition;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报表下钻纯函数离线单测（无 DB 依赖）：
 * 维度适用性 {@link ReportDrillRules#dimensionApplies}、父级过滤谓词
 * {@link ReportDrillRules#parentCondition}、父级日期解析 {@link ReportDrillRules#resolveDay}、
 * 耗时分桶谓词 {@link ReportDrillRules#durationBucketCondition}、下钻标题
 * {@link ReportDrillRules#drillTitle}。
 *
 * <p>条件类方法以 {@code DSL.using(SQLDialect.MYSQL).renderInlined(...)} 渲染后断言字段与边界值，
 * 与 {@code ReportServiceStatsTest#render} 同口径。</p>
 */
class ReportDrillRulesTest {

    private static final ReportRange.ResolvedWindow OCT_WINDOW = new ReportRange.ResolvedWindow(
            LocalDate.of(2026, 10, 1).atStartOfDay(),
            LocalDate.of(2026, 10, 31).atTime(23, 59, 59, 999_000_000),
            ReportRange.BucketMode.DAY);

    private static final ReportRange.ResolvedWindow JAN_WINDOW = new ReportRange.ResolvedWindow(
            LocalDate.of(2026, 1, 1).atStartOfDay(),
            LocalDate.of(2026, 1, 31).atTime(23, 59, 59, 999_000_000),
            ReportRange.BucketMode.DAY);

    private static String render(Condition c) {
        return DSL.using(SQLDialect.MYSQL).renderInlined(c).replace("`", "");
    }

    // ---------------- dimensionApplies ----------------

    @Test
    void dimensionApplies_timeDayAndDurationAlwaysApply() {
        for (ReportType t : ReportType.values()) {
            assertTrue(ReportDrillRules.dimensionApplies(t, DrillDimension.TIME_DAY));
            assertTrue(ReportDrillRules.dimensionApplies(t, DrillDimension.DURATION));
        }
    }

    @Test
    void dimensionApplies_moduleStatusRequestPath_onlyOpLogViews() {
        for (ReportType t : new ReportType[]{ReportType.USAGE, ReportType.CATEGORY, ReportType.STATUS, ReportType.API_CALL}) {
            assertTrue(ReportDrillRules.dimensionApplies(t, DrillDimension.MODULE));
            assertTrue(ReportDrillRules.dimensionApplies(t, DrillDimension.STATUS));
            assertTrue(ReportDrillRules.dimensionApplies(t, DrillDimension.REQUEST_PATH));
        }
        for (ReportType t : new ReportType[]{ReportType.JOB_EXEC, ReportType.LOGIN}) {
            assertFalse(ReportDrillRules.dimensionApplies(t, DrillDimension.MODULE));
            assertFalse(ReportDrillRules.dimensionApplies(t, DrillDimension.STATUS));
            assertFalse(ReportDrillRules.dimensionApplies(t, DrillDimension.REQUEST_PATH));
        }
    }

    @Test
    void dimensionApplies_loginType_onlyLogin() {
        assertTrue(ReportDrillRules.dimensionApplies(ReportType.LOGIN, DrillDimension.LOGIN_TYPE));
        assertFalse(ReportDrillRules.dimensionApplies(ReportType.USAGE, DrillDimension.LOGIN_TYPE));
        assertFalse(ReportDrillRules.dimensionApplies(ReportType.JOB_EXEC, DrillDimension.LOGIN_TYPE));
    }

    // ---------------- resolveDay ----------------

    @Test
    void resolveDay_isoDate() {
        assertEquals(LocalDate.of(2026, 10, 12),
                ReportDrillRules.resolveDay("2026-10-12", OCT_WINDOW));
    }

    @Test
    void resolveDay_monthDayWithinWindow() {
        assertEquals(LocalDate.of(2026, 10, 12),
                ReportDrillRules.resolveDay("10-12", OCT_WINDOW));
    }

    @Test
    void resolveDay_monthDayNotInWindow_fallsBackToToday() {
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay("10-12", JAN_WINDOW));
    }

    @Test
    void resolveDay_blankOrNull_fallsBackToToday() {
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay(null, OCT_WINDOW));
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay("  ", OCT_WINDOW));
    }

    // ---------------- durationBucketCondition ----------------

    @Test
    void durationBucketCondition_nullBucket() {
        String sql = render(ReportDrillRules.durationBucketCondition("无耗时记录", JooqTables.SYS_JOB_LOG));
        assertTrue(sql.contains("duration_ms"), sql);
        assertTrue(sql.toLowerCase().contains("is null"), sql);
    }

    @Test
    void durationBucketCondition_lessThan100() {
        String sql = render(ReportDrillRules.durationBucketCondition("<100ms", JooqTables.SYS_JOB_LOG));
        assertTrue(sql.contains("duration_ms") && sql.contains("100"), sql);
        assertFalse(sql.contains("500"), sql);
    }

    @Test
    void durationBucketCondition_100to500() {
        String sql = render(ReportDrillRules.durationBucketCondition("100-500ms", JooqTables.SYS_JOB_LOG));
        assertTrue(sql.contains("100") && sql.contains("500"), sql);
    }

    @Test
    void durationBucketCondition_500to1000() {
        String sql = render(ReportDrillRules.durationBucketCondition("500ms-1s", JooqTables.SYS_JOB_LOG));
        assertTrue(sql.contains("500") && sql.contains("1000"), sql);
    }

    @Test
    void durationBucketCondition_1000to5000() {
        String sql = render(ReportDrillRules.durationBucketCondition("1s-5s", JooqTables.SYS_JOB_LOG));
        assertTrue(sql.contains("1000") && sql.contains("5000"), sql);
    }

    @Test
    void durationBucketCondition_over5000() {
        String sql = render(ReportDrillRules.durationBucketCondition(">5s", JooqTables.SYS_JOB_LOG));
        assertTrue(sql.contains("5000"), sql);
        assertFalse(sql.toLowerCase().contains("is null"), sql);
    }

    @Test
    void durationBucketCondition_unknownLabel_noCondition() {
        String sql = render(ReportDrillRules.durationBucketCondition("nope", JooqTables.SYS_JOB_LOG));
        assertFalse(sql.toLowerCase().contains("duration_ms"), sql);
    }

    // ---------------- parentCondition ----------------

    private static void assertNoCondition(Condition c) {
        String sql = render(c).toLowerCase();
        assertFalse(sql.contains("module_code") || sql.contains("status")
                || sql.contains("success") || sql.contains("login_type")
                || sql.contains("request_path") || sql.contains("duration_ms"), sql);
    }

    @Test
    void parentCondition_blankOrNonApplicable_noCondition() {
        assertNoCondition(ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.MODULE, "  "));
        // TIME_DAY 永不叠加过滤
        assertNoCondition(ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.TIME_DAY, "2026-10-12"));
        // MODULE 不适用于 JOB_EXEC
        assertNoCondition(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.MODULE, "sys"));
    }

    @Test
    void parentCondition_module_onOpLogView() {
        String sql = render(ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.MODULE, "order"));
        assertTrue(sql.contains("module_code") && sql.contains("order"), sql);
    }

    @Test
    void parentCondition_status_opLog_successVsFailure() {
        String ok = render(ReportDrillRules.parentCondition(ReportType.STATUS, DrillDimension.STATUS, "成功"));
        assertTrue(ok.contains("success") && (ok.contains("= 1") || ok.contains("=1")), ok);
        String fail = render(ReportDrillRules.parentCondition(ReportType.STATUS, DrillDimension.STATUS, "失败"));
        assertTrue(fail.contains("success") && (fail.contains("= 0") || fail.contains("=0")), fail);
    }

    @Test
    void parentCondition_status_notApplicableToJobExec_noCondition() {
        // STATUS 维度仅适用于基于 sys_operation_log 的视角；JOB_EXEC 下退化为 noCondition
        // （parentCondition 内部 STATUS 分支的 JOB_EXEC 子句属不可达死代码，与原 ReportService 行为一致）。
        assertNoCondition(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.STATUS, "成功"));
        assertNoCondition(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.STATUS, "失败"));
    }

    @Test
    void parentCondition_loginType_passwordVsRaw() {
        String pw = render(ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.LOGIN_TYPE, "密码登录")).toLowerCase();
        assertTrue(pw.contains("login_type") && pw.contains("'password'"), pw);
        String oauth = render(ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.LOGIN_TYPE, "OAUTH")).toLowerCase();
        assertTrue(oauth.contains("login_type") && oauth.contains("'oauth'"), oauth);
    }

    @Test
    void parentCondition_requestPath_onApiCall() {
        String sql = render(ReportDrillRules.parentCondition(ReportType.API_CALL, DrillDimension.REQUEST_PATH, "/api/user"));
        assertTrue(sql.contains("request_path") && sql.contains("/api/user"), sql);
    }

    @Test
    void parentCondition_duration_onJobExec_mapsToBucket() {
        String sql = render(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.DURATION, "100-500ms"));
        assertTrue(sql.contains("duration_ms") && sql.contains("100") && sql.contains("500"), sql);
    }

    @Test
    void parentCondition_duration_notApplicableToLogin_noCondition() {
        assertNoCondition(ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.DURATION, "100-500ms"));
    }

    // ---------------- drillTitle ----------------

    @Test
    void drillTitle_formatsPerDimension() {
        String t1 = ReportDrillRules.drillTitle(ReportType.USAGE, DrillDimension.TIME_DAY, "2026-10-12");
        assertTrue(t1.contains("2026-10-12") && t1.contains("逐小时分布"), t1);
        assertEquals("模块「order」趋势", ReportDrillRules.drillTitle(ReportType.USAGE, DrillDimension.MODULE, "order"));
        assertEquals("状态「成功」趋势", ReportDrillRules.drillTitle(ReportType.USAGE, DrillDimension.STATUS, "成功"));
        assertEquals("登录方式「密码登录」趋势", ReportDrillRules.drillTitle(ReportType.LOGIN, DrillDimension.LOGIN_TYPE, "密码登录"));
        assertEquals("接口「/api/x」趋势", ReportDrillRules.drillTitle(ReportType.API_CALL, DrillDimension.REQUEST_PATH, "/api/x"));
        assertEquals("耗时「100-500ms」明细", ReportDrillRules.drillTitle(ReportType.JOB_EXEC, DrillDimension.DURATION, "100-500ms"));
    }
}
