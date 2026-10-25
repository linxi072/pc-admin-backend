package com.acme.scaffold.workflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 流程监控配置装配（M23）。
 *
 * <p>与 {@code system.export.config.ExportConfig} 同范式：用 {@link EnableConfigurationProperties}
 * 显式注册 {@link WorkflowMonitorProperties}，避免依赖全局 {@code @ConfigurationPropertiesScan} 扫描范围。</p>
 */
@Configuration
@EnableConfigurationProperties(WorkflowMonitorProperties.class)
public class WorkflowMonitorConfig {
}
