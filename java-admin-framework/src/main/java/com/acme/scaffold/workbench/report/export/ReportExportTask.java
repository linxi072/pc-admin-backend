package com.acme.scaffold.workbench.report.export;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.workbench.report.csv.ReportCsvWriter;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportType;
import com.acme.scaffold.workbench.report.dto.ReportVO;
import com.acme.scaffold.workbench.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 报表管理导出（导出中心模板，与「报表管理」页导出同一数据与行结构）。
 *
 * <p>经导出中心统一调度：模板由 Spring 容器自动收集，前端零维护清单；
 * 行模型与序列化复用 {@link ReportCsvWriter}（与 {@code ReportController#export} 完全一致）。</p>
 *
 * <p><b>权限兜底</b>：导出中心下载端点仅校验 {@code system:export:read}，
 * 而报表数据自身另需 {@code report:read}（见 {@code ReportController} 类级注解）。
 * 若不在此复核，持有导出权限但无报表权限的角色可经本入口绕过 {@code report:read}
 * 拉取报表数据（权限放大）。故 {@link #csv(Map)} 内显式校验当前主体权限。</p>
 *
 * <p>多段结构（标题段 + 汇总/分布/明细/趋势四段）无法用「单表头 + 行」表达，
 * 故覆写 {@link #csv(Map)} 自产字节；{@link #headers()} / {@link #rows(Map)} 不再被调用。</p>
 */
@Component
@RequiredArgsConstructor
public class ReportExportTask implements ExportTask {

    /** 模板编码（导出中心路由键）。 */
    public static final String CODE = "report";

    /** 报表数据自身权限码（导出中心端点权限之外的兜底校验）。 */
    private static final String REPORT_READ = "report:read";

    private final ReportService reportService;
    private final SecurityContextFacade securityContextFacade;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "报表管理导出";
    }

    @Override
    public String fileName() {
        return "report-export";
    }

    /** 多段结构不使用单表头契约（见类注释），返回空列表。 */
    @Override
    public List<String> headers() {
        return List.of();
    }

    /** 多段结构不使用行契约（见类注释），返回空列表。 */
    @Override
    public List<List<String>> rows(Map<String, String> params) {
        return List.of();
    }

    @Override
    public byte[] csv(Map<String, String> params) {
        requireReportRead();
        ReportQuery query = toQuery(params);
        ReportVO vo = reportService.report(query);
        return ReportCsvWriter.toCsvBytes(ReportCsvWriter.lines(query, vo));
    }

    /** 权限兜底：当前主体须持有 report:read（与 ReportController 类级 @PreAuthorize 同码）。 */
    private void requireReportRead() {
        CurrentPrincipal principal = securityContextFacade.getCurrentPrincipal().orElse(null);
        if (principal == null || !principal.hasPermission(REPORT_READ)) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN,
                    "无报表导出权限（需 " + REPORT_READ + "）");
        }
    }

    /**
     * 查询参数 → {@link ReportQuery}（与 {@code ReportController} 的默认值一致）。
     *
     * <p>非法枚举值 / 布尔值宽容回退默认（导出入口不做强校验，避免拼错参数直接 500）；
     * 时间范围仅支持预设类型，CUSTOM 所需起止日期不经本入口传入。</p>
     */
    private static ReportQuery toQuery(Map<String, String> params) {
        ReportType type = parseEnum(params.get("type"), ReportType.class, ReportType.USAGE);
        ReportRange range = parseEnum(params.get("rangeType"), ReportRange.class, ReportRange.LAST_7D);
        Boolean success = parseBoolean(params.get("success"));
        return new ReportQuery(type, range, null, null,
                blankToNull(params.get("module")), success, blankToNull(params.get("keyword")),
                null, null);
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> enumType, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * 布尔参数白名单映射：true/1 → TRUE，false/0 → FALSE，其余不可解析值 → null（不筛选）。
     *
     * <p>不可解析时不猜（如 "yes" 若猜成 false 会把导出结果误导为「仅失败记录」），按不筛选处理。</p>
     */
    private static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if ("true".equalsIgnoreCase(v) || "1".equals(v)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(v) || "0".equals(v)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
