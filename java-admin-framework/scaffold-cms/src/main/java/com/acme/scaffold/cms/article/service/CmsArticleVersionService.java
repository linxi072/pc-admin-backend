package com.acme.scaffold.cms.article.service;

import com.acme.scaffold.cms.article.domain.ArticleVersionView;
import com.acme.scaffold.cms.article.domain.model.CmsArticle;
import com.acme.scaffold.cms.article.domain.model.CmsArticleVersion;
import com.acme.scaffold.cms.article.domain.rule.CmsVersionRules;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章版本历史（C4）：update 前固化主表快照、版本列表、回滚写回主表。
 *
 * <p>回滚写回主表时显式清空 {@code scheduled_publish_at}（若目标版本为空），
 * 且回滚动作本身再固化一个新版本，历史快照不删（与表单版本管理同构）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsArticleVersionService {

    private static final SqlTables.TableRef VERSION = SqlTables.CMS_ARTICLE_VERSION;
    private static final SqlTables.TableRef ARTICLE = SqlTables.CMS_ARTICLE;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /**
     * 固化快照（在 articleService.update 写主表之前调用）。
     *
     * <p>内容与最新版本一致时跳过（避免无变更重复快照），并发下以 max(versionNo)+1 分配版本号。</p>
     *
     * @param article 更新前的文章实体
     * @param remark 变更备注
     */
    @Transactional
    public void snapshot(CmsArticle article, String remark) {
        List<CmsArticleVersion> versions = listEntities(article.getId());
        CmsArticleVersion latest = versions.isEmpty() ? null : versions.get(versions.size() - 1);
        if (latest != null && CmsVersionRules.sameContent(
                latest.getTitle(), latest.getSummary(), latest.getContent(),
                article.getTitle(), article.getSummary(), article.getContent())) {
            return; // 内容未变，跳过重复快照
        }
        int nextNo = CmsVersionRules.nextVersionNo(
                versions.stream().map(CmsArticleVersion::getVersionNo).toList());
        CmsArticleVersion snap = new CmsArticleVersion();
        snap.setArticleId(article.getId());
        snap.setVersionNo(nextNo);
        copyContent(article, snap);
        snap.setRemark(remark);
        snap.setCreatedBy(currentOperator());
        SqlWriters.insert(dsl, VERSION, snap);
    }

    /** 版本列表（按 version_no 升序）。 */
    @Transactional(readOnly = true)
    public List<ArticleVersionView> listVersions(Long articleId) {
        return listEntities(articleId).stream().map(this::toView).toList();
    }

    /**
     * 回滚：把目标版本内容写回主表，并固化一个新版本记录回滚动作。
     */
    @Transactional
    public void rollback(Long articleId, int versionNo) {
        CmsArticle current = SqlWriters.fetchById(dsl, ARTICLE, CmsArticle.class, articleId);
        if (current == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "文章不存在: " + articleId);
        }
        List<CmsArticleVersion> versions = listEntities(articleId);
        CmsArticleVersion target = versions.stream()
                .filter(v -> v.getVersionNo() != null && v.getVersionNo() == versionNo)
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND,
                        "目标版本不存在: " + versionNo));
        int currentNo = versions.isEmpty() ? 0 : versions.get(versions.size() - 1).getVersionNo();
        if (!CmsVersionRules.canRollback(currentNo, versionNo)) {
            throw BusinessException.badRequest("目标版本即当前版本，无需回滚");
        }
        CmsArticle update = new CmsArticle();
        update.setCategoryId(target.getCategoryId());
        update.setTitle(target.getTitle());
        update.setSummary(target.getSummary());
        update.setContent(target.getContent());
        update.setAuthor(target.getAuthor());
        update.setCoverUrl(target.getCoverUrl());
        update.setStatus(target.getStatus());
        update.setViewCount(target.getViewCount());
        update.setPublishedAt(target.getPublishedAt());
        update.setUpdatedBy(currentOperator());
        // 快照不含排期字段，回滚后必须清空；目标版本非下线态时同时清空下线时间
        Set<String> nullColumns = new java.util.HashSet<>(Set.of("scheduled_publish_at"));
        if (!"OFFLINE".equals(target.getStatus())) {
            nullColumns.add("offline_at");
        }
        SqlWriters.updateByIdWithNulls(dsl, ARTICLE, articleId, update, nullColumns);
        snapshot(update, "回滚自 v" + versionNo);
        log.info("文章已回滚 articleId={} targetVersion={}", articleId, versionNo);
    }

    /** 纯函数式回填：实体 → 快照（供单测复用的静态映射独立在下方 copyContent）。 */
    private List<CmsArticleVersion> listEntities(Long articleId) {
        return new ArrayList<>(SqlWriters.fetchList(dsl, VERSION, CmsArticleVersion.class,
                DSL.and(SqlWriters.notDeleted(VERSION),
                        VERSION.field("article_id", Long.class).eq(articleId)),
                VERSION.field("version_no", Integer.class).asc()));
    }

    private static void copyContent(CmsArticle from, CmsArticleVersion to) {
        to.setCategoryId(from.getCategoryId());
        to.setTitle(from.getTitle());
        to.setSummary(from.getSummary());
        to.setContent(from.getContent());
        to.setAuthor(from.getAuthor());
        to.setCoverUrl(from.getCoverUrl());
        to.setStatus(from.getStatus());
        to.setViewCount(from.getViewCount());
        to.setPublishedAt(from.getPublishedAt());
        to.setOfflineAt(from.getOfflineAt());
    }

    private ArticleVersionView toView(CmsArticleVersion v) {
        ArticleVersionView view = new ArticleVersionView();
        view.setId(v.getId());
        view.setArticleId(v.getArticleId());
        view.setVersionNo(v.getVersionNo());
        view.setCategoryId(v.getCategoryId());
        view.setTitle(v.getTitle());
        view.setSummary(v.getSummary());
        view.setContent(v.getContent());
        view.setAuthor(v.getAuthor());
        view.setStatus(v.getStatus());
        view.setViewCount(v.getViewCount());
        view.setPublishedAt(v.getPublishedAt());
        view.setOfflineAt(v.getOfflineAt());
        view.setRemark(v.getRemark());
        view.setCreatedBy(v.getCreatedBy());
        view.setCreatedAt(v.getCreatedAt());
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
