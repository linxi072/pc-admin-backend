package com.acme.scaffold.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话无关改密请求：用于「首次登录 / 管理员重置后强制改密」闭环。
 *
 * <p>与普通自助改密（{@code ChangePasswordRequest}）的区别：本请求携带 {@code username}，
 * 由服务端用「用户名 + 原密码」完成认证，<b>无需已登录会话</b>。
 * 这使得被标记为「需改密」({@code password_expired=1}) 的用户即便无法登录，
 * 也能先改密再正常进入系统，避免「登录被拦却无法改密」的死锁。
 *
 * @param username     用户名
 * @param oldPassword  原密码（用于身份认证）
 * @param newPassword  新密码
 */
public record AuthChangePasswordRequest(
        @NotBlank(message = "{validation.username.notBlank}")
        String username,

        @NotBlank(message = "{validation.oldPassword.notBlank}")
        String oldPassword,

        @NotBlank(message = "{validation.password.notBlank}")
        @Size(min = 8, max = 64, message = "{validation.password.size}")
        String newPassword) {
}
