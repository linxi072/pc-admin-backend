package com.acme.scaffold.cms.article.domain;

import java.time.LocalDateTime;

/**
 * 公开文章视图（C7）：仅含对外字段，排除 tenant_id / version / deleted / 审计字段等内部信息。
 * author 属可公开字段（署名），予以保留；content 列表场景不返回。
 */
public class PublicArticleView {

    private Long id;
    private Long categoryId;
    private String title;
    private String summary;
    private String author;
    private String coverUrl;
    private LocalDateTime publishedAt;

    public PublicArticleView() {
    }

    public PublicArticleView(Long id, Long categoryId, String title, String summary,
                             String author, String coverUrl, LocalDateTime publishedAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.title = title;
        this.summary = summary;
        this.author = author;
        this.coverUrl = coverUrl;
        this.publishedAt = publishedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
