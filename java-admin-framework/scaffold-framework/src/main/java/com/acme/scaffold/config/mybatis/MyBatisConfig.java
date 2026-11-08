package com.acme.scaffold.config.mybatis;

import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.monitor.detection.NPlusOneListener;
import com.acme.scaffold.monitor.detection.NPlusOneProbe;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.monitor.detection.SlowQueryListener;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.ExecuteListener;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * MyBatis 装配中心（替代原 JooqConfig）。
 *
 * <ul>
 *   <li>SqlSessionFactory / SqlSessionTemplate 接管连接与 Spring 声明式事务；</li>
 *   <li>{@link DynamicMapper} 以 @*Provider 动态 SQL 执行全部兼容层查询；</li>
 *   <li>Record → POJO 仍由 SnakeRecordMapper 完成（snake_case → camelCase）；</li>
 *   <li>慢 SQL 与 N+1 观测通过 ExecuteListener 挂载，行为与迁移前一致。</li>
 * </ul>
 */
@Configuration
public class MyBatisConfig {

    @Bean
    public NPlusOneProbe nPlusOneProbe(@Value("${app.mybatis.nplus1-threshold:10}") int threshold,
                                     @Value("${app.mybatis.nplus1-fail-on-threshold:false}") boolean failOnThreshold) {
        return new NPlusOneProbe(threshold, failOnThreshold);
    }

    @Bean
    public NPlusOneSuspectRegistry nPlusOneSuspectRegistry(
            @Value("${app.mybatis.nplus1-max-suspects:200}") int capacity) {
        return new NPlusOneSuspectRegistry(capacity);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        // 保持 snake_case 原样返回，SnakeRecordMapper 负责转 camelCase
        cfg.setMapUnderscoreToCamelCase(false);
        cfg.setCallSettersOnNulls(true);
        cfg.setJdbcTypeForNull(JdbcType.NULL);
        bean.setConfiguration(cfg);
        SqlSessionFactory factory = bean.getObject();
        factory.getConfiguration().addMapper(DynamicMapper.class);
        return factory;
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean
    public DSLContext dslContext(SqlSessionTemplate template,
                                 @Value("${app.mybatis.slow-query-threshold-ms:500}") long slowQueryThresholdMs,
                                 NPlusOneProbe nPlusOneProbe,
                                 NPlusOneSuspectRegistry suspectRegistry,
                                 TenantProperties tenantProperties) {
        TenantEnforcement.configure(tenantProperties);
        List<ExecuteListener> listeners = List.of(
                new SlowQueryListener(slowQueryThresholdMs),
                new NPlusOneListener(nPlusOneProbe, suspectRegistry));
        DynamicMapper mapper = template.getMapper(DynamicMapper.class);
        return new DSLContext(mapper, listeners);
    }
}
