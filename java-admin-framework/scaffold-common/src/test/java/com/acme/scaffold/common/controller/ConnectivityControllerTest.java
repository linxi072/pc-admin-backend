package com.acme.scaffold.common.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 前后端联通测试（后端侧）：验证统一响应体 {@link Result} 包络字段一致性、各错误码映射，
 * 以及多语言协商链路。
 *
 * <p>策略：以 standalone MockMvc 装配<b>真实</b> {@link ConnectivityController}（生产探针端点）
 * 与<b>真实</b> {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取
 * {@code i18n/messages*} 资源束，UTF-8、{@code fallbackToSystemLocale=false}），完全复刻线上
 * 「HTTP 请求 → 全局异常映射 → 统一包络」链路；另以测试内 {@link DiagnosticController}
 * 刻意抛出各类异常，逐一对齐 400/401/403/404/405/409/422/429/500/504 的状态码、错误码与本地化文案。</p>
 *
 * <p>多语言通过 {@code Accept-Language} 请求头驱动（与线上 {@code LocaleChangeInterceptor} /
 * {@code AcceptHeaderLocaleResolver} 协商机制一致），并辅以直接调用 handler 的确定性单测。</p>
 */
class ConnectivityControllerTest {

    private static final String ZH = "zh-CN";
    private static final String EN = "en-US";

    private I18nService i18n() {
        ReloadableResourceBundleMessageSource src = new ReloadableResourceBundleMessageSource();
        src.setBasenames("classpath:i18n/messages", "classpath:i18n/validation");
        src.setDefaultEncoding("UTF-8");
        src.setFallbackToSystemLocale(false);
        src.setCacheSeconds(-1);
        return new I18nService(src);
    }

    private GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(i18n());
    }

    private MockMvc mvc() {
        ConnectivityController ctrl = new ConnectivityController(i18n());
        return MockMvcBuilders.standaloneSetup(ctrl, new DiagnosticController())
                .setControllerAdvice(handler())
                .build();
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ---------- 正常请求 / 响应流程 + 包络字段一致性 ----------

    @Test
    @DisplayName("ping 返回成功包络，字段齐备且与前端契约一致（默认 zh-CN）")
    void ping_returnsSuccessEnvelopeWithConsistentFields() throws Exception {
        mvc().perform(get("/api/connectivity/ping")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.serverTime").exists())
                .andExpect(jsonPath("$.data.receivedLocale").value(ZH))
                .andExpect(jsonPath("$.data.nonce").value(Matchers.not(Matchers.emptyString())))
                .andExpect(jsonPath("$.data.framework").value(ConnectivityController.FRAMEWORK))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("ping 回显 en-US 语言标签，验证多语言协商链路生效")
    void ping_echoesEnUsLocale() throws Exception {
        mvc().perform(get("/api/connectivity/ping")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, EN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.receivedLocale").value(EN));
    }

    @Test
    @DisplayName("ping nonce 每次请求不同，避免前端误判缓存联通")
    void ping_nonceDiffersAcrossCalls() throws Exception {
        String n1 = mvc().perform(get("/api/connectivity/ping"))
                .andReturn().getResponse().getContentAsString();
        String n2 = mvc().perform(get("/api/connectivity/ping"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(n1, n2, "两次 ping 响应应不同（nonce 随机）");
    }

    // ---------- 常见错误码与异常场景 ----------

    @Test
    @DisplayName("参数校验异常 → 400 / COMMON_001，data 为字段错误 Map")
    void validation_error_mapsTo400() throws Exception {
        mvc().perform(get("/api/connectivity/diag/validation")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.username").value("must not be blank"));
    }

    @Test
    @DisplayName("未认证异常 → 401 / COMMON_401")
    void unauthorized_mapsTo401() throws Exception {
        expectError("/api/connectivity/diag/unauthorized", HttpStatus.UNAUTHORIZED, "COMMON_401", "未认证或登录已失效");
    }

    @Test
    @DisplayName("无权限异常 → 403 / COMMON_403")
    void forbidden_mapsTo403() throws Exception {
        expectError("/api/connectivity/diag/forbidden", HttpStatus.FORBIDDEN, "COMMON_403", "无权限访问该资源");
    }

    @Test
    @DisplayName("资源不存在 → 404 / COMMON_404")
    void notFound_mapsTo404() throws Exception {
        expectError("/api/connectivity/diag/notfound", HttpStatus.NOT_FOUND, "COMMON_404", "资源不存在");
    }

    @Test
    @DisplayName("数据冲突 → 409 / COMMON_409")
    void conflict_mapsTo409() throws Exception {
        expectError("/api/connectivity/diag/conflict", HttpStatus.CONFLICT, "COMMON_409", "数据冲突，请刷新后重试");
    }

    @Test
    @DisplayName("请求方法不被允许 → 405 / COMMON_405")
    void methodNotSupported_mapsTo405() throws Exception {
        // 端点仅声明 GET，用 POST 触发 HttpRequestMethodNotSupportedException
        mvc().perform(post("/api/connectivity/diag/method")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_405"))
                .andExpect(jsonPath("$.message").value("请求方法不被允许"));
    }

    @Test
    @DisplayName("请求体不可读 → 400 / COMMON_001")
    void unreadable_mapsTo400() throws Exception {
        mvc().perform(get("/api/connectivity/diag/unreadable")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @DisplayName("业务异常 → 422 / COMMON_422（默认构造走资源束键化）")
    void business_mapsTo422() throws Exception {
        mvc().perform(get("/api/connectivity/diag/business")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMON_422"))
                .andExpect(jsonPath("$.message").value("业务处理失败"));
    }

    @Test
    @DisplayName("未预期异常 → 500 / COMMON_500")
    void internal_mapsTo500() throws Exception {
        expectError("/api/connectivity/diag/internal", HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "系统内部错误");
    }

    @Test
    @DisplayName("网络超时异常 → 504 / COMMON_504（新增映射）")
    void timeout_mapsTo504() throws Exception {
        mvc().perform(get("/api/connectivity/diag/timeout")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("COMMON_504"))
                .andExpect(jsonPath("$.message").value("请求超时，请稍后重试"));
    }

    // ---------- 错误码多语言本地化 ----------

    @Test
    @DisplayName("错误文案按 Accept-Language 本地化：en-US 返回英文文案")
    void error_message_localizedToEnUs() throws Exception {
        mvc().perform(get("/api/connectivity/diag/timeout")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, EN))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("COMMON_504"))
                .andExpect(jsonPath("$.message").value("Request timed out, please retry later"));
    }

    @Test
    @DisplayName("handler 直接调用：超时映射 504 且按 Locale 解析（确定性单测）")
    void timeout_handler_resolvesByLocale() {
        GlobalExceptionHandler h = handler();

        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        ResponseEntity<Result<Void>> zh = h.handleTimeout(new TimeoutException("slow"));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, zh.getStatusCode());
        assertEquals("COMMON_504", zh.getBody().code());
        assertEquals("请求超时，请稍后重试", zh.getBody().message());

        LocaleContextHolder.setLocale(Locale.US);
        ResponseEntity<Result<Void>> en = h.handleTimeout(new TimeoutException("slow"));
        assertEquals("Request timed out, please retry later", en.getBody().message());
    }

    // ---------- 超时探针端点（真实 504 链路闭环） ----------

    @Test
    @DisplayName("slow 延迟解析（纯函数）：null/空白 -> 默认 5000ms，须大于探针默认超时阈值")
    void resolveDelayMs_defaultsToFiveSeconds() {
        assertEquals(5000, ConnectivityController.DEFAULT_DELAY_MS,
                "默认延迟必须大于 connectivity-spec.json 的 2000ms 阈值，否则默认请求无法触发超时");
        assertEquals(ConnectivityController.DEFAULT_DELAY_MS, ConnectivityController.resolveDelayMs(null));
        assertEquals(ConnectivityController.DEFAULT_DELAY_MS, ConnectivityController.resolveDelayMs("   "));
    }

    @Test
    @DisplayName("slow 延迟解析（纯函数）：整数按 [0,10000] 收敛")
    void resolveDelayMs_clampsToBounds() {
        assertEquals(0, ConnectivityController.resolveDelayMs("-5"), "负值应归零");
        assertEquals(0, ConnectivityController.resolveDelayMs("0"));
        assertEquals(1500, ConnectivityController.resolveDelayMs("1500"));
        assertEquals(10000, ConnectivityController.resolveDelayMs("999999"), "超限应取 MAX_DELAY_MS 上限");
        assertEquals(10000, ConnectivityController.MAX_DELAY_MS);
    }

    @Test
    @DisplayName("slow 非数字参数 → 400 / COMMON_001（经真实 handler 映射，防线程耗尽型滥用）")
    void slow_invalidDelayMapsTo400() throws Exception {
        mvc().perform(get("/api/connectivity/slow").param("delayMs", "abc")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message").value("delayMs 必须为 0-10000 之间的整数（毫秒）"));
    }

    @Test
    @DisplayName("slow delayMs=0 立即返回成功包络并回显生效延迟（快路径，不拖慢测试套件）")
    void slow_zeroDelayReturnsEnvelopeImmediately() throws Exception {
        mvc().perform(get("/api/connectivity/slow").param("delayMs", "0")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.delayMs").value(0));
    }

    // ---------- 辅助 ----------

    private void expectError(String uri, HttpStatus http, String code, String message) throws Exception {
        mvc().perform(get(uri).header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().is(http.value()))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    /**
     * 测试内诊断端点：刻意抛出各类异常，供 standalone MockMvc 经真实 {@link GlobalExceptionHandler}
     * 映射后断言状态码/错误码/文案。仅存在于测试类，不进入生产装配。
     */
    @RestController
    @RequestMapping("/api/connectivity/diag")
    static class DiagnosticController {

        @GetMapping("/validation")
        void validation() throws Exception {
            BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "payload");
            br.addError(new FieldError("payload", "username", "must not be blank"));
            throw new MethodArgumentNotValidException(null, br);
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new BadCredentialsException("no auth");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/notfound")
        void notFound() throws Exception {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/connectivity/diag/notfound");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("dup");
        }

        @GetMapping("/method")
        void method() {
            // 仅 GET；测试侧以 POST 触发 405
        }

        @GetMapping("/unreadable")
        void unreadable() {
            throw new HttpMessageNotReadableException("malformed body");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR);
        }

        @GetMapping("/internal")
        void internal() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/timeout")
        void timeout() throws Exception {
            throw new TimeoutException("slow downstream");
        }
    }
}
