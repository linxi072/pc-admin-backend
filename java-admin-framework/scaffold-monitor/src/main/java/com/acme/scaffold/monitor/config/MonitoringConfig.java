package com.acme.scaffold.monitor.config;

import com.acme.scaffold.monitor.alert.AlertProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控相关配置：启用告警接收端配置属性（A2）与定时任务基础设施（告警规则周期评估）。
 */
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
@EnableScheduling
public class MonitoringConfig {
}
