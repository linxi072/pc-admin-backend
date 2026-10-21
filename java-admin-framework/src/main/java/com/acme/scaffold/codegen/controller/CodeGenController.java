package com.acme.scaffold.codegen.controller;

import com.acme.scaffold.codegen.dto.GenOption;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;
import com.acme.scaffold.codegen.service.CodeGenService;
import com.acme.scaffold.codegen.service.TableSchemaService;
import com.acme.scaffold.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 代码生成器接口：表结构读取、生成预览、zip 打包下载。
 *
 * <p><b>权限</b>：{@code codegen:read}（表清单/字段/预览）、{@code codegen:download}（打包下载）。
 * 权限码由 V22 迁移登记到 {@code sys_api_resource} 并授予 SUPER_ADMIN，同时登记
 * {@code sys_menu} 菜单（route=/dev/codegen）驱动侧边栏可见性。</p>
 *
 * <p><b>安全边界</b>：生成结果仅以文本/zip 返回，服务端不落盘；SQL 参数经 jOOQ 绑定，
 * 表名仅用于查询不参与拼表。</p>
 */
@Tag(name = "代码生成")
@RestController
@RequestMapping("/api/codegen")
@RequiredArgsConstructor
public class CodeGenController {

    private final TableSchemaService tableSchemaService;
    private final CodeGenService codeGenService;

    @Operation(summary = "库内业务表清单")
    @GetMapping("/tables")
    @PreAuthorize("hasAuthority('codegen:read')")
    public Result<List<String>> tables() {
        return Result.success(tableSchemaService.listTables());
    }

    @Operation(summary = "表字段元信息（含 Java 类型映射）")
    @GetMapping("/tables/{tableName}")
    @PreAuthorize("hasAuthority('codegen:read')")
    public Result<TableMetaVO> tableMeta(@PathVariable String tableName) {
        TableMetaVO meta = tableSchemaService.tableMeta(tableName);
        if (meta == null) {
            throw new com.acme.scaffold.common.exception.BusinessException(
                    com.acme.scaffold.common.error.CommonErrorCode.NOT_FOUND, "表不存在: " + tableName);
        }
        return Result.success(meta);
    }

    @Operation(summary = "生成预览（后端六层 + 可选前端两文件）")
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('codegen:read')")
    public Result<List<GeneratedFileVO>> preview(@RequestBody GenOption option) {
        return Result.success(codeGenService.preview(option));
    }

    @Operation(summary = "打包下载（zip，entry 为建议落盘相对路径）")
    @PostMapping("/download")
    @PreAuthorize("hasAuthority('codegen:download')")
    public ResponseEntity<byte[]> download(@RequestBody GenOption option) {
        byte[] zip = codeGenService.downloadZip(option);
        String filename = URLEncoder.encode(codeGenService.zipName(option), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zip.length)
                .body(zip);
    }
}
