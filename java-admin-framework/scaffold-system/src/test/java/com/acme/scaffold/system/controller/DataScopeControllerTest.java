package com.acme.scaffold.system.controller;

import com.acme.scaffold.security.permission.DataScopeResourceRegistry;
import com.acme.scaffold.system.service.OrgService;
import com.acme.scaffold.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据权限资源发现端点测试：校验 {@link DataScopeResourceRegistry} 能扫出已标注
 * {@code @DataScope} 的资源（system:user / system:org），且端点按约定返回。
 *
 * <p>用 standalone MockMvc 隔离 Spring Security（不跑鉴权拦截器），聚焦发现逻辑与契约。
 * 注册表通过 {@link DataScopeResourceRegistry#registerFromClass(Class)} 直接登记真实业务类，
 * 复刻启动期 {@code BeanPostProcessor} 的扫描行为。
 */
class DataScopeControllerTest {

    private MockMvc mvc() {
        DataScopeResourceRegistry registry = new DataScopeResourceRegistry();
        registry.registerFromClass(UserService.class);
        registry.registerFromClass(OrgService.class);
        return MockMvcBuilders.standaloneSetup(new DataScopeController(registry)).build();
    }

    @Test
    void resources_discoversAnnotatedScopes() throws Exception {
        mvc().perform(get("/api/system/data-scope/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].resourceCode").value("system:user"))
                .andExpect(jsonPath("$.data[0].resourceName").value("用户管理"))
                .andExpect(jsonPath("$.data[1].resourceCode").value("system:org"))
                .andExpect(jsonPath("$.data[1].resourceName").value("部门管理"));
    }
}
