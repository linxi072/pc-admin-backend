package com.acme.scaffold.im.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.event.domain.ImMessageSentEvent;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.im.dto.ConversationCreateRequest;
import com.acme.scaffold.im.dto.ConversationView;
import com.acme.scaffold.im.dto.MessageSendRequest;
import com.acme.scaffold.im.dto.MessageView;
import com.acme.scaffold.im.support.ImH2;
import com.acme.scaffold.im.support.RecordingEventPublisher;
import com.acme.scaffold.im.support.StubSecurityContextFacade;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消息服务集成测试：H2 承载 DDL，直接实例化 Service；用 {@link RecordingEventPublisher}
 * 断言「消息发送」经 R2 事件总线广播；多用户场景经不同 {@link StubSecurityContextFacade} 模拟未读计数与已读回执。
 */
class ImMessageServiceTest {

    private DSLContext dsl;
    private RecordingEventPublisher publisher;

    private ImConversationService convU1;
    private ImMessageService msgU1;
    private ImMessageService msgU2;
    private ImMessageService msgU3;
    private ImConversationService convU2;

    @BeforeEach
    void setUp() throws Exception {
        dsl = ImH2.newContext();
        publisher = new RecordingEventPublisher();
        StubSecurityContextFacade u1 = new StubSecurityContextFacade(1L, "tester", "测试员");
        StubSecurityContextFacade u2 = new StubSecurityContextFacade(2L, "peer", "Peer");
        StubSecurityContextFacade u3 = new StubSecurityContextFacade(3L, "stranger", "Stranger");
        convU1 = new ImConversationService(dsl, u1);
        msgU1 = new ImMessageService(dsl, u1, publisher);
        convU2 = new ImConversationService(dsl, u2);
        msgU2 = new ImMessageService(dsl, u2, publisher);
        msgU3 = new ImMessageService(dsl, u3, publisher);
    }

    private long p2pBetween1And2() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("P2P");
        req.setMemberIds(List.of(2L));
        return convU1.createConversation(req);
    }

    private long groupOf123() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("GROUP");
        req.setTitle("群");
        req.setMemberIds(List.of(2L, 3L));
        return convU1.createConversation(req);
    }

    private long send(long convId, String content) {
        MessageSendRequest req = new MessageSendRequest();
        req.setConversationId(convId);
        req.setContent(content);
        return msgU1.send(req);
    }

    @Test
    void sendStoresMessageAndEmitsEvent() {
        long convId = p2pBetween1And2();
        long msgId = send(convId, "你好");
        assertNotNull(msgId);
        assertEquals(1, publisher.publishedEvents().size());
        assertTrue(publisher.publishedEvents().get(0) instanceof ImMessageSentEvent);
        ImMessageSentEvent ev = (ImMessageSentEvent) publisher.publishedEvents().get(0);
        assertEquals(convId, ev.getConversationId());
        assertEquals(msgId, ev.getMessageId());
        assertEquals(1, ev.getReceiverIds().size());
        assertTrue(ev.getReceiverIds().contains(2L));
        assertTrue(!ev.getReceiverIds().contains(1L));

        MessageView v = msgU1.getById(msgId);
        assertEquals("你好", v.getContent());
        assertEquals(1L, v.getSenderId());
        assertEquals("SENT", v.getStatus());
    }

    @Test
    void sendToMissingConversationFails() {
        MessageSendRequest req = new MessageSendRequest();
        req.setConversationId(999L);
        req.setContent("x");
        assertThrows(BusinessException.class, () -> msgU1.send(req));
    }

    @Test
    void groupSendExcludesSenderFromReceivers() {
        long convId = groupOf123();
        send(convId, "公告");
        ImMessageSentEvent ev = (ImMessageSentEvent) publisher.publishedEvents().get(0);
        assertEquals(2, ev.getReceiverIds().size());
        assertTrue(ev.getReceiverIds().contains(2L));
        assertTrue(ev.getReceiverIds().contains(3L));
        assertTrue(!ev.getReceiverIds().contains(1L));
    }

    @Test
    void unreadCountAndReadReceipt() {
        long convId = p2pBetween1And2();
        send(convId, "第一条");
        // 接收方(2)有 1 条未读；发送方(1)不计入自己
        assertEquals(1L, msgU2.unreadCount());
        assertEquals(0L, msgU1.unreadCount());

        ConversationView v2Before = convU2.getConversation(convId);
        assertEquals(1L, v2Before.getUnreadCount());

        msgU2.markRead(convId);
        assertEquals(0L, msgU2.unreadCount());
        ConversationView v2After = convU2.getConversation(convId);
        assertEquals(0L, v2After.getUnreadCount());
    }

    @Test
    void recallClearsContentAndBlocksReRecall() {
        long convId = p2pBetween1And2();
        long msgId = send(convId, "可撤回");
        msgU1.recall(msgId);
        MessageView v = msgU1.getById(msgId);
        assertEquals("RECALLED", v.getStatus());
        assertNull(v.getContent());
        // 仅本人可撤回；接收方(2)撤回应被拒
        assertThrows(BusinessException.class, () -> msgU2.recall(msgId));
        // 已撤回不可再撤回
        assertThrows(BusinessException.class, () -> msgU1.recall(msgId));
    }

    @Test
    void nonMemberCannotReadMessage() {
        long convId = p2pBetween1And2();
        long msgId = send(convId, "私有");
        // 用户 3 非会话成员，读取应被拒
        assertThrows(BusinessException.class, () -> msgU3.getById(msgId));
    }

    @Test
    void listMessagesOrderedAscending() {
        long convId = p2pBetween1And2();
        send(convId, "甲");
        send(convId, "乙");
        send(convId, "丙");
        PageResult<MessageView> page = msgU1.listMessages(convId, 1, 30);
        assertEquals(3L, page.total());
        assertEquals("甲", page.records().get(0).getContent());
        assertEquals("丙", page.records().get(2).getContent());
    }
}
