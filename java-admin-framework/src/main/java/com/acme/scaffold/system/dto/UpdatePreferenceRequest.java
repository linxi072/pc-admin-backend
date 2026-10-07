package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通知与隐私偏好设置请求。
 * <p>全部字段为包装类型且不做分组校验：前端按区块局部提交，
 * 未提交的字段保持原值（Service 按 null 跳过），避免"改通知顺手清空隐私"。
 * <p>开关取值限定 0/1，故用 {@code @Min(0) @Max(1)} 而非 {@code @Min}单约束。
 *
 * @param notifySiteMessage 站内信通知
 * @param notifyEmail       邮件通知
 * @param notifyMobile      短信通知
 * @param showLoginLog      展示我的登录记录
 * @param maskMobile        对外脱敏手机号
 * @param discoverable      允许被搜索到
 */
public record UpdatePreferenceRequest(
        @Min(value = 0, message = "开关取值只能为 0 或 1") @Max(value = 1, message = "开关取值只能为 0 或 1") Integer notifySiteMessage,
        @Min(value = 0, message = "开关取值只能为 0 或 1") @Max(value = 1, message = "开关取值只能为 0 或 1") Integer notifyEmail,
        @Min(value = 0, message = "开关取值只能为 0 或 1") @Max(value = 1, message = "开关取值只能为 0 或 1") Integer notifyMobile,
        @Min(value = 0, message = "开关取值只能为 0 或 1") @Max(value = 1, message = "开关取值只能为 0 或 1") Integer showLoginLog,
        @Min(value = 0, message = "开关取值只能为 0 或 1") @Max(value = 1, message = "开关取值只能为 0 或 1") Integer maskMobile,
        @Min(value = 0, message = "开关取值只能为 0 或 1") @Max(value = 1, message = "开关取值只能为 0 或 1") Integer discoverable) {
}