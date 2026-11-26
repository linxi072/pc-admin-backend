package com.acme.scaffold.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 幂等记录写路径租户隔离回归：相同 key/scope 不跨租户覆盖状态、响应快照或在途记录。 */
class IdempotencyTenantScopeTest {

    private CapturingMapper mapper;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setEnforce(true);
        props.setDefaultTenantId(0L);
        TenantEnforcement.configure(props);
        TenantContext.set(7L, false);

        mapper = new CapturingMapper();
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new IdempotencyService(new DSLContext(mapper), new ObjectMapper(), transactionManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void markSuccessShouldScopeByTenant() {
        service.markSuccess("key", "scope", Map.of("ok", true));

        assertTrue(mapper.statements.stream().anyMatch(this::isTenantScoped));
    }

    @Test
    void markFailedShouldScopeByTenant() {
        service.markFailed("key", "scope", "E001", "boom");

        assertTrue(mapper.statements.stream().anyMatch(this::isTenantScoped));
    }

    @Test
    void releaseShouldScopeByTenant() {
        service.release("key", "scope");

        assertTrue(mapper.statements.stream().anyMatch(this::isTenantScoped));
    }

    @Test
    void disabledEnforcementShouldNotInjectTenantCondition() {
        TenantEnforcement.configure(null);
        mapper.statements.clear();

        service.markSuccess("key", "scope", Map.of("ok", true));

        assertFalse(mapper.statements.stream().anyMatch(this::isTenantScoped));
    }

    private boolean isTenantScoped(DynamicStatement statement) {
        return statement.rawSql().contains("tenant_id") && statement.getBinds().contains(7L);
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
