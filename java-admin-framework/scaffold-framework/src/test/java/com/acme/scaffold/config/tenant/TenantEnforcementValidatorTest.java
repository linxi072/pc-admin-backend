package com.acme.scaffold.config.tenant;

import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.common.tenant.TenantRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantEnforcementValidator} 启动护栏行为测试：验证 enforce 关闭/开启且配置合法时启动放行，
 * 以及 enforce=true 但配置非法时 fail-fast 抛异常中止启动。
 */
class TenantEnforcementValidatorTest {

    @AfterEach
    void reset() {
        // 复位静态快照，避免跨测试污染 TenantEnforcement
        TenantEnforcement.configure(null);
    }

    private TenantEnforcementValidator validatorWith(TenantProperties props) {
        return new TenantEnforcementValidator(props);
    }

    @Test
    @DisplayName("enforce=false 时启动放行（info 路径，不阻断既有单租户部署）")
    void disabledStartupPasses() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(false);

        assertDoesNotThrow(() -> validatorWith(props).run());
    }

    @Test
    @DisplayName("enforce=true 且配置自洽时启动放行（warn 路径）")
    void enabledValidConfigStartupPasses() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);

        assertDoesNotThrow(() -> validatorWith(props).run());
    }

    @Test
    @DisplayName("enforce=true 但 defaultTenantId 为 null 时启动 fail-fast 抛异常")
    void enabledInvalidConfigStartupFails() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validatorWith(props).run());
        // 校验失败信息应被包装并向上传播
        assertTrue(ex.getMessage().contains("配置校验失败"));
    }
}
