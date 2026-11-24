package com.acme.scaffold.cms.article.service;

import com.acme.scaffold.cms.article.domain.event.ArticlePublishedEvent;
import com.acme.scaffold.cms.article.domain.rule.CmsArticleRules;
import com.acme.scaffold.cms.article.domain.rule.CmsBatchMoveRules;
import com.acme.scaffold.cms.article.domain.rule.CmsStatusRules;
import com.acme.scaffold.cms.article.domain.model.CmsArticle;
import com.acme.scaffold.cms.article.domain.ArticleCreateRequest;
import com.acme.scaffold.cms.article.domain.ArticleUpdateRequest;
import com.acme.scaffold.cms.article.domain.ArticleView;
import com.acme.scaffold.cms.article.domain.BatchMoveArticleRequest;
import com.acme.scaffold.cms.article.domain.BatchMoveArticleView;
import com.acme.scaffold.common.api.domain.PageQuery;
import com.acme.scaffold.common.api.domain.PageResult;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CmsArticleVersionService versionService;

    /**
     * 创建文章；默认进入 DRAFT 草稿态，所属栏目须存在。
     *
     * <p>若请求直接以 PUBLISHED 创建，则与 {@link #publish} 保持同一发布语义（补登发布时间 +
     * 广播一次发布事件），避免出现「状态已发布但 publishedAt 为空 / 下游未收到事件」的不一致。</p>
     */
    @Transactional
    public Long create(ArticleCreateRequest req) {
        requireCategory(req.getCategoryId());
        CmsArticle entity = new CmsArticle();
        entity.setCategoryId(req.getCategoryId());
        entity.setTitle(req.getTitle());
        entity.setSummary(req.getSummary());
        entity.setContent(req.getContent());
        entity.setAuthor(CmsArticleRules.resolveAuthor(req.getAuthor(), currentOperator()));
        entity.setCoverUrl(req.getCoverUrl());
        entity.setScheduledPublishAt(req.getScheduledPublishAt());
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(CmsArticleRules.resolveInitialStatus(
                req.getStatus(), req.getScheduledPublishAt(), now));
        boolean published = CmsStatusRules.isPublishable(entity.getStatus());
        if (published) {
            entity.setPublishedAt(now);
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
        CmsArticle existing = requireExisting(req.getId());
        requireCategory(req.getCategoryId());
        // C4：更新前固化快照（内容无变化时版本服务内部自动跳过）
        versionService.snapshot(existing, "更新前快照");
        existing.setCategoryId(req.getCategoryId());
        existing.setTitle(req.getTitle());
        existing.setSummary(req.getSummary());
        existing.setContent(req.getContent());
        existing.setAuthor(req.getAuthor());
        existing.setCoverUrl(req.getCoverUrl());
        existing.setScheduledPublishAt(req.getScheduledPublishAt());
        java.util.Set<String> nullColumns = new java.util.HashSet<>();
        if (req.getScheduledPublishAt() == null) {
            nullColumns.add("scheduled_publish_at"); // 全量更新语义：未传排期即取消排期
        }
        boolean becamePublished = false;
        if (req.getStatus() != null) {
            String targetStatus = CmsArticleRules.resolveInitialStatus(
                    req.getStatus(), req.getScheduledPublishAt(), LocalDateTime.now());
            becamePublished = CmsStatusRules.becamePublished(existing.getStatus(), targetStatus);
            existing.setStatus(targetStatus);
            if (becamePublished) {
                existing.setPublishedAt(LocalDateTime.now());
            }
            if (CmsStatusRules.shouldClearOfflineAt(targetStatus)) {
                existing.setOfflineAt(null);
                nullColumns.add("offline_at");
            }
        }
        existing.setUpdatedBy(currentOperator());
        if (nullColumns.isEmpty()) {
            SqlWriters.updateById(dsl, ARTICLE, existing.getId(), existing);
        } else {
            // updateById 跳过 null 字段，置空列必须显式声明。
            SqlWriters.updateByIdWithNulls(dsl, ARTICLE, existing.getId(), existing, nullColumns);
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
        CmsArticle existing = requireExisting(id);
        if (CmsStatusRules.isPublishNoop(existing.getStatus())) {
            log.info("文章已处于发布态，跳过重复发布 articleId={}", id);
            return;
        }
        existing.setStatus("PUBLISHED");
        existing.setPublishedAt(LocalDateTime.now());
        existing.setOfflineAt(null);
        existing.setScheduledPublishAt(null);
        existing.setUpdatedBy(currentOperator());
        // 重新发布（OFFLINE/SCHEDULED → PUBLISHED）须清下线时间与排期；updateById 跳过 null，故显式置空。
        SqlWriters.updateByIdWithNulls(dsl, ARTICLE, existing.getId(), existing,
                Set.of("offline_at", "scheduled_publish_at"));
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
        CmsArticle existing = requireExisting(id);
        if (CmsStatusRules.isOfflineNoop(existing.getStatus())) {
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

    /**
     * 定时发布扫描（C5）：发布所有 SCHEDULED 且排期时刻已到的文章。
     *
     * <p>逐条复用 {@link #publish} 的幂等与事件语义；单条失败仅告警不阻塞其余，
     * 由 {@code CmsScheduledPublishJob} 周期调用。</p>
     *
     * @return 本次成功发布的文章数
     */
    @Transactional
    public int publishDue() {
        LocalDateTime now = LocalDateTime.now();
        List<CmsArticle> due = SqlWriters.fetchList(dsl, ARTICLE, CmsArticle.class,
                DSL.and(SqlWriters.notDeleted(ARTICLE),
                        ARTICLE.field("status", String.class).eq(CmsArticleRules.SCHEDULED),
                        ARTICLE.field("scheduled_publish_at", LocalDateTime.class).le(now)));
        int published = 0;
        for (CmsArticle article : due) {
            try {
                publish(article.getId()); // SCHEDULED 非 PUBLISHED，publish 幂等检查不会跳过
                published++;
            } catch (RuntimeException e) {
                log.warn("定时发布失败 articleId={} title={}", article.getId(), article.getTitle(), e);
            }
        }
        if (published > 0) {
            log.info("定时发布完成 due={} published={}", due.size(), published);
        }
        return published;
    }

    /** 按 id 查询（含逻辑删除过滤），不存在抛 404。 */
    @Transactional(readOnly = true)
    public ArticleView getById(Long id) {
        CmsArticle entity = SqlWriters.fetchById(dsl, ARTICLE, CmsArticle.class, id);
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
        String titlePattern = CmsArticleRules.likePattern(title);
        if (titlePattern != null) {
            conds.add(ARTICLE.field("title", String.class).like(titlePattern));
        }
        if (status != null && !status.isBlank()) {
            conds.add(ARTICLE.field("status", String.class).eq(status.trim()));
        }
        Condition cond = DSL.and(conds);
        PageQuery q = PageQuery.of(page, size, sortField, sortDirection);
        var raw = SqlWriters.page(dsl, ARTICLE, CmsArticle.class, cond, q,
                SqlSorts.resolve(ARTICLE, q, ARTICLE_SORT_FIELDS,
                        ARTICLE.field("published_at", LocalDateTime.class).desc(),
                        ARTICLE.field("id", Long.class).desc()));
        List<ArticleView> views = raw.records().stream().map(this::toView).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /**
     * 批量移动文章到目标栏目（事务内原子执行）。
     *
     * <p>入参经 {@link CmsBatchMoveRules} 归一（去重 / 去非法 id）并校验上限；目标栏目须存在（复用
     * {@link #requireCategory}）。逐条查询文章，未匹配到有效文章的 id 记入 {@code skippedIds} 返回，
     * 不做部分更新；任一数据库更新异常将导致整体回滚。</p>
     *
     * @return 移动结果（moved 数量 + 跳过的 id + 目标栏目）
     */
    @Transactional
    public BatchMoveArticleView batchMove(BatchMoveArticleRequest req) {
        if (req == null || !CmsBatchMoveRules.validIds(req.getIds())) {
            throw BusinessException.badRequest("待移动文章不能为空，单次最多 "
                    + CmsBatchMoveRules.MAX_IDS + " 篇");
        }
        if (!CmsBatchMoveRules.validTargetCategory(req.getCategoryId())) {
            throw BusinessException.badRequest("目标栏目不能为空");
        }
        requireCategory(req.getCategoryId());

        List<Long> ids = CmsBatchMoveRules.normalizeIds(req.getIds());
        List<Long> skipped = new ArrayList<>();
        int moved = 0;
        String operator = currentOperator();
        for (Long id : ids) {
            CmsArticle article = SqlWriters.fetchById(dsl, ARTICLE, CmsArticle.class, id);
            if (article == null) {
                skipped.add(id); // 不存在或已逻辑删除，跳过并回报
                continue;
            }
            CmsArticle patch = new CmsArticle();
            patch.setCategoryId(req.getCategoryId());
            patch.setUpdatedBy(operator);
            SqlWriters.updateById(dsl, ARTICLE, id, patch);
            moved++;
        }
        log.info("CMS 批量移动完成 ids={} target={} moved={} skipped={}",
                ids.size(), req.getCategoryId(), moved, skipped.size());
        return new BatchMoveArticleView(moved, skipped, req.getCategoryId());
    }

    // ---------------- 内部辅助 ----------------

    private CmsArticle requireExisting(Long id) {
        CmsArticle entity = SqlWriters.fetchById(dsl, ARTICLE, CmsArticle.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + id);
        }
        return entity;
    }

    private void requireCategory(Long categoryId) {
        if (!CmsArticleRules.validCategoryId(categoryId)) {
            throw BusinessException.badRequest("所属栏目不能为空");
        }
        if (SqlWriters.fetchById(dsl, SqlTables.CMS_CATEGORY, com.acme.scaffold.cms.category.domain.model.CmsCategory.class,
                categoryId) == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "所属栏目不存在: " + categoryId);
        }
    }

    /** 统一发布事件出口：所有「转入 PUBLISHED」的路径共用，保证事件字段与次数一致。 */
    private void publishEvent(CmsArticle entity) {
        eventPublisher.publish(new ArticlePublishedEvent(
                entity.getTenantId(), "cms", entity.getId(), entity.getTitle(), entity.getCategoryId()));
    }

    private ArticleView toView(CmsArticle e) {
        ArticleView v = new ArticleView(e.getId(), e.getCategoryId(), e.getTitle(), e.getSummary(), e.getAuthor(),
                e.getCoverUrl(), e.getStatus(), e.getViewCount(), e.getPublishedAt(),
                e.getCreatedAt(), e.getUpdatedAt());
        v.setOfflineAt(e.getOfflineAt());
        v.setScheduledPublishAt(e.getScheduledPublishAt());
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
