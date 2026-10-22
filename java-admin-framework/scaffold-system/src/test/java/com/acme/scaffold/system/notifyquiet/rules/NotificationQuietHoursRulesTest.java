package com.acme.scaffold.system.notifyquiet.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationQuietHoursRules} 离线单测：时间解析、半开区间命中（含跨午夜与边界）、
 * 退化窗口防御、告警穿透决策与按接收者过滤。全部为纯函数断言，不触库、不依赖 Spring 上下文。
 */
class NotificationQuietHoursRulesTest {

    private static final LocalTime T_22 = LocalTime.of(22, 0);
    private static final LocalTime T_08 = LocalTime.of(8, 0);
    private static final LocalTime T_09 = LocalTime.of(9, 0);
    private static final LocalTime T_12 = LocalTime.of(12, 0);

    // ------------------------------------------------------------------
    // 时间解析
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseTime：合法 HH:mm（含 00:00 / 23:59 边界与首尾空白）")
    void parseTime_acceptsValid() {
        assertEquals(T_22, NotificationQuietHoursRules.parseTime("22:00"));
        assertEquals(LocalTime.of(0, 0), NotificationQuietHoursRules.parseTime("00:00"));
        assertEquals(LocalTime.of(23, 59), NotificationQuietHoursRules.parseTime("23:59"));
        assertEquals(LocalTime.of(8, 30), NotificationQuietHoursRules.parseTime(" 08:30 "));
    }

    @Test
    @DisplayName("parseTime：非法格式一律 null（空/越界/单位数/带秒/非数字）")
    void parseTime_rejectsInvalid() {
        assertNull(NotificationQuietHoursRules.parseTime(null));
        assertNull(NotificationQuietHoursRules.parseTime(""));
        assertNull(NotificationQuietHoursRules.parseTime("24:00"));
        assertNull(NotificationQuietHoursRules.parseTime("22:60"));
        assertNull(NotificationQuietHoursRules.parseTime("9:00"));
        assertNull(NotificationQuietHoursRules.parseTime("22:0"));
        assertNull(NotificationQuietHoursRules.parseTime("22:00:00"));
        assertNull(NotificationQuietHoursRules.parseTime("abc"));
    }

    @Test
    @DisplayName("formatTime：两位补零往返，null 透传")
    void formatTime_roundTrip() {
        assertEquals("22:00", NotificationQuietHoursRules.formatTime(T_22));
        assertEquals("08:05", NotificationQuietHoursRules.formatTime(LocalTime.of(8, 5)));
        assertNull(NotificationQuietHoursRules.formatTime(null));
    }

    @Test
    @DisplayName("isValidWindow：起止相同或任一为空即非法（写入侧拒绝）")
    void isValidWindow_rejectsDegenerate() {
        assertTrue(NotificationQuietHoursRules.isValidWindow(T_22, T_08));
        assertFalse(NotificationQuietHoursRules.isValidWindow(T_09, T_09));
        assertFalse(NotificationQuietHoursRules.isValidWindow(null, T_08));
        assertFalse(NotificationQuietHoursRules.isValidWindow(T_22, null));
    }

