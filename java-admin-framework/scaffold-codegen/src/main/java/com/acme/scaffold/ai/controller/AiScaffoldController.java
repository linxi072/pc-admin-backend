package com.acme.scaffold.ai.controller;

import com.acme.scaffold.ai.dto.ScaffoldRequest;
import com.acme.scaffold.ai.dto.ScaffoldResult;
import com.acme.scaffold.ai.service.AiScaffoldService;
import com.acme.scaffold.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 辅助开发接口：自然语言 → 表结构草案 + 迁移 SQL + 代码草稿（需人工复核）。
 *
 * <p>权限码 {@code ai:scaffold:generate} 由 {@code ApiResourceAutoSyncRunner} 扫描
 * {@code @PreAuthorize} 自动登记并授予 SUPER_ADMIN。生成物为草稿，须人工复核后落地。</p>
 */
@Tag(name = "AI 辅助开发")
@RestController
@RequestMapping("/api/ai/scaffold")
@RequiredArgsConstructor
public class AiScaffoldController {

    private final AiScaffoldService scaffoldService;

    @Operation(summary = "根据自然语言生成脚手架草稿")
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('ai:scaffold:generate')")
    public Result<ScaffoldResult> generate(@RequestBody ScaffoldRequest request) {
        ScaffoldResult result = scaffoldService.generate(request.prompt(), request.moduleName());
        return Result.success(result);
    }
}
