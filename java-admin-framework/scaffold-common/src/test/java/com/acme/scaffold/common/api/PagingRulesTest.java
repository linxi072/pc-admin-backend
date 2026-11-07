package com.acme.scaffold.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PagingRules} 单测：OFFSET 护栏收敛后的阈值、归一与文案逐字兼容性。
 *
 * <p>三处调用点（用户列表 / 站内信收件箱 / 已发送消息）原本各自内联同一算法并各存一份
 * 5000 魔数；本测试固化「阈值与前端 keyset 软阈值一致」「错误消息逐字不变」两条契约。</p>
 */
class PagingRulesTest {

    @Test
    @DisplayName("常量：与前端 OFFSET_SOFT_LIMIT 及 PageQuery 的 size 钳制对齐")
    void constantsAreSharedSingleSourceOfTruth() {
        assertEquals(5000L, PagingRules.MAX_OFFSET);
        assertEquals(100, PagingRules.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("offsetOf：page/size 归一，size 上限 100")
    void offsetOfNormalizesInputs() {
        assertEquals(0L, PagingRules.offsetOf(1, 10));
        assertEquals(10L, PagingRules.offsetOf(2, 10));
        assertEquals(5000L, PagingRules.offsetOf(501, 10));
        assertEquals(0L, PagingRules.offsetOf(0, 10));
        assertEquals(0L, PagingRules.offsetOf(-3, 10));
        assertEquals(1L, PagingRules.offsetOf(2, 0)); // size=0 归一为 1，与既有实现一致
        assertEquals(200L, PagingRules.offsetOf(3, 10_000)); // size 被钳制到 100
    }

    @Test
    @DisplayName("offsetOf：Integer.MIN_VALUE 页不因 int 溢出被误判为深翻页")
    void offsetOfDoesNotOverflowOnExtremePage() {
        assertEquals(0L, PagingRules.offsetOf(Integer.MIN_VALUE, 10));
        assertFalse(PagingRules.exceedsOffset(Integer.MIN_VALUE, 10));
    }

    @Test
    @DisplayName("exceedsOffset：严格大于 5000 才拦截，与前端 shouldUseKeyset 同口径")
    void exceedsOffsetIsStrictlyGreaterThanLimit() {
        assertFalse(PagingRules.exceedsOffset(501, 10));
        assertTrue(PagingRules.exceedsOffset(502, 10));
        assertFalse(PagingRules.exceedsOffset(1, 10));
        assertTrue(PagingRules.exceedsOffset(1001, 10));
    }

    @Test
    @DisplayName("护栏文案：与收敛前三条错误消息逐字一致（接口行为不回归）")
    void offsetLimitMessageIsByteIdenticalToLegacyMessages() {
        assertEquals("用户列表分页偏移过大（超过 5000 行），请改用游标分页接口 /api/system/users/page-keyset",
                PagingRules.offsetLimitMessage("用户列表", "/api/system/users/page-keyset"));
        assertEquals("站内信分页偏移过大（超过 5000 行），请改用游标分页接口 /api/system/messages/mine-keyset",
                PagingRules.offsetLimitMessage("站内信", "/api/system/messages/mine-keyset"));
        assertEquals("已发送消息分页偏移过大（超过 5000 行），请改用游标分页接口 /api/system/messages/sent-keyset",
                PagingRules.offsetLimitMessage("已发送消息", "/api/system/messages/sent-keyset"));
    }
}
