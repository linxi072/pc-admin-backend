package com.acme.scaffold.system.controller;

import com.acme.scaffold.security.permission.ColumnPermissionResourceRegistry;
import com.acme.scaffold.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字段级数据权限资源发现端点测试：校验 {@link ColumnPermissionResourceRegistry} 能扫出已标注
 * {@code @ColumnPermission} 的资源（system:user 含 mobile/email 字段），且端点按约定返回。
 *
 * <p>用 standalone MockMvc 隔离 Spring Security（不跑鉴权拦截器），聚焦发现逻辑与契约。
 * 注册表通过 {@link ColumnPermissionResourceRegistry#registerFromClass(Class)} 直接登记真实业务类，
 * 复刻启动期 {@code BeanPostProcessor} 的扫描行为。</p>
 */
class ColumnPermissionControllerTest {

    private MockMvc mvc() {
        ColumnPermissionResourceRegistry registry = new ColumnPermissionResourceRegistry();
        registry.registerFromClass(UserService.class);
        return MockMvcBuilders.standaloneSetup(new ColumnPermissionController(registry)).build();
    }

    @Test
    void resources_discoversUserColumnPermission() throws Exception {
        mvc().perform(get("/api/system/column-permissions/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].resourceCode").value("system:user"))
                .andExpect(jsonPath("$.data[0].resourceName").value("用户管理"))
                .andExpect(jsonPath("$.data[0].fields.length()").value(2))
                .andExpect(jsonPath("$.data[0].fields[0].field").value("mobile"))
                .andExpect(jsonPath("$.data[0].fields[0].label").value("手机号"))
                .andExpect(jsonPath("$.data[0].fields[0].strategy").value("MASK"))
                .andExpect(jsonPath("$.data[0].fields[0].sensitiveType").value("MOBILE"))
                .andExpect(jsonPath("$.data[0].fields[1].field").value("email"))
                .andExpect(jsonPath("$.data[0].fields[1].sensitiveType").value("EMAIL"));
    }
}
