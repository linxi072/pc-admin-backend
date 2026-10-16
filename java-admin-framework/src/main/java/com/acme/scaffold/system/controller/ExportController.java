package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.ExportTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出中心接口（系统管理 → 导出中心）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>导出模板由 {@link ExportTask} 实现类自动收集，新增导出只需新增一个 Bean，无需改接口；</li>
 *   <li>CSV 生成走零依赖 {@link CsvExporter}（UTF-8 BOM + RFC4180 转义），不引入 POI；
 *      单表结构用 {@link ExportTask#csv(Map)} 默认实现，多段结构（报表）由实现覆写自产；</li>
 *   <li>{@code /templates} 列出全部可用模板，{@code /download} 流式返回 text/csv，
 *       查询参数全量透传（{@code code} 除外），各模板按需取值。</li>
 * </ul>
 *
 * 权限码：system:export:read（由 ApiResourceAutoSyncRunner 启动时按 @PreAuthorize 自动登记并授予 SUPER_ADMIN）。
 * 注意：本端点权限不等于被导出数据自身的权限（如报表导出另需 report:read，由 ReportExportTask 内校验）。
 */
@Tag(name = "导出中心")
@RestController
@RequestMapping("/api/system/export")
@RequiredArgsConstructor
public class ExportController {

    private final List<ExportTask> exportTasks;

    @Operation(summary = "导出模板列表（导出中心可触发的全部模板）")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('system:export:read')")
    public Result<List<ExportTemplateView>> templates() {
        List<ExportTemplateView> list = exportTasks.stream()
                .map(t -> new ExportTemplateView(t.code(), t.displayName(), t.fileName()))
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "按模板导出 CSV（返回 text/csv 二进制流，UTF-8 BOM）")
    @GetMapping("/download")
    @PreAuthorize("hasAuthority('system:export:read')")
    @AuditOperation(module = "export", type = "READ", name = "导出CSV")
    public ResponseEntity<byte[]> download(@RequestParam Map<String, String> allParams) {
        String code = allParams.get("code");
        ExportTask task = exportTasks.stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND, "未知的导出模板: " + code));

        // 查询参数全量透传（code 除外）：各模板按需取值（files 取 sourceModule/uploaderName/originalName，
        // report 取 type/rangeType/keyword 等）。空串归一为 null，保持与旧版固定三键一致的行为。
        Map<String, String> params = new LinkedHashMap<>();
        allParams.forEach((k, v) -> {
            if (!"code".equals(k)) {
                params.put(k, blankToNull(v));
            }
        });

        // 单表结构走默认实现（headers+rows → CsvExporter）；多段结构（如报表）由 task 覆写 csv() 自产
        byte[] csv = task.csv(params);

        String fileName = task.fileName() + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(csv.length)
                .body(csv);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 导出模板视图（编码 / 展示名 / 文件名）。 */
    public record ExportTemplateView(String code, String displayName, String fileName) {
    }
}
