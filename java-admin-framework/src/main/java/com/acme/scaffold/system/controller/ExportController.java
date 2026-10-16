package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.CsvExporter;
import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.system.export.config.ExportProperties;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 *   <li>支持两种格式：CSV 与 XLSX。CSV 走零依赖 {@code CsvExporter}（UTF-8 BOM + RFC4180 转义）；
 *       XLSX 由同样零依赖的 {@link XlsxDataExporter}（JDK {@code java.util.zip} 手写最小合规 OOXML）生成，
 *       二者均<b>不引入 POI</b>，离线构建友好；</li>
 *   <li>单表结构（{@link ExportTask#headers()} 非空）走「headers + rows」单 Sheet 路径；
 *       多段结构（报表）覆写 {@link ExportTask#xlsxSheets(Map)} 后走多 Sheet 工作簿路径
 *       （汇总 / 分布 / 明细 / 趋势各占一页），两种结构均可一键双格式；</li>
 *   <li>{@code /templates} 列出全部可用模板及其<b>支持的格式</b>，{@code /download} 流式返回，
 *       查询参数全量透传（{@code code} / {@code format} 除外），各模板按需取值。</li>
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
    private final ExportProperties exportProperties;

    @Operation(summary = "导出模板列表（导出中心可触发的全部模板及其支持格式）")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('system:export:read')")
    public Result<List<ExportTemplateView>> templates() {
        List<ExportTemplateView> list = exportTasks.stream()
                .map(t -> new ExportTemplateView(t.code(), t.displayName(), t.fileName(), supportedFormats(t)))
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "按模板导出文件（format=CSV 或 XLSX；XLSX 由纯 Java 零依赖写出）")
    @GetMapping("/download")
    @PreAuthorize("hasAuthority('system:export:read')")
    @AuditOperation(module = "export", type = "READ", name = "导出数据")
    public ResponseEntity<byte[]> download(@RequestParam Map<String, String> allParams) {
        String code = allParams.get("code");
        ExportTask task = exportTasks.stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND, "未知的导出模板: " + code));

        // 兼容旧调用：缺省 format 视为 CSV
        String rawFormat = allParams.get("format");
        ExportFormat format = (rawFormat == null || rawFormat.isBlank())
                ? ExportFormat.CSV
                : ExportFormat.from(rawFormat);
        if (format == null) {
            throw BusinessException.badRequest("不支持的导出格式: " + rawFormat);
        }

        // 查询参数全量透传（code / format 除外）：各模板按需取值（files 取 sourceModule/uploaderName/originalName，
        // report 取 type/rangeType/keyword 等）。空串归一为 null，保持与旧版固定三键一致的行为。
        Map<String, String> params = new LinkedHashMap<>();
        allParams.forEach((k, v) -> {
            if (!"code".equals(k) && !"format".equals(k)) {
                params.put(k, blankToNull(v));
            }
        });

        byte[] body = (format == ExportFormat.CSV) ? toCsv(task, params) : toXlsx(task, params);
        return fileResponse(body, task.fileName(), format);
    }

    /**
     * 生成 CSV 字节。
     *
     * <p>单表结构（{@code headers()} 非空）在此取一次数据行并完成行数上限校验，再自行序列化——
     * 不直接委托 {@link ExportTask#csv(Map)}，否则校验与序列化会各触发一次 {@code rows(params)}
     * 查询（多数模板是查库），平白翻倍。多段结构（报表）由模板自产字节、行数受其自身约束，
     * 仍走 {@link ExportTask#csv(Map)}。历史缺陷：CSV 分支曾完全不做上限校验。</p>
     */
    private byte[] toCsv(ExportTask task, Map<String, String> params) {
        List<String> headers = task.headers();
        if (headers.isEmpty()) {
            return task.csv(params);
        }
        List<List<String>> rows = task.rows(params);
        int maxRows = exportProperties.getMaxRows();
        if (rows.size() > maxRows) {
            throw BusinessException.badRequest(
                    "导出行数(" + rows.size() + ")超出上限(" + maxRows + ")，请缩小筛选范围后分批导出");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers, rows);
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("CSV 生成失败", e);
        }
        return bos.toByteArray();
    }

    /**
     * 生成 XLSX 字节。单表结构（{@code headers()} 非空）走单 Sheet 路径；
     * 多段结构走 {@link ExportTask#xlsxSheets(Map)} 多 Sheet 工作簿路径（未提供该能力则拒绝）。
     * 各 Sheet 数据行总数超 {@code app.export.max-rows} 上限则拒绝，避免单次导出耗尽内存。
     */
    private byte[] toXlsx(ExportTask task, Map<String, String> params) {
        List<String> headers = task.headers();
        if (!headers.isEmpty()) {
            List<List<String>> rows = task.rows(params);
            int maxRows = exportProperties.getMaxRows();
            if (rows.size() > maxRows) {
                throw BusinessException.badRequest(
                        "导出行数(" + rows.size() + ")超出上限(" + maxRows + ")，请缩小筛选范围后分批导出");
            }
            return XlsxDataExporter.export(headers, rows);
        }
        List<XlsxSheet> sheets = task.xlsxSheets(params);
        if (sheets.isEmpty()) {
            throw BusinessException.badRequest(
                    "模板「" + task.code() + "」未提供 XLSX 导出能力，请改用 CSV 导出");
        }
        int totalRows = sheets.stream().mapToInt(s -> s.rows().size()).sum();
        int maxRows = exportProperties.getMaxRows();
        if (totalRows > maxRows) {
            throw BusinessException.badRequest(
                    "导出行数(" + totalRows + ")超出上限(" + maxRows + ")，请缩小筛选范围后分批导出");
        }
        return XlsxDataExporter.exportWorkbook(sheets);
    }

    /** 支持 XLSX 与否由模板声明（单表结构默认支持；多段结构提供 xlsxSheets 后支持）。 */
    private static List<String> supportedFormats(ExportTask task) {
        return task.supportsXlsx() ? List.of("CSV", "XLSX") : List.of("CSV");
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

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 导出模板视图（编码 / 展示名 / 文件名 / 支持格式）。 */
    public record ExportTemplateView(String code, String displayName, String fileName, List<String> formats) {
    }
}
