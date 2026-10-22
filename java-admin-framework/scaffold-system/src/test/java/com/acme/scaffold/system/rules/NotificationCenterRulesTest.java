package com.acme.scaffold.system.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationCenterRules} 纯函数规则测试：无 Spring 上下文，离线可跑。
 * 覆盖近期条目上限钳制、未读合计推导、两类条目合并排序（时间倒序 + null 沉底）+ 截断。
 *
 * <p>mergeAndSortRecent 已泛型化（rules 包不依赖 dto 视图，避免与 dto 形成包级循环），
 * 测试以中性记录 {@link Entry} 验证纯排序/截断逻辑，与具体视图类型解耦。
 */
class NotificationCenterRulesTest {

    /** 中性条目：仅携带 source 与时间键，用于验证合并排序逻辑本身。 */
    private record Entry(String source, LocalDateTime at) {}

    private static Entry item(String source, LocalDateTime at) {
        return new Entry(source, at);
    }

    // ------------------------------------------------------------------
    // clampRecentLimit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("clampRecentLimit：null 取默认 DEFAULT_RECENT(10)")
    void clamp_nullUsesDefault() {
        assertEquals(NotificationCenterRules.DEFAULT_RECENT, NotificationCenterRules.clampRecentLimit(null));
    }

    @Test
    @DisplayName("clampRecentLimit：0 与负数收敛到 1")
    void clamp_zeroAndNegativeClampTo1() {
        assertEquals(1, NotificationCenterRules.clampRecentLimit(0));
        assertEquals(1, NotificationCenterRules.clampRecentLimit(-5));
    }

    @Test
    @DisplayName("clampRecentLimit：合法区间内原样返回")
    void clamp_inRangeAsIs() {
        assertEquals(1, NotificationCenterRules.clampRecentLimit(1));
        assertEquals(25, NotificationCenterRules.clampRecentLimit(25));
        assertEquals(NotificationCenterRules.MAX_RECENT, NotificationCenterRules.clampRecentLimit(50));
    }

    @Test
    @DisplayName("clampRecentLimit：超过 MAX_RECENT(50) 收敛到上限")
    void clamp_overMaxClampToMax() {
        assertEquals(NotificationCenterRules.MAX_RECENT, NotificationCenterRules.clampRecentLimit(51));
        assertEquals(NotificationCenterRules.MAX_RECENT, NotificationCenterRules.clampRecentLimit(9999));
    }

    // ------------------------------------------------------------------
    // totalUnread
    // ------------------------------------------------------------------

    @Test
    @DisplayName("totalUnread：双方为 0 得 0")
    void total_bothZero() {
        assertEquals(0L, NotificationCenterRules.totalUnread(0, 0));
    }

    @Test
    @DisplayName("totalUnread：公告未读 + 站内信未读")
    void total_sum() {
        assertEquals(8L, NotificationCenterRules.totalUnread(3, 5));
        assertEquals(7L, NotificationCenterRules.totalUnread(0, 7));
    }

    // ------------------------------------------------------------------
    // mergeAndSortRecent
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mergeAndSortRecent：两空列表返回空")
    void merge_bothEmpty() {
        assertTrue(NotificationCenterRules.mergeAndSortRecent(List.of(), List.of(), Entry::at, 10).isEmpty());
    }

    @Test
    @DisplayName("mergeAndSortRecent：null 入参安全返回空")
    void merge_nullSafe() {
        assertTrue(NotificationCenterRules.mergeAndSortRecent(null, null, Entry::at, 10).isEmpty());
    }

    @Test
    @DisplayName("mergeAndSortRecent：按 occurredAt 倒序，null 沉底")
    void merge_sortsByOccurredAtDescWithNullsLast() {
        LocalDateTime now = LocalDateTime.now();
        List<Entry> merged = NotificationCenterRules.mergeAndSortRecent(
                List.of(item("ANNOUNCEMENT", now.minusDays(1)), item("ANNOUNCEMENT", null)),
                List.of(item("MESSAGE", now.minusHours(2))),
                Entry::at, 50);

        assertEquals(3, merged.size());
        // 站内信（2小时前）最新，应排第一；occurredAt=null 的公告沉底
        assertEquals("MESSAGE", merged.get(0).source());
        assertEquals("ANNOUNCEMENT", merged.get(1).source());
        assertEquals("ANNOUNCEMENT", merged.get(2).source());
        assertEquals(null, merged.get(2).at());
    }

    @Test
    @DisplayName("mergeAndSortRecent：limit 小于合并数时截断到 limit")
    void merge_truncatesToLimit() {
        LocalDateTime now = LocalDateTime.now();
        List<Entry> merged = NotificationCenterRules.mergeAndSortRecent(
                List.of(item("ANNOUNCEMENT", now.minusDays(1))),
                List.of(item("MESSAGE", now.minusHours(2)), item("MESSAGE", now.minusHours(3))),
                Entry::at, 2);

        assertEquals(2, merged.size());
        // 截断后保留最新的 2 条，第一条应为最近的站内信
        assertEquals("MESSAGE", merged.get(0).source());
    }

    @Test
    @DisplayName("mergeAndSortRecent：limit 大于合并数时原样返回")
    void merge_limitLargerThanSize() {
        LocalDateTime now = LocalDateTime.now();
        List<Entry> merged = NotificationCenterRules.mergeAndSortRecent(
                List.of(item("ANNOUNCEMENT", now.minusDays(1))),
                List.of(item("MESSAGE", now.minusHours(2))),
                Entry::at, 50);

        assertEquals(2, merged.size());
    }
}
