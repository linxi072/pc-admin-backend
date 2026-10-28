package com.acme.scaffold.system.controller;

import com.acme.scaffold.system.dto.ConfigGroupVO;
import com.acme.scaffold.system.dto.ConfigVO;
import com.acme.scaffold.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数中心端点测试（M36）：分组聚合与热更新刷新。
 *
 * <p>standalone MockMvc + mock {@link ConfigService}，不加载 Spring Security / 审计拦截器，
 * 聚焦端点契约与编排逻辑。</p>
 */
class ConfigControllerTest {

    private final ConfigService configService = Mockito.mock(ConfigService.class);
    private final ConfigController controller = new ConfigController(configService);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void groups_returnsGroupedConfigs() throws Exception {
        ConfigVO vo = new ConfigVO(1L, "security.jwt.issuer", "JWT 签发者", "x", "x", "STRING", "r", 0, "ACTIVE");
        when(configService.listGrouped()).thenReturn(List.of(new ConfigGroupVO("security", "安全", List.of(vo))));

        mvc.perform(get("/api/system/configs/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].group").value("security"))
                .andExpect(jsonPath("$.data[0].label").value("安全"))
                .andExpect(jsonPath("$.data[0].items.length()").value(1))
                .andExpect(jsonPath("$.data[0].items[0].configKey").value("security.jwt.issuer"));
    }

    @Test
    void refresh_invokesServiceAndReturnsOk() throws Exception {
        mvc.perform(post("/api/system/configs/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        Mockito.verify(configService).forceRefresh();
    }
}
