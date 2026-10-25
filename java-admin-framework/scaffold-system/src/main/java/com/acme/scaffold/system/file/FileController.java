package com.acme.scaffold.system.file;

import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理接口（运维管理 → 文件管理）。
 *
 * <p>权限码：
 * <ul>
 *   <li>上传 system:file:upload</li>
 *   <li>下载 system:file:download</li>
 *   <li>列表 system:file:read</li>
 *   <li>删除 system:file:delete</li>
 * </ul>
 * V50 新增秒传预检 / 统计 / 批量删除三个端点，分别复用上述上传/读取/删除权限码，
 * 不新增权限码（SUPER_ADMIN 授权不变）。接口资源由 ApiResourceAutoSyncRunner 在启动时按
 * PreAuthorize 自动登记并授予 SUPER_ADMIN，因此迁移中无需手工写入 sys_api_resource。
 */
@Slf4j
@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/system/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件（可复用公共接口，业务模块可传 sourceModule 归集来源）")
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('system:file:upload')")
    @RateLimit(max = 20, windowSeconds = 60, scope = RateLimit.Scope.IP)
    @AuditOperation(module = "file", type = "CREATE", name = "上传文件")
    public Result<FileView> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam("sourceModule") String sourceModule,
                                   @RequestParam(value = "bizId", required = false) String bizId) {
        return Result.success(fileService.upload(file, sourceModule, bizId));
    }

    @Operation(summary = "文件列表（可按来源模块 / 上传人 / 文件名筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:file:read')")
    public Result<PageResult<FileView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String uploaderName,
            @RequestParam(required = false) String originalName,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(fileService.page(page, size, sourceModule, uploaderName,
                originalName, sortField, sortDirection));
    }

    @Operation(summary = "文件列表（keyset 游标分页，适合深翻页）")
    @GetMapping("/keyset")
    @PreAuthorize("hasAuthority('system:file:read')")
    public Result<KeysetPageResult<FileView>> keyset(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String uploaderName,
            @RequestParam(required = false) String originalName) {
        return Result.success(fileService.keysetPage(size, cursor, sourceModule, uploaderName, originalName));
    }

    @Operation(summary = "下载文件（返回二进制流，原始文件名作为附件名）")
    @GetMapping("/download/{id}")
    @PreAuthorize("hasAuthority('system:file:download')")
    @AuditOperation(module = "file", type = "READ", name = "下载文件")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileDownload fd = fileService.download(id);
        String encoded = URLEncoder.encode(fd.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(fd.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(fd.contentType()))
                .body(fd.resource());
    }

    @Operation(summary = "秒传预检（前端上传前计算内容 SHA-256 回传，命中则提示可秒传）")
    @GetMapping("/dedup-check")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public Result<DedupCheckView> dedupCheck(@RequestParam String hash) {
        return Result.success(fileService.checkDedup(hash));
    }

    @Operation(summary = "文件统计（总量 / 占用空间 / 按来源模块拆分）")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system:file:read')")
    public Result<FileStatsView> stats() {
        return Result.success(fileService.stats());
    }

    @Operation(summary = "批量删除文件（部分成功语义：单项失败不影响其余，返回成功数与失败明细）")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('system:file:delete')")
    @AuditOperation(module = "file", type = "DELETE", name = "批量删除文件")
    public Result<BatchDeleteView> batchDelete(@RequestBody(required = false) List<Long> ids) {
        return Result.success(fileService.batchDelete(ids));
    }

    @Operation(summary = "删除文件（逻辑删除记录；物理文件按引用计数保护，被共享时保留）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:file:delete')")
    @AuditOperation(module = "file", type = "DELETE", name = "删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }
}
