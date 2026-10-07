package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 个人中心资料修改请求。
 * <p>仅允许改「本人可自维护」的字段：昵称、手机、邮箱、头像。
 * 用户名/角色/部门/状态属管理字段，必须走 /api/system/users，由管理员操作。
 *
 * @param displayName 昵称
 * @param mobile      手机号（可空表示不变，传空串表示解绑，由 Service 统一处理）
 * @param email       邮箱
 * @param avatarUrl   头像相对路径
 */
public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 100, message = "昵称长度不能超过 100")
        String displayName,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String mobile,

        @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "邮箱格式不正确")
        String email,

        @Size(max = 255, message = "头像地址过长")
        String avatarUrl) {
}