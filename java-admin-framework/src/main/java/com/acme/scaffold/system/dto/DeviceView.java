package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 登录设备视图（账号安全）。
 * <p>数据来源为 sys_refresh_token：每条未过期、未吊销的记录代表一次活跃登录会话，
 * 因此「设备」本质是登录会话，不额外建表。
 *
 * @param sessionId 会话标识：<b>前端回传此值用于下线操作</b>。不用自增 id 也不暴露 token_hash，
 *                  避免泄露可推断的信息
 * @param deviceId  设备标识：与 sessionId 同值（一次登录 = 一个设备条目），
 *                  保留该字段是为兼容既有前端按设备维度下线的调用方式
 * @param current   是否为当前会话：用于前端置灰「当前设备」禁止自下线
 */
public record DeviceView(
        String sessionId,
        String deviceId,
        String deviceName,
        String userAgent,
        String ipAddress,
        LocalDateTime issuedAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        boolean current) {
}