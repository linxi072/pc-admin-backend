package com.acme.scaffold.system.maskstrategy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.maskstrategy.domain.CreateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.domain.model.SysMaskStrategy;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** 脱敏策略缓存失效回归：任一写操作必须全量失效，平台租户变更也要清掉业务租户缓存。 */
class MaskStrategyCacheInvalidationTest {

    @Test
    void createShouldInvalidateAllTenantCaches() throws Exception {
        MaskStrategyService service = new MaskStrategyService(mock(DSLContext.class));
        seedCache(service, 0L, 7L);
        long before = service.cacheVersion();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), any(Condition.class))).thenReturn(null);
            service.create(new CreateMaskStrategyRequest(
                    "USER_MOBILE", "手机号", "sys_user.mobile", "MOBILE", "ALL", null, 0, true, null));
        }

        assertTrue(cacheOf(service).isEmpty(), "创建后所有租户缓存都应失效");
        assertEquals(before + 1, service.cacheVersion());
    }

    @Test
    void toggleShouldInvalidateOwnerTenantCache() throws Exception {
        MaskStrategyService service = new MaskStrategyService(mock(DSLContext.class));
        seedCache(service, 0L, 7L);
        long before = service.cacheVersion();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), eq(1L))).thenReturn(strategy(1L, 7L));
            service.toggle(1L, false);
        }

        assertTrue(cacheOf(service).isEmpty(), "停用后策略所属租户缓存必须失效");
        assertEquals(before + 1, service.cacheVersion());
    }

    @Test
    void platformStrategyChangeShouldInvalidateBusinessTenantCache() throws Exception {
        MaskStrategyService service = new MaskStrategyService(mock(DSLContext.class));
        // 业务租户缓存里含有继承自平台租户 0 的策略快照。
        seedCache(service, 7L);
        long before = service.cacheVersion();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), eq(9L))).thenReturn(strategy(9L, 0L));
            service.toggle(9L, false);
        }

        assertTrue(cacheOf(service).isEmpty(), "平台策略变更后业务租户缓存也必须失效");
        assertEquals(before + 1, service.cacheVersion());
    }

    private static void seedCache(MaskStrategyService service, Long... tenantIds) throws Exception {
        Map<Long, List<SysMaskStrategy>> cache = cacheOf(service);
        for (Long tenantId : tenantIds) {
            cache.put(tenantId, List.of(strategy(1L, tenantId)));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, List<SysMaskStrategy>> cacheOf(MaskStrategyService service) throws Exception {
        Field field = MaskStrategyService.class.getDeclaredField("activeCache");
        field.setAccessible(true);
        return (Map<Long, List<SysMaskStrategy>>) field.get(service);
    }

    private static SysMaskStrategy strategy(Long id, Long tenantId) {
        SysMaskStrategy s = new SysMaskStrategy();
        s.setId(id);
        s.setTenantId(tenantId);
        s.setStrategyCode("USER_MOBILE");
        s.setFieldKey("sys_user.mobile");
        s.setSensitiveType("MOBILE");
        s.setApplyScope("ALL");
        s.setEnabled(1);
        s.setPriority(0);
        return s;
    }
}
