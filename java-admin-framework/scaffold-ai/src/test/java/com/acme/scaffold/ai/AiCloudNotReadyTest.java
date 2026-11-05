package com.acme.scaffold.ai;

import com.acme.scaffold.ai.config.AiProperties;
import com.acme.scaffold.ai.controller.AiScaffoldController;
import com.acme.scaffold.ai.port.CloudLlmProvider;
import com.acme.scaffold.ai.port.HeuristicLlmProvider;
import com.acme.scaffold.ai.service.AiScaffoldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F5 云模型「能力未就绪」语义的独立集成测试（Docker-free，surefire 可执行）。
 *
 * <p>覆盖端口与编排层、异常建议、以及 HTTP 层：云 provider 装配但未配置/未实现接入时，
 * 必须显式表达 501，不得静默回退启发式，也不得被全局兜底异常处理器改写为笼统 500。</p>
 */
class AiCloudNotReadyTest {

    private static AiProperties props() {
        AiProperties p = new AiProperties();
        p.setMaxPromptTokens(2000);
        p.setAuthor("tester");
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<CloudLlmProvider> cloudAvailable(CloudLlmProvider cloud) {
        ObjectProvider<CloudLlmProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cloud);
        return provider;
    }

    private static AiScaffoldService serviceWithCloud(CloudLlmProvider cloud) {
        return new AiScaffoldService(new HeuristicLlmProvider(), cloudAvailable(cloud), props());
    }

    @Test
    void cloudProviderWithoutEndpointThrowsExplicit501() {
        // 直接 new 时 @Value 未注入，endpoint 为 null，等价于「开启了云开关但没配地址」
        CloudLlmProvider cloud = new CloudLlmProvider();

        LlmNotImplementedException ex = assertThrows(LlmNotImplementedException.class,
                () -> cloud.parse("订单表含金额"));

        assertTrue(ex.getMessage().contains("ai.llm.endpoint"), "未配置端点须给出可操作提示");
        // 单参数简写设置的是 value；须经 @AliasFor 合并后读取 code，才是 Spring 运行时的真实语义
        ResponseStatus rs = AnnotatedElementUtils.findMergedAnnotation(
                LlmNotImplementedException.class, ResponseStatus.class);
        assertNotNull(rs, "异常类型须显式标注响应状态");
        assertEquals(HttpStatus.NOT_IMPLEMENTED, rs.code(), "须为 501 而非默认 500");
    }

    @Test
    void cloudProviderWithEndpointButPlaceholderStillThrows501() {
        CloudLlmProvider cloud = new CloudLlmProvider();
        ReflectionTestUtils.setField(cloud, "endpoint", "https://llm.example.com/v1/generate");

        LlmNotImplementedException ex = assertThrows(LlmNotImplementedException.class,
                () -> cloud.parse("订单表含金额"));

        assertTrue(ex.getMessage().contains("https://llm.example.com/v1/generate"),
                "占位接线须回显当前 endpoint 便于定位");
        assertTrue(ex.getMessage().contains("尚未实现"), "须明确表达能力未就绪");
    }

    @Test
    void adviceReturnsStructured501AndHasHighestPrecedence() {
        ResponseEntity<Map<String, Object>> response = new AiExceptionAdvice()
                .handleLlmNotImplemented(new LlmNotImplementedException("云能力未就绪"));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("LLM_NOT_IMPLEMENTED", body.get("error"));
        assertEquals("云能力未就绪", body.get("message"));
        assertEquals(501, body.get("status"));

        Order order = AiExceptionAdvice.class.getAnnotation(Order.class);
        assertNotNull(order, "必须以最高优先级装配，避免被全局 catch-all 抢先");
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
        RestControllerAdvice advice = AiExceptionAdvice.class.getAnnotation(RestControllerAdvice.class);
        assertNotNull(advice);
        assertArrayEquals(new String[]{"com.acme.scaffold.ai"}, advice.basePackages(),
                "建议应限定在 ai 模块，不干扰其他模块的异常编排");
    }

    @Test
    void scaffoldServiceDoesNotSilentlyFallbackWhenCloudUnavailable() {
        // 云 provider 一旦装配即被优选；不可用时应显式抛出，而不是悄悄退回启发式产出「看似成功」的草稿
        AiScaffoldService service = serviceWithCloud(new CloudLlmProvider());

        LlmNotImplementedException ex = assertThrows(LlmNotImplementedException.class,
                () -> service.generate("订单表含金额", "order"));

        assertTrue(ex.getMessage().contains("云大模型"));
    }

    @Test
    void httpLayerReturns501EvenWhenCatchAllAdviceRegisteredFirst() throws Exception {
        AiScaffoldService service = serviceWithCloud(new CloudLlmProvider());
        // 故意把全局兜底 advice 放在前面注册，验证 @Order(HIGHEST_PRECEDENCE) 确实生效
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new AiScaffoldController(service))
                .setControllerAdvice(new CatchAllAdvice(), new AiExceptionAdvice())
                .build();

        mvc.perform(post("/api/ai/scaffold/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"订单表含金额\",\"moduleName\":\"order\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("LLM_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.message").exists());
    }

    /** 模拟 scaffold-common 的全局兜底处理器，用于验证 501 不会被改写为 500。 */
    @Order(Ordered.LOWEST_PRECEDENCE)
    @RestControllerAdvice
    static class CatchAllAdvice {

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handle(Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "status", 500));
        }
    }
}
