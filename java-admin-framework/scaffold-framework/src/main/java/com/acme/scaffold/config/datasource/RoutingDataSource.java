package com.acme.scaffold.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 基于 ThreadLocal 上下文的路由数据源。
 *
 * <p>每次获取连接时调用 {@link #determineCurrentLookupKey()} 解析当前数据源名称：
 * <ul>
 *   <li>返回具体名称 → 命中 targetDataSources 中对应的具名数据源；</li>
 *   <li>返回 null（未切换）→ 回落 defaultTargetDataSource（主库）；</li>
 * </ul>
 * 因此现有所有服务（注入 DSLContext / DataSource）在「不切换」时行为完全不变。
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
