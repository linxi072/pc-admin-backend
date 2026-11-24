package com.acme.scaffold.cms.article.service;

import com.acme.scaffold.cms.article.domain.PublicArticleView;
import com.acme.scaffold.cms.article.domain.model.CmsArticle;
import com.acme.scaffold.cms.article.domain.rule.CmsArticleRules;
import com.acme.scaffold.cms.article.domain.rule.CmsPublicRules;
import com.acme.scaffold.cms.article.domain.rule.CmsStatusRules;
import com.acme.scaffold.common.api.domain.PageQuery;
import com.acme.scaffold.common.api.domain.PageResult;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公开内容查询（C7）：仅返回 PUBLISHED 文章，强分页（size≤50）与字段脱敏。
 *
 * <p>与文章管理端共用 {@link SqlTables#CMS_ARTICLE}，但条件固定追加 status=PUBLISHED，
 * 视图经 {@link #toPublic} 白名单映射，杜绝内部字段泄露。</p>
 */
@Service
@RequiredArgsConstructor
public class CmsPublicService {

    private static final SqlTables.TableRef ARTICLE = SqlTables.CMS_ARTICLE;

    /** 公开列表可排序字段白名单（对外字段子集）。 */
    private static final Map<String, String> PUBLIC_SORT_FIELDS = SqlSorts.whitelist(
            "id", "id", "publishedAt", "published_at");

    private final DSLContext dsl;

    @Transactional(readOnly = true)
    public PageResult<PublicArticleView> page(int page, int size, Long categoryId, String keyword) {
        int p = CmsPublicRules.clampPage(page);
        int s = CmsPublicRules.clampSize(size);
        List<Condition> conds = new ArrayList<>();
        conds.add(SqlWriters.notDeleted(ARTICLE));
        conds.add(ARTICLE.field("status", String.class).eq(CmsStatusRules.PUBLISHED));
        if (CmsArticleRules.validCategoryId(categoryId)) {
            conds.add(ARTICLE.field("category_id", Long.class).eq(categoryId));
        }
        String pattern = CmsArticleRules.likePattern(keyword);
        if (pattern != null) {
            conds.add(ARTICLE.field("title", String.class).like(pattern));
        }
        PageQuery q = PageQuery.of(p, s);
        var raw = SqlWriters.page(dsl, ARTICLE, CmsArticle.class, DSL.and(conds), q,
                SqlSorts.resolve(ARTICLE, q, PUBLIC_SORT_FIELDS,
                        ARTICLE.field("published_at", java.time.LocalDateTime.class).desc(),
                        ARTICLE.field("id", Long.class).desc()));
        List<PublicArticleView> views = raw.records().stream().map(this::toPublic).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    private PublicArticleView toPublic(CmsArticle e) {
        return new PublicArticleView(e.getId(), e.getCategoryId(), e.getTitle(), e.getSummary(),
                e.getAuthor(), e.getCoverUrl(), e.getPublishedAt());
    }
}
