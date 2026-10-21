package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.monitor.alert.AlertProperties;
import com.acme.scaffold.monitor.alert.AlertReceiverService;
import com.acme.scaffold.monitor.alert.AlertWebhookController;
import com.acme.scaffold.monitor.alert.controller.AlertInboxController;
import com.acme.scaffold.monitor.alert.service.AlertInboxService;
import com.acme.scaffold.monitor.dto.AlertInboxItemView;
import com.acme.scaffold.monitor.dto.AlertInboxStatsView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警收件箱管理端到端边界测试（离线可执行切片）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link AlertInboxController} 与<b>真实</b>
 * {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取 {@code i18n/messages*}
 * 资源束），服务层以 Mockito 桩接。覆盖「HTTP 路由 → 控制器编排 → 服务调用 → 统一 {@link Result}
 * 包络」全链路，在无需 Alertmanager 与运行时的前提下对收件箱查询/汇总/清空接口做契约级验证。</p>
 *
 * <p><b>分层口径</b>：筛选值归一、limit 钳制、汇总统计由 {@code AlertInboxRulesTest} 与
 * {@code AlertInboxServiceTest} 覆盖；本类只验证 HTTP 契约与参数透传（AI-02/AI-05 守护
 * 「控制器原样透传、归一在服务层」的分层，与幂等记录模块 ID-02/ID-06 同手法）。
 * AI-06 守护存量 webhook 读取端点在本轮安全硬化后仍保持 200 兼容。</p>
 */
@DisplayName("告警收件箱管理端到端边界测试")
class AlertInboxE2eTest {

    private AlertInboxService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AlertInboxService.class);
        AlertInboxController controller = new AlertInboxController(service);
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
    @DisplayName("AI-01 收件箱列表默认参数 → 200 / code=0 / 数组与视图字段正确")
    void list_defaults_ok() throws Exception {
        when(service.list(null, null, null)).thenReturn(List.of(
                new AlertInboxItemView("uuid-1", 1760972400000L, "firing", "HighErrorRate",
                        "critical", "error rate > 1%", "10.0.0.1:8080",
                        "2026-10-20T09:00:00Z", null)));

        mvc.perform(get("/api/monitoring/alerts/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("uuid-1"))
                .andExpect(jsonPath("$.data[0].receivedAtEpochMillis").value(1760972400000L))
                .andExpect(jsonPath("$.data[0].status").value("firing"))
                .andExpect(jsonPath("$.data[0].name").value("HighErrorRate"))
                .andExpect(jsonPath("$.data[0].severity").value("critical"))
                .andExpect(jsonPath("$.data[0].summary").value("error rate > 1%"));
    }

    @Test
    @DisplayName("AI-02 筛选参数原样透传服务层（归一/钳制在服务层完成，与 ID-02 同手法）")
    void list_filters_passedThrough() throws Exception {
        when(service.list(eq("critical"), eq("firing"), eq(50))).thenReturn(List.of());

        mvc.perform(get("/api/monitoring/alerts/inbox")
                        .param("severity", "critical")
                        .param("status", "firing")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        verify(service).list("critical", "firing", 50);
    }

    @Test
    @DisplayName("AI-03 汇总 → 200 / code=0 / 容量/分布/时间戳字段正确")
    void stats_ok() throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("critical", 2);
        counts.put("warning", 1);
        when(service.stats()).thenReturn(new AlertInboxStatsView(
                200, 3, 2, 1, counts, 1760972400000L, 1760968800000L));

        mvc.perform(get("/api/monitoring/alerts/inbox/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.capacity").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.firingCount").value(2))
                .andExpect(jsonPath("$.data.resolvedCount").value(1))
                .andExpect(jsonPath("$.data.severityCounts.critical").value(2))
                .andExpect(jsonPath("$.data.severityCounts.warning").value(1))
                .andExpect(jsonPath("$.data.mostRecentEpochMillis").value(1760972400000L))
                .andExpect(jsonPath("$.data.oldestRetainedEpochMillis").value(1760968800000L));
    }

    @Test
    @DisplayName("AI-04 清空收件箱 → 200 / code=0 / 返回清理条数并委托服务层")
    void clear_ok() throws Exception {
        when(service.clear()).thenReturn(5);

        mvc.perform(delete("/api/monitoring/alerts/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(5));

        verify(service).clear();
    }

    @Test
    @DisplayName("AI-05 limit 缺省不传 → 控制器传 null 交由服务层取默认值（分层守护）")
    void list_limitAbsent_passesNull() throws Exception {
        when(service.list(null, null, null)).thenReturn(List.of());

        mvc.perform(get("/api/monitoring/alerts/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(service).list(null, null, null);
    }

    @Test
    @DisplayName("AI-06 存量 webhook 读取端点安全硬化后仍 200 兼容（真实接收服务装配）")
    void legacyWebhookInbox_stillOk() throws Exception {
        AlertProperties props = new AlertProperties();
        AlertReceiverService receiver = new AlertReceiverService(props, event -> {
        });
        AlertWebhookController legacy = new AlertWebhookController(receiver, props);
        MockMvc legacyMvc = MockMvcBuilders.standaloneSetup(legacy).build();

        legacyMvc.perform(get("/api/monitoring/alerts/webhook/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("AI-07 服务层异常 → 经真实 GlobalExceptionHandler 映射为统一包络（不裸奔 500）")
    void list_serviceThrows_mappedByHandler() throws Exception {
        when(service.list(null, null, null))
                .thenThrow(new com.acme.scaffold.common.exception.BusinessException(
                        com.acme.scaffold.common.error.CommonErrorCode.INTERNAL_ERROR, "收件箱读取失败"));

        mvc.perform(get("/api/monitoring/alerts/inbox"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_500"));
    }
}
