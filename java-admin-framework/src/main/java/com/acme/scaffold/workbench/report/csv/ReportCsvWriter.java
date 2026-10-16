package com.acme.scaffold.workbench.report.csv;

import com.acme.scaffold.common.export.CsvExporter;
import com.acme.scaffold.workbench.report.dto.ReportCategoryStatVO;
import com.acme.scaffold.workbench.report.dto.ReportDetailTableVO;
import com.acme.scaffold.workbench.report.dto.ReportDistributionVO;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import com.acme.scaffold.workbench.report.dto.ReportStatusStatVO;
import com.acme.scaffold.workbench.report.dto.ReportType;
import com.acme.scaffold.workbench.report.dto.ReportVO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报表 CSV 行模型纯函数：{@link ReportVO} → 扁平行序列（零依赖、无 Spring / DB，便于离线单测）。
 *
 * <p>从 {@code ReportService} 抽出（原私有 {@code appendUsageCsv/appendStatCsv/escapeCsv}），
 * 使报表编排与 CSV 序列化职责分离；序列化（转义 / BOM / 行尾）统一委托
 * {@link CsvExporter}，不再自建第二套转义规则。</p>
 *
 * <p>行结构（与迁移前逐字节对齐，仅行尾由 LF 统一为 CRLF、转义补 {@code \r} 判定）：
 * <ul>
 *   <li>标题段：报表名 / 统计窗口 / 报表类型 / 关键字（有关键字时）；</li>
 *   <li>使用视角（USAGE/CATEGORY/STATUS）：汇总指标 + 类别分布 + 状态分布 + 趋势四段；</li>
 *   <li>统计视角（JOB_EXEC/LOGIN/API_CALL）：汇总指标 + 分布 + 明细 + 趋势四段。</li>
 * </ul>
 */
public final class ReportCsvWriter {

    private ReportCsvWriter() {
    }

    /**
     * 生成完整 CSV 行序列（首行为标题行，空行为段间分隔）。
     *
     * @param query 原始查询（取关键字与文件名要素）
     * @param vo    报表数据（已由 {@code ReportService.report} 组装）
     * @return 扁平行序列；行内字段为原始值，转义由 {@link CsvExporter} 统一处理
     */
    public static List<List<String>> lines(ReportQuery query, ReportVO vo) {
        List<List<String>> lines = new ArrayList<>();
        lines.add(List.of("报表管理导出", "", ""));
        lines.add(List.of("统计窗口", vo.start() + " ~ " + vo.end()));
        lines.add(List.of("报表类型", vo.title()));
        if (query.keyword() != null && !query.keyword().isBlank()) {
            lines.add(List.of("关键字", query.keyword().trim()));
        }
        if (isStatView(vo.type())) {
            appendStatLines(lines, vo);
        } else {
            appendUsageLines(lines, vo);
        }
        return lines;
    }

    /** 下载文件名（不含路径）：{@code 报表管理_<RANGE>_<起>_<止>.csv}。 */
    public static String fileName(ReportQuery query, ReportVO vo) {
        return String.format("报表管理_%s_%s_%s.csv",
                query.rangeType().name(), vo.start().substring(0, 10), vo.end().substring(0, 10));
    }

