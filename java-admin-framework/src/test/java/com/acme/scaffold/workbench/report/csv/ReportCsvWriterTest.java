package com.acme.scaffold.workbench.report.csv;

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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReportCsvWriter} 离线单测：行结构 / BOM / 转义（委托 CsvExporter）/ CRLF / 文件名。
 */
class ReportCsvWriterTest {

    private static ReportQuery query(ReportType type, String keyword) {
        return new ReportQuery(type, ReportRange.LAST_7D, null, null, null, null, keyword, null, null);
    }

    private static ReportSummaryVO summary() {
        return new ReportSummaryVO(100L, 20L, 95.5, 5L, 120L, 10L, 900L, 600L);
    }

    private static ReportVO usageVo() {
        return new ReportVO(ReportType.USAGE.name(), "使用报表", ReportRange.LAST_7D.name(),
                "2026-10-10 00:00:00", "2026-10-16 23:59:59", summary(),
                List.of(new ReportSeriesPointVO("10-15", 30L, 8L, 2L)),
                List.of(new ReportCategoryStatVO("user,manage", 60L, 60.0),
                        new ReportCategoryStatVO("role", 40L, 40.0)),
                List.of(new ReportStatusStatVO("成功", 95L, 95.0),
                        new ReportStatusStatVO("失败", 5L, 5.0)),
                List.of(),
                null);
    }

    private static ReportVO statVo() {
        return new ReportVO(ReportType.JOB_EXEC.name(), "任务执行报表", ReportRange.LAST_7D.name(),
                "2026-10-10 00:00:00", "2026-10-16 23:59:59", summary(),
                List.of(new ReportSeriesPointVO("10-15", 30L, 8L, 2L)),
                List.of(),
                List.of(),
                List.of(new ReportDistributionVO("<100ms", 80L, 80.0)),
                new ReportDetailTableVO(
                        List.of(new ReportDetailTableVO.Column("job", "任务名"),
                                new ReportDetailTableVO.Column("cost", "耗时(ms)")),
                        List.of(Map.of("job", "同步\"订单\"数据", "cost", "120"),
                                Map.of("job", "无耗时行")))); // 缺 cost key → null → 空串
    }

    @Test
    @DisplayName("使用视角：标题段 + 汇总/类别/状态/趋势四段行结构")
    void usageLinesStructure() {
        List<List<String>> lines = ReportCsvWriter.lines(query(ReportType.USAGE, null), usageVo());

        assertEquals(List.of("报表管理导出", "", ""), lines.get(0));
        assertEquals(List.of("统计窗口", "2026-10-10 00:00:00 ~ 2026-10-16 23:59:59"), lines.get(1));
        assertEquals(List.of("报表类型", "使用报表"), lines.get(2));
        // 第三行后为：空行 / 汇总表头 / 9 项汇总 / 空行 / 类别表头+2 行 / 空行 / 状态表头+2 行 / 空行 / 趋势表头+1 行
        assertTrue(lines.get(3).isEmpty());
        assertEquals(List.of("汇总指标", "数值", ""), lines.get(4));
        assertEquals(List.of("访问量", "100", ""), lines.get(5));
        assertTrue(lines.contains(List.of("类别分布", "操作次数", "占比(%)")));
        // 含逗号的 moduleCode 以原始值入行（转义由 CsvExporter 负责）
        assertTrue(lines.contains(List.of("user,manage", "60", "60.0")));
        assertEquals(List.of("时间", "访问量", "活跃用户数", "失败数"),
                lines.get(lines.size() - 2));
        assertEquals(List.of("10-15", "30", "8", "2"), lines.get(lines.size() - 1));
    }

    @Test
    @DisplayName("关键字非空时标题段追加关键字行")
    void keywordLineAppended() {
        List<List<String>> lines = ReportCsvWriter.lines(query(ReportType.USAGE, "  订单  "), usageVo());

        assertEquals(List.of("关键字", "订单"), lines.get(3));
    }

    @Test
    @DisplayName("统计视角：汇总/分布/明细/趋势四段；缺 key 的行值归一为空串")
    void statLinesStructure() {
        List<List<String>> lines = ReportCsvWriter.lines(query(ReportType.JOB_EXEC, null), statVo());

        assertTrue(lines.contains(List.of("汇总指标", "数值", "")));
        assertTrue(lines.contains(List.of("执行次数", "100", "")));
        assertTrue(lines.contains(List.of("涉及任务数", "20", "")));
        assertTrue(lines.contains(List.of("分布", "数量", "占比(%)")));
        assertTrue(lines.contains(List.of("<100ms", "80", "80.0")));
        assertTrue(lines.contains(List.of("明细")));
        assertTrue(lines.contains(List.of("任务名", "耗时(ms)")));
        assertTrue(lines.contains(List.of("同步\"订单\"数据", "120")));
        // 缺 cost key：null → 空串（List.of 不接受 null，须已归一）
        assertTrue(lines.contains(List.of("无耗时行", "")));
        assertEquals(List.of("时间", "执行次数", "涉及任务数", "失败数"),
                lines.get(lines.size() - 2));
    }

    @Test
    @DisplayName("toCsvBytes：UTF-8 BOM 前缀 + CRLF 行尾 + 逗号/引号转义（委托 CsvExporter）")
    void csvBytesHaveBomCrlfAndEscaping() {
        byte[] bytes = ReportCsvWriter.toCsvBytes(
                ReportCsvWriter.lines(query(ReportType.USAGE, null), usageVo()));
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"), "须以 BOM 开头（Excel 中文兼容）");
        assertTrue(csv.contains("\r\n"), "行尾为 CRLF");
        // 含逗号的 moduleCode 被整体引号包裹；内部双引号翻倍
        assertTrue(csv.contains("\"user,manage\",60,60.0"), "逗号字段须被引号包裹");
    }

    @Test
    @DisplayName("toCsvBytes：空行序列不抛异常（headers 为空 → 仅 BOM + CRLF）")
    void emptyLinesDoNotThrow() {
        byte[] bytes = ReportCsvWriter.toCsvBytes(List.of());
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"));
        assertEquals("\uFEFF\r\n", csv);
    }

    @Test
    @DisplayName("fileName：报表管理_<RANGE>_<起十日>_<止十日>.csv")
    void fileNameFormat() {
        assertEquals("报表管理_LAST_7D_2026-10-10_2026-10-16.csv",
                ReportCsvWriter.fileName(query(ReportType.USAGE, null), usageVo()));
    }

    @Test
    @DisplayName("BOM 字节序列精确为 EF BB BF")
    void bomBytesExact() {
        byte[] bytes = ReportCsvWriter.toCsvBytes(
                ReportCsvWriter.lines(query(ReportType.USAGE, null), usageVo()));

        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                new byte[]{bytes[0], bytes[1], bytes[2]});
    }
}
