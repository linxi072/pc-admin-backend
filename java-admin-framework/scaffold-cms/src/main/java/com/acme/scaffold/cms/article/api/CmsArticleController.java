package com.acme.scaffold.cms.article.api;

import com.acme.scaffold.cms.article.service.CmsArticleService;
import com.acme.scaffold.cms.article.service.CmsArticleVersionService;
import com.acme.scaffold.cms.article.service.CmsPreviewTokenService;
import com.acme.scaffold.cms.article.domain.ArticleCreateRequest;
import com.acme.scaffold.cms.article.domain.ArticleUpdateRequest;
import com.acme.scaffold.cms.article.domain.ArticleVersionView;
import com.acme.scaffold.cms.article.domain.ArticleView;
import com.acme.scaffold.cms.article.domain.BatchMoveArticleRequest;
import com.acme.scaffold.cms.article.domain.BatchMoveArticleView;
import com.acme.scaffold.cms.article.domain.PreviewTokenView;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.domain.PageResult;
import java.util.List;
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
    private final CmsArticleVersionService versionService;
    private final CmsPreviewTokenService previewTokenService;

    @Operation(summary = "创建文章（默认草稿态）")
    @PostMapping
    @PreAuthorize("hasAuthority('cms:article:manage')")
    @AuditOperation(module = "cms", type = "CREATE", name = "创建文章")
    public Result<Long> create(@Valid @RequestBody ArticleCreateRequest req) {
        return Result.success(articleService.create(req));
    }

    @Operation(summary = "批量移动文章到目标栏目（单事务，最多 200 篇）")
    @PostMapping("/batch-move")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    @AuditOperation(module = "cms", type = "UPDATE", name = "批量移动文章")
    public Result<BatchMoveArticleView> batchMove(@Valid @RequestBody BatchMoveArticleRequest req) {
        return Result.success(articleService.batchMove(req));
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

    @Operation(summary = "文章版本列表（C4）")
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    public Result<List<ArticleVersionView>> versions(@PathVariable Long id) {
        return Result.success(versionService.listVersions(id));
    }

    @Operation(summary = "回滚文章到指定版本（C4）")
    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    @AuditOperation(module = "cms", type = "UPDATE", name = "回滚文章版本")
    public Result<Void> rollback(@PathVariable Long id, @RequestParam int versionNo) {
        versionService.rollback(id, versionNo);
        return Result.success();
    }

    @Operation(summary = "生成草稿预览令牌（C6）")
    @PostMapping("/{id}/preview-token")
    @PreAuthorize("hasAuthority('cms:article:manage')")
    public Result<PreviewTokenView> previewToken(@PathVariable Long id,
                                                 @RequestParam(required = false) Integer ttlHours) {
        return Result.success(previewTokenService.generate(id, ttlHours));
    }
}
