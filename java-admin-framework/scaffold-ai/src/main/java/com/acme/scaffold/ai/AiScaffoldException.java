package com.acme.scaffold.ai;

/**
 * AI 脚手架业务异常：参数校验失败、提示词超过成本门禁、或云大模型未配置/未实现时抛出。
 */
public class AiScaffoldException extends RuntimeException {

    public AiScaffoldException(String message) {
        super(message);
    }
}
