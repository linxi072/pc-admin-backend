package com.acme.scaffold.workbench.report.export;

import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.workbench.report.dto.ReportCategoryStatVO;
import com.acme.scaffold.workbench.report.dto.ReportDetailTableVO;
import com.acme.scaffold.workbench.report.dto.ReportDistributionVO;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import com.acme.scaffold.workbench.report.dto.ReportStatusStatVO;
import com.acme.scaffold.workbench.report.dto.ReportSummaryVO;
import com.acme.scaffold.workbench.report.dto.ReportType;
import com.acme.scaffold.workbench.report.dto.ReportVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReportSheets} 纯函数单测：ReportVO → 多 Sheet 模型的结构与口径。
 *
 * <p>口径断言与 {@code ReportCsvWriterTest} 的 CSV 段断言同源（指标名 / 字段序完全一致），
 * 保证报表两种导出格式永不漂移；产物可经 {@code XlsxDataExporter#exportWorkbook} 写出闭环。</p>
 */
class ReportSheetsTest {

    private static ReportVO usageVo() {
        return new ReportVO(ReportType.USAGE.name(), "使用报表", ReportRange.LAST_7D.name(),
                "2026-10-10 00:00:00", "2026-10-16 23:59:59",
                new ReportSummaryVO(100L, 20L, 95.5, 5L, 120L, 10L, 900L, 600L),
                List.of(new ReportSeriesPointVO("10-15", 30L, 8L, 2L)),
                List.of(new ReportCategoryStatVO("user", 60L, 60.0)),
                List.of(new ReportStatusStatVO("成功", 95L, 95.0)),
                List.of(),
                null);
    }

    private static ReportVO statVo(boolean withDetails) {
        return new ReportVO(ReportType.JOB_EXEC.name(), "任务执行报表", ReportRange.LAST_7D.name(),
                "2026-10-10 00:00:00", "2026-10-16 23:59:59",
                new ReportSummaryVO(50L, 3L, 98.0, 1L, 2L, 100L, 800L, 500L),
                List.of(new ReportSeriesPointVO("10-15", 12L, 2L, 1L)),
                List.of(),
                List.of(),
                List.of(new ReportDistributionVO("daily-report", 30L, 60.0)),
                withDetails
                        ? new ReportDetailTableVO(
                        List.of(new ReportDetailTableVO.Column("name", "任务名"),
                                new ReportDetailTableVO.Column("count", "执行次数")),
                        List.of(Map.of("name", "daily-report", "count", "30"),
                                Map.of("name", "monthly-report"))) // 缺 count key → 归一空串
                        : null);
    }

    @Test
    @DisplayName("使用视角：汇总 + 类别分布 + 状态分布 + 趋势 四页，指标口径与 CSV 汇总段一致")
    void usageViewSheets() {
        List<XlsxSheet> sheets = ReportSheets.sheets(new ReportQuery(ReportType.USAGE, ReportRange.LAST_7D,
                null, null, null, null, null, null, null), usageVo());

        assertEquals(List.of("汇总", "类别分布", "状态分布", "趋势"), sheets.stream().map(XlsxSheet::name).toList());
        XlsxSheet summary = sheets.get(0);
        assertEquals(List.of("指标", "数值"), summary.headers());
        assertEquals(List.of("访问量", "100"), summary.rows().get(0));
        assertEquals(List.of("涉及模块数", "5"), summary.rows().get(4));
        assertEquals(List.of("user", "60", "60.0"), sheets.get(1).rows().get(0));
        assertEquals(List.of("成功", "95", "95.0"), sheets.get(2).rows().get(0));
        assertEquals(List.of("时间", "访问量", "活跃用户数", "失败数"), sheets.get(3).headers());
        assertEquals(List.of("10-15", "30", "8", "2"), sheets.get(3).rows().get(0));
    }

    @Test
    @DisplayName("统计视角：汇总 + 分布 + 明细 + 趋势 四页，主/次指标按类型取中文名")
    void statViewSheetsWithDetails() {
        List<XlsxSheet> sheets = ReportSheets.sheets(new ReportQuery(ReportType.JOB_EXEC, ReportRange.LAST_7D,
                null, null, null, null, null, null, null), statVo(true));

        assertEquals(List.of("汇总", "分布", "明细", "趋势"), sheets.stream().map(XlsxSheet::name).toList());
        XlsxSheet summary = sheets.get(0);
        assertEquals(List.of("执行次数", "50"), summary.rows().get(0), "主指标中文名按 JOB_EXEC");
        assertEquals(List.of("涉及任务数", "3"), summary.rows().get(1));
        assertEquals(List.of("daily-report", "30", "60.0"), sheets.get(1).rows().get(0));
        XlsxSheet detail = sheets.get(2);
        assertEquals(List.of("任务名", "执行次数"), detail.headers(), "明细表头取列中文名");
        assertEquals(List.of("daily-report", "30"), detail.rows().get(0));
        assertEquals(List.of("monthly-report", ""), detail.rows().get(1), "缺 key 归一空串（与 CSV 明细段一致）");
        assertEquals(List.of("时间", "执行次数", "涉及任务数", "失败数"), sheets.get(3).headers());
    }

    @Test
    @DisplayName("统计视角无明细（details 为空）：明细页省略，仅三页")
    void statViewWithoutDetailsSkipsDetailSheet() {
        List<XlsxSheet> sheets = ReportSheets.sheets(new ReportQuery(ReportType.JOB_EXEC, ReportRange.LAST_7D,
                null, null, null, null, null, null, null), statVo(false));

        assertEquals(List.of("汇总", "分布", "趋势"), sheets.stream().map(XlsxSheet::name).toList());
    }

    @Test
    @DisplayName("Sheet 模型可经零依赖写出器闭环为多 Sheet 工作簿字节")
    void sheetsRoundTripThroughWorkbookWriter() {
        List<XlsxSheet> sheets = ReportSheets.sheets(new ReportQuery(ReportType.USAGE, ReportRange.LAST_7D,
                null, null, null, null, null, null, null), usageVo());
        byte[] bytes = XlsxDataExporter.exportWorkbook(sheets);
        assertTrue(bytes.length > 0);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    @Test
    @DisplayName("XlsxSheet 防御性拷贝：null 行/表头归一空列表，原始行对象修改不污染模型")
    void xlsxSheetDefensiveCopy() {
        java.util.ArrayList<java.util.List<String>> mutableRows = new java.util.ArrayList<>();
        java.util.ArrayList<String> row = new java.util.ArrayList<>(List.of("a", "b"));
        mutableRows.add(row);
        XlsxSheet sheet = new XlsxSheet("明细", List.of("c1", "c2"), mutableRows);

        assertEquals(List.of("c1", "c2"), sheet.headers());
        assertEquals(1, sheet.rows().size());
        row.set(0, "modified");
        assertEquals("a", sheet.rows().get(0).get(0), "内部行只读，外部修改不影响模型");
        assertThrowsForNullName();
    }

    private static void assertThrowsForNullName() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new XlsxSheet(null, List.of(), List.of()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new XlsxSheet("", List.of(), List.of()));
    }
}
