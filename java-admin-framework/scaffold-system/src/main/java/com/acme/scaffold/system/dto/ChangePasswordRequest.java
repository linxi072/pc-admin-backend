package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 个人中心自助修改密码请求。
 * <p>与管理员重置密码（ResetPasswordRequest，仅需新密码）的区别：
 * 自助改密必须校验<b>原密码</b>，防止会话被劫持后直接改密据为己有。
 * <p>改密成功后 Service 会自增 token_version，使所有旧令牌立即失效。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 */
public record ChangePasswordRequest(
        @NotBlank(message = "{validation.oldPassword.notBlank}")
        String oldPassword,

        @NotBlank(message = "{validation.password.notBlank}")
        @Size(min = 8, max = 64, message = "{validation.password.size}")
        String newPassword) {
}
