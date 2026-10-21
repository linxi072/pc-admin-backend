package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.observability.controller.ObservabilityController;
import com.acme.scaffold.observability.dto.MetricView;
import com.acme.scaffold.observability.dto.SpanView;
import com.acme.scaffold.observability.dto.TraceSummaryView;
import com.acme.scaffold.observability.service.ObservabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可观测性管理端到端边界测试（离线可执行切片）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link ObservabilityController} 与<b>真实</b>
 * {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取 {@code i18n/messages*} 资源束），
 * 服务层以 Mockito 桩接。覆盖「HTTP 路由 → 控制器编排 → 服务调用 → 统一 {@link Result} 包络 /
 * 异常映射 / 输入校验」全链路，在无需 Collector 与 micrometer 后端的前提下对可观测性接口做契约级验证。</p>
 */
@DisplayName("可观测性管理端到端边界测试")
class ObservabilityE2eTest {

    private ObservabilityService observabilityService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        observabilityService = mock(ObservabilityService.class);
        ObservabilityController controller = new ObservabilityController(observabilityService);
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
    @DisplayName("OB-01 最近 span 列表 → 200 / code=0 / 视图字段（traceId/status/耗时毫秒/属性）正确")
    void traces_ok() throws Exception {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("http.method", "GET");
        attributes.put("http.status_code", "200");
        SpanView view = new SpanView("t-001", "s-001", null, "GET /api/users",
                "SERVER", "OK", null, 1_700_000_000_000L, 42L, attributes);
        when(observabilityService.recentSpans(any(), any(), any())).thenReturn(List.of(view));

        mvc.perform(get("/api/observability/traces").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].traceId").value("t-001"))
                .andExpect(jsonPath("$.data[0].name").value("GET /api/users"))
                .andExpect(jsonPath("$.data[0].status").value("OK"))
                .andExpect(jsonPath("$.data[0].durationMillis").value(42))
                .andExpect(jsonPath("$.data[0].attributes['http.status_code']").value("200"));
    }

    @Test
    @DisplayName("OB-02 按 traceId 检索 → 200 / code=0 / 同一追踪两条 span")
    void traceBy_ok() throws Exception {
        when(observabilityService.spansByTraceId("t-001")).thenReturn(List.of(
                new SpanView("t-001", "s-1", null, "GET /a", "SERVER", "OK", null, 1L, 5L, Map.of()),
                new SpanView("t-001", "s-2", null, "POST /b", "SERVER", "ERROR", "boom", 2L, 7L, Map.of())));

        mvc.perform(get("/api/observability/traces/t-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].statusMessage").value("boom"));
    }

    @Test
    @DisplayName("OB-03 运行态摘要 → 200 / code=0 / 双链路开关与留存统计")
    void summary_ok() throws Exception {
        when(observabilityService.summary()).thenReturn(
                new TraceSummaryView(true, 200, 12, 10L, 2L, 0L, false));

        mvc.perform(get("/api/observability/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.recorderEnabled").value(true))
                .andExpect(jsonPath("$.data.exporterEnabled").value(false))
                .andExpect(jsonPath("$.data.retained").value(12))
                .andExpect(jsonPath("$.data.okCount").value(10))
                .andExpect(jsonPath("$.data.errorCount").value(2));
    }

    @Test
    @DisplayName("OB-04 指标快照 → 200 / code=0 / name/type/value 正确")
    void metrics_ok() throws Exception {
        when(observabilityService.metricSnapshot(any())).thenReturn(List.of(
                new MetricView("app.observability.test.spans", "COUNTER", "span 总数", null, Map.of(), 1.0)));

        mvc.perform(get("/api/observability/metrics").param("prefix", "app.observability."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].name").value("app.observability.test.spans"))
                .andExpect(jsonPath("$.data[0].type").value("COUNTER"))
                .andExpect(jsonPath("$.data[0].value").value(1.0));
    }

    @Test
    @DisplayName("OB-05 limit=0 → 400 / COMMON_001（服务层输入校验，异常经 handler 映射）")
    void traces_limitOutOfRange_validation() throws Exception {
        when(observabilityService.recentSpans(anyInt(), any(), any()))
                .thenThrow(BusinessException.badRequest("limit 须在 1 ~ 200 之间，当前=0"));
        mvc.perform(get("/api/observability/traces").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("OB-06 清空本地留存 → 200 / code=0 / data=3（清除条数）")
    void clearTraces_ok() throws Exception {
        when(observabilityService.clearSpans()).thenReturn(3);
        mvc.perform(delete("/api/observability/traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(3));
    }
}
