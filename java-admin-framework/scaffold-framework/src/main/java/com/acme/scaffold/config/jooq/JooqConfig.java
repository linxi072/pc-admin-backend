package com.acme.scaffold.config.jooq;

import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.jooq.mapping.SnakeRecordMapperProvider;
import com.acme.scaffold.monitor.detection.NPlusOneListener;
import com.acme.scaffold.monitor.detection.NPlusOneProbe;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

/**
 * jOOQ 配置：使用动态 DSL（无需代码生成）。
 * <ul>
 *   <li>通过 Spring 事务管理器接入声明式事务（@Transactional 生效）；</li>
 *   <li>MySQL 方言，渲染列名不加引号（snake_case 兼容）；</li>
 *   <li>统一使用 SnakeRecordMapper 完成 Record → POJO 映射。</li>
 * </ul>
 */
@Configuration
public class JooqConfig {

    @Bean
    public NPlusOneProbe nPlusOneProbe(@Value("${app.jooq.nplus1-threshold:10}") int threshold) {
        return new NPlusOneProbe(threshold);
    }

    @Bean
    public NPlusOneSuspectRegistry nPlusOneSuspectRegistry(
            @Value("${app.jooq.nplus1-max-suspects:200}") int capacity) {
        return new NPlusOneSuspectRegistry(capacity);
    }

    @Bean
    public DSLContext dslContext(DataSource dataSource,
                                 @Value("${app.jooq.slow-query-threshold-ms:500}") long slowQueryThresholdMs,
                                 NPlusOneProbe nPlusOneProbe,
                                 NPlusOneSuspectRegistry nPlusOneSuspectRegistry,
                                 TenantProperties tenantProperties) {
        // M19 P3：将租户隔离配置快照注入 TenantEnforcement（静态单点裁决，enforce=false 默认零回归）。
        TenantEnforcement.configure(tenantProperties);
        // TransactionAwareDataSourceProxy 使 jOOQ 获取的连接自动加入 Spring 声明式事务
        // （@Transactional 对 jOOQ 语句生效），无需额外的 jOOQ-Spring 依赖。
        DataSourceConnectionProvider connectionProvider =
                new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource));
        org.jooq.Configuration configuration = new DefaultConfiguration()
                .set(connectionProvider)
                .set(new DefaultTransactionProvider(connectionProvider))
                .set(SQLDialect.MYSQL)
                // Record → POJO 映射由 SnakeRecordMapperProvider 统一完成
                .set(new SnakeRecordMapperProvider())
                // 慢 SQL 观测（替代原 MyBatis-Plus 的 InnerInterceptor）
                .set(new DefaultExecuteListenerProvider(new SlowQueryListener(slowQueryThresholdMs)),
                // N+1 查询告警：同构 SQL 在单请求内重复执行超阈值即告警，并向疑似事件留存登记
                     new DefaultExecuteListenerProvider(
                             new NPlusOneListener(nPlusOneProbe, nPlusOneSuspectRegistry)))
                .set(new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER));
        return org.jooq.impl.DSL.using(configuration);
    }
}
