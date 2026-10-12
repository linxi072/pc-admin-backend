package com.acme.scaffold.codegen;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 代码生成器配置：启用生成参数配置属性（包名前缀 / 默认作者 / 前端路径提示）。
 */
@Configuration
@EnableConfigurationProperties(CodeGenProperties.class)
public class CodeGenConfig {
}
