package com.acme.scaffold.cms.article.api;

import com.acme.scaffold.cms.article.dto.ArticleCreateRequest;
import com.acme.scaffold.cms.article.dto.ArticleUpdateRequest;
import com.acme.scaffold.cms.article.dto.ArticleView;
import com.acme.scaffold.cms.article.application.CmsArticleService;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章管理接口。管理类操作统一要求 cms:article:manage；发布/下线另需 cms:article:publish。
 */
@Tag(name = "内容管理-文章")
@RestController
@RequestMapping("/api/cms/article")
@RequiredArgsConstructor
public class CmsArticleController {

    private final CmsArticleService articleService;

    @Operation(summary = "创建文章（默认草稿态）")
    @PostMapping
    @PreAuthorize("hasAuthority('cms:article:manage')")
    @AuditOperation(module = "cms", type = "CREATE", name = "创建文章")
    public Result<Long> create(@Valid @RequestBody ArticleCreateRequest req) {
        return Result.success(articleService.create(req));
    }

    @Operation(summary = "更新文章")
    @PutMapping
    @PreAuthorize("hasAuthority('cms:article:manage')")
    @AuditOperation(module = "cms", type = "UPDATE", name = "更新文章")
    public Result<Void> update(@Valid @RequestBody ArticleUpdateRequest req) {
        articleService.update(req);
        return Result.success();
    }

    @Operation(summary = "删除文章")
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    @AuditOperation(module = "cms", type = "DELETE", name = "删除文章")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "文章详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    public Result<ArticleView> getById(@PathVariable Long id) {
        return Result.success(articleService.getById(id));
    }

    @Operation(summary = "发布文章（DRAFT/OFFLINE → PUBLISHED）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('cms:article:publish')")
    @AuditOperation(module = "cms", type = "UPDATE", name = "发布文章")
    public Result<Void> publish(@PathVariable Long id) {
        articleService.publish(id);
        return Result.success();
    }

    @Operation(summary = "下线文章（PUBLISHED → OFFLINE）")
    @PostMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('cms:article:publish')")
    @AuditOperation(module = "cms", type = "UPDATE", name = "下线文章")
    public Result<Void> offline(@PathVariable Long id) {
        articleService.offline(id);
        return Result.success();
    }

    @Operation(summary = "浏览计数 +1")
    @PostMapping("/{id}/view")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    public Result<Void> view(@PathVariable Long id) {
        articleService.incrementViewCount(id);
        return Result.success();
    }

    @Operation(summary = "文章分页（支持栏目/标题/状态筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    public Result<PageResult<ArticleView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(articleService.page(page, size, categoryId, title, status,
                sortField, sortDirection));
    }
}
