package com.acme.scaffold.workbench.report.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportType;
import com.acme.scaffold.workbench.report.dto.ReportTypeMeta;
import com.acme.scaffold.workbench.report.dto.ReportVO;
import com.acme.scaffold.workbench.report.dto.SortField;
import com.acme.scaffold.workbench.report.dto.SortOrder;
import com.acme.scaffold.workbench.report.dto.ReportCsvVO;
import com.acme.scaffold.workbench.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报表管理接口。
 *
 * <p>基于业务日志聚合（设计方案 §报表管理）：提供使用 / 类别 / 状态三类使用视角，
 * 以及定时任务执行 / 系统登录 / 接口调用三类统计视角；支持按时间范围、类别、状态、关键字筛选与排序，
 * 并可导出 CSV。关键字按报表类型作用于不同字段（任务名 / 用户名 / 请求路径），使用视角忽略。</p>
 *
 * <p><b>权限</b>：须持有 {@code report:read}（由 V19 菜单+接口资源种子、并经 V20 修正授予 SUPER_ADMIN）。
 * 与审计日志一样属于运营分析类敏感数据，故加 {@code @PreAuthorize}；侧边栏菜单亦仅在授权角色下可见。</p>
 */
@Tag(name = "报表管理")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('report:read')")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "报表类型列表（供前端切换 Tab / 维度）")
    @GetMapping("/types")
    public Result<List<ReportTypeMeta>> types() {
        List<ReportTypeMeta> list = Arrays.stream(ReportType.values())
                .map(t -> new ReportTypeMeta(t.getCode(), t.getLabel(), t.getDescription()))
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @Operation(summary = "报表数据：按时间范围 / 类别 / 状态 / 关键字聚合（支持筛选与排序）")
    @GetMapping("/data")
    public Result<ReportVO> data(
            @RequestParam(defaultValue = "USAGE") ReportType type,
            @RequestParam(defaultValue = "LAST_7D") ReportRange rangeType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "COUNT") SortField sortBy,
            @RequestParam(required = false, defaultValue = "DESC") SortOrder order) {
        ReportQuery query = new ReportQuery(type, rangeType, start, end, module, success, keyword, sortBy, order);
        return Result.success(reportService.report(query));
    }

    @Operation(summary = "报表数据导出（CSV，UTF-8 BOM，可直接用 Excel 打开）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "USAGE") ReportType type,
            @RequestParam(defaultValue = "LAST_7D") ReportRange rangeType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "COUNT") SortField sortBy,
            @RequestParam(required = false, defaultValue = "DESC") SortOrder order) {
        ReportQuery query = new ReportQuery(type, rangeType, start, end, module, success, keyword, sortBy, order);
        ReportCsvVO export = reportService.exportCsv(query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(export.content());
    }
}
