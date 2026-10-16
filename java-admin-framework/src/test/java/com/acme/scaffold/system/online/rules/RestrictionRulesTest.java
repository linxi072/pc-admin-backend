package com.acme.scaffold.security.online.rules;

import com.acme.scaffold.system.entity.SysLoginRestrictionDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictionRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0, 0);

    private static SysLoginRestrictionDO restriction(String status, LocalDateTime expireAt) {
        SysLoginRestrictionDO r = new SysLoginRestrictionDO();
        r.setStatus(status);
        r.setExpireAt(expireAt);
        return r;
    }

    // ---------- 时长白名单 ----------

    @Test
    void allowedDurations_coverAllFrontendPresets() {
        assertTrue(RestrictionRules.isAllowedDuration(30));    // 30 分钟
        assertTrue(RestrictionRules.isAllowedDuration(60));    // 1 小时
        assertTrue(RestrictionRules.isAllowedDuration(720));   // 12 小时
        assertTrue(RestrictionRules.isAllowedDuration(1440));  // 1 天
        assertTrue(RestrictionRules.isAllowedDuration(4320));  // 3 天
        assertTrue(RestrictionRules.isAllowedDuration(10080)); // 7 天
        assertTrue(RestrictionRules.isAllowedDuration(0));     // 长期
    }

    @Test
    void disallowedDurations_rejected() {
        assertFalse(RestrictionRules.isAllowedDuration(1));      // 任意分钟数
        assertFalse(RestrictionRules.isAllowedDuration(1000));   // 非白名单
        assertFalse(RestrictionRules.isAllowedDuration(-1));     // 负数
        assertFalse(RestrictionRules.isAllowedDuration(Integer.MAX_VALUE));
        assertFalse(RestrictionRules.isAllowedDuration(null));
    }

    // ---------- 到期时间计算 ----------

    @Test
    void computeExpireAt_addsMinutesForFiniteDuration() {
        LocalDateTime expireAt = RestrictionRules.computeExpireAt(NOW, 60);
        assertEquals(NOW.plusMinutes(60), expireAt);
    }

    @Test
    void computeExpireAt_nullForPermanent() {
        assertNull(RestrictionRules.computeExpireAt(NOW, RestrictionRules.PERMANENT));
    }

    // ---------- 限制生效判定（登录链路与列表展示共用） ----------

    @Test
    void active_whenActiveAndNotExpired() {
        assertTrue(RestrictionRules.isActive(
                restriction(SysLoginRestrictionDO.STATUS_ACTIVE, NOW.plusMinutes(5)), NOW));
    }

    @Test
    void active_whenActiveAndPermanent() {
        // 长期限制：expireAt 为空
        assertTrue(RestrictionRules.isActive(
                restriction(SysLoginRestrictionDO.STATUS_ACTIVE, null), NOW));
    }

    @Test
    void inactive_whenExactlyAtExpireAt() {
        // 边界值：恰好等于到期时间视为已到期（isAfter 严格大于）
        assertFalse(RestrictionRules.isActive(
                restriction(SysLoginRestrictionDO.STATUS_ACTIVE, NOW), NOW));
    }

    @Test
    void inactive_whenExpired() {
        assertFalse(RestrictionRules.isActive(
                restriction(SysLoginRestrictionDO.STATUS_ACTIVE, NOW.minusSeconds(1)), NOW));
    }

    @Test
    void inactive_whenReleased() {
        // 已手动解除：即使 expireAt 还未到也不生效
        assertFalse(RestrictionRules.isActive(
                restriction(SysLoginRestrictionDO.STATUS_RELEASED, NOW.plusMinutes(5)), NOW));
    }

    @Test
    void inactive_whenNull() {
        assertFalse(RestrictionRules.isActive(null, NOW));
    }
}
