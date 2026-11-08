package com.acme.scaffold.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 辅助开发配置：启用生成参数配置属性（成本门禁阈值 / 默认作者 / 前端路径提示）。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
