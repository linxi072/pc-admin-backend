package com.acme.scaffold.system.export.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 导出中心配置装配。
 *
 * <p>参考 {@code config.cache.CacheConfig} 的写法：用 {@link EnableConfigurationProperties}
 * 显式注册 {@link ExportProperties}，避免依赖全局 {@code @ConfigurationPropertiesScan} 扫描范围。</p>
 */
@Configuration
@EnableConfigurationProperties(ExportProperties.class)
public class ExportConfig {
}
