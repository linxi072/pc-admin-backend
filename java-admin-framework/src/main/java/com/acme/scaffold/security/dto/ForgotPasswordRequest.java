package com.acme.scaffold.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 「忘记密码」自助重置请求：以「用户名 + 手机号/邮箱核验」完成身份认证，无需原密码。
 *
 * <p>适用场景：用户忘记原密码，无法走 {@link AuthChangePasswordRequest}（需原密码）通道。
 * 手机号与邮箱至少提供一个，须与库中记录匹配（核验规则见 {@code ForgotPasswordRules}）。
 *
 * <p>安全约定：服务端对「用户不存在」「验证信息不匹配」返回<b>同一</b>错误
 * （{@code AUTH_001 用户名或验证信息不匹配}），防止攻击者通过差异提示枚举有效账号。
 *
 * @param username    用户名
 * @param newPassword 新密码
 * @param mobile      手机号（与库中记录匹配，二选一）
 * @param email       邮箱（与库中记录匹配，二选一）
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "{validation.username.notBlank}")
        String username,

        @NotBlank(message = "{validation.password.notBlank}")
        @Size(min = 8, max = 64, message = "{validation.password.size}")
        String newPassword,

        String mobile,

        String email) {
}
