package com.acme.scaffold.system.online.adapter;

import com.acme.scaffold.security.online.OnlineSessionDataPort;
import com.acme.scaffold.system.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link OnlineSessionDataPort} 的 system 侧适配器实现。
 *
 * <p>委托 {@code system.service.OnlineUserService} 完成在线会话登记 / 置离线，
 * 使 security 侧仅依赖 {@code security.online} 定义的抽象端口，不直接 import
 * {@code system.service}（B1/B5 同款解耦约束）。</p>
 */
@Component
@RequiredArgsConstructor
public class OnlineSessionDataAdapter implements OnlineSessionDataPort {

    private final OnlineUserService onlineUserService;

    @Override
    public void registerSession(Long userId, String username, String displayName, String deviceId,
                                String clientId, String ip, String userAgent) {
        onlineUserService.registerSession(userId, username, displayName, deviceId, clientId, ip, userAgent);
    }

    @Override
    public int markOfflineByUser(Long userId, String reason) {
        return onlineUserService.markOfflineByUser(userId, reason);
    }

    @Override
    public Boolean sessionState(Long userId, String deviceId) {
        return onlineUserService.sessionState(userId, deviceId);
    }

    @Override
    public void touch(Long userId, String deviceId) {
        onlineUserService.touch(userId, deviceId);
    }
}