    /**
     * 行序列 → CSV 字节（UTF-8 BOM + CRLF + RFC4180 转义，经 {@link CsvExporter}）。
     *
     * <p>多段结构以「首行作表头、其余作行」表达；空行以空字段列表表示。
     * Excel 可直接打开（BOM 保证中文不乱码）。</p>
     */
    public static byte[] toCsvBytes(List<List<String>> lines) {
        List<String> headers = lines.isEmpty() ? List.of() : lines.get(0);
        List<List<String>> rows = lines.size() <= 1 ? List.of() : lines.subList(1, lines.size());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers, rows);
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("CSV 生成失败", e);
        }
        return bos.toByteArray();
    }

    private static boolean isStatView(String type) {
        return ReportType.JOB_EXEC.name().equals(type)
                || ReportType.LOGIN.name().equals(type)
                || ReportType.API_CALL.name().equals(type);
    }

    private static void appendUsageLines(List<List<String>> lines, ReportVO vo) {
        lines.add(List.of());
        lines.add(List.of("汇总指标", "数值", ""));
        lines.add(List.of("访问量", String.valueOf(vo.summary().totalVisits()), ""));
        lines.add(List.of("活跃用户数", String.valueOf(vo.summary().activeUsers()), ""));
        lines.add(List.of("成功率(%)", String.valueOf(vo.summary().successRate()), ""));
        lines.add(List.of("失败次数", String.valueOf(vo.summary().failedVisits()), ""));
        lines.add(List.of("涉及模块数", String.valueOf(vo.summary().moduleCount()), ""));
        lines.add(List.of("平均耗时(ms)", String.valueOf(vo.summary().avgDurationMs()), ""));
        lines.add(List.of("最大耗时(ms)", String.valueOf(vo.summary().maxDurationMs()), ""));
        lines.add(List.of("P95耗时(ms)", String.valueOf(vo.summary().p95DurationMs()), ""));
        lines.add(List.of());
        lines.add(List.of("类别分布", "操作次数", "占比(%)"));
        for (ReportCategoryStatVO c : vo.categoryStats()) {
            lines.add(List.of(c.moduleCode(), String.valueOf(c.count()), String.valueOf(c.ratio())));
        }
        lines.add(List.of());
        lines.add(List.of("状态分布", "操作次数", "占比(%)"));
        for (ReportStatusStatVO s : vo.statusStats()) {
            lines.add(List.of(s.statusLabel(), String.valueOf(s.count()), String.valueOf(s.ratio())));
        }
        lines.add(List.of());
        lines.add(List.of("时间", "访问量", "活跃用户数", "失败数"));
        for (ReportSeriesPointVO p : vo.trend()) {
            lines.add(List.of(p.bucket(), String.valueOf(p.visits()),
                    String.valueOf(p.activeUsers()), String.valueOf(p.failed())));
        }
    }

    private static void appendStatLines(List<List<String>> lines, ReportVO vo) {
        String secondary = secondaryLabel(vo.type());
        lines.add(List.of());
        lines.add(List.of("汇总指标", "数值", ""));
        lines.add(List.of(mainLabel(vo.type()), String.valueOf(vo.summary().totalVisits()), ""));
        lines.add(List.of(secondary, String.valueOf(vo.summary().activeUsers()), ""));
        lines.add(List.of("成功率(%)", String.valueOf(vo.summary().successRate()), ""));
        lines.add(List.of("失败次数", String.valueOf(vo.summary().failedVisits()), ""));
        lines.add(List.of("平均耗时(ms)", String.valueOf(vo.summary().avgDurationMs()), ""));
        lines.add(List.of("最大耗时(ms)", String.valueOf(vo.summary().maxDurationMs()), ""));
        lines.add(List.of("P95耗时(ms)", String.valueOf(vo.summary().p95DurationMs()), ""));
        lines.add(List.of());
        lines.add(List.of("分布", "数量", "占比(%)"));
        for (ReportDistributionVO d : vo.distribution()) {
            lines.add(List.of(d.label(), String.valueOf(d.count()), String.valueOf(d.ratio())));
        }
        lines.add(List.of());
        ReportDetailTableVO details = vo.details();
        lines.add(List.of("明细"));
        if (details != null && details.columns() != null && !details.columns().isEmpty()) {
            List<String> header = new ArrayList<>();
            for (ReportDetailTableVO.Column c : details.columns()) {
                header.add(c.label());
            }
            lines.add(header);
            for (Map<String, String> row : details.rows()) {
                List<String> line = new ArrayList<>();
                for (ReportDetailTableVO.Column c : details.columns()) {
                    // 行数据可能缺 key（null 视作空串，与迁移前 escapeCsv(null) → "" 一致；
                    // List.of 不接受 null 元素，故此处必须归一）
                    String v = row.get(c.key());
                    line.add(v == null ? "" : v);
                }
                lines.add(line);
            }
        }
        lines.add(List.of());
        lines.add(List.of("时间", mainLabel(vo.type()), secondary, "失败数"));
        for (ReportSeriesPointVO p : vo.trend()) {
            lines.add(List.of(p.bucket(), String.valueOf(p.visits()),
                    String.valueOf(p.activeUsers()), String.valueOf(p.failed())));
        }
    }

    /** 汇总/趋势段主指标中文名（按统计类型）。 */
    private static String mainLabel(String type) {
        if (ReportType.JOB_EXEC.name().equals(type)) return "执行次数";
        if (ReportType.LOGIN.name().equals(type)) return "登录次数";
        return "调用量";
    }

    /** 汇总/趋势段次指标中文名（按统计类型）。 */
    private static String secondaryLabel(String type) {
        if (ReportType.JOB_EXEC.name().equals(type)) return "涉及任务数";
        if (ReportType.LOGIN.name().equals(type)) return "活跃登录用户";
        return "涉及端点数";
    }
}
