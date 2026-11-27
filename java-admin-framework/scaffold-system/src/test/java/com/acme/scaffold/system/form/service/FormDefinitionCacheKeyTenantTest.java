package com.acme.scaffold.system.form.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.form.domain.model.FormDefinition;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** 表单定义缓存键租户维度回归：同名表单编码不得跨租户命中缓存。 */
class FormDefinitionCacheKeyTenantTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void sameFormCodeInDifferentTenantsShouldUseDifferentCacheKeys() throws Exception {
        TenantEnforcement.configure(enforcingProps());
        FormDefinitionService service = new FormDefinitionService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.FORM_DEFINITION),
                    eq(FormDefinition.class), any(Condition.class))).thenReturn(enabledDefinition());

            TenantContext.set(1L, false);
            service.getEnabledByCode("LEAVE");
            TenantContext.set(2L, false);
            service.getEnabledByCode("LEAVE");

            assertEquals(Set.of("T1|LEAVE", "T2|LEAVE"), cacheOf(service).keySet());
        }
    }

    @Test
    void disabledEnforcementShouldUseFixedTenantPrefix() throws Exception {
        TenantEnforcement.configure(null);
        FormDefinitionService service = new FormDefinitionService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.FORM_DEFINITION),
                    eq(FormDefinition.class), any(Condition.class))).thenReturn(enabledDefinition());

            TenantContext.set(9L, false);
            service.getEnabledByCode("LEAVE");

            assertEquals(Set.of("T0|LEAVE"), cacheOf(service).keySet());
        }
    }

    @Test
    void cacheKeyShouldReflectCurrentTenant() throws Exception {
        TenantEnforcement.configure(enforcingProps());
        FormDefinitionService service = new FormDefinitionService(mock(DSLContext.class));

        TenantContext.set(7L, false);
        assertEquals("T7|LEAVE", cacheKey(service, "LEAVE"));
    }

    private static FormDefinition enabledDefinition() {
        FormDefinition d = new FormDefinition();
        d.setId(1L);
        d.setFormCode("LEAVE");
        d.setFormName("请假");
        d.setSchemaJson("{}");
        d.setTargetType("NONE");
        d.setEnabled(1);
        return d;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FormDefinition> cacheOf(FormDefinitionService service) throws Exception {
        Field field = FormDefinitionService.class.getDeclaredField("codeCache");
        field.setAccessible(true);
        return (Map<String, FormDefinition>) field.get(service);
    }

    private static String cacheKey(FormDefinitionService service, String formCode) throws Exception {
        var method = FormDefinitionService.class.getDeclaredMethod("cacheKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, formCode);
    }

    private static TenantProperties enforcingProps() {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setEnforce(true);
        props.setDefaultTenantId(0L);
        props.setPlatformTables(List.of("sys_menu", "sys_dict_data"));
        return props;
    }
}
