package com.acme.scaffold.common.exception;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.i18n.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GlobalExceptionHandler} 单测：锁定 D4（缺参 400 包络）与 D6（禁用特性 404 明确包络）。
 */
class GlobalExceptionHandlerTest {

    private final I18nService i18n = mock(I18nService.class);

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(i18n);

    @Test
    @DisplayName("D4：缺少必填请求参数时返回 400 + 校验失败包络，提示缺失参数名")
    void missingParameter_returnsValidationErrorEnvelope() throws NoSuchMethodException {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("dimension", "DrillDimension");
        when(i18n.getMessage(anyString())).thenReturn("请求参数校验失败");

        ResponseEntity<Result<Void>> response = handler.handleMissingParameter(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(CommonErrorCode.VALIDATION_ERROR.code(), response.getBody().code());
        assertTrue(response.getBody().message().contains("dimension"),
                "应提示缺失的参数名: " + response.getBody().message());
    }

    @Test
    @DisplayName("D6：禁用特性路径（/api/system/chaos）命中 404 时返回明确的功能未启用包络")
    void disabledChaosFeature_returnsFeatureDisabledEnvelope() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/system/chaos/explode");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/system/chaos/explode");

        ResponseEntity<Result<Void>> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(CommonErrorCode.FEATURE_DISABLED.code(), response.getBody().code());
        assertEquals(CommonErrorCode.FEATURE_DISABLED.message(), response.getBody().message());
    }

    @Test
    @DisplayName("D6：禁用特性路径（/api/system/config-center）同样返回功能未启用包络")
    void disabledConfigCenterFeature_returnsFeatureDisabledEnvelope() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/system/config-center/get");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/system/config-center/get");

        ResponseEntity<Result<Void>> response = handler.handleNotFound(ex, request);

        assertEquals(CommonErrorCode.FEATURE_DISABLED.code(), response.getBody().code());
    }

    @Test
    @DisplayName("普通 404 不受影响，仍返回 COMMON_404")
    void normalNotFound_returnsCommon404() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/unknown/route");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/unknown/route");

        ResponseEntity<Result<Void>> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(CommonErrorCode.NOT_FOUND.code(), response.getBody().code());
    }
}
