package com.acme.scaffold.common.idempotency;

import com.acme.scaffold.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 幂等性错误码。
 *
 * <p>HTTP 语义选择：
 * <ul>
 *   <li>409 CONFLICT——「请求正在处理中」，客户端应稍后重试而非换 key 重发；</li>
 *   <li>422 UNPROCESSABLE_ENTITY——同 key 但请求内容变了，属于客户端误用，重试不会成功；</li>
 *   <li>400 BAD_REQUEST——幂等键本身非法。</li>
 * </ul>
 */
public enum IdempotencyErrorCode implements ErrorCode {

    IN_PROGRESS("IDEMPOTENCY_001", "请求正在处理中，请稍后重试", HttpStatus.CONFLICT.value()),
    DUPLICATE("IDEMPOTENCY_002", "重复提交，请求已被幂等拦截", HttpStatus.CONFLICT.value()),
    FINGERPRINT_MISMATCH("IDEMPOTENCY_003", "幂等键已使用且请求内容不一致", HttpStatus.UNPROCESSABLE_ENTITY.value()),
    KEY_INVALID("IDEMPOTENCY_004", "Idempotency-Key 格式非法", HttpStatus.BAD_REQUEST.value());

    private final String code;
    private final String message;
    private final int httpStatus;

    IdempotencyErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
