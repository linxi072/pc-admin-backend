package com.acme.scaffold.system.notifyquiet.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.system.notifyquiet.domain.rule.NotificationQuietHoursRules.QuietSetting;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 免打扰时段规则单测（H4）：时间解析、当日/跨午夜窗口、告警穿透与批量接收者过滤。 */
class NotificationQuietHoursRulesTest {

    @Test
    void shouldParseAndFormatTime() {
        assertEquals(LocalTime.of(22, 0), NotificationQuietHoursRules.parseTime("22:00"));
        assertEquals(LocalTime.of(8, 5), NotificationQuietHoursRules.parseTime(" 08:05 ")); // trim
        assertEquals(LocalTime.of(0, 0), NotificationQuietHoursRules.parseTime("00:00"));
        assertEquals(LocalTime.of(23, 59), NotificationQuietHoursRules.parseTime("23:59"));

        // 非法格式：空、越界小时/分钟、秒位、非数字
        assertNull(NotificationQuietHoursRules.parseTime(null));
        assertNull(NotificationQuietHoursRules.parseTime(""));
        assertNull(NotificationQuietHoursRules.parseTime("24:00"));
        assertNull(NotificationQuietHoursRules.parseTime("12:60"));
        assertNull(NotificationQuietHoursRules.parseTime("12:30:00"));
        assertNull(NotificationQuietHoursRules.parseTime("ab:cd"));

        assertEquals("22:00", NotificationQuietHoursRules.formatTime(LocalTime.of(22, 0)));
        assertEquals("08:05", NotificationQuietHoursRules.formatTime(LocalTime.of(8, 5)));
        assertNull(NotificationQuietHoursRules.formatTime(null));
    }

    @Test
    void shouldValidateWindow() {
        LocalTime t = LocalTime.of(9, 0);
        assertTrue(NotificationQuietHoursRules.isValidWindow(t, LocalTime.of(12, 0)));
        assertTrue(NotificationQuietHoursRules.isValidWindow(LocalTime.of(22, 0), LocalTime.of(8, 0)));
        assertFalse(NotificationQuietHoursRules.isValidWindow(t, t)); // 退化窗口
        assertFalse(NotificationQuietHoursRules.isValidWindow(null, t));
        assertFalse(NotificationQuietHoursRules.isValidWindow(t, null));
    }

