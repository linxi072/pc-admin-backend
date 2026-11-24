package com.acme.scaffold.cms.article.api;

import com.acme.scaffold.cms.article.domain.ArticleView;
import com.acme.scaffold.cms.article.service.CmsPreviewTokenService;
import com.acme.scaffold.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 草稿预览公开接口（C6）：凭令牌免登录只读查看文章，feature-gate 默认关闭。
 *
 * <p>启用方式：{@code cms.preview.enabled=true}。令牌无效/过期统一 404，不泄露有效性。</p>
 */
@Tag(name = "内容管理-预览（公开）")
@RestController
@RequestMapping("/api/cms/preview")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cms.preview", name = "enabled", havingValue = "true")
public class CmsPreviewController {

    private final CmsPreviewTokenService previewTokenService;

    @Operation(summary = "凭令牌预览文章（C6，公开）")
    @GetMapping("/{token}")
    public Result<ArticleView> preview(@PathVariable String token) {
        return Result.success(previewTokenService.resolve(token));
    }
}
