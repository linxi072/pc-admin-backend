package com.acme.scaffold.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.common.api.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/** 全局异常映射回归测试：调用方媒体类型错误返回 4xx，不再误报为 500。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unsupportedContentTypeShouldReturn415() {
        ResponseEntity<Result<Void>> response = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("text/plain"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("COMMON_415", response.getBody().code());
        assertEquals("不支持的媒体类型，请检查 Content-Type", response.getBody().message());
    }

    @Test
    void notAcceptableHeaderShouldReturn406() {
        ResponseEntity<Result<Void>> response = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException("application/xml"));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertEquals("COMMON_406", response.getBody().code());
        assertEquals("无法生成可接受的响应内容，请检查 Accept", response.getBody().message());
    }

    @Test
    void unreadableBodyShouldStillReturn400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "bad json", new RuntimeException("parse error"), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Result<Void>> response = handler.handleUnreadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("COMMON_001", response.getBody().code());
        assertEquals("请求体格式错误", response.getBody().message());
    }

    @Test
    void unexpectedExceptionShouldStillReturn500() {
        ResponseEntity<Result<Void>> response = handler.handleUnexpected(
                new RuntimeException("boom"), new MockHttpServletRequest("POST", "/api/auth/login"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("COMMON_500", response.getBody().code());
        assertEquals("系统内部错误", response.getBody().message());
    }
}
