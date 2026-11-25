package com.acme.scaffold.workbench.report.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.workbench.report.domain.DrillDimension;
import com.acme.scaffold.workbench.report.domain.ReportRange.ResolvedWindow;
import com.acme.scaffold.workbench.report.domain.ReportType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 报表下钻纯函数单测：维度适用性、父级谓词、日期解析与标题拼装。 */
class ReportDrillRulesTest {

    private static final ResolvedWindow WINDOW = new ResolvedWindow(
            LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59), null);

    @Test
    void timeAndDurationDimensionsApplyToEveryReportType() {
        for (ReportType type : ReportType.values()) {
            assertTrue(ReportDrillRules.dimensionApplies(type, DrillDimension.TIME_DAY));
            assertTrue(ReportDrillRules.dimensionApplies(type, DrillDimension.DURATION));
        }
    }

    @Test
    void logDimensionsApplyOnlyToOperationLogAndLoginViews() {
        for (DrillDimension d : List.of(DrillDimension.MODULE, DrillDimension.STATUS, DrillDimension.REQUEST_PATH)) {
            assertTrue(ReportDrillRules.dimensionApplies(ReportType.USAGE, d));
            assertTrue(ReportDrillRules.dimensionApplies(ReportType.CATEGORY, d));
            assertTrue(ReportDrillRules.dimensionApplies(ReportType.STATUS, d));
            assertTrue(ReportDrillRules.dimensionApplies(ReportType.API_CALL, d));
            assertFalse(ReportDrillRules.dimensionApplies(ReportType.JOB_EXEC, d));
            assertFalse(ReportDrillRules.dimensionApplies(ReportType.LOGIN, d));
        }
        assertTrue(ReportDrillRules.dimensionApplies(ReportType.LOGIN, DrillDimension.LOGIN_TYPE));
        assertFalse(ReportDrillRules.dimensionApplies(ReportType.USAGE, DrillDimension.LOGIN_TYPE));
    }

    @Test
    void blankParentOrInapplicableDimensionShouldDegradeToNoCondition() {
        assertTrue(ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.MODULE, "  ").isEmpty());
        assertTrue(ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.MODULE, null).isEmpty());
        assertTrue(ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.MODULE, "x").isEmpty());
        assertTrue(ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.TIME_DAY, "2026-10-12").isEmpty());
    }

    @Test
    void moduleAndRequestPathShouldBindParentAsParameter() {
        Condition module = ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.MODULE, "auth");
        assertTrue(module.sql().contains("module_code = ?"));
        assertEquals(List.of("auth"), module.binds());

        Condition path = ReportDrillRules.parentCondition(ReportType.API_CALL, DrillDimension.REQUEST_PATH, "/api/a");
        assertTrue(path.sql().contains("request_path = ?"));
        assertEquals(List.of("/api/a"), path.binds());
    }

    @Test
    void statusShouldMapToSuccessFlagOrJobStatus() {
        Condition success = ReportDrillRules.parentCondition(ReportType.USAGE, DrillDimension.STATUS, "成功");
        assertTrue(success.sql().contains("success = ?"));
        assertEquals(List.of(1), success.binds());

        Condition failure = ReportDrillRules.parentCondition(ReportType.STATUS, DrillDimension.STATUS, "失败");
        assertEquals(List.of(0), failure.binds());

        // STATUS 维度按设计不适用于 JOB_EXEC（仅操作日志视角），应退化为不下钻；
        // parentCondition 中保留的 JOB_EXEC 分支当前不可达，测试锁定实际对外语义。
        assertTrue(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.STATUS, "成功").isEmpty());
        assertTrue(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.STATUS, "异常").isEmpty());
    }

    @Test
    void loginTypeShouldMapChineseLabelAndKeepUnknownValues() {
        Condition password = ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.LOGIN_TYPE, "密码登录");
        assertTrue(password.sql().contains("login_type = ?"));
        assertEquals(List.of("PASSWORD"), password.binds());
        assertEquals(List.of("SMS"),
                ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.LOGIN_TYPE, "SMS").binds());
    }

    @Test
    void durationDimensionShouldApplyOnlyToJobAndApiCall() {
        assertTrue(ReportDrillRules.parentCondition(ReportType.JOB_EXEC, DrillDimension.DURATION, "100-500ms").sql()
                .contains("duration_ms"));
        assertTrue(ReportDrillRules.parentCondition(ReportType.API_CALL, DrillDimension.DURATION, "100-500ms").sql()
                .contains("duration_ms"));
        assertTrue(ReportDrillRules.parentCondition(ReportType.LOGIN, DrillDimension.DURATION, "100-500ms").isEmpty());
    }

    @Test
    void durationBucketShouldMapEveryLabelAndRejectUnknown() {
        String[] labels = ReportStats.DURATION_LABELS;
        assertTrue(ReportDrillRules.durationBucketCondition(labels[0], SqlTables.SYS_JOB_LOG).sql().contains("IS NULL"));
        assertEquals(List.of(100L),
                ReportDrillRules.durationBucketCondition(labels[1], SqlTables.SYS_JOB_LOG).binds());
        assertEquals(List.of(100L, 500L),
                ReportDrillRules.durationBucketCondition(labels[2], SqlTables.SYS_JOB_LOG).binds());
        assertEquals(List.of(5000L),
                ReportDrillRules.durationBucketCondition(labels[5], SqlTables.SYS_JOB_LOG).binds());
        assertTrue(ReportDrillRules.durationBucketCondition("unknown", SqlTables.SYS_JOB_LOG).isEmpty());
    }

    @Test
    void resolveDayShouldPreferFullDateThenWindowMonthDayThenToday() {
        assertEquals(LocalDate.of(2026, 10, 12),
                ReportDrillRules.resolveDay("2026-10-12", WINDOW));
        assertEquals(LocalDate.of(2026, 10, 5),
                ReportDrillRules.resolveDay("10-05", WINDOW));
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay("12-25", WINDOW), "窗口外 MM-dd 回落今日");
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay("not-a-date", WINDOW));
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay(null, WINDOW));
        assertEquals(LocalDate.now(), ReportDrillRules.resolveDay("  ", WINDOW));
    }

    @Test
    void drillTitleShouldUseTypeLabelAndDimensionTemplates() {
        assertEquals("使用统计 · 2026-10-12 逐小时分布",
                ReportDrillRules.drillTitle(ReportType.USAGE, DrillDimension.TIME_DAY, "2026-10-12"));
        assertEquals("模块「auth」趋势",
                ReportDrillRules.drillTitle(ReportType.USAGE, DrillDimension.MODULE, "auth"));
        assertEquals("状态「成功」趋势",
                ReportDrillRules.drillTitle(ReportType.STATUS, DrillDimension.STATUS, "成功"));
        assertEquals("登录方式「密码登录」趋势",
                ReportDrillRules.drillTitle(ReportType.LOGIN, DrillDimension.LOGIN_TYPE, "密码登录"));
        assertEquals("接口「/api/a」趋势",
                ReportDrillRules.drillTitle(ReportType.API_CALL, DrillDimension.REQUEST_PATH, "/api/a"));
        assertEquals("耗时「100-500ms」明细",
                ReportDrillRules.drillTitle(ReportType.JOB_EXEC, DrillDimension.DURATION, "100-500ms"));
        assertEquals("模块「」趋势",
                ReportDrillRules.drillTitle(ReportType.USAGE, DrillDimension.MODULE, null));
    }
}