    // ------------------------------------------------------------------
    // 命中判定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isQuiet：当日窗口 [09:00, 12:00) 左闭右开")
    void isQuiet_sameDayWindowHalfOpen() {
        assertFalse(NotificationQuietHoursRules.isQuiet(LocalTime.of(8, 59), T_09, T_12));
        assertTrue(NotificationQuietHoursRules.isQuiet(T_09, T_09, T_12));
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(11, 59), T_09, T_12));
        assertFalse(NotificationQuietHoursRules.isQuiet(T_12, T_09, T_12));
    }

    @Test
    @DisplayName("isQuiet：跨午夜窗口 [22:00, 08:00) 命中两侧")
    void isQuiet_crossMidnightWindow() {
        assertFalse(NotificationQuietHoursRules.isQuiet(LocalTime.of(21, 59), T_22, T_08));
        assertTrue(NotificationQuietHoursRules.isQuiet(T_22, T_22, T_08));
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(3, 30), T_22, T_08));
        assertTrue(NotificationQuietHoursRules.isQuiet(LocalTime.of(7, 59), T_22, T_08));
        assertFalse(NotificationQuietHoursRules.isQuiet(T_08, T_22, T_08));
    }

    @Test
    @DisplayName("isQuiet：退化窗口（起止相同）与 null now 一律不命中（防御历史脏数据）")
    void isQuiet_degenerateNeverHits() {
        LocalTime noon = LocalTime.of(12, 0);
        assertFalse(NotificationQuietHoursRules.isQuiet(noon, noon, noon));
        assertFalse(NotificationQuietHoursRules.isQuiet(null, T_22, T_08));
    }

    // ------------------------------------------------------------------
    // 穿透决策
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isAlert：大小写不敏感、trim；null/空白/其他类型为 false")
    void isAlert_normalizesCase() {
        assertTrue(NotificationQuietHoursRules.isAlert("ALERT"));
        assertTrue(NotificationQuietHoursRules.isAlert("alert"));
        assertTrue(NotificationQuietHoursRules.isAlert(" Alert "));
        assertFalse(NotificationQuietHoursRules.isAlert("NOTICE"));
        assertFalse(NotificationQuietHoursRules.isAlert(null));
        assertFalse(NotificationQuietHoursRules.isAlert(""));
    }

    @Test
    @DisplayName("suppresses：未命中窗口一律放行；ALERT 且配置穿透才放行")
    void suppresses_alertBreakthrough() {
        assertFalse(NotificationQuietHoursRules.suppresses("NOTICE", false, true));
        assertTrue(NotificationQuietHoursRules.suppresses("NOTICE", true, true));
        assertTrue(NotificationQuietHoursRules.suppresses("SYSTEM", true, true));
        assertTrue(NotificationQuietHoursRules.suppresses("CC", true, true));
        assertTrue(NotificationQuietHoursRules.suppresses(null, true, true));
        assertFalse(NotificationQuietHoursRules.suppresses("ALERT", true, true));
        assertTrue(NotificationQuietHoursRules.suppresses("ALERT", true, false));
        assertFalse(NotificationQuietHoursRules.suppresses("ALERT", false, false));
    }

    @Test
    @DisplayName("isSuppressed：无行/停用/窗口外放行；窗口内按类型与穿透裁决")
    void isSuppressed_perUserDecision() {
        LocalTime now = LocalTime.of(23, 0);
        NotificationQuietHoursRules.QuietSetting on = new NotificationQuietHoursRules.QuietSetting(
                true, T_22, T_08, true);
        NotificationQuietHoursRules.QuietSetting off = new NotificationQuietHoursRules.QuietSetting(
                false, T_22, T_08, true);
        NotificationQuietHoursRules.QuietSetting noBreak = new NotificationQuietHoursRules.QuietSetting(
                true, T_22, T_08, false);

        assertFalse(NotificationQuietHoursRules.isSuppressed("NOTICE", now, null));
        assertFalse(NotificationQuietHoursRules.isSuppressed("NOTICE", now, off));
        assertTrue(NotificationQuietHoursRules.isSuppressed("NOTICE", now, on));
        assertFalse(NotificationQuietHoursRules.isSuppressed("ALERT", now, on));
        assertTrue(NotificationQuietHoursRules.isSuppressed("ALERT", now, noBreak));
        assertFalse(NotificationQuietHoursRules.isSuppressed("NOTICE", LocalTime.of(12, 0), on));
    }

    // ------------------------------------------------------------------
    // 按接收者过滤
    // ------------------------------------------------------------------

    @Test
    @DisplayName("filterReceivers：null/空列表原样返回（不触库语义）")
    void filterReceivers_emptyGuards() {
        assertEquals(List.of(), NotificationQuietHoursRules.filterReceivers(
                "NOTICE", LocalTime.of(23, 0), null, Map.of()));
        assertEquals(List.of(), NotificationQuietHoursRules.filterReceivers(
                "NOTICE", LocalTime.of(23, 0), List.of(), Map.of()));
    }

    @Test
    @DisplayName("filterReceivers：保序过滤——抑制者剔除、null 元素剔除、ALERT 穿透保留")
    void filterReceivers_mixedPopulation() {
        LocalTime now = LocalTime.of(23, 0);
        NotificationQuietHoursRules.QuietSetting on = new NotificationQuietHoursRules.QuietSetting(
                true, T_22, T_08, true);
        NotificationQuietHoursRules.QuietSetting off = new NotificationQuietHoursRules.QuietSetting(
                false, T_22, T_08, true);
        Map<Long, NotificationQuietHoursRules.QuietSetting> settings = Map.of(
                1L, on,    // 免打扰中，NOTICE 抑制
                4L, off);  // 配置存在但停用，放行
        // List.of 不允许 null 元素，用可变列表承载「脏数据」形态的 null 接收者
        List<Long> ids = new ArrayList<>(List.of(1L, 2L, 4L));
        ids.add(1, null);

        List<Long> kept = NotificationQuietHoursRules.filterReceivers("NOTICE", now, ids, settings);

        assertEquals(List.of(2L, 4L), kept);
    }

    @Test
    @DisplayName("filterReceivers：ALERT 且配置穿透的用户在免打扰窗口内保留")
    void filterReceivers_alertBreakthroughKept() {
        LocalTime now = LocalTime.of(23, 0);
        NotificationQuietHoursRules.QuietSetting on = new NotificationQuietHoursRules.QuietSetting(
                true, T_22, T_08, true);
        Map<Long, NotificationQuietHoursRules.QuietSetting> settings = Map.of(1L, on);

        List<Long> kept = NotificationQuietHoursRules.filterReceivers("ALERT", now,
                List.of(1L, 2L), settings);

        assertEquals(List.of(1L, 2L), kept);
    }

    @Test
    @DisplayName("filterReceivers：全部抑制时返回空列表（调用方据此记 SKIPPED）")
    void filterReceivers_allSuppressed() {
        LocalTime now = LocalTime.of(23, 0);
        NotificationQuietHoursRules.QuietSetting on = new NotificationQuietHoursRules.QuietSetting(
                true, T_22, T_08, false);
        Map<Long, NotificationQuietHoursRules.QuietSetting> settings = Map.of(1L, on, 2L, on);

        List<Long> kept = NotificationQuietHoursRules.filterReceivers("NOTICE", now,
                List.of(1L, 2L), settings);

        assertEquals(List.of(), kept);
    }
}
