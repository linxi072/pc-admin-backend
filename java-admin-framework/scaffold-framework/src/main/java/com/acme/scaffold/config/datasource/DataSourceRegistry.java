package com.acme.scaffold.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 持有全部具名 Hikari 数据源，供 {@link RoutingDataSource} 路由与按名查找使用。
 *
 * <p>构建阶段即完成连接池初始化（fail-fast）：缺失主数据源、或某数据源缺少 url 时
 * 在应用启动期抛异常，避免运行期才暴露配置错误。
 */
@Component
public class DataSourceRegistry {

    private final String primaryName;
    private final Map<String, DataSource> sources = new LinkedHashMap<>();

    public DataSourceRegistry(MultiDataSourceProperties properties) {
        this.primaryName = properties.getPrimary();
        if (!properties.getEntries().containsKey(primaryName)) {
            throw new IllegalStateException(
                    "多数据源配置缺失主数据源：app.datasources.primary=" + primaryName
                            + " 但在 entries 中未找到对应项");
        }
        properties.getEntries().forEach((name, p) -> sources.put(name, build(name, p)));
    }

    private HikariDataSource build(String name, MultiDataSourceProperties.DataSourceProperties p) {
        if (p.getUrl() == null || p.getUrl().isBlank()) {
            throw new IllegalStateException("数据源 [" + name + "] 缺少 url，"
                    + "请通过环境变量注入（约定：DB_" + name.toUpperCase().replace('-', '_') + "_URL）");
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("hikari-" + name);
        ds.setDriverClassName(p.getDriverClassName());
        ds.setJdbcUrl(p.getUrl());
        ds.setUsername(p.getUsername());
        ds.setPassword(p.getPassword());
        MultiDataSourceProperties.Pool pool = p.getPool();
        ds.setMinimumIdle(pool.getMinimumIdle());
        ds.setMaximumPoolSize(pool.getMaximumPoolSize());
        ds.setConnectionTimeout(pool.getConnectionTimeout());
        ds.setValidationTimeout(pool.getValidationTimeout());
        ds.setIdleTimeout(pool.getIdleTimeout());
        ds.setMaxLifetime(pool.getMaxLifetime());
        ds.setConnectionTestQuery(pool.getConnectionTestQuery());
        return ds;
    }

    /** 按名称获取具名数据源（未配置则抛异常）。 */
    public DataSource get(String name) {
        DataSource ds = sources.get(name);
        if (ds == null) {
            throw new IllegalArgumentException("未配置的数据源：" + name + "，已配置：" + sources.keySet());
        }
        return ds;
    }

    /** 主数据源（默认回落目标）。 */
    public DataSource primary() {
        return sources.get(primaryName);
    }

    public String primaryName() {
        return primaryName;
    }

    /** 全部具名数据源（供路由数据源设置 targetDataSources）。 */
    public Map<String, DataSource> all() {
        return sources;
    }

    public Collection<String> names() {
        return sources.keySet();
    }

    /** 应用关闭时释放所有连接池。 */
    public void destroy() {
        sources.values().forEach(ds -> {
            if (ds instanceof HikariDataSource hikari) {
                hikari.close();
            }
        });
    }
}
