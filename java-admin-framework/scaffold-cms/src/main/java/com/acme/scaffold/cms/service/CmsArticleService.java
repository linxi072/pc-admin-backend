package com.acme.scaffold.cms.service;

import com.acme.scaffold.cms.domain.CmsArticleDO;
import com.acme.scaffold.cms.dto.ArticleCreateRequest;
import com.acme.scaffold.cms.dto.ArticleUpdateRequest;
import com.acme.scaffold.cms.dto.ArticleView;
import com.acme.scaffold.cms.event.ArticlePublishedEvent;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文章服务：文章 CRUD、发布/下线态流转、浏览计数，并在发布时经 R2 事件总线广播
 * {@link ArticlePublishedEvent}（解耦通知/检索等下游）。
 *
 * <p>持久化经 {@link JooqWriters}（动态 DSL），租户隔离由框架统一裁决；
 * 富文本正文以 TEXT 承载，由前端约定 HTML/Markdown 格式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsArticleService {

    /** 列表可排序字段白名单：客户端字段名 → 数据库列名。 */
    private static final Map<String, String> ARTICLE_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "title", "title",
            "viewCount", "view_count",
            "publishedAt", "published_at",
            "createdAt", "created_at");

    private static final JooqTables.TableRef ARTICLE = JooqTables.CMS_ARTICLE;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final EventPublisher eventPublisher;

    /** 创建文章；默认进入 DRAFT 草稿态，所属栏目须存在。 */
    @Transactional
    public Long create(ArticleCreateRequest req) {
        requireCategory(req.getCategoryId());
        CmsArticleDO entity = new CmsArticleDO();
        entity.setCategoryId(req.getCategoryId());
        entity.setTitle(req.getTitle());
        entity.setSummary(req.getSummary());
        entity.setContent(req.getContent());
        entity.setAuthor(req.getAuthor() != null ? req.getAuthor() : currentOperator());
        entity.setCoverUrl(req.getCoverUrl());
        entity.setStatus(req.getStatus() == null ? "DRAFT" : req.getStatus());
        entity.setViewCount(0L);
        entity.setCreatedBy(currentOperator());
        entity.setUpdatedBy(currentOperator());
        JooqWriters.insert(dsl, ARTICLE, entity);
        return entity.getId();
    }

    /** 更新文章（按 id 选择性更新）。若栏目变更须存在；若置为 PUBLISHED 且未记录发布时间则补登。 */
    @Transactional
    public void update(ArticleUpdateRequest req) {
        CmsArticleDO existing = requireExisting(req.getId());
        requireCategory(req.getCategoryId());
        existing.setCategoryId(req.getCategoryId());
        existing.setTitle(req.getTitle());
        existing.setSummary(req.getSummary());
        existing.setContent(req.getContent());
        existing.setAuthor(req.getAuthor());
        existing.setCoverUrl(req.getCoverUrl());
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
            if ("PUBLISHED".equals(req.getStatus()) && existing.getPublishedAt() == null) {
                existing.setPublishedAt(LocalDateTime.now());
            }
        }
        existing.setUpdatedBy(currentOperator());
        JooqWriters.updateById(dsl, ARTICLE, existing.getId(), existing);
    }

    /**
     * 发布文章：DRAFT/OFFLINE → PUBLISHED，记录发布时间、清除下线时间，
     * 并经 R2 事件总线广播 {@link ArticlePublishedEvent}（订阅方错误隔离，无订阅者时安全忽略）。
     */
    @Transactional
    public void publish(Long id) {
        CmsArticleDO existing = requireExisting(id);
        existing.setStatus("PUBLISHED");
        existing.setPublishedAt(LocalDateTime.now());
        existing.setOfflineAt(null);
        existing.setUpdatedBy(currentOperator());
        JooqWriters.updateById(dsl, ARTICLE, existing.getId(), existing);
        eventPublisher.publish(new ArticlePublishedEvent(
                existing.getTenantId(), "cms", existing.getId(), existing.getTitle(), existing.getCategoryId()));
        log.info("文章已发布 articleId={} title={}", existing.getId(), existing.getTitle());
    }

    /** 下线文章：PUBLISHED → OFFLINE，记录下线时间。 */
    @Transactional
    public void offline(Long id) {
        CmsArticleDO existing = requireExisting(id);
        existing.setStatus("OFFLINE");
        existing.setOfflineAt(LocalDateTime.now());
        existing.setUpdatedBy(currentOperator());
        JooqWriters.updateById(dsl, ARTICLE, existing.getId(), existing);
    }

    /** 浏览计数 +1（乐观自增，失败仅告警不阻塞）。 */
    @Transactional
    public void incrementViewCount(Long id) {
        Field<Long> vc = ARTICLE.field("view_count", Long.class);
        try {
            dsl.update(ARTICLE.table())
                    .set(vc, vc.plus(1))
                    .where(ARTICLE.field("id", Long.class).eq(id))
                    .execute();
        } catch (RuntimeException e) {
            log.warn("浏览计数更新失败 articleId={}", id, e);
        }
    }

    /** 按 id 查询（含逻辑删除过滤），不存在抛 404。 */
    @Transactional(readOnly = true)
    public ArticleView getById(Long id) {
        CmsArticleDO entity = JooqWriters.fetchById(dsl, ARTICLE, CmsArticleDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + id);
        }
        return toView(entity);
    }

    /** 删除文章（逻辑删除）。 */
    @Transactional
    public void delete(Long id) {
        requireExisting(id);
        JooqWriters.delete(dsl, ARTICLE, id, true);
    }

    /** 分页查询（支持栏目 / 标题关键字 / 状态筛选，逻辑删除自动排除）。 */
    @Transactional(readOnly = true)
    public PageResult<ArticleView> page(int page, int size, Long categoryId, String title,
                                        String status, String sortField, String sortDirection) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(ARTICLE));
        if (categoryId != null) {
            conds.add(ARTICLE.field("category_id", Long.class).eq(categoryId));
        }
        if (title != null && !title.isBlank()) {
            conds.add(ARTICLE.field("title", String.class).like("%" + title.trim() + "%"));
        }
        if (status != null && !status.isBlank()) {
            conds.add(ARTICLE.field("status", String.class).eq(status.trim()));
        }
        Condition cond = DSL.and(conds);
        PageQuery q = PageQuery.of(page, size, sortField, sortDirection);
        var raw = JooqWriters.page(dsl, ARTICLE, CmsArticleDO.class, cond, q,
                JooqSorts.resolve(ARTICLE, q, ARTICLE_SORT_FIELDS,
                        ARTICLE.field("published_at", LocalDateTime.class).desc(),
                        ARTICLE.field("id", Long.class).desc()));
        List<ArticleView> views = raw.records().stream().map(this::toView).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    // ---------------- 内部辅助 ----------------

    private CmsArticleDO requireExisting(Long id) {
        CmsArticleDO entity = JooqWriters.fetchById(dsl, ARTICLE, CmsArticleDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + id);
        }
        return entity;
    }

    private void requireCategory(Long categoryId) {
        if (categoryId == null || categoryId == 0L) {
            throw BusinessException.badRequest("所属栏目不能为空");
        }
        if (JooqWriters.fetchById(dsl, JooqTables.CMS_CATEGORY, com.acme.scaffold.cms.domain.CmsCategoryDO.class,
                categoryId) == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "所属栏目不存在: " + categoryId);
        }
    }

    private ArticleView toView(CmsArticleDO e) {
        ArticleView v = new ArticleView(e.getId(), e.getCategoryId(), e.getTitle(), e.getSummary(), e.getAuthor(),
                e.getCoverUrl(), e.getStatus(), e.getViewCount(), e.getPublishedAt(),
                e.getCreatedAt(), e.getUpdatedAt());
        v.setOfflineAt(e.getOfflineAt());
        return v;
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
