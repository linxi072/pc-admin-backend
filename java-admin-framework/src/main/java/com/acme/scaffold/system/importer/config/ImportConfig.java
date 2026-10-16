package com.acme.scaffold.system.importer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 导入中心配置装配。
 *
 * <p>参考 {@code system.export.config.ExportConfig} 的写法：用 {@link EnableConfigurationProperties}
 * 显式注册 {@link ImportProperties}，避免依赖全局 {@code @ConfigurationPropertiesScan} 扫描范围。</p>
 */
@Configuration
@EnableConfigurationProperties(ImportProperties.class)
public class ImportConfig {
}
