package com.acme.scaffold.config.mybatis;

import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.ExecuteListener;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;

import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

/** 为具名数据源构建独立 DSLContext（多数据源显式按名访问路径）。 */
public final class MyBatisContextFactory {

    private MyBatisContextFactory() {
    }

    public static DSLContext build(DataSource dataSource, List<ExecuteListener> listeners) {
        try {
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
            bean.setDataSource(dataSource);
            org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
            cfg.setMapUnderscoreToCamelCase(false);
            cfg.setCallSettersOnNulls(true);
            cfg.setJdbcTypeForNull(JdbcType.NULL);
            bean.setConfiguration(cfg);
            SqlSessionFactory factory = bean.getObject();
            factory.getConfiguration().addMapper(DynamicMapper.class);
            SqlSessionTemplate template = new SqlSessionTemplate(factory);
            return new DSLContext(template.getMapper(DynamicMapper.class), listeners);
        } catch (Exception e) {
            throw new IllegalStateException("构建具名数据源的 MyBatis DSLContext 失败", e);
        }
    }
}
