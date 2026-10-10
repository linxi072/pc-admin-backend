package com.acme.scaffold.monitor;

import com.acme.scaffold.monitor.alert.AlertProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 监控相关配置：启用告警接收端配置属性（A2）。
 */
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class MonitoringConfig {
}