    @Test
    void shouldMatchSameDayWindowHalfOpen() {
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(12, 0);
        assertFalse(NotificationQuietHoursRules.isQuiet(LocalTime.of(8, 59), start, end));
        assertTrue(NotificationQuietHoursRules.isQuiet(start, start, end)); // 起点含
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(11, 59), start, end));
        assertFalse(NotificationQuietHoursRules.isQuiet(end, start, end)); // 终点不含
        assertFalse(NotificationQuietHoursRules.isQuiet(LocalTime.of(12, 1), start, end));
    }

    @Test
    void shouldMatchCrossMidnightWindow() {
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(8, 0);
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(22, 0), start, end));
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(23, 30), start, end));
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(0, 0), start, end));
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(7, 59), start, end));
        assertFalse(NotificationQuietHoursRules.isQuiet(end, start, end)); // 08:00 终点不含
        assertFalse(NotificationQuietHoursRules.isQuiet(LocalTime.of(12, 0), start, end)); // 白天不命中
    }

    @Test
    void shouldDefendDegenerateAndNullInputs() {
        LocalTime t = LocalTime.of(9, 0);
        assertFalse(NotificationQuietHoursRules.isQuiet(null, t, LocalTime.of(10, 0)));
        assertFalse(NotificationQuietHoursRules.isQuiet(t, null, LocalTime.of(10, 0)));
        assertFalse(NotificationQuietHoursRules.isQuiet(t, t, t)); // 退化不命中，防脏数据全量误抑制
    }

    @Test
    void shouldDecideAlertBreakthrough() {
        assertTrue(NotificationQuietHoursRules.isAlert("ALERT"));
        assertTrue(NotificationQuietHoursRules.isAlert(" alert ")); // trim + 大小写不敏感
        assertFalse(NotificationQuietHoursRules.isAlert("NOTICE"));
        assertFalse(NotificationQuietHoursRules.isAlert(null));

        // 非免打扰时段一律放行
        assertFalse(NotificationQuietHoursRules.suppresses("ALERT", false, true));
        // 免打扰时段：普通消息抑制
        assertTrue(NotificationQuietHoursRules.suppresses("NOTICE", true, false));
        assertTrue(NotificationQuietHoursRules.suppresses("SYSTEM", true, true)); // 非告警不穿透
        // 告警 + 穿透开启 → 放行；关闭 → 抑制
        assertFalse(NotificationQuietHoursRules.suppresses("ALERT", true, true));
        assertTrue(NotificationQuietHoursRules.suppresses("ALERT", true, false));
    }

    @Test
    void shouldDecideSuppressionForSetting() {
        LocalTime now = LocalTime.of(23, 0);
        QuietSetting enabled = new QuietSetting(true, LocalTime.of(22, 0), LocalTime.of(8, 0), false);
        QuietSetting breakthrough = new QuietSetting(true, LocalTime.of(22, 0), LocalTime.of(8, 0), true);
        QuietSetting disabled = new QuietSetting(false, LocalTime.of(22, 0), LocalTime.of(8, 0), false);

        assertTrue(NotificationQuietHoursRules.isSuppressed("NOTICE", now, enabled));
        assertTrue(NotificationQuietHoursRules.isSuppressed("ALERT", now, enabled)); // 穿透关闭 → ALERT 也抑制
        assertFalse(NotificationQuietHoursRules.isSuppressed("ALERT", now, breakthrough));
        assertFalse(NotificationQuietHoursRules.isSuppressed("NOTICE", now, disabled)); // 未启用
        assertFalse(NotificationQuietHoursRules.isSuppressed("NOTICE", now, null)); // 无配置行
        assertFalse(NotificationQuietHoursRules.isSuppressed("NOTICE", LocalTime.of(12, 0), enabled));
    }

    @Test
    void shouldFilterReceiversInOrder() {
        LocalTime now = LocalTime.of(23, 0);
        QuietSetting suppressed = new QuietSetting(true, LocalTime.of(22, 0), LocalTime.of(8, 0), false);
        QuietSetting breakthrough = new QuietSetting(true, LocalTime.of(22, 0), LocalTime.of(8, 0), true);
        Map<Long, QuietSetting> settings = Map.of(
                1L, suppressed,
                2L, breakthrough,
                3L, new QuietSetting(false, LocalTime.of(22, 0), LocalTime.of(8, 0), false));
        List<Long> receivers = Arrays.asList(1L, 2L, 3L, 4L, null, 2L);

        // NOTICE：1 被抑制；2（穿透仅对 ALERT 生效，对 NOTICE 仍抑制）；3 未启用、4 无配置 → 保留；null 剔除
        assertEquals(List.of(3L, 4L),
                NotificationQuietHoursRules.filterReceivers("NOTICE", now, receivers, settings));
        // ALERT：1 被抑制；2 穿透放行；3/4 保留
        assertEquals(List.of(2L, 3L, 4L, 2L),
                NotificationQuietHoursRules.filterReceivers("ALERT", now, receivers, settings));
    }

    @Test
    void shouldHandleEmptyReceiverInputs() {
        assertTrue(NotificationQuietHoursRules.filterReceivers("ALERT", LocalTime.NOON, null, Map.of()).isEmpty());
        assertTrue(NotificationQuietHoursRules.filterReceivers("ALERT", LocalTime.NOON, List.of(), null).isEmpty());
        // settings 为 null 时所有 id 放行
        assertEquals(List.of(1L, 2L), NotificationQuietHoursRules.filterReceivers(
                "NOTICE", LocalTime.NOON, List.of(1L, 2L), null));
    }

    @Test
    void shouldExposeExpectedConstants() {
        assertEquals("22:00", NotificationQuietHoursRules.DEFAULT_START);
        assertEquals("08:00", NotificationQuietHoursRules.DEFAULT_END);
        assertEquals("ALERT", NotificationQuietHoursRules.MSG_TYPE_ALERT);
        assertEquals("免打扰时段内已抑制", NotificationQuietHoursRules.REASON_QUIET_HOURS);
        assertTrue(NotificationQuietHoursRules.parseTime(NotificationQuietHoursRules.DEFAULT_START) != null);
        assertTrue(NotificationQuietHoursRules.parseTime(NotificationQuietHoursRules.DEFAULT_END) != null);
    }
}
