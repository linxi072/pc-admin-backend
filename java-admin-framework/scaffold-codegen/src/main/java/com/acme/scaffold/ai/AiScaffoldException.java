package com.acme.scaffold.ai;

/**
 * AI 脚手架业务异常：参数校验失败、提示词超过成本门禁、或云大模型未配置/未实现/调用失败时抛出。
 */
public class AiScaffoldException extends RuntimeException {

    public AiScaffoldException(String message) {
        super(message);
    }

    public AiScaffoldException(String message, Throwable cause) {
        super(message, cause);
    }
}
