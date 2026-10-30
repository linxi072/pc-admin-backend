package com.acme.scaffold.security.online;

/**
 * 在线会话数据访问端口（防腐层）。
 *
 * <p>由 {@code security.online} 定义，system 侧提供适配器委托 {@code OnlineUserService}，
 * 消除 security 对 {@code system.service.OnlineUserService} 的直连
 * （B1/B5 同款解耦约束）。</p>
 */
public interface OnlineSessionDataPort {

    String REASON_LOGOUT = "LOGOUT";
    String REASON_FORCE_LOGOUT = "FORCE_LOGOUT";
    String REASON_RESTRICTED = "RESTRICTED";
    String REASON_INACTIVITY = "INACTIVITY";
    String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    /** 登录成功后登记在线会话（upsert）：同设备重复登录重置登录时间与会话状态。 */
    void registerSession(Long userId, String username, String displayName, String deviceId,
                         String clientId, String ip, String userAgent);

    /** 登出 / 改密：把该用户全部在线会话置离线，返回影响行数。 */
    int markOfflineByUser(Long userId, String reason);
}
