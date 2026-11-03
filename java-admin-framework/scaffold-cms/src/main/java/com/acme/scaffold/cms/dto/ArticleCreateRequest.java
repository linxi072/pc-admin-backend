package com.acme.scaffold.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

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
}
