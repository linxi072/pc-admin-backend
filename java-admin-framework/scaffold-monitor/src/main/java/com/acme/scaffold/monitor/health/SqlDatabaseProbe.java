package com.acme.scaffold.monitor.health;

import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 基于 jOOQ 的数据库可用性探测：执行 {@code SELECT 1} 级轻量语句，捕获异常即视为不可用。
 * 仅探测「数据库可连通」这一提供服务必需的依赖，不涉及业务表。
 */
@Component
public class SqlDatabaseProbe implements DatabaseProbe {

    private final DSLContext dsl;

    public SqlDatabaseProbe(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean isAvailable() {
        try {
            return dsl.fetchExists(DSL.selectOne());
        } catch (Exception ex) {
            return false;
        }
    }
}
