package com.acme.scaffold.system.notifyquiet.domain;

import lombok.Data;

import java.time.LocalTime;

/**
 * 通知免打扰时段实体（对应 {@code sys_notification_quiet_hours}，DDL 见 V44__notification_quiet_hours.sql）。
 *
 * <p><b>懒行模型</b>：与通知偏好（{@code sys_notification_preference}）同范式——仅当用户显式
 * 配置过免打扰才落行；无行即「无免打扰」，与「设过又还原」在库里同构（删行即还原，
 * 故本表无 {@code deleted} 列）。</p>
 *
 * <p><b>按接收者维度、不按渠道</b>：免打扰是时间维总闸（先于 M13 偏好过滤生效），
 * 一名用户至多一行，唯一键 {@code uk_user} 兜底幂等；写入走「查现存 → 更新或插入」
 * （本仓 jOOQ 动态 DSL 无 upsert API）。</p>
 *
 * <p><b>时间口径</b>：{@code start_time} / {@code end_time} 为 TIME 列，半开区间
 * {@code [start, end)}，按<b>服务器本地时间</b>裁决（{@code LocalTime.now()}）；
 * {@code start > end} 表达跨午夜窗口（如 22:00–08:00）。</p>
 */
@Data
public class SysNotificationQuietHoursDO {

    private Long id;

    private Long tenantId = 0L;

    /** 免打扰配置所属用户（自服务页只写本人，与 ProfileService / 通知偏好同边界）。 */
    private Long userId;

    /** 是否启用免打扰：1 = 启用，0 = 关闭（保留配置、不生效）。 */
    private Integer enabled;

    /** 免打扰开始时间（含，服务器本地时间）。 */
    private LocalTime startTime;

    /** 免打扰结束时间（不含，服务器本地时间）。 */
    private LocalTime endTime;

    /** 告警消息是否穿透免打扰：1 = 穿透（ALERT 类型照常投递），0 = 一并抑制。 */
    private Integer alertBreakthrough;
}
