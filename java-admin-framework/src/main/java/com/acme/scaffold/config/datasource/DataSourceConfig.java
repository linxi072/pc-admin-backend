package com.acme.scaffold.config.datasource;

import com.acme.scaffold.config.jooq.JooqConfig;
import com.acme.scaffold.jooq.mapping.SnakeRecordMapperProvider;
import com.acme.scaffold.monitor.detection.SlowQueryListener;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.jooq.impl.DefaultTransactionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源装配中心。
 *
 * <p>要点：
 * <ul>
 *   <li><b>@Primary RoutingDataSource</b>：持有全部具名数据源并按 {@link DataSourceContextHolder}
 *       路由；上下文为空时回落主库。现有服务注入的 DSLContext / DataSource 默认行为不变。</li>
 *   <li><b>@Primary PlatformTransactionManager</b>：基于路由数据源，保证 @Transactional 与路由一致
 *       （声明式事务在连接获取阶段即按当前路由解析目标库）。</li>
 *   <li><b>Map&lt;String, DSLContext&gt;</b>：为每个具名数据源构建独立 DSLContext，供显式按名访问
 *       （见 {@link DataSourceRouter}），不受路由上下文影响。</li>
 * </ul>
 *
 * <p>注：Spring Boot 自带的单数据源自动装配（DataSourceAutoConfiguration）已在 application.yml
 * 通过 spring.autoconfigure.exclude 关闭，改由本配置接管。
 */
@Configuration
@EnableConfigurationProperties(MultiDataSourceProperties.class)
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource routingDataSource(DataSourceRegistry registry) {
        RoutingDataSource ds = new RoutingDataSource();
        Map<Object, Object> targets = new LinkedHashMap<>(registry.all());
        ds.setTargetDataSources(targets);
        ds.setDefaultTargetDataSource(registry.primary());
        ds.setLenientFallback(true);
        ds.afterPropertiesSet();
        return ds;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource routingDataSource) {
        return new DataSourceTransactionManager(routingDataSource);
    }

    @Bean
    public Map<String, DSLContext> dslContexts(DataSourceRegistry registry,
            @Value("${app.jooq.slow-query-threshold-ms:500}") long slowQueryThresholdMs) {
        Map<String, DSLContext> map = new LinkedHashMap<>();
        registry.all().forEach((name, ds) -> map.put(name, buildDsl(ds, slowQueryThresholdMs)));
        return map;
    }

    /** 与 JooqConfig 保持一致的 DSLContext 构造（动态 DSL / MySQL 方言 / 慢 SQL 监听 / Snake 映射）。 */
    private DSLContext buildDsl(DataSource ds, long slowQueryThresholdMs) {
        DataSourceConnectionProvider connectionProvider =
                new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(ds));
        org.jooq.Configuration configuration = new DefaultConfiguration()
                .set(connectionProvider)
                .set(new DefaultTransactionProvider(connectionProvider))
                .set(SQLDialect.MYSQL)
                .set(new SnakeRecordMapperProvider())
                .set(new DefaultExecuteListenerProvider(new SlowQueryListener(slowQueryThresholdMs)))
                .set(new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER));
        return org.jooq.impl.DSL.using(configuration);
    }
}
