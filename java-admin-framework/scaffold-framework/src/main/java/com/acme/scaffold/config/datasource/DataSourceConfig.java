package com.acme.scaffold.config.datasource;

import com.acme.scaffold.config.mybatis.MyBatisContextFactory;
import com.acme.scaffold.monitor.detection.NPlusOneListener;
import com.acme.scaffold.monitor.detection.NPlusOneProbe;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.monitor.detection.SlowQueryListener;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.ExecuteListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多数据源装配中心。
 *
 * <p>要点：
 * <ul>
 *   <li><b>@Primary RoutingDataSource</b>：持有全部具名数据源并按 {@link DataSourceContextHolder}
 *       路由；上下文为空时回落主库。</li>
 *   <li><b>@Primary PlatformTransactionManager</b>：基于路由数据源，保证 @Transactional 与路由一致。</li>
 *   <li><b>Map&lt;String, DSLContext&gt;</b>：为每个具名数据源构建独立的 MyBatis SqlSession
 *       与 DSLContext，供显式按名访问（见 {@link DataSourceRouter}）。</li>
 * </ul>
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
            @Value("${app.mybatis.slow-query-threshold-ms:500}") long slowQueryThresholdMs,
            NPlusOneProbe nPlusOneProbe,
            NPlusOneSuspectRegistry suspectRegistry) {
        List<ExecuteListener> listeners = List.of(
                new SlowQueryListener(slowQueryThresholdMs),
                new NPlusOneListener(nPlusOneProbe, suspectRegistry));
        Map<String, DSLContext> map = new LinkedHashMap<>();
        registry.all().forEach((name, ds) -> map.put(name, MyBatisContextFactory.build(ds, listeners)));
        return map;
    }
}
