package com.acme.scaffold.cms.article.domain.event;

import com.acme.scaffold.common.event.DomainEvent;

/**
 * 文章发布事件（ROADMAP R2 · 跨域副作用解耦示范）。
 *
 * <p>由 {@code cms} 模块在「文章由 DRAFT/OFFLINE 转为 PUBLISHED」时发布；
 * 订阅方（如通知中心推送站内信、搜索引擎建索引）各自响应，{@code cms} 不直连下游，
 * 实现模块解耦。当前未内置订阅者，事件总线会安全忽略（错误隔离），后续可按需挂载。</p>
 */
public class ArticlePublishedEvent extends DomainEvent {

    /** 发布的文章主键。 */
    private final Long articleId;

    /** 文章标题（通知/检索展示用）。 */
    private final String title;

    /** 所属栏目主键（便于订阅方按栏目路由）。 */
    private final Long categoryId;

    public ArticlePublishedEvent(Long tenantId, String source, Long articleId, String title, Long categoryId) {
        super(tenantId, source);
        this.articleId = articleId;
        this.title = title;
        this.categoryId = categoryId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public String getTitle() {
        return title;
    }

    public Long getCategoryId() {
        return categoryId;
    }
}
