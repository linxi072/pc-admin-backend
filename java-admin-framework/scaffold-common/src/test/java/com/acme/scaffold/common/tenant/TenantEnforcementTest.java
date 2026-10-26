package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantEnforcement} 隔离裁决逻辑测试：覆盖 enforce 开关、作用域设置、跨租户旁路、平台维表四类条件，
 * 以及 {@link TenantEnforcement#validateConfiguration()} 启动护栏（enforce=true 时的配置自洽校验）。
 */
class TenantEnforcementTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
        TenantEnforcement.configure(null); // 复位快照，避免跨测试污染
    }

    @Test
    @DisplayName("enforce=false 时对任意表均不强制隔离")
    void disabledNoEnforcement() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(false);
        TenantEnforcement.configure(props);
        TenantContext.setTenantId(7);

        assertFalse(TenantEnforcement.isEnabled());
        assertFalse(TenantEnforcement.shouldEnforce("some_biz_table"));
    }

    @Test
    @DisplayName("enforce=true 且已设作用域且非平台表时强制隔离")
    void enabledEnforcesBusinessTable() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        TenantEnforcement.configure(props);
        TenantContext.setTenantId(7);

        assertTrue(TenantEnforcement.isEnabled());
        assertTrue(TenantEnforcement.shouldEnforce("some_biz_table"));
    }

    @Test
    @DisplayName("enforce=true 但作用域未设置时不强制隔离（避免无主体调用误判）")
    void noScopeNoEnforcement() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        TenantEnforcement.configure(props);
        TenantContext.clear();

        assertFalse(TenantEnforcement.shouldEnforce("some_biz_table"));
    }

    @Test
    @DisplayName("enforce=true 但处于跨租户旁路时不强制隔离")
    void bypassNoEnforcement() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        TenantEnforcement.configure(props);
        TenantContext.setTenantId(7);

        boolean inside = TenantContext.runWithBypass(() -> {
            assertFalse(TenantEnforcement.shouldEnforce("some_biz_table"), "旁路内不应隔离");
            return true;
        });
        assertTrue(inside);
    }

    @Test
    @DisplayName("enforce=true 时平台维表（platform-tables）豁免隔离")
    void platformTableExempt() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setPlatformTables(List.of("sys_tenant", "sys_menu"));
        TenantEnforcement.configure(props);
        TenantContext.setTenantId(7);

        assertFalse(TenantEnforcement.shouldEnforce("sys_tenant"));
        assertFalse(TenantEnforcement.shouldEnforce("sys_menu"));
        assertTrue(TenantEnforcement.shouldEnforce("some_biz_table"));
    }

    @Test
    @DisplayName("未配置属性（null 快照）时不应强制隔离")
    void nullConfigNoEnforcement() {
        TenantEnforcement.configure(null);
        TenantContext.setTenantId(7);
        assertFalse(TenantEnforcement.isEnabled());
        assertFalse(TenantEnforcement.shouldEnforce("some_biz_table"));
    }

    // ------------------------------------------------------------------
    // validateConfiguration() 启动护栏
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enforce=false 时配置即使非法，validateConfiguration 也直接放行（不阻断既有单租户部署）")
    void disabledValidatePassesEvenWithInvalidConfig() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(false);
        props.setDefaultTenantId(null);
        props.setPlatformTables(List.of());
        TenantEnforcement.configure(props);

        assertFalse(TenantEnforcement.isEnabled());
        assertDoesNotThrow(TenantEnforcement::validateConfiguration);
    }

    @Test
    @DisplayName("enforce=true 且配置自洽（默认租户合法、平台维表非空）时 validateConfiguration 通过")
    void enabledValidConfigPasses() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);
        TenantEnforcement.configure(props);

        assertTrue(TenantEnforcement.isEnabled());
        assertDoesNotThrow(TenantEnforcement::validateConfiguration);
    }

    @Test
    @DisplayName("enforce=true 但 defaultTenantId 为 null 时 validateConfiguration 抛 IllegalStateException")
    void enabledNullDefaultTenantIdThrows() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(null);
        TenantEnforcement.configure(props);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                TenantEnforcement::validateConfiguration);
        assertTrue(ex.getMessage().contains("defaultTenantId"),
                "异常信息应指明 defaultTenantId 非法，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("enforce=true 但 platform-tables 为空时 validateConfiguration 抛 IllegalStateException")
    void enabledEmptyPlatformTablesThrows() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);
        props.setPlatformTables(List.of());
        TenantEnforcement.configure(props);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                TenantEnforcement::validateConfiguration);
        assertTrue(ex.getMessage().contains("platform-tables"),
                "异常信息应指明 platform-tables 为空，实际：" + ex.getMessage());
    }
}
