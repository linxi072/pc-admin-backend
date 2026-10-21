package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.kafka.controller.KafkaMonitorController;
import com.acme.scaffold.kafka.dto.KafkaConsumerStatusView;
import com.acme.scaffold.kafka.dto.KafkaPoisonMessageView;
import com.acme.scaffold.kafka.service.KafkaMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kafka 有序消费监控与死信补偿端到端边界测试（离线可执行切片）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link KafkaMonitorController} 与<b>真实</b>
 * {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取 {@code i18n/messages*} 资源束），
 * 服务层以 Mockito 桩接。覆盖「HTTP 路由 → 控制器编排 → 服务调用 → 统一 {@link Result} 包络 /
 * 异常映射 / 输入校验」全链路，在无需 Kafka broker 的前提下对监控与补偿接口做契约级验证。</p>
 */
@DisplayName("Kafka 消费监控端到端边界测试")
class KafkaMonitorE2eTest {

    private KafkaMonitorService monitorService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        monitorService = mock(KafkaMonitorService.class);
        KafkaMonitorController controller = new KafkaMonitorController(monitorService);
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
    @DisplayName("KM-01 消费容器状态 → 200 / code=0 / enabled=true 且指标正确")
    void status_ok() throws Exception {
        when(monitorService.status()).thenReturn(
                new KafkaConsumerStatusView(true, true, 1024L, 7L, 3L, 2, 1L, 16));
        mvc.perform(get("/api/kafka/consumer/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.handledCount").value(1024))
                .andExpect(jsonPath("$.data.poisonPending").value(2));
    }

    @Test
    @DisplayName("KM-02 死信列表 → 200 / code=0 / 分页包络正确")
    void poison_list_ok() throws Exception {
        KafkaPoisonMessageView view = new KafkaPoisonMessageView(
                "pid-1", "order-topic", 0, 42L, "order-99", "{\"amount\":1}", 3, "java.lang.IllegalState", 1_700_000_000_000L);
        when(monitorService.listPoison(anyInt(), anyInt(), any()))
                .thenReturn(PageResult.of(1, 10, 1L, List.of(view)));
        mvc.perform(get("/api/kafka/consumer/poison"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value("pid-1"))
                .andExpect(jsonPath("$.data.records[0].topic").value("order-topic"));
    }

    @Test
    @DisplayName("KM-03 重试毒消息 → 200 / code=0 / data=true")
    void retry_ok() throws Exception {
        when(monitorService.retry("pid-1")).thenReturn(true);
        mvc.perform(post("/api/kafka/consumer/poison/pid-1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("KM-04 丢弃不存在的毒消息 → 200 / code=0 / data=false（未找到）")
    void discard_notFound() throws Exception {
        when(monitorService.discard("ghost")).thenReturn(false);
        mvc.perform(post("/api/kafka/consumer/poison/ghost/discard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("KM-05 空 ID 重试 → 400 / COMMON_001（服务层输入校验拦截，异常经 handler 映射）")
    void retry_blankId_validation() throws Exception {
        // 服务层 requireNonBlankId 对空 ID 抛 BusinessException(VALIDATION_ERROR)，
        // 经 GlobalExceptionHandler 映射为 400 / COMMON_001（空 ID 难以经 HTTP 路径变量传递，此处以桩接验证映射）
        when(monitorService.retry(any())).thenThrow(BusinessException.badRequest("毒消息 ID 不能为空"));
        mvc.perform(post("/api/kafka/consumer/poison/x/retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
