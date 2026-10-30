package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.CsvExporter;
import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.common.importer.FailedRow;
import com.acme.scaffold.common.importer.ImportFormat;
import com.acme.scaffold.common.importer.ImportOutcome;
import com.acme.scaffold.common.importer.ImportResult;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.MultiSegmentImportTask;
import com.acme.scaffold.common.importer.SegmentSpec;
import com.acme.scaffold.security.ratelimit.RateLimit;
import com.acme.scaffold.system.service.ImportCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入中心接口（系统管理 → 导入中心），与 {@code ExportController} 对称。
 *
 * <p>设计要点：
 * <ul>
 *   <li>导入模板由 {@link ImportTask} 实现类自动收集，新增导入只需新增一个 Bean，无需改接口；</li>
 *   <li>支持两种格式：CSV 与 XLSX。解析均为零依赖实现（{@code CsvDataReader} / {@code XlsxDataReader}，
 *       后者用 JDK {@code java.util.zip} + StAX 解析 OOXML），<b>不引入 POI</b>，离线构建友好；</li>
 *   <li>{@code /template} 下载「仅表头」的空白模板（默认 XLSX，便于用户直接在 Excel 中填写），
 *       与 {@code /} 上传接口共用同一表头定义，避免模板与校验规则漂移；</li>
 *   <li>{@code dryRun=true} 为预检：完成表头校验与逐行业务校验但不落库，便于用户先确认再导入；</li>
 *   <li>{@code errorReport=true} 时失败行随响应生成 CSV 报告（含原始行值，防公式注入），
 *       与本次导入同请求完成，不产生二次执行。</li>
 * </ul>
 *
 * <p>权限码：{@code system:import:read}（模板列表 / 下载模板）、{@code system:import:write}（上传导入）。
 * 由 ApiResourceAutoSyncRunner 启动时按 @PreAuthorize 自动登记并授予 SUPER_ADMIN。
 * 注意：本端点权限不等于被导入数据自身的权限（如字典数据另需 {@code system:dict:create} 语义，
 * 由 {@code DictDataImportTask} 调用既有服务隐式承载）。
 */
