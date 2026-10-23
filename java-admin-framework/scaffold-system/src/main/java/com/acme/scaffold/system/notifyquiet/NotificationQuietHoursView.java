package com.acme.scaffold.system.notifyquiet;

import com.acme.scaffold.system.notifyquiet.domain.SysNotificationQuietHoursDO;
import com.acme.scaffold.system.notifyquiet.rules.NotificationQuietHoursRules;


/**
 * 免打扰时段视图（个人中心「免打扰」页数据源，对应 {@code sys_notification_quiet_hours} 表）。
 *
 * <p>时间为 {@code HH:mm} 字符串（前端时间选择器直接绑定，免去序列化格式协商）；
 * {@code quietNow} 为服务端按<b>服务器本地时间</b>实时计算的当前状态提示，
 * 仅用于页面展示「此刻是否处于免打扰中」，不参与发送链路裁决（发送链路以
 * {@link NotificationQuietHoursRules#isQuiet} 实时判定为准，避免视图快照过期）。</p>
 *
 * @param enabled          是否启用免打扰（未配置时为 false）
 * @param startTime        开始时间 {@code HH:mm}（未配置时为默认 22:00）
 * @param endTime          结束时间 {@code HH:mm}（未配置时为默认 08:00）
 * @param alertBreakthrough 告警消息是否穿透（未配置时默认 true：故障告警不应被吞）
 * @param quietNow         当前是否正处于免打扰中（仅展示）
 */
public record NotificationQuietHoursView(
        boolean enabled,
        String startTime,
        String endTime,
        boolean alertBreakthrough,
        boolean quietNow) {

    /** 未配置用户的默认视图（不落库：懒行模型，无行 = 无免打扰）。 */
    public static NotificationQuietHoursView defaults() {
        return new NotificationQuietHoursView(false,
                NotificationQuietHoursRules.DEFAULT_START,
                NotificationQuietHoursRules.DEFAULT_END,
                true, false);
    }

    /**
     * 由持久化对象构造视图（null 返回 {@link #defaults()}）。
     *
     * @param quietNow 调用方按当前时钟计算的「此刻是否免打扰中」
     */
    public static NotificationQuietHoursView of(SysNotificationQuietHoursDO d, boolean quietNow) {
        if (d == null) {
            return defaults();
        }
        return new NotificationQuietHoursView(
                d.getEnabled() != null && d.getEnabled() != 0,
                NotificationQuietHoursRules.formatTime(d.getStartTime()),
                NotificationQuietHoursRules.formatTime(d.getEndTime()),
                d.getAlertBreakthrough() == null || d.getAlertBreakthrough() != 0,
                quietNow);
    }
}
