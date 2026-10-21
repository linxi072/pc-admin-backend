package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.system.dto.ExportTemplateView;
import com.acme.scaffold.system.export.service.ExportCenterService;
import com.acme.scaffold.system.export.service.ExportCenterService.ExportedFile;
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
import java.util.List;
import java.util.Map;

/**
 * 导出中心接口（系统管理 → 导出中心）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>导出模板由 {@link com.acme.scaffold.common.export.ExportTask} 实现类自动收集，新增导出只需新增一个 Bean；</li>
 *   <li>编排逻辑（格式解析 / 行数上限 / CSV·XLSX 分流 / 字节生成）已下沉至 {@link ExportCenterService}，
 *       本控制器仅负责参数接收与响应包装（薄控制器）；</li>
 *   <li>支持 CSV 与 XLSX 双格式（均零依赖手写，免 POI）；{@code /templates} 列出模板与格式，
 *       {@code /download} 流式返回，查询参数全量透传。</li>
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

    private final ExportCenterService exportCenterService;

    @Operation(summary = "导出模板列表（导出中心可触发的全部模板及其支持格式）")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('system:export:read')")
    public Result<List<ExportTemplateView>> templates() {
        return Result.success(exportCenterService.listTemplates());
    }

    @Operation(summary = "按模板导出文件（format=CSV 或 XLSX；XLSX 由纯 Java 零依赖写出）")
    @GetMapping("/download")
    @PreAuthorize("hasAuthority('system:export:read')")
    @AuditOperation(module = "export", type = "READ", name = "导出数据")
    public ResponseEntity<byte[]> download(@RequestParam Map<String, String> allParams) {
        ExportedFile file = exportCenterService.download(allParams);
        return fileResponse(file.body(), file.fileName(), file.format());
    }

    private static ResponseEntity<byte[]> fileResponse(byte[] body, String fileName, ExportFormat format) {
        String fullName = fileName + "." + format.extension();
        String encoded = URLEncoder.encode(fullName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(format.contentType()))
                .contentLength(body.length)
                .body(body);
    }
}
