package com.acme.scaffold.cms.article.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章创建请求。新建文章默认进入 DRAFT 草稿态。
 */
public class ArticleCreateRequest implements Serializable {

    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 512)
    private String summary;

    /** 富文本正文（HTML/Markdown 由前端约定）。 */
    private String content;

    @Size(max = 64)
    private String author;

    @Size(max = 512)
    private String coverUrl;

    private String status = "DRAFT";

    /** 定时发布时间（C5）：null 表示不排期。 */
    private LocalDateTime scheduledPublishAt;

    /** 业务排序号（C10）：null 按 0 处理，越小越靠前。 */
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(9999)
    private Integer sortNo;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public LocalDateTime getScheduledPublishAt() {
        return scheduledPublishAt;
    }

    public void setScheduledPublishAt(LocalDateTime scheduledPublishAt) {
        this.scheduledPublishAt = scheduledPublishAt;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
