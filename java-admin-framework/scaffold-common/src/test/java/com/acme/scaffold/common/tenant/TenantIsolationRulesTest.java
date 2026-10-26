package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantIsolationRules#buildStatus} 只读诊断视图装配测试：覆盖 enforce 开关、作用域回填、
 * 配置校验三态（OK/INVALID/DISABLED）以及「配置快照未注入」这类运行态与配置态分歧的诊断场景。
 */
class TenantIsolationRulesTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
        TenantEnforcement.configure(null); // 复位静态快照，避免跨测试污染
    }

    @Test
    @DisplayName("enforce=false（默认）时：强制隔离未生效，校验状态 DISABLED，无当前作用域")
    void disabledByDefault() {
        TenantProperties props = new TenantProperties(); // 默认 enforce=false
        TenantEnforcement.configure(props);

        TenantIsolationStatusView v = TenantIsolationRules.buildStatus(props);

        assertFalse(v.enforceEnabled());
        assertFalse(v.scopeSet());
        assertNull(v.currentTenantId());
        assertEquals(TenantIsolationRules.VALIDATION_DISABLED, v.validationStatus());
        assertTrue(v.validationMessage().contains("enforce=false"));
    }

    @Test
    @DisplayName("enforce=true 且配置自洽且已设平台管理员作用域：OK + 作用域回填")
    void enabledValidWithScope() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);
        TenantEnforcement.configure(props);
        TenantContext.set(7L, true); // tenantId=7, platformAdmin=true

        TenantIsolationStatusView v = TenantIsolationRules.buildStatus(props);

        assertTrue(v.enforceEnabled());
        assertTrue(v.configured());
        assertTrue(v.scopeSet());
        assertEquals(7L, v.currentTenantId());
        assertTrue(v.currentPlatformAdmin());
        assertFalse(v.currentBypass());
        assertEquals(TenantIsolationRules.VALIDATION_OK, v.validationStatus());
        assertTrue(v.validationMessage().contains("已生效"));
    }

    @Test
    @DisplayName("enforce=true 且配置自洽但未设作用域：OK + scopeSet=false/currentTenantId=null")
    void enabledValidNoScope() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);
        TenantEnforcement.configure(props);
        TenantContext.clear();

        TenantIsolationStatusView v = TenantIsolationRules.buildStatus(props);

        assertTrue(v.enforceEnabled());
        assertFalse(v.scopeSet());
        assertNull(v.currentTenantId());
        assertFalse(v.currentPlatformAdmin());
        assertEquals(TenantIsolationRules.VALIDATION_OK, v.validationStatus());
    }

    @Test
    @DisplayName("enforce=true 但 defaultTenantId=null：校验 INVALID 且消息含 defaultTenantId")
    void invalidDefaultTenantId() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(null);
        TenantEnforcement.configure(props);

        TenantIsolationStatusView v = TenantIsolationRules.buildStatus(props);

        assertEquals(TenantIsolationRules.VALIDATION_INVALID, v.validationStatus());
        assertTrue(v.validationMessage().contains("defaultTenantId"),
                "INVALID 消息应指明 defaultTenantId，实际：" + v.validationMessage());
    }

    @Test
    @DisplayName("enforce=true 但 platform-tables 为空：校验 INVALID 且消息含 platform-tables")
    void invalidEmptyPlatformTables() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);
        props.setPlatformTables(List.of());
        TenantEnforcement.configure(props);

        TenantIsolationStatusView v = TenantIsolationRules.buildStatus(props);

        assertEquals(TenantIsolationRules.VALIDATION_INVALID, v.validationStatus());
        assertTrue(v.validationMessage().contains("platform-tables"),
                "INVALID 消息应指明 platform-tables 为空，实际：" + v.validationMessage());
    }

    @Test
    @DisplayName("配置快照未注入（TenantEnforcement 为 null）：configured=false，即便 props 声明 enforce=true 运行态仍为关闭")
    void notConfiguredSnapshot() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true); // 注入的 bean 声称开启
        TenantEnforcement.configure(null); // 但启动期未注入快照

        TenantIsolationStatusView v = TenantIsolationRules.buildStatus(props);

        assertTrue(v.isolationEnabled()); // 配置态：声称开启
        assertFalse(v.configured());      // 运行态：快照未注入
        assertFalse(v.enforceEnabled());  // 运行态：强制隔离实际未生效
        assertEquals(TenantIsolationRules.VALIDATION_DISABLED, v.validationStatus());
    }
}
