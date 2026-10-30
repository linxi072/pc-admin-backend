package com.acme.scaffold.workbench.report.export;

import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.workbench.report.csv.ReportCsvWriter;
import com.acme.scaffold.workbench.report.dto.ReportDetailTableVO;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 报表多 Sheet 模型纯函数：{@link ReportVO} → {@link XlsxSheet} 列表（零依赖、无 Spring / DB）。
 *
 * <p>与 {@link ReportCsvWriter}（多段 CSV）同源同口径：汇总 / 分布 / 明细 / 趋势各映射为一个
 * 工作表，指标中文名（主 / 次指标）直接复用 {@code ReportCsvWriter} 的标签函数，
 * 保证两种格式导出的字段口径永不漂移。序列化（样式 / 列宽 / XML 转义）委托
 * {@code XlsxDataExporter#exportWorkbook}，本类只负责「报表数据 → 行模型」。</p>
 *
 * <p>Sheet 结构：
 * <ul>
 *   <li>汇总：指标 / 数值 两列；</li>
 *   <li>使用视角（USAGE/CATEGORY/STATUS）：类别分布 + 状态分布两页；
 *       统计视角（JOB_EXEC/LOGIN/API_CALL）：分布一页 + 明细一页（有明细列时）；</li>
 *   <li>趋势：时间 / 主指标 / 次指标 / 失败数。</li>
 * </ul>
 */
public final class ReportSheets {

    private ReportSheets() {
    }

    /**
     * 生成完整工作表列表（顺序：汇总 → 分布 → 明细 → 趋势）。
     *
     * @param query 原始查询（保留参数以备扩展，如按关键字过滤的说明段）
     * @param vo    报表数据（已由 {@code ReportService.report} 组装）
     * @return 至少包含「汇总」「趋势」两页的工作表列表
     */
    public static List<XlsxSheet> sheets(ReportQuery query, ReportVO vo) {
        List<XlsxSheet> sheets = new ArrayList<>();
        sheets.add(new XlsxSheet("汇总", List.of("指标", "数值"), summaryRows(vo)));
        if (ReportCsvWriter.isStatView(vo.type())) {
            sheets.add(new XlsxSheet("分布", List.of("名称", "数量", "占比(%)"), distributionRows(vo)));
            XlsxSheet detail = detailSheet(vo);
            if (detail != null) {
                sheets.add(detail);
            }
        } else {
            sheets.add(new XlsxSheet("类别分布", List.of("模块", "操作次数", "占比(%)"), categoryRows(vo)));
            sheets.add(new XlsxSheet("状态分布", List.of("状态", "操作次数", "占比(%)"), statusRows(vo)));
        }
        sheets.add(new XlsxSheet("趋势", trendHeaders(vo.type()), trendRows(vo)));
        return sheets;
    }

    /** 汇总页行：与 CSV 汇总段字段一一对应（统计视角前两行为主 / 次指标）。 */
    private static List<List<String>> summaryRows(ReportVO vo) {
        List<List<String>> rows = new ArrayList<>();
        if (ReportCsvWriter.isStatView(vo.type())) {
            rows.add(List.of(ReportCsvWriter.mainLabel(vo.type()), String.valueOf(vo.summary().totalVisits())));
            rows.add(List.of(ReportCsvWriter.secondaryLabel(vo.type()), String.valueOf(vo.summary().activeUsers())));
        } else {
            rows.add(List.of("访问量", String.valueOf(vo.summary().totalVisits())));
            rows.add(List.of("活跃用户数", String.valueOf(vo.summary().activeUsers())));
        }
        rows.add(List.of("成功率(%)", String.valueOf(vo.summary().successRate())));
        rows.add(List.of("失败次数", String.valueOf(vo.summary().failedVisits())));
        if (!ReportCsvWriter.isStatView(vo.type())) {
            rows.add(List.of("涉及模块数", String.valueOf(vo.summary().moduleCount())));
        }
        rows.add(List.of("平均耗时(ms)", String.valueOf(vo.summary().avgDurationMs())));
        rows.add(List.of("最大耗时(ms)", String.valueOf(vo.summary().maxDurationMs())));
        rows.add(List.of("P95耗时(ms)", String.valueOf(vo.summary().p95DurationMs())));
        return rows;
    }

    /** 统计视角分布页行（label / 数量 / 占比）。 */
    private static List<List<String>> distributionRows(ReportVO vo) {
        List<List<String>> rows = new ArrayList<>();
        for (var d : vo.distribution()) {
            rows.add(List.of(d.label(), String.valueOf(d.count()), String.valueOf(d.ratio())));
        }
        return rows;
    }

    /** 使用视角类别分布页行（模块 / 操作次数 / 占比）。 */
    private static List<List<String>> categoryRows(ReportVO vo) {
        List<List<String>> rows = new ArrayList<>();
        for (var c : vo.categoryStats()) {
            rows.add(List.of(c.moduleCode(), String.valueOf(c.count()), String.valueOf(c.ratio())));
        }
        return rows;
    }

    /** 使用视角状态分布页行（状态 / 操作次数 / 占比）。 */
    private static List<List<String>> statusRows(ReportVO vo) {
        List<List<String>> rows = new ArrayList<>();
        for (var s : vo.statusStats()) {
            rows.add(List.of(s.statusLabel(), String.valueOf(s.count()), String.valueOf(s.ratio())));
        }
        return rows;
    }

    /**
     * 明细页（仅统计视角且明细表有列时存在）：表头取列中文名，行按列 key 取值（缺失归一空串，
     * 与 CSV 明细段 {@code null → ""} 行为一致）。
     */
    private static XlsxSheet detailSheet(ReportVO vo) {
        ReportDetailTableVO details = vo.details();
        if (details == null || details.columns() == null || details.columns().isEmpty()) {
            return null;
        }
        List<String> headers = new ArrayList<>(details.columns().size());
        for (ReportDetailTableVO.Column c : details.columns()) {
            headers.add(c.label());
        }
        List<List<String>> rows = new ArrayList<>(details.rows().size());
        for (java.util.Map<String, String> row : details.rows()) {
            List<String> line = new ArrayList<>(details.columns().size());
            for (ReportDetailTableVO.Column c : details.columns()) {
                String v = row.get(c.key());
                line.add(v == null ? "" : v);
            }
            rows.add(line);
        }
        return new XlsxSheet("明细", headers, rows);
    }

    /** 趋势页表头：使用视角为「时间/访问量/活跃用户数/失败数」，统计视角主 / 次指标按类型。 */
    private static List<String> trendHeaders(String type) {
        if (ReportCsvWriter.isStatView(type)) {
            return List.of("时间", ReportCsvWriter.mainLabel(type), ReportCsvWriter.secondaryLabel(type), "失败数");
        }
        return List.of("时间", "访问量", "活跃用户数", "失败数");
    }

    /** 趋势页行（时间桶 / 访问量 / 活跃用户 / 失败数）。 */
    private static List<List<String>> trendRows(ReportVO vo) {
        List<List<String>> rows = new ArrayList<>(vo.trend().size());
        for (var p : vo.trend()) {
            rows.add(List.of(p.bucket(), String.valueOf(p.visits()),
                    String.valueOf(p.activeUsers()), String.valueOf(p.failed())));
        }
        return rows;
    }
}
