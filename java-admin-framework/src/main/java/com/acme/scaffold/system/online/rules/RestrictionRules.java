package com.acme.scaffold.security.online.rules;

import com.acme.scaffold.system.entity.SysLoginRestrictionDO;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 登录限制纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p>覆盖：时长白名单校验、到期时间计算、限制生效判定（登录链路与列表展示共用同一判定，
 * 避免「页面显示生效中、登录却放行」的口径分裂）。
 */
public final class RestrictionRules {

    /** 长期限制（分钟数 0）：不到期，仅可手动解除。 */
    public static final int PERMANENT = 0;

    /** 前端预设时长白名单（分钟）：30 分钟 / 1 小时 / 12 小时 / 1 天 / 3 天 / 7 天 / 长期。 */
    public static final Set<Integer> ALLOWED_DURATIONS =
            Set.of(30, 60, 720, 1440, 4320, 10080, PERMANENT);

    private RestrictionRules() {
    }

    /** 时长是否在白名单内（拒绝任意数值，防止误配置超长期限）。 */
    public static boolean isAllowedDuration(Integer minutes) {
        return minutes != null && ALLOWED_DURATIONS.contains(minutes);
    }

    /**
     * 计算到期时间。
     *
     * @param now            当前时间
     * @param durationMinutes 限制时长（分钟）；{@link #PERMANENT} 返回 {@code null} 表示长期
     */
    public static LocalDateTime computeExpireAt(LocalDateTime now, int durationMinutes) {
        if (durationMinutes == PERMANENT) {
            return null;
        }
        return now.plusMinutes(durationMinutes);
    }

    /**
     * 限制记录当前是否生效。
     *
     * <p>口径：{@code status=ACTIVE} 且（长期 或 {@code expireAt} 晚于当前时间）。
     * 到期未扫到的记录同样判为不生效（登录放行），与定时任务的最终解除互为兜底。
     */
    public static boolean isActive(SysLoginRestrictionDO restriction, LocalDateTime now) {
        if (restriction == null || !SysLoginRestrictionDO.STATUS_ACTIVE.equals(restriction.getStatus())) {
            return false;
        }
        LocalDateTime expireAt = restriction.getExpireAt();
        return expireAt == null || expireAt.isAfter(now);
    }
}
