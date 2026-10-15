package com.acme.scaffold.system.notification;

import java.util.Map;

/**
 * 对外 HTTP 回调的极简客户端契约，便于离线单测用 mock 替换真实实现。
 */
public interface WebhookClient {

    /** 向指定地址 POST 一个 JSON 报文。 */
    void post(String url, Map<String, Object> body);
}
