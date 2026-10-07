package com.acme.scaffold.system.dto;

/**
 * 通知与隐私偏好视图。
 * <p>用 boolean 而非 Integer：语义上就是开关，布尔可读性优于 0/1，
 * 由 Service 负责与数据库 TINYINT 互转。
 */
public record PreferenceView(
        boolean notifySiteMessage,
        boolean notifyEmail,
        boolean notifyMobile,
        boolean showLoginLog,
        boolean maskMobile,
        boolean discoverable) {
}