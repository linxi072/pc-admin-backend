package com.acme.scaffold.system.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessageReadRules} 单元测试：比率推导边界、催办批量收敛、是否仍有未读判定。
 * 纯函数离线可跑，不依赖 Spring 上下文与数据库。
 *
 * <p>与公告规则（分母用发布时活跃用户快照、有 isRemindable 状态门）不同，
 * 站内信是 eager 投递模型，分母即回执表行数，本类只验证 eager 口径下的推导正确性。
 */
class MessageReadRulesTest {

    // ------------------------------------------------------------------
    // readRate
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已读率：正常比率按百分比一位小数")
    void readRate_normalRatio() {
        assertEquals(50.0, MessageReadRules.readRate(5, 10));
        assertEquals(33.3, MessageReadRules.readRate(1, 3));
        assertEquals(100.0, MessageReadRules.readRate(10, 10));
    }

    @Test
    @DisplayName("已读率：分母缺失（totalCount<=0）一律 0，不用 readCount 自造分母")
    void readRate_missingDenominator() {
        assertEquals(0.0, MessageReadRules.readRate(7, 0));
        assertEquals(0.0, MessageReadRules.readRate(7, -1));
    }

    @Test
    @DisplayName("已读率：分子越界收敛，保证比率落在 [0,100]")
    void readRate_clampsNumerator() {
        assertEquals(0.0, MessageReadRules.readRate(-3, 10));
        assertEquals(100.0, MessageReadRules.readRate(20, 10));
    }

    // ------------------------------------------------------------------
    // unreadCount
    // ------------------------------------------------------------------

    @Test
    @DisplayName("未读人数：分母缺失返回 0（eager 模型下分母=回执行数，缺失即无接收人）")
    void unreadCount_missingDenominator() {
        assertEquals(0, MessageReadRules.unreadCount(3, 0));
    }

    @Test
    @DisplayName("未读人数：正常相减，分子越界时不为负")
    void unreadCount_normalAndClamped() {
        assertEquals(4, MessageReadRules.unreadCount(6, 10));
        assertEquals(0, MessageReadRules.unreadCount(10, 10));
        assertEquals(0, MessageReadRules.unreadCount(15, 10)); // 分子超分母 → 0，不为负
    }

    // ------------------------------------------------------------------
    // resolveRemindLimit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("催办批量：null 取默认值")
    void remindLimit_nullUsesDefault() {
        assertEquals(MessageReadRules.DEFAULT_REMIND_LIMIT,
                MessageReadRules.resolveRemindLimit(null));
    }

    @Test
    @DisplayName("催办批量：合法值原样通过")
    void remindLimit_validPassThrough() {
        assertEquals(1, MessageReadRules.resolveRemindLimit(1));
        assertEquals(100, MessageReadRules.resolveRemindLimit(100));
    }

    @Test
    @DisplayName("催办批量：越界收敛到 [1, MAX]")
    void remindLimit_clampsOutOfRange() {
        assertEquals(1, MessageReadRules.resolveRemindLimit(0));
        assertEquals(1, MessageReadRules.resolveRemindLimit(-50));
        assertEquals(MessageReadRules.MAX_REMIND_LIMIT,
                MessageReadRules.resolveRemindLimit(9999));
    }

    // ------------------------------------------------------------------
    // hasUnread
    // ------------------------------------------------------------------

    @Test
    @DisplayName("是否仍有未读：有未读才 true，全读或分母缺失为 false")
    void hasUnread_onlyWhenRemaining() {
        assertTrue(MessageReadRules.hasUnread(0, 5));
        assertTrue(MessageReadRules.hasUnread(7, 10));
        assertFalse(MessageReadRules.hasUnread(10, 10)); // 全读
        assertFalse(MessageReadRules.hasUnread(0, 0));   // 分母缺失
    }
}
