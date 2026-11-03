package com.acme.scaffold.cms.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章视图（列表/详情展示；content 在列表场景可由调用方按需裁剪）。
 */
public class ArticleView implements Serializable {

    private Long id;

    private Long categoryId;

    private String title;

    private String summary;

    private String author;

    private String coverUrl;

    private String status;

    private Long viewCount;

    private LocalDateTime publishedAt;

    private LocalDateTime offlineAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public ArticleView() {
    }

    public ArticleView(Long id, Long categoryId, String title, String summary, String author,
                       String coverUrl, String status, Long viewCount,
                       LocalDateTime publishedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.title = title;
        this.summary = summary;
        this.author = author;
        this.coverUrl = coverUrl;
        this.status = status;
        this.viewCount = viewCount;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getOfflineAt() {
        return offlineAt;
    }

    public void setOfflineAt(LocalDateTime offlineAt) {
        this.offlineAt = offlineAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
