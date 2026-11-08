package com.acme.scaffold.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 辅助开发配置：提示词成本门禁与生成代码默认元信息。
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 提示词最大估算 token 数（成本门禁；超出拒生成）。默认 2000。 */
    private int maxPromptTokens = 2000;

    /** 生成代码默认作者（写入文件头注释）。 */
    private String author = "ai-scaffold";

    /** 前端路由/源码路径提示（生成 Vue / api.js 的 path 前缀，仅作展示）。 */
    private String webPath = "pc-admin-vue3-vite/src";

    public int getMaxPromptTokens() {
        return maxPromptTokens;
    }

    public void setMaxPromptTokens(int maxPromptTokens) {
        this.maxPromptTokens = maxPromptTokens;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getWebPath() {
        return webPath;
    }

    public void setWebPath(String webPath) {
        this.webPath = webPath;
    }
}
