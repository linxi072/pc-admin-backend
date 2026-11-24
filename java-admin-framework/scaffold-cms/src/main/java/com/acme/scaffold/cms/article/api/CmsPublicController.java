package com.acme.scaffold.cms.article.api;

import com.acme.scaffold.cms.article.domain.PublicArticleView;
import com.acme.scaffold.cms.article.service.CmsPublicService;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.domain.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开内容查询（C7）：为官网/门户提供只读文章 API，仅 PUBLISHED、强分页、字段脱敏。
 *
 * <p>feature-gate {@code cms.public.enabled} 默认关闭；开启后由 SecurityConfig 放行匿名访问。
 * 限流复用网关规则（{@code gateway.enabled}）。</p>
 */
@Tag(name = "内容管理-公开查询")
@RestController
@RequestMapping("/api/cms/public")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cms.public", name = "enabled", havingValue = "true")
public class CmsPublicController {

    private final CmsPublicService publicService;

    @Operation(summary = "公开文章分页（仅 PUBLISHED，size≤50，C7）")
    @GetMapping("/articles")
    public Result<PageResult<PublicArticleView>> articles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.success(publicService.page(page, size, categoryId, keyword));
    }
}
