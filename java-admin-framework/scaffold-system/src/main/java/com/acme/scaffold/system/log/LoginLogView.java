package com.acme.scaffold.system.log;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.security.entity.SysLoginLogDO;

import java.time.LocalDateTime;

/**
 * 登录日志视图（V29 登录日志页数据源，对应 sys_login_log 表）。
 *
 * <p>登录日志在认证成功与失败两条路径上均由 {@code LoginLogRecorder} 落库，
 * 与报表域「登录统计」同源，但报表只出聚合数，本视图出逐条明细，供安全审计排查。</p>
 *
 * <p><b>脱敏</b>：IP 属个人轨迹信息，默认按 {@link SensitiveMasker#maskIp} 掩码（保留网段）；
 * 仅当调用方持有 {@code system:login-log:sensitive} 权限时（{@code sensitiveVisible=true}）才回传原文。
 * 应用层统一经 {@link #of(SysLoginLogDO, boolean)} 构造，避免各处漏脱敏。</p>
 *
 * @param id         日志 ID
 * @param userId     用户 ID（失败登录时用户不存在，为 null）
 * @param username   登录时提交的用户名
 * @param loginType  登录类型（PASSWORD / REFRESH 等）
 * @param result     结果：SUCCESS / FAILURE
 * @param failureCode 失败码（AUTH_001 等；成功为 null）
 * @param ipAddress  登录 IP（默认掩码，持有敏感权限时回传原文）
 * @param userAgent  客户端 UA
 * @param traceId    链路追踪 ID，可经审计日志 trace 接口串联一次请求全链路
 * @param occurredAt 发生时间
 */
public record LoginLogView(
        Long id,
        Long userId,
        String username,
        String loginType,
        String result,
        String failureCode,
        String ipAddress,
        String userAgent,
        String traceId,
        LocalDateTime occurredAt) {

    /**
     * 由持久化对象构造视图，并按 {@code sensitiveVisible} 决定 IP 是否脱敏。
     *
     * @param d                登录日志持久化对象（null 返回 null）
     * @param sensitiveVisible 是否回传未脱敏 IP（由 Controller 依据 {@code system:login-log:sensitive} 权限决定）
     * @return 登录日志视图
     */
    public static LoginLogView of(SysLoginLogDO d, boolean sensitiveVisible) {
        if (d == null) {
            return null;
        }
        return new LoginLogView(d.getId(), d.getUserId(), d.getUsername(), d.getLoginType(),
                d.getResult(), d.getFailureCode(),
                sensitiveVisible ? d.getIpAddress() : SensitiveMasker.maskIp(d.getIpAddress()),
                d.getUserAgent(), d.getTraceId(), d.getOccurredAt());
    }
}
