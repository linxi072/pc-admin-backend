package com.acme.scaffold.system.form;

import com.acme.scaffold.system.form.domain.FormDefinitionDO;
import com.acme.scaffold.system.form.dto.CreateFormDefinitionRequest;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.UpdateFormDefinitionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FormDefinitionController} 控制器测试（M22 深化）。
 *
 * <p>用 standalone MockMvc 直接驱动控制器，{@code FormDefinitionService} 以 mock 注入，
 * 固化「路由正确 + 委托到正确服务方法 + 响应包络 {@code $.code="0"}」三件事；校验/唯一性等
 * 业务语义由 {@link FormDefinitionServiceTest} 覆盖。离线可跑。</p>
 */
class FormDefinitionControllerTest {

    private static final String VALID_SCHEMA = "{\"version\":1,\"title\":\"t\",\"fields\":[{\"key\":\"a\",\"label\":\"A\",\"type\":\"INPUT\"}]}";

    private final FormDefinitionService service = mock(FormDefinitionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FormDefinitionController(service)).build();

    private static FormDefinitionView view(Long id, String code) {
        FormDefinitionDO d = new FormDefinitionDO();
        d.setId(id);
        d.setFormCode(code);
        d.setFormName("名称");
        d.setSchemaJson(VALID_SCHEMA);
        d.setTargetType("NONE");
        d.setEnabled(1);
        return FormDefinitionView.of(d);
    }

    @Test
    void list_delegatesToService() throws Exception {
        when(service.list()).thenReturn(List.of(view(1L, "a"), view(2L, "b")));
        mvc.perform(get("/api/system/form-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getById_delegatesToService() throws Exception {
        when(service.getById(1L)).thenReturn(view(1L, "a"));
        mvc.perform(get("/api/system/form-definitions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.formCode").value("a"));
    }

    @Test
    void create_delegatesAndReturnsId() throws Exception {
        when(service.create(any(CreateFormDefinitionRequest.class))).thenReturn(42L);
        mvc.perform(post("/api/system/form-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formCode\":\"a\",\"formName\":\"名称\",\"schemaJson\":" +
                                "\"{\\\"version\\\":1,\\\"fields\\\":[{\\\"key\\\":\\\"a\\\",\\\"label\\\":\\\"A\\\",\\\"type\\\":\\\"INPUT\\\"}]}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("42"));
        verify(service, times(1)).create(any(CreateFormDefinitionRequest.class));
    }

    @Test
    void update_delegatesToService() throws Exception {
        mvc.perform(put("/api/system/form-definitions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formCode\":\"a\",\"formName\":\"名称\",\"schemaJson\":" +
                                "\"{\\\"version\\\":1,\\\"fields\\\":[{\\\"key\\\":\\\"a\\\",\\\"label\\\":\\\"A\\\",\\\"type\\\":\\\"INPUT\\\"}]}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        verify(service, times(1)).update(eq(1L), any(UpdateFormDefinitionRequest.class));
    }

    @Test
    void remove_delegatesToService() throws Exception {
        mvc.perform(delete("/api/system/form-definitions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        verify(service, times(1)).remove(1L);
    }
}
