package com.acme.scaffold.system.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 实时用户目录适配器回归：强隔离下列表查询必须带当前租户条件。 */
class RealtimeUserDirectoryAdapterTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void enforcedTenantShouldScopeUserQuery() {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setEnforce(true);
        props.setDefaultTenantId(0L);
        TenantEnforcement.configure(props);
        TenantContext.set(7L, false);
        CapturingMapper mapper = new CapturingMapper();

        new RealtimeUserDirectoryAdapter(new DSLContext(mapper)).userIdsInTenant(7L);

        assertTrue(mapper.statements.stream().anyMatch(s ->
                s.rawSql().contains("tenant_id") && s.getBinds().contains(7L)));
    }

    @Test
    void disabledEnforcementShouldKeepPlatformView() {
        TenantEnforcement.configure(null);
        CapturingMapper mapper = new CapturingMapper();

        new RealtimeUserDirectoryAdapter(new DSLContext(mapper)).userIdsInTenant(0L);

        assertFalse(mapper.statements.stream().anyMatch(s -> s.rawSql().contains("tenant_id")));
    }

    private static final class CapturingMapper implements DynamicMapper {

        private final List<DynamicStatement> statements = new ArrayList<>();

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            statements.add(statement);
            return List.of();
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            statements.add(statement);
            return null;
        }

        @Override
        public int update(DynamicStatement statement) {
            statements.add(statement);
            return 1;
        }

        @Override
        public int insert(DynamicStatement statement) {
            statements.add(statement);
            return 1;
        }

        @Override
        public int delete(DynamicStatement statement) {
            statements.add(statement);
            return 1;
        }
    }
}
