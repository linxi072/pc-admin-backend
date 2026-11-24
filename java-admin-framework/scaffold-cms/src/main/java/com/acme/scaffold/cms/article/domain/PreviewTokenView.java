package com.acme.scaffold.cms.article.domain;

import java.time.LocalDateTime;

/** 预览令牌响应（C6）：生成后返回给管理端用于分享。 */
public class PreviewTokenView {

    private String token;
    private Long articleId;
    private LocalDateTime expiresAt;

    public PreviewTokenView() {
    }

    public PreviewTokenView(String token, Long articleId, LocalDateTime expiresAt) {
        this.token = token;
        this.articleId = articleId;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
