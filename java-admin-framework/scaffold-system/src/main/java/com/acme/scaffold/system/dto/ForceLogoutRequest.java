package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 强制下线入参：按在线会话主键批量吊销。
 *
 * <p>语义：吊销目标会话的刷新令牌（设备侧失效）+ 置会话离线；
 * 被下线设备的下一个请求即被会话校验过滤器拒绝（401），实现「操作后实时生效」。
 */
public record ForceLogoutRequest(
        @NotEmpty(message = "{validation.forceLogout.sessionIds.notEmpty}")
        @Size(max = 100, message = "{validation.forceLogout.sessionIds.size}")
        List<Long> sessionIds) {
}
