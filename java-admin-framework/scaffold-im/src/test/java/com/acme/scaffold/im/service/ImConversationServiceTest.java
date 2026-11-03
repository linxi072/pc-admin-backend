package com.acme.scaffold.im.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.im.dto.ConversationCreateRequest;
import com.acme.scaffold.im.dto.ConversationView;
import com.acme.scaffold.im.support.ImH2;
import com.acme.scaffold.im.support.StubSecurityContextFacade;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话服务集成测试：H2 承载 DDL，直接实例化 Service；多用户场景经不同 {@link StubSecurityContextFacade} 模拟。
 */
class ImConversationServiceTest {

    private DSLContext dsl;
    private ImConversationService conversationService;

    @BeforeEach
    void setUp() throws Exception {
        dsl = ImH2.newContext();
        conversationService = new ImConversationService(dsl, new StubSecurityContextFacade());
    }

    private long createP2P(long peer) {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("P2P");
        req.setMemberIds(List.of(peer));
        return conversationService.createConversation(req);
    }

    private long createGroup(List<Long> peers, String title) {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("GROUP");
        req.setTitle(title);
        req.setMemberIds(peers);
        return conversationService.createConversation(req);
    }

    @Test
    void createP2pAddsBothMembers() {
        long convId = createP2P(2L);
        ConversationView v = conversationService.getConversation(convId);
        assertEquals("P2P", v.getConversationType());
        assertEquals(2, v.getMemberCount());
        assertTrue(v.getMemberIds().contains(1L));
        assertTrue(v.getMemberIds().contains(2L));
    }

    @Test
    void createGroupIncludesOwnerAndPeers() {
        long convId = createGroup(List.of(2L, 3L), "研发群");
        ConversationView v = conversationService.getConversation(convId);
        assertEquals("GROUP", v.getConversationType());
        assertEquals(3, v.getMemberCount());
        assertEquals(1L, v.getOwnerId());
        assertTrue(v.getMemberIds().contains(1L));
        assertTrue(v.getMemberIds().contains(2L));
        assertTrue(v.getMemberIds().contains(3L));
    }

    @Test
    void createP2pRejectsMultiplePeers() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("P2P");
        req.setMemberIds(List.of(2L, 3L));
        assertThrows(BusinessException.class, () -> conversationService.createConversation(req));
    }

    @Test
    void createRejectsEmptyMembers() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("P2P");
        req.setMemberIds(List.of());
        assertThrows(BusinessException.class, () -> conversationService.createConversation(req));
    }

    @Test
    void createRejectsUnknownType() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("XYZ");
        req.setMemberIds(List.of(2L));
        assertThrows(BusinessException.class, () -> conversationService.createConversation(req));
    }

    @Test
    void addMemberIncreasesCount() {
        long convId = createGroup(List.of(2L, 3L), "群");
        conversationService.addMember(convId, 4L);
        assertEquals(4, conversationService.getConversation(convId).getMemberCount());
    }

    @Test
    void removeMemberDecreasesCount() {
        long convId = createGroup(List.of(2L, 3L), "群");
        conversationService.removeMember(convId, 2L);
        assertEquals(2, conversationService.getConversation(convId).getMemberCount());
    }

    @Test
    void quitRemovesCurrentUserFromList() {
        long convId = createGroup(List.of(2L, 3L), "群");
        conversationService.quitConversation(convId);
        // 当前用户(1)退出后，其会话列表不再包含该会话
        boolean stillVisible = conversationService.listMyConversations().stream()
                .anyMatch(v -> v.getId().equals(convId));
        assertFalse(stillVisible);
    }

    @Test
    void deleteConversationExcludesFromList() {
        long convId = createGroup(List.of(2L, 3L), "群");
        conversationService.deleteConversation(convId);
        boolean stillVisible = conversationService.listMyConversations().stream()
                .anyMatch(v -> v.getId().equals(convId));
        assertFalse(stillVisible);
    }
}
