package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantIsolationRules;
import com.acme.scaffold.common.tenant.TenantIsolationStatusView;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.common.tenant.TenantRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantIsolationStatusController#status()} 只读诊断端点测试：直接实例化 Controller（无 Spring 上下文依赖），
 * 校验响应包络与态势视图装配结果。
 */
class TenantIsolationStatusControllerTest {

    @AfterEach
    void clear() {
        TenantEnforcement.configure(null);
    }

    @Test
    @DisplayName("status() 返回封装成功的态势视图，enforce 关闭时为 DISABLED")
    void statusReturnsViewWhenDisabled() {
        TenantProperties props = new TenantProperties();
        TenantEnforcement.configure(props); // enforce=false 默认

        TenantIsolationStatusController controller = new TenantIsolationStatusController(props);

        Result<TenantIsolationStatusView> result = controller.status();
        assertTrue(result.isSuccess());
        assertNotNull(result.data());
        assertEquals(TenantIsolationRules.VALIDATION_DISABLED, result.data().validationStatus());
    }

    @Test
    @DisplayName("status() 在 enforce 开启且配置自洽时返回 OK")
    void statusReturnsOkWhenEnabledValid() {
        TenantProperties props = new TenantProperties();
        props.setEnforce(true);
        props.setDefaultTenantId(TenantRules.PLATFORM_TENANT_ID);
        TenantEnforcement.configure(props);

        TenantIsolationStatusController controller = new TenantIsolationStatusController(props);

        Result<TenantIsolationStatusView> result = controller.status();
        assertTrue(result.isSuccess());
        assertNotNull(result.data());
        assertEquals(TenantIsolationRules.VALIDATION_OK, result.data().validationStatus());
        assertTrue(result.data().enforceEnabled());
    }
}
