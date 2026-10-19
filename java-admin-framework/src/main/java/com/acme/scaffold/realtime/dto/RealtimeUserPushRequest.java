package com.acme.scaffold.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 向指定用户集合定向推送实时消息的请求。
 * <p>{@code type} 复用 RealtimePushService 定义的类型常量；
 * {@code userIds} 为空时由 Bean Validation 在控制器层拦截（返回 {@code COMMON_001}）。
 */
public record RealtimeUserPushRequest(
        @NotEmpty(message = "{validation.realtime.userIds.notEmpty}") List<Long> userIds,
        @NotBlank(message = "{validation.realtime.type.notBlank}") String type,
        Object payload) {
}
