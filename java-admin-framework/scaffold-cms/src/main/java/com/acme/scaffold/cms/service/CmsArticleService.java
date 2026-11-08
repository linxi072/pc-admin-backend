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
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章服务：文章 CRUD、发布/下线态流转、浏览计数，并在发布时经 R2 事件总线广播
 * {@link ArticlePublishedEvent}（解耦通知/检索等下游）。
 *
 * <p>持久化经 {@link SqlWriters}（动态 DSL），租户隔离由框架统一裁决；
 * 富文本正文以 TEXT 承载，由前端约定 HTML/Markdown 格式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsArticleService {

    /** 列表可排序字段白名单：客户端字段名 → 数据库列名。 */
    private static final Map<String, String> ARTICLE_SORT_FIELDS = SqlSorts.whitelist(
            "id", "id",
            "title", "title",
            "viewCount", "view_count",
            "publishedAt", "published_at",
            "createdAt", "created_at");

    private static final SqlTables.TableRef ARTICLE = SqlTables.CMS_ARTICLE;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final EventPublisher eventPublisher;

    /**
     * 创建文章；默认进入 DRAFT 草稿态，所属栏目须存在。
     *
     * <p>若请求直接以 PUBLISHED 创建，则与 {@link #publish} 保持同一发布语义（补登发布时间 +
     * 广播一次发布事件），避免出现「状态已发布但 publishedAt 为空 / 下游未收到事件」的不一致。</p>
     */
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
        boolean published = "PUBLISHED".equals(entity.getStatus());
        if (published) {
            entity.setPublishedAt(LocalDateTime.now());
        }
        entity.setViewCount(0L);
        entity.setCreatedBy(currentOperator());
        entity.setUpdatedBy(currentOperator());
        SqlWriters.insert(dsl, ARTICLE, entity);
        if (published) {
            publishEvent(entity);
        }
        return entity.getId();
    }

    /**
     * 更新文章（按 id 选择性更新）。栏目变更须存在；状态流转与 {@link #publish}/{@link #offline}
     * 共用同一套语义：转入 PUBLISHED 时补登发布时间并广播一次事件，转出 OFFLINE 时清空下线时间。
     */
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
        boolean becamePublished = false;
        boolean clearOfflineAt = false;
        if (req.getStatus() != null) {
            becamePublished = "PUBLISHED".equals(req.getStatus())
                    && !"PUBLISHED".equals(existing.getStatus());
            existing.setStatus(req.getStatus());
            if (becamePublished) {
                existing.setPublishedAt(LocalDateTime.now());
            }
            if (!"OFFLINE".equals(req.getStatus())) {
                existing.setOfflineAt(null);
                clearOfflineAt = true;
            }
        }
        existing.setUpdatedBy(currentOperator());
        if (clearOfflineAt) {
            // updateById 跳过 null 字段，清空 offline_at 必须显式声明置空列。
            SqlWriters.updateByIdWithNulls(dsl, ARTICLE, existing.getId(), existing, Set.of("offline_at"));
        } else {
            SqlWriters.updateById(dsl, ARTICLE, existing.getId(), existing);
        }
        if (becamePublished) {
            publishEvent(existing);
        }
    }

    /**
     * 发布文章：DRAFT/OFFLINE → PUBLISHED，记录发布时间、清除下线时间，
     * 并经 R2 事件总线广播一次 {@link ArticlePublishedEvent}（订阅方错误隔离）。
     *
     * <p><b>幂等</b>：已处于 PUBLISHED 时直接返回，不刷新 {@code publishedAt}、不重复广播事件，
     * 避免下游通知/检索订阅方对同一篇文章重复消费。重复发布请先 {@link #offline} 下线。</p>
     */
    @Transactional
    public void publish(Long id) {
        CmsArticleDO existing = requireExisting(id);
        if ("PUBLISHED".equals(existing.getStatus())) {
            log.info("文章已处于发布态，跳过重复发布 articleId={}", id);
            return;
        }
        existing.setStatus("PUBLISHED");
        existing.setPublishedAt(LocalDateTime.now());
        existing.setOfflineAt(null);
        existing.setUpdatedBy(currentOperator());
        // 重新发布（OFFLINE → PUBLISHED）必须清除下线时间；updateById 跳过 null，故显式置空。
        SqlWriters.updateByIdWithNulls(dsl, ARTICLE, existing.getId(), existing, Set.of("offline_at"));
        publishEvent(existing);
        log.info("文章已发布 articleId={} title={}", existing.getId(), existing.getTitle());
    }

    /**
     * 下线文章：PUBLISHED → OFFLINE，记录下线时间。
     *
     * <p><b>幂等</b>：已处于 OFFLINE 时直接返回，不重复刷新 {@code offlineAt}。</p>
     */
    @Transactional
    public void offline(Long id) {
        CmsArticleDO existing = requireExisting(id);
        if ("OFFLINE".equals(existing.getStatus())) {
            log.info("文章已处于下线态，跳过重复下线 articleId={}", id);
            return;
        }
        existing.setStatus("OFFLINE");
        existing.setOfflineAt(LocalDateTime.now());
        existing.setUpdatedBy(currentOperator());
        SqlWriters.updateById(dsl, ARTICLE, existing.getId(), existing);
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
        CmsArticleDO entity = SqlWriters.fetchById(dsl, ARTICLE, CmsArticleDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + id);
        }
        return toView(entity);
    }

    /** 删除文章（逻辑删除）。 */
    @Transactional
    public void delete(Long id) {
        requireExisting(id);
        SqlWriters.delete(dsl, ARTICLE, id, true);
    }

    /** 分页查询（支持栏目 / 标题关键字 / 状态筛选，逻辑删除自动排除）。 */
    @Transactional(readOnly = true)
    public PageResult<ArticleView> page(int page, int size, Long categoryId, String title,
                                        String status, String sortField, String sortDirection) {
        List<Condition> conds = new ArrayList<>();
        conds.add(SqlWriters.notDeleted(ARTICLE));
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
        var raw = SqlWriters.page(dsl, ARTICLE, CmsArticleDO.class, cond, q,
                SqlSorts.resolve(ARTICLE, q, ARTICLE_SORT_FIELDS,
                        ARTICLE.field("published_at", LocalDateTime.class).desc(),
                        ARTICLE.field("id", Long.class).desc()));
        List<ArticleView> views = raw.records().stream().map(this::toView).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    // ---------------- 内部辅助 ----------------

    private CmsArticleDO requireExisting(Long id) {
        CmsArticleDO entity = SqlWriters.fetchById(dsl, ARTICLE, CmsArticleDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + id);
        }
        return entity;
    }

    private void requireCategory(Long categoryId) {
        if (categoryId == null || categoryId == 0L) {
            throw BusinessException.badRequest("所属栏目不能为空");
        }
        if (SqlWriters.fetchById(dsl, SqlTables.CMS_CATEGORY, com.acme.scaffold.cms.domain.CmsCategoryDO.class,
                categoryId) == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "所属栏目不存在: " + categoryId);
        }
    }

    /** 统一发布事件出口：所有「转入 PUBLISHED」的路径共用，保证事件字段与次数一致。 */
    private void publishEvent(CmsArticleDO entity) {
        eventPublisher.publish(new ArticlePublishedEvent(
                entity.getTenantId(), "cms", entity.getId(), entity.getTitle(), entity.getCategoryId()));
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
