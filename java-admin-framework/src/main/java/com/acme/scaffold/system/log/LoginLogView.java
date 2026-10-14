package com.acme.scaffold.system.log;

import java.time.LocalDateTime;

/**
 * 登录日志视图（V29 登录日志页数据源，对应 sys_login_log 表）。
 *
 * <p>登录日志在认证成功与失败两条路径上均由 {@code LoginLogRecorder} 落库，
 * 与报表域「登录统计」同源，但报表只出聚合数，本视图出逐条明细，供安全审计排查。
 *
 * @param id         日志 ID
 * @param userId     用户 ID（失败登录时用户不存在，为 null）
 * @param username   登录时提交的用户名
 * @param loginType  登录类型（PASSWORD / REFRESH 等）
 * @param result     结果：SUCCESS / FAILURE
 * @param failureCode 失败码（AUTH_001 等；成功为 null）
 * @param ipAddress  登录 IP（展示层按用户偏好掩码）
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
}
