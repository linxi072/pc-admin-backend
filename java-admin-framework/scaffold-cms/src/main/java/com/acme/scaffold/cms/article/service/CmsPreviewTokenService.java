package com.acme.scaffold.cms.article.service;

import com.acme.scaffold.cms.article.domain.ArticleView;
import com.acme.scaffold.cms.article.domain.PreviewTokenView;
import com.acme.scaffold.cms.article.domain.model.CmsArticle;
import com.acme.scaffold.cms.article.domain.model.CmsPreviewToken;
import com.acme.scaffold.cms.article.domain.rule.CmsPreviewTokenRules;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 草稿预览令牌（C6）：为任意态文章生成有时效的只读令牌，供公开端点免登录查看。
 *
 * <p>令牌为无连字符 UUID，默认 24h 过期；校验经参数化 DSL 查询，过期/不存在统一拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class CmsPreviewTokenService {

    private static final SqlTables.TableRef TOKEN = SqlTables.CMS_PREVIEW_TOKEN;
    private static final SqlTables.TableRef ARTICLE = SqlTables.CMS_ARTICLE;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /** 生成令牌（文章须存在）。 */
    @Transactional
    public PreviewTokenView generate(Long articleId, Integer ttlHours) {
        CmsArticle article = SqlWriters.fetchById(dsl, ARTICLE, CmsArticle.class, articleId);
        if (article == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + articleId);
        }
        LocalDateTime now = LocalDateTime.now();
        CmsPreviewToken token = new CmsPreviewToken();
        token.setArticleId(articleId);
        token.setToken(CmsPreviewTokenRules.generateToken());
        token.setExpiresAt(CmsPreviewTokenRules.expiryAfter(now, ttlHours));
        token.setCreatedBy(currentOperator());
        SqlWriters.insert(dsl, TOKEN, token);
        return new PreviewTokenView(token.getToken(), articleId, token.getExpiresAt());
    }

    /** 校验令牌并返回文章视图（过期/非法/文章不存在统一抛 404，避免泄露令牌有效性）。 */
    @Transactional(readOnly = true)
    public ArticleView resolve(String tokenValue) {
        if (!CmsPreviewTokenRules.validFormat(tokenValue)) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "预览令牌无效或已过期");
        }
        CmsPreviewToken token = SqlWriters.fetchOne(dsl, TOKEN, CmsPreviewToken.class,
                DSL.and(SqlWriters.notDeleted(TOKEN),
                        TOKEN.field("token", String.class).eq(tokenValue)));
        if (token == null || CmsPreviewTokenRules.isExpired(token.getExpiresAt(), LocalDateTime.now())) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "预览令牌无效或已过期");
        }
        CmsArticle article = SqlWriters.fetchById(dsl, ARTICLE, CmsArticle.class, token.getArticleId());
        if (article == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "预览令牌无效或已过期");
        }
        ArticleView view = new ArticleView(article.getId(), article.getCategoryId(), article.getTitle(),
                article.getSummary(), article.getAuthor(), article.getCoverUrl(), article.getStatus(),
                article.getViewCount(), article.getPublishedAt(), article.getCreatedAt(), article.getUpdatedAt());
        view.setOfflineAt(article.getOfflineAt());
        view.setScheduledPublishAt(article.getScheduledPublishAt());
        return view;
    }

    private String currentOperator() {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> {
                    CurrentPrincipal cp = p;
                    return cp.displayName() != null ? cp.displayName() : cp.username();
                })
                .orElse("system");
    }
}
