package com.acme.scaffold.system.notifyquiet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 更新我的免打扰时段请求（个人中心「免打扰」页保存）。
 *
 * <p>整份提交即全量快照（免打扰只有一组起止时间，无矩阵语义）；
 * 起止相同、格式非 {@code HH:mm} 由服务端二次校验拒绝
 * （Bean Validation 只管非空，格式与窗口合法性在 {@code NotificationQuietHoursService} 裁决，
 * 与 M13「先全额校验再落库」同范式）。</p>
 *
 * @param enabled          是否启用免打扰
 * @param startTime        开始时间 {@code HH:mm}（服务器本地时间）
 * @param endTime          结束时间 {@code HH:mm}（服务器本地时间；大于 start 表达跨午夜窗口）
 * @param alertBreakthrough 告警消息是否穿透免打扰
 */
public record UpdateQuietHoursRequest(
        @NotNull(message = "{validation.quietHours.switch.notNull}")
        Boolean enabled,
        @NotBlank(message = "{validation.quietHours.time.notBlank}")
        String startTime,
        @NotBlank(message = "{validation.quietHours.time.notBlank}")
        String endTime,
        @NotNull(message = "{validation.quietHours.switch.notNull}")
        Boolean alertBreakthrough) {
}
