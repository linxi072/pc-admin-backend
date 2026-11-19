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

    /** 云大模型接入配置（ai.llm.*）。 */
    private final Llm llm = new Llm();

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

    public Llm getLlm() {
        return llm;
    }

    /**
     * 云大模型接入参数（OpenAI 兼容 chat/completions 协议）。
     *
     * <p>默认 {@code enabled=false}：离线构建仅使用启发式提供者；置 true 时装配
     * {@code CloudLlmProvider}，由编排层优选并支持失败降级回退。</p>
     */
    public static class Llm {

        /** 是否启用云大模型（false 时该 Bean 不装配，走启发式）。 */
        private boolean enabled;

        /** OpenAI 兼容端点（如 https://api.example.com/v1/chat/completions）。 */
        private String endpoint;

        /** API Key（Bearer 方式注入 Authorization 头；留空则不携带鉴权头）。 */
        private String apiKey;

        /** 模型标识（如 gpt-4o-mini / qwen-plus / deepseek-chat）。 */
        private String model = "gpt-4o-mini";

        /** 调用超时（毫秒）。 */
        private long timeoutMs = 15_000;

        /** 云调用运行期失败（网络/协议/解析）时是否降级回退启发式提供者。 */
        private boolean fallbackOnFailure = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public boolean isFallbackOnFailure() {
            return fallbackOnFailure;
        }

        public void setFallbackOnFailure(boolean fallbackOnFailure) {
            this.fallbackOnFailure = fallbackOnFailure;
        }
    }
}
