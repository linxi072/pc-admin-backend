package com.acme.scaffold.system.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnnouncementReadRules} 单元测试：比率推导边界、催办批量收敛、可催办状态判定。
 * 纯函数离线可跑，不依赖 Spring 上下文与数据库。
 */
class AnnouncementReadRulesTest {

    // ------------------------------------------------------------------
    // readRate
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已读率：正常比率按百分比一位小数")
    void readRate_normalRatio() {
        assertEquals(50.0, AnnouncementReadRules.readRate(5, 10));
        assertEquals(33.3, AnnouncementReadRules.readRate(1, 3));
        assertEquals(100.0, AnnouncementReadRules.readRate(10, 10));
    }

    @Test
    @DisplayName("已读率：分母缺失（audienceCount<=0）一律 0，不用 readCount 自造分母")
    void readRate_missingDenominator() {
        assertEquals(0.0, AnnouncementReadRules.readRate(7, 0));
        assertEquals(0.0, AnnouncementReadRules.readRate(7, -1));
    }

    @Test
    @DisplayName("已读率：分子越界收敛，保证比率落在 [0,100]")
    void readRate_clampsNumerator() {
        assertEquals(0.0, AnnouncementReadRules.readRate(-3, 10));
        assertEquals(100.0, AnnouncementReadRules.readRate(20, 10));
    }

    // ------------------------------------------------------------------
    // unreadCount
    // ------------------------------------------------------------------

    @Test
    @DisplayName("未读人数：分母缺失返回 0（懒模型下不可枚举全量未读）")
    void unreadCount_missingDenominator() {
        assertEquals(0, AnnouncementReadRules.unreadCount(3, 0));
    }

    @Test
    @DisplayName("未读人数：正常相减，分子越界时不为负")
    void unreadCount_normalAndClamped() {
        assertEquals(4, AnnouncementReadRules.unreadCount(6, 10));
        assertEquals(0, AnnouncementReadRules.unreadCount(10, 10));
        assertEquals(0, AnnouncementReadRules.unreadCount(15, 10)); // 分子超分母 → 0，不为负
    }

    // ------------------------------------------------------------------
    // resolveRemindLimit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("催办批量：null 取默认值")
    void remindLimit_nullUsesDefault() {
        assertEquals(AnnouncementReadRules.DEFAULT_REMIND_LIMIT,
                AnnouncementReadRules.resolveRemindLimit(null));
    }

    @Test
    @DisplayName("催办批量：合法值原样通过")
    void remindLimit_validPassThrough() {
        assertEquals(1, AnnouncementReadRules.resolveRemindLimit(1));
        assertEquals(100, AnnouncementReadRules.resolveRemindLimit(100));
    }

    @Test
    @DisplayName("催办批量：越界收敛到 [1, MAX]")
    void remindLimit_clampsOutOfRange() {
        assertEquals(1, AnnouncementReadRules.resolveRemindLimit(0));
        assertEquals(1, AnnouncementReadRules.resolveRemindLimit(-50));
        assertEquals(AnnouncementReadRules.MAX_REMIND_LIMIT,
                AnnouncementReadRules.resolveRemindLimit(9999));
    }

    // ------------------------------------------------------------------
    // isRemindable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("可催办状态：仅 PUBLISHED")
    void isRemindable_onlyPublished() {
        assertTrue(AnnouncementReadRules.isRemindable("PUBLISHED"));
        assertFalse(AnnouncementReadRules.isRemindable("DRAFT"));
        assertFalse(AnnouncementReadRules.isRemindable("OFFLINE"));
        assertFalse(AnnouncementReadRules.isRemindable(null));
    }
}
