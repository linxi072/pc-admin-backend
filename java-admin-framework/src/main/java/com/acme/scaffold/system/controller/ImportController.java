package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.CsvExporter;
import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.importer.ImportFormat;
import com.acme.scaffold.common.importer.ImportResult;
import com.acme.scaffold.common.importer.ImportTask;
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
 *   <li>{@code dryRun=true} 为预检：完成表头校验与逐行业务校验但不落库，便于用户先确认再导入。</li>
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

    @Operation(summary = "导入模板列表（导入中心可触发的全部模板及其表头）")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('system:import:read')")
    public Result<List<ImportTemplateView>> templates() {
        List<ImportTemplateView> list = importCenterService.tasks().stream()
                .map(t -> new ImportTemplateView(t.code(), t.displayName(), t.headers()))
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "下载导入模板（仅表头，format=CSV 或 XLSX，默认 XLSX）")
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
        List<String> headers = task.headers();
        byte[] body = (format == ImportFormat.CSV)
                ? csvBytes(headers)
                : XlsxDataExporter.export(headers, List.of());
        return fileResponse(body, "导入模板-" + task.displayName(), format);
    }

    @Operation(summary = "上传并导入数据（dryRun=true 仅预检不落库）")
    @PostMapping("/{code}")
    @PreAuthorize("hasAuthority('system:import:write')")
    @RateLimit(max = 10, windowSeconds = 60, scope = RateLimit.Scope.IP)
    // 不记录入参：MultipartFile 无法安全序列化进审计摘要（其余导入上下文由业务模块自身审计覆盖）
    @AuditOperation(module = "import", type = "CREATE", name = "导入数据", recordParams = false)
    public Result<ImportResult> upload(@PathVariable("code") String code,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "dryRun", required = false, defaultValue = "false")
                                       boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.badRequest("读取上传文件失败");
        }
        return Result.success(importCenterService.importData(code, file.getOriginalFilename(), content, dryRun));
    }

    /** 生成「仅表头」的 CSV 模板（UTF-8 BOM，Excel 可直接打开）。 */
    private static byte[] csvBytes(List<String> headers) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers, List.of());
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("模板生成失败", e);
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

    /** 导入模板视图（编码 / 展示名 / 表头）。 */
    public record ImportTemplateView(String code, String displayName, List<String> headers) {
    }
}
