package com.acme.scaffold.system.notification.unsubscribe;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.notification.unsubscribe.NotificationUnsubscribeService.UnsubscribeOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link NotificationUnsubscribeController} 契约测试：GET 查询串透传、响应包络结构、
 * 无效令牌的错误包络。
 *
 * <p>standalone MockMvc + mock Service + 最小 advice（复刻生产全局异常处理器对
 * {@link BusinessException} 的状态码映射，与 {@code ImportControllerTest} 同范式），
 * 使错误分支断言真实反映线上行为。端点匿名放行（安全白名单）由
 * {@code SecurityPublicApiTest} 反射守护；服务层验签/写偏好语义由
 * {@code NotificationUnsubscribeServiceTest} 覆盖。
 */
@DisplayName("通知退订接口：免登录 GET 契约")
class NotificationUnsubscribeControllerTest {

    private final NotificationUnsubscribeService service = mock(NotificationUnsubscribeService.class);

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new NotificationUnsubscribeController(service))
                .setControllerAdvice(new TestAdvice())
                .build();
    }

    @Test
    @DisplayName("退订成功：返回结果包络（渠道/类型/变更标志/文案）")
    void unsubscribe_returnsOutcomeEnvelope() throws Exception {
        when(service.unsubscribe(any())).thenReturn(
                new UnsubscribeOutcome("EMAIL", "NOTICE", true, "已为您退订该类型通知"));

        mvc().perform(get("/api/notification/unsubscribe").param("token", "v1.abc.sig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.msgType").value("NOTICE"))
                .andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.message").exists());

        verify(service).unsubscribe("v1.abc.sig");
    }

    @Test
    @DisplayName("重复点击：changed=false 幂等文案")
    void unsubscribe_repeatClick_reportsNoChange() throws Exception {
        when(service.unsubscribe(any())).thenReturn(
                new UnsubscribeOutcome("SMS", "ALERT", false, "您已退订该类型通知，无需重复操作"));

        mvc().perform(get("/api/notification/unsubscribe").param("token", "v1.abc.sig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));
    }

    @Test
    @DisplayName("无效令牌：400 + 统一错误包络，不返回成功体")
    void unsubscribe_invalidToken_returnsErrorEnvelope() throws Exception {
        when(service.unsubscribe(any())).thenThrow(new BusinessException(
                CommonErrorCode.VALIDATION_ERROR, "退订链接无效或已过期，请从最新通知邮件中重新获取"));

        mvc().perform(get("/api/notification/unsubscribe").param("token", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.VALIDATION_ERROR.code()));
    }

    @Test
    @DisplayName("缺少 token 查询串：参数绑定 400，不触达服务层")
    void unsubscribe_missingToken_paramRequired() throws Exception {
        mvc().perform(get("/api/notification/unsubscribe"))
                .andExpect(status().isBadRequest());
        verify(service, never()).unsubscribe(any());
    }

    /** 复刻生产全局异常处理器对 BusinessException → HTTP 状态码的映射。 */
    @RestControllerAdvice
    static class TestAdvice {

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().httpStatus())
                    .body(Result.error(ex.getErrorCode().code(), ex.getMessage()));
        }
    }
}
