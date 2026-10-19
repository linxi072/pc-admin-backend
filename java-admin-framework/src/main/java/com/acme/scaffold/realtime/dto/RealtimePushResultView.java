package com.acme.scaffold.realtime.dto;

/**
 * 实时推送结果视图：成功触达的会话数（离线用户、已失效会话不计入）。
 */
public record RealtimePushResultView(int delivered) {
}
