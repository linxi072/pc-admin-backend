package com.acme.scaffold.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Swagger / OpenAPI 文档访问策略（{@code app.swagger.*}）。
 *
 * <p>默认 {@code false}：文档端点（{@code /v3/api-docs/**}、{@code /swagger-ui/**}、
 * {@code /swagger-ui.html}）需 JWT 鉴权，仅授权用户可访问；本地联调置 {@code true} 放开匿名访问。</p>
 *
 * <p>生产环境文档本身已被 {@code springdoc.api-docs.enabled=false} 关闭，即便放开匿名也无内容可访问，
 * 此处仅作为访问策略的显式开关，避免在安全配置里硬编码「文档永久匿名」。</p>
 *
 * <p>置于 {@code security.config}（与其唯一消费者 {@link SecurityConfig} 同包），避免
 * {@code security.config → config.web} 的跨层依赖（H3）。</p>
 */
@ConfigurationProperties(prefix = "app.swagger")
public class SwaggerProperties {

    /** 是否允许匿名访问 Swagger 文档端点（默认 false，需 JWT 鉴权）。 */
    private boolean publicAccess = false;

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }
}
