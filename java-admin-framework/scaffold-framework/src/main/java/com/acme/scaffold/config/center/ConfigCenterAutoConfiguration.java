package com.acme.scaffold.config.center;

import com.acme.scaffold.common.event.EventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 配置中心装配（R16）。
 *
 * <p>默认 {@code app.config-center.enabled=false} 时整体不注册任何 Bean（端口 / 服务 / 控制器），
 * 与既有行为零回归。启用时注册本地适配器（默认 {@code type=local}）+ 服务。</p>
 *
 * <p>远端类型（nacos / apollo）的 {@link ConfigCenterPort} 实现由外部 jar 经 R1 插件机制提供，
 * 本配置仅装配本地默认实现，避免离线仓库引入其 SDK 依赖。非 local 类型且未提供对应 Bean 时，
 * 启动期将抛出 {@link IllegalStateException}，由运维按文档接入，避免「静默降级」掩盖配置错误。</p>
 */
@Configuration
@EnableConfigurationProperties(ConfigCenterProperties.class)
@ConditionalOnProperty(prefix = "app.config-center", name = "enabled", havingValue = "true")
public class ConfigCenterAutoConfiguration {

    @Bean
    public ConfigCenterPort configCenterPort(ConfigurableEnvironment environment, ConfigCenterProperties props) {
        String type = props.getType() == null ? "local" : props.getType();
        if ("local".equalsIgnoreCase(type)) {
            return new LocalConfigCenterAdapter(environment);
        }
        // 非 local 类型需外部 jar 提供 ConfigCenterPort Bean（见类注释）。
        throw new IllegalStateException(
                "配置中心类型 '" + type + "' 需要外部 jar 提供 ConfigCenterPort 实现（参照 R1 插件机制）");
    }

    @Bean
    public ConfigCenterService configCenterService(ConfigCenterPort port,
                                                   EventPublisher eventPublisher,
                                                   ConfigCenterProperties props) {
        return new ConfigCenterService(port, eventPublisher, props);
    }
}
