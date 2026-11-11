package com.acme.scaffold.config.web;

import com.acme.scaffold.common.constant.CommonConstants;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.config.multiregion.MultiRegionInterceptor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：CORS 仅允许开发前端来源；生产应由网关/反向代理控制，不要在此放开通配。
 * <p>同时把头像上传目录映射为静态资源，使前端可直接以 URL 预览头像。
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** 头像访问前缀，与 ProfileService 中保存的 avatarUrl 前缀保持一致。 */
    private static final String AVATAR_URL_PREFIX = CommonConstants.AVATAR_URL_PREFIX;

    private final TenantProperties tenantProperties;
    private final SecurityContextFacade securityContextFacade;
    private final ObjectProvider<MultiRegionInterceptor> multiRegionInterceptor;

    @Value("${app.upload.avatar-dir:./uploads/avatar}")
    private String avatarDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 租户上下文先行：MultiRegionInterceptor 依赖 TenantContext 已设置，故注册在后。
        registry.addInterceptor(tenantInterceptor());
        // 多区域拦截器仅当 app.multiregion.enabled=true 时才作为 Bean 存在；关闭时跳过注册，零回归。
        MultiRegionInterceptor mr = multiRegionInterceptor.getIfAvailable();
        if (mr != null) {
            registry.addInterceptor(mr);
        }
    }

    @Bean
    public TenantInterceptor tenantInterceptor() {
        return new TenantInterceptor(tenantProperties, securityContextFacade);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path location = Paths.get(avatarDir).toAbsolutePath().normalize();
        registry.addResourceHandler(AVATAR_URL_PREFIX + "**")
                .addResourceLocations(location.toUri().toString());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Trace-Id", "traceparent"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
