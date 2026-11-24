package com.acme.scaffold.realtime.domain;

/**
 * 在线会话视图：userId 与其当前活跃 WebSocket 会话数（同一用户多端登录累加）。
 */
public record RealtimeSessionView(Long userId, int sessionCount) {
}
