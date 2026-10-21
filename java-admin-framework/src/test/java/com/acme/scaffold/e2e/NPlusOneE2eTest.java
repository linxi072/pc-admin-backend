package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.monitor.controller.NPlusOneController;
import com.acme.scaffold.monitor.dto.NPlusOneStatusView;
import com.acme.scaffold.monitor.dto.NPlusOneSuspectView;
import com.acme.scaffold.monitor.service.NPlusOneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N+1 查询探测端到端边界测试（离线可执行切片）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link NPlusOneController} 与<b>真实</b>
 * {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取 {@code i18n/messages*} 资源束），
 * 服务层以 Mockito 桩接。覆盖「HTTP 路由 → 控制器编排 → 服务调用 → 统一 {@link Result} 包络 /
 * 异常映射 / 输入校验」全链路，在无需数据库执行的前提下对探测查询与清理接口做契约级验证。</p>
 */
@DisplayName("N+1 查询探测端到端边界测试")
class NPlusOneE2eTest {

    private NPlusOneService nPlusOneService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        nPlusOneService = mock(NPlusOneService.class);
        NPlusOneController controller = new NPlusOneController(nPlusOneService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(i18n()))
                .build();
    }

    private I18nService i18n() {
        ReloadableResourceBundleMessageSource src = new ReloadableResourceBundleMessageSource();
        src.setBasenames("classpath:i18n/messages", "classpath:i18n/validation");
        src.setDefaultEncoding("UTF-8");
        src.setFallbackToSystemLocale(false);
        src.setCacheSeconds(-1);
        return new I18nService(src);
    }

    @Test
    @DisplayName("NP-01 疑似事件列表 → 200 / code=0 / 最新在前")
    void suspects_ok() throws Exception {
        NPlusOneSuspectView view = new NPlusOneSuspectView(
                "select * from sys_user where id=?", "select * from sys_user where id = 7", 12, "http-nio-1", 1_700_000_000_000L);
        when(nPlusOneService.recentSuspects(anyInt())).thenReturn(List.of(view));
        mvc.perform(get("/api/system/monitor/n-plus-one/suspects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].fingerprint").value("select * from sys_user where id=?"))
                .andExpect(jsonPath("$.data[0].count").value(12))
                .andExpect(jsonPath("$.data[0].threadName").value("http-nio-1"));
    }

    @Test
    @DisplayName("NP-02 探测运行态 → 200 / code=0 / 阈值与累计口径正确")
    void status_ok() throws Exception {
        when(nPlusOneService.status()).thenReturn(
                new NPlusOneStatusView(10, 200, 3, 42L, 1_700_000_000_123L));
        mvc.perform(get("/api/system/monitor/n-plus-one/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.threshold").value(10))
                .andExpect(jsonPath("$.data.capacity").value(200))
                .andExpect(jsonPath("$.data.registrySize").value(3))
                .andExpect(jsonPath("$.data.totalBreaches").value(42));
    }

    @Test
    @DisplayName("NP-03 limit 越界 → 400 / COMMON_001（服务层输入校验，异常经 handler 映射）")
    void suspects_invalidLimit() throws Exception {
        // 服务层 validateLimit 对越界 limit 抛 BusinessException(VALIDATION_ERROR)，
        // 经 GlobalExceptionHandler 映射为 400 / COMMON_001
        when(nPlusOneService.recentSuspects(0)).thenThrow(BusinessException.badRequest("查询条数需在 1~200 之间"));
        mvc.perform(get("/api/system/monitor/n-plus-one/suspects").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("NP-04 清空留存 → 200 / code=0 / data=true")
    void clear_ok() throws Exception {
        when(nPlusOneService.clear()).thenReturn(true);
        mvc.perform(delete("/api/system/monitor/n-plus-one/suspects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("NP-05 limit 缺省 → 200 / code=0 / 默认按 50 条查询")
    void suspects_defaultLimit() throws Exception {
        when(nPlusOneService.recentSuspects(50)).thenReturn(List.of());
        mvc.perform(get("/api/system/monitor/n-plus-one/suspects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
        verify(nPlusOneService).recentSuspects(50);
    }
}
