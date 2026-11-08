package com.acme.scaffold.config.multiregion;

import com.acme.scaffold.config.datasource.DataSourceRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 多区域部署支撑自动装配（R4）。
 *
 * <p>仅当 {@code app.multiregion.enabled=true} 时加载；关闭时完全不装配任何 R4 Bean，
 * 既有的单实例 / 单数据源行为零回归。启用后：
 * <ul>
 *   <li>{@link TenantUnitMapping}：租户 → 部署单元（数据源名）解析器；</li>
 *   <li>{@link MultiRegionInterceptor}：Web 请求期按租户切路由上下文；</li>
 *   <li>{@link MultiRegionRouter}：非 Web 路径的编程式按租户切路由；</li>
 *   <li>数据库结构由 DBA 对每个单元分别执行 db/init/ 初始化（应用不自动迁移）。</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.multiregion", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MultiRegionProperties.class)
public class MultiRegionAutoConfiguration {

    @Bean
    public TenantUnitMapping tenantUnitMapping(MultiRegionProperties props, DataSourceRegistry registry) {
        return TenantUnitMapping.from(props, registry.primaryName());
    }

    @Bean
    public MultiRegionInterceptor multiRegionInterceptor(TenantUnitMapping mapping) {
        return new MultiRegionInterceptor(mapping);
    }

    @Bean
    public MultiRegionRouter multiRegionRouter(TenantUnitMapping mapping) {
        return new MultiRegionRouter(mapping);
    }

}
