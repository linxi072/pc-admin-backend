package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.system.dto.SaveRoleColumnPermissionRequest;
import com.acme.scaffold.system.service.RoleColumnPermissionService;
import com.acme.scaffold.system.service.RoleDataScopeService;
import com.acme.scaffold.system.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色字段权限端点（GET/PUT /api/system/roles/{id}/column-permissions）契约测试。
 * 采用 standalone MockMvc，聚焦于「端点 → 服务」的调用契约与响应封装，不做安全拦截（由集成测试覆盖）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("角色字段权限端点")
class RoleColumnPermissionControllerTest {

    @Mock
    private RoleService roleService;
    @Mock
    private RoleDataScopeService roleDataScopeService;
    @Mock
    private RoleColumnPermissionService roleColumnPermissionService;

    @InjectMocks
    private RoleController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET 返回角色已配置的字段权限码集合")
    void listColumnPermissions() throws Exception {
        Set<String> codes = new LinkedHashSet<>();
        codes.add("system:user:column:mobile");
        codes.add("system:user:column:email");
        when(roleColumnPermissionService.codes(42L)).thenReturn(codes);

        mockMvc.perform(get("/api/system/roles/42/column-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0]").value("system:user:column:mobile"))
                .andExpect(jsonPath("$.data[1]").value("system:user:column:email"));

        verify(roleColumnPermissionService).codes(42L);
    }

    @Test
    @DisplayName("PUT 以覆盖式保存角色字段权限码")
    void saveColumnPermissions() throws Exception {
        Set<String> codes = new LinkedHashSet<>();
        codes.add("system:user:column:mobile");

        mockMvc.perform(put("/api/system/roles/42/column-permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"system:user:column:mobile\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(roleColumnPermissionService).save(eq(42L), eq(codes));
    }

    @Test
    @DisplayName("PUT 空集合表示全部字段脱敏/隐藏（覆盖式清空）")
    void saveColumnPermissions_empty() throws Exception {
        mockMvc.perform(put("/api/system/roles/42/column-permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[]}"))
                .andExpect(status().isOk());

        verify(roleColumnPermissionService).save(eq(42L), eq(Set.of()));
    }
}
