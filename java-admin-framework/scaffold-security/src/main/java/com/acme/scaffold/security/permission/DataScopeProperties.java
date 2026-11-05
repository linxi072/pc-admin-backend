package com.acme.scaffold.security.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据权限兜底策略配置（P0-1）。
 *
 * <p>绑定 {@code app.datascope.default-policy}：取值 {@code ALL} / {@code SELF}，
 * 默认 {@code SELF}（fail-closed，配置缺失即最小可见）。</p>
 *
 * <p>作为 {@code @Component} 注册，由 Spring Boot 自动绑定 {@code @ConfigurationProperties}；
 * 未配置时回落到 {@link DataScopeDefaultPolicy#SELF}。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.datascope")
public class DataScopeProperties {

    private DataScopeDefaultPolicy defaultPolicy = DataScopeDefaultPolicy.SELF;

    public DataScopeDefaultPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(DataScopeDefaultPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }
}