@Tag(name = "导入中心")
@RestController
@RequestMapping("/api/system/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportCenterService importCenterService;

    @Operation(summary = "导入模板列表（导入中心可触发的全部模板及其表头/多段定义）")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('system:import:read')")
    public Result<List<ImportTemplateView>> templates() {
        List<ImportTemplateView> list = importCenterService.tasks().stream()
                .map(t -> {
                    if (t instanceof MultiSegmentImportTask ms) {
                        // 多段模板：暴露各段名与段内表头；headers 取首段表头以兼容旧前端
                        List<SegmentTemplateView> segs = ms.segments().stream()
                                .map(s -> new SegmentTemplateView(s.name(), s.headers()))
                                .toList();
                        List<String> primary = ms.segments().isEmpty()
                                ? List.of() : ms.segments().get(0).headers();
                        return new ImportTemplateView(t.code(), t.displayName(), primary, true, segs);
                    }
                    return new ImportTemplateView(t.code(), t.displayName(), t.headers(), false, List.of());
                })
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "下载导入模板（仅表头；多段模板仅支持 XLSX 多工作表，单段模板支持 CSV / XLSX，默认 XLSX）")
    @GetMapping("/template")
    @PreAuthorize("hasAuthority('system:import:read')")
    @AuditOperation(module = "import", type = "READ", name = "下载导入模板")
    public ResponseEntity<byte[]> template(@RequestParam("code") String code,
                                           @RequestParam(value = "format", required = false) String rawFormat) {
        ImportTask task = importCenterService.task(code);
        ImportFormat format = (rawFormat == null || rawFormat.isBlank())
                ? ImportFormat.XLSX
                : ImportFormat.from(rawFormat);
        if (format == null) {
            throw BusinessException.badRequest("不支持的模板格式: " + rawFormat);
        }
        // 多段模板：仅 XLSX 多工作表可承载多段；CSV 无法承载，直接拒绝
        if (task instanceof MultiSegmentImportTask ms) {
            if (format != ImportFormat.XLSX) {
                throw BusinessException.badRequest("多段导入模板仅支持 XLSX 格式下载");
            }
            List<XlsxSheet> sheets = ms.segments().stream()
                    .map(s -> new XlsxSheet(s.name(), s.headers(), List.of()))
                    .toList();
            byte[] body = XlsxDataExporter.exportWorkbook(sheets);
            return fileResponse(body, "导入模板-" + task.displayName(), ImportFormat.XLSX);
        }
        List<String> headers = task.headers();
        byte[] body = (format == ImportFormat.CSV)
                ? csvBytes(headers)
                : XlsxDataExporter.export(headers, List.of());
        return fileResponse(body, "导入模板-" + task.displayName(), format);
    }

    /**
     * 上传并导入数据（dryRun=true 仅预检不落库）。
     *
     * <p>{@code errorReport=true} 时：若存在失败行，响应改为 <b>CSV 失败报告附件</b>
     * （列：数据行号 / 失败原因 / 模板各列原始值；UTF-8 BOM，Excel 可直接打开），
     * 用户可离线修复后重传；无失败行时仍返回 JSON 统计。同一请求内完成，<b>不会二次执行导入</b>。</p>
     */
    @Operation(summary = "上传并导入数据（dryRun=true 仅预检不落库；errorReport=true 失败时返回 CSV 报告附件）")
    @PostMapping("/{code}")
    @PreAuthorize("hasAuthority('system:import:write')")
    @RateLimit(max = 10, windowSeconds = 60, scope = RateLimit.Scope.IP)
    // 不记录入参：MultipartFile 无法安全序列化进审计摘要（其余导入上下文由业务模块自身审计覆盖）
    @AuditOperation(module = "import", type = "CREATE", name = "导入数据", recordParams = false)
    public ResponseEntity<?> upload(@PathVariable("code") String code,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "dryRun", required = false, defaultValue = "false")
                                    boolean dryRun,
                                    @RequestParam(value = "errorReport", required = false, defaultValue = "false")
                                    boolean errorReport) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.badRequest("读取上传文件失败");
        }
        if (errorReport) {
            ImportOutcome outcome = importCenterService.importDetailed(code, file.getOriginalFilename(), content, dryRun);
            if (outcome.result().failureCount() > 0) {
                return errorReportResponse(importCenterService.task(code), outcome);
            }
            return jsonResult(outcome.result());
        }
        return jsonResult(importCenterService.importData(code, file.getOriginalFilename(), content, dryRun));
    }

    /** 生成「导入失败报告」CSV（数据段 + 行号 + 原因 + 模板各列原始值；防公式注入）。 */
    private static ResponseEntity<byte[]> errorReportResponse(ImportTask task, ImportOutcome outcome) {
        boolean multi = task instanceof MultiSegmentImportTask;
        List<String> headers = new ArrayList<>();
        if (multi) {
            headers.add("数据段");
        }
        headers.add("数据行号");
        headers.add("失败原因");
        // 值列：单段=模板表头；多段=各段表头按段序拼接（去重保序），使报告为统一宽度的表
        final List<String> valueColumns;
        if (multi) {
            List<String> union = new ArrayList<>();
            MultiSegmentImportTask ms = (MultiSegmentImportTask) task;
            for (SegmentSpec s : ms.segments()) {
                for (String h : s.headers()) {
                    if (!union.contains(h)) {
                        union.add(h);
                    }
                }
            }
            valueColumns = union;
        } else {
            valueColumns = task.headers();
        }
        headers.addAll(valueColumns);

        List<List<String>> rows = outcome.failedRows().stream()
                .map(f -> reportRow(task, f, valueColumns))
                .toList();
        return fileResponse(csvBytes(headers, rows), "导入失败报告-" + task.displayName(), ImportFormat.CSV);
    }

    private static List<String> reportRow(ImportTask task, FailedRow failed, List<String> valueColumns) {
        boolean multi = task instanceof MultiSegmentImportTask;
        List<String> cells = new ArrayList<>();
        if (multi) {
            cells.add(sanitizeFormula(failed.segment()));
        }
        cells.add(String.valueOf(failed.rowNo()));
        cells.add(sanitizeFormula(failed.message()));
        for (String header : valueColumns) {
            cells.add(sanitizeFormula(failed.values().getOrDefault(header, "")));
        }
        return cells;
    }

    /**
     * 防 CSV 公式注入（OWASP 建议）：以 {@code = + - @}、Tab 或 CR 开头的单元格
     * 前缀单引号使其按文本展示。报告仅供人工核对修复，前缀不影响数据语义。
     */
    private static String sanitizeFormula(String cell) {
        if (cell == null || cell.isEmpty()) {
            return cell;
        }
        char first = cell.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + cell;
        }
        return cell;
    }

    private static ResponseEntity<Object> jsonResult(ImportResult result) {
        return ResponseEntity.ok(Result.success(result));
    }

    /** 生成「仅表头」的 CSV 模板（UTF-8 BOM，Excel 可直接打开）。 */
    private static byte[] csvBytes(List<String> headers) {
        return csvBytes(headers, List.of());
    }

    private static byte[] csvBytes(List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers, rows);
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("CSV 生成失败", e);
        }
        return bos.toByteArray();
    }

    private static ResponseEntity<byte[]> fileResponse(byte[] body, String fileName, ImportFormat format) {
        String fullName = fileName + "." + format.extension();
        String encoded = URLEncoder.encode(fullName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(format.contentType()))
                .contentLength(body.length)
                .body(body);
    }

    /** 单段视图（段名 / 段内表头），多段模板下随 {@link ImportTemplateView#segments()} 返回。 */
    public record SegmentTemplateView(String name, List<String> headers) {
    }

    /** 导入模板视图（编码 / 展示名 / 首段表头 / 是否多段 / 各段定义）。 */
    public record ImportTemplateView(String code, String displayName, List<String> headers,
                                     boolean multiSegment, List<SegmentTemplateView> segments) {
        public ImportTemplateView {
            headers = headers == null ? List.of() : List.copyOf(headers);
            segments = segments == null ? List.of() : List.copyOf(segments);
        }
    }
}
