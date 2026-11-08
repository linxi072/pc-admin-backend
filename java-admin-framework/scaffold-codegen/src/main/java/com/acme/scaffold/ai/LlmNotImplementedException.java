package com.acme.scaffold.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 大模型能力未就绪异常（F5）：云模型接入未配置 / 未实现时抛出，映射 HTTP 501 Not Implemented。
 *
 * <p>与 {@link AiScaffoldException}（通用业务异常，默认 500）区分：本异常明确表达
 * 「能力尚未提供」而非「请求处理失败」，便于前端/网关据此返回可理解的未就绪提示，
 * 而非笼统 500。由 {@code AiExceptionAdvice} 以最高优先级拦截，确保不被全局兜底异常处理器改写为 500。</p>
 */
@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class LlmNotImplementedException extends AiScaffoldException {

    public LlmNotImplementedException(String message) {
        super(message);
    }
}
