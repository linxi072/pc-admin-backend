package com.acme.scaffold.security.token.port;

import java.time.LocalDateTime;

/**
 * 在线会话回收端口（消费方定义）：刷新令牌被回收后，需按同一截止时间将残留的
 * {@code sys_online_session} 行联动置离线，避免其长期以「空闲」身份残留在在线列表。
 *
 * <p>由 {@code system.service.OnlineUserService} 实现并经 Spring 注入，
 * 打破 {@code security.token} 与 {@code system.service} 的包级循环
 * （端口由消费方定义、实现方依赖，遵循项目既有 Port 模式）。
 */
public interface OnlineSessionReleasePort {

    /**
     * 将最后活跃时间早于截止时间的在线会话置为离线。
     *
     * @param inactiveBefore 截止时间（通常为 now - session-max-inactive）
     * @return 受影响行数
     */
    int releaseInactiveSessions(LocalDateTime inactiveBefore);
}
