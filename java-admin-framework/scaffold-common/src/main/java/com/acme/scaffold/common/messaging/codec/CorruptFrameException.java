package com.acme.scaffold.common.messaging.codec;

/**
 * 协议错误：帧损坏/截断/越界（§3.1 协议防御）。
 *
 * <p>采用非受检异常：协议错误在连接层统一转换为关闭码（4003），
 * 调用方（解码层 / 测试）按普通逻辑分支处理即可。
 */
public class CorruptFrameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CorruptFrameException(String message) {
        super(message);
    }

    public CorruptFrameException(String message, Throwable cause) {
        super(message, cause);
    }
}
