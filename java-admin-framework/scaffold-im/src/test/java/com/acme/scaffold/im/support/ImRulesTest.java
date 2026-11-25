package com.acme.scaffold.im.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 站内消息纯规则单测：会话创建校验、成员集合、消息归一、已读游标与会话排序。 */
class ImRulesTest {

    @Test
    void createValidationShouldAcceptValidP2pAndGroup() {
        assertDoesNotThrow(() -> ImRules.validateCreateConversation("P2P", List.of(2L), 1L));
        assertDoesNotThrow(() -> ImRules.validateCreateConversation("GROUP", List.of(2L, 3L, 2L), 1L));
    }

    @Test
    void createValidationShouldRejectInvalidType() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> ImRules.validateCreateConversation("CHAT", List.of(2L), 1L));
        assertTrue(ex.getMessage().contains("P2P 或 GROUP"));
    }

    @Test
    void createValidationShouldRejectEmptyPeers() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> ImRules.validateCreateConversation("P2P", null, 1L));
        assertTrue(ex.getMessage().contains("成员不能为空"));
        assertThrows(BusinessException.class, () -> ImRules.validateCreateConversation("GROUP", List.of(), 1L));
    }

    @Test
    void createValidationShouldEnforceP2pPeerCount() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> ImRules.validateCreateConversation("P2P", List.of(2L, 3L), 1L));
        assertTrue(ex.getMessage().contains("仅支持 1 名对方成员"));
    }

    @Test
    void createValidationShouldRejectOwnerInPeers() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> ImRules.validateCreateConversation("P2P", List.of(1L), 1L));
        assertTrue(ex.getMessage().contains("不能包含创建者本人"));
    }

    @Test
    void memberIdsShouldPutOwnerFirstAndDeduplicate() {
        assertEquals(List.of(1L, 2L, 3L),
                List.copyOf(ImRules.resolveMemberIds(1L, List.of(2L, 3L, 2L, 1L))));
        assertEquals(Set.of(1L), ImRules.resolveMemberIds(1L, List.of()));
    }

    @Test
    void msgTypeShouldFallbackToTextForBlankInput() {
        assertEquals("TEXT", ImRules.normalizeMsgType(null));
        assertEquals("TEXT", ImRules.normalizeMsgType("  "));
        assertEquals("IMAGE", ImRules.normalizeMsgType("IMAGE"));
    }

    @Test
    void previewShouldTruncateWithEllipsisOnlyWhenTooLong() {
        String exact = "a".repeat(ImRules.PREVIEW_MAX);
        assertEquals(exact, ImRules.preview(exact, ImRules.PREVIEW_MAX));
        assertEquals(exact + ImRules.PREVIEW_ELLIPSIS, ImRules.preview(exact + "b", ImRules.PREVIEW_MAX));
        assertEquals("短消息", ImRules.preview("短消息", ImRules.PREVIEW_MAX));
    }

    @Test
    void readCursorShouldOnlyAdvanceForward() {
        assertFalse(ImRules.shouldAdvanceReadCursor(null, null), "无消息时保留既有游标");
        assertFalse(ImRules.shouldAdvanceReadCursor(5L, null));
        assertTrue(ImRules.shouldAdvanceReadCursor(null, 5L), "首次已读应推进");
        assertTrue(ImRules.shouldAdvanceReadCursor(4L, 5L));
        assertFalse(ImRules.shouldAdvanceReadCursor(5L, 5L), "重复标记幂等");
        assertFalse(ImRules.shouldAdvanceReadCursor(6L, 5L), "游标不回退");
    }

    @Test
    void conversationSortShouldPutLatestFirstAndNullLast() {
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 25, 10, 0);
        LocalDateTime t2 = t1.plusHours(1);
        assertTrue(ImRules.compareLastMessageAt(t2, t1) < 0);
        assertTrue(ImRules.compareLastMessageAt(t1, t2) > 0);
        assertEquals(0, ImRules.compareLastMessageAt(t1, t1));
        assertTrue(ImRules.compareLastMessageAt(null, t1) > 0);
        assertTrue(ImRules.compareLastMessageAt(t1, null) < 0);
        assertEquals(0, ImRules.compareLastMessageAt(null, null));
    }
}
