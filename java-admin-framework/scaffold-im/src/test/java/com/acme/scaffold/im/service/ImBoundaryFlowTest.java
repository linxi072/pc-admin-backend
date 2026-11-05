package com.acme.scaffold.im.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.im.dto.ConversationCreateRequest;
import com.acme.scaffold.im.dto.ConversationView;
import com.acme.scaffold.im.dto.MessageSendRequest;
import com.acme.scaffold.im.support.ImH2;
import com.acme.scaffold.im.support.RecordingEventPublisher;
import com.acme.scaffold.im.support.StubSecurityContextFacade;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IM 会话/消息边界集成测试（H2）：补齐既有 happy-path 测试未覆盖的边界与回归点。
 *
 * <p>回归目标：① 创建会话不得把创建者自己列为对端成员；② 成员唯一键含已删除行，
 * 重新添加被移除成员须「复活」而非 INSERT 撞键；③ 群主退群交接须按最早剩余成员确定性选取；
 * ④ 已撤回消息不计入未读；⑤ markRead 游标只前进不回退、无消息时不清空；
 * ⑥ 非成员发送与空内容被拒。</p>
 */
class ImBoundaryFlowTest {

    private DSLContext dsl;
    private RecordingEventPublisher publisher;

    private ImConversationService convU1;
    private ImConversationService convU2;
    private ImMessageService msgU1;
    private ImMessageService msgU2;
    private ImMessageService msgU3;

    @BeforeEach
    void setUp() throws Exception {
        dsl = ImH2.newContext();
        publisher = new RecordingEventPublisher();
        StubSecurityContextFacade u1 = new StubSecurityContextFacade(1L, "tester", "测试员");
        StubSecurityContextFacade u2 = new StubSecurityContextFacade(2L, "peer", "Peer");
        StubSecurityContextFacade u3 = new StubSecurityContextFacade(3L, "stranger", "Stranger");
        convU1 = new ImConversationService(dsl, u1);
        convU2 = new ImConversationService(dsl, u2);
        msgU1 = new ImMessageService(dsl, u1, publisher);
        msgU2 = new ImMessageService(dsl, u2, publisher);
        msgU3 = new ImMessageService(dsl, u3, publisher);
    }

    private long p2p() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("P2P");
        req.setMemberIds(List.of(2L));
        return convU1.createConversation(req);
    }

    private long group() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setConversationType("GROUP");
        req.setTitle("群");
        req.setMemberIds(List.of(2L, 3L));
        return convU1.createConversation(req);
    }

    private long send(ImMessageService service, long convId, String content) {
        MessageSendRequest req = new MessageSendRequest();
        req.setConversationId(convId);
        req.setContent(content);
        return service.send(req);
    }

    @Test
    @DisplayName("创建会话：对端成员包含创建者本人被拒（避免单人 P2P / 人数不足的群）")
    void create_rejectsSelfAsPeer() {
        ConversationCreateRequest p2pReq = new ConversationCreateRequest();
        p2pReq.setConversationType("P2P");
        p2pReq.setMemberIds(List.of(1L));
        assertThrows(BusinessException.class, () -> convU1.createConversation(p2pReq));

        ConversationCreateRequest groupReq = new ConversationCreateRequest();
        groupReq.setConversationType("GROUP");
        groupReq.setTitle("群");
        groupReq.setMemberIds(List.of(1L, 2L));
        assertThrows(BusinessException.class, () -> convU1.createConversation(groupReq));
    }

    @Test
    @DisplayName("addMember：重新添加已移除成员复活旧行，不撞 (tenant,conv,user) 唯一键")
    void addMember_revivesRemovedMember() {
        long convId = group();
        convU1.removeMember(convId, 2L);
        assertEquals(2, convU1.getConversation(convId).getMemberCount());

        // 修复前：fetchMember 只查 notDeleted，会走到 INSERT 并撞唯一键抛异常
        convU1.addMember(convId, 2L);

        ConversationView v = convU1.getConversation(convId);
        assertEquals(3, v.getMemberCount());
        assertTrue(v.getMemberIds().contains(2L));
        assertEquals(1, v.getMemberIds().stream().filter(id -> id.equals(2L)).count(), "不得出现重复成员");
    }

    @Test
    @DisplayName("addMember：活跃成员仍被拒绝重复添加")
    void addMember_rejectsActiveDuplicate() {
        long convId = group();
        assertThrows(BusinessException.class, () -> convU1.addMember(convId, 2L));
    }

    @Test
    @DisplayName("quitConversation：群主退出按最早剩余成员确定性交接")
    void quit_ownerTransfersToEarliestRemainingMember() {
        long convId = group();
        assertEquals(1L, convU1.getConversation(convId).getOwnerId());

        convU1.quitConversation(convId);

        ConversationView v = convU2.getConversation(convId);
        assertNotEquals(1L, v.getOwnerId(), "群主已退出，不应仍为原群主");
        assertTrue(v.getMemberIds().contains(v.getOwnerId()), "新群主必须是剩余成员之一");
        assertEquals(2L, v.getOwnerId(), "应按成员行 id 升序取最早剩余成员");
    }

    @Test
    @DisplayName("未读计数：已撤回消息不再计入（消息服务与会话视图口径一致）")
    void unread_excludesRecalledMessages() {
        long convId = p2p();
        long first = send(msgU1, convId, "第一条");
        send(msgU1, convId, "第二条");
        assertEquals(2L, msgU2.unreadCount());
        assertEquals(2L, convU2.getConversation(convId).getUnreadCount());

        msgU1.recall(first);

        assertEquals(1L, msgU2.unreadCount());
        assertEquals(1L, convU2.getConversation(convId).getUnreadCount());
    }

    @Test
    @DisplayName("markRead：幂等且只前进不回退")
    void markRead_isIdempotent() {
        long convId = p2p();
        send(msgU1, convId, "第一条");
        send(msgU1, convId, "第二条");
        assertEquals(2L, msgU2.unreadCount());

        msgU2.markRead(convId);
        msgU2.markRead(convId);
        assertEquals(0L, msgU2.unreadCount());

        send(msgU1, convId, "第三条");
        assertEquals(1L, msgU2.unreadCount());
        msgU2.markRead(convId);
        assertEquals(0L, msgU2.unreadCount());
    }

    @Test
    @DisplayName("markRead：会话内消息全部逻辑删除后不清空已读游标")
    void markRead_doesNotResetCursorWhenNoMessages() {
        long convId = p2p();
        long msgId = send(msgU1, convId, "唯一一条");
        msgU2.markRead(convId);
        assertEquals(0L, msgU2.unreadCount());

        Long before = lastReadCursorOfUser2();
        assertNotNull(before);
        assertEquals(msgId, before);

        JooqWriters.delete(dsl, JooqTables.IM_MESSAGE, msgId, true);
        msgU2.markRead(convId);

        assertEquals(before, lastReadCursorOfUser2(), "无消息时不得把游标清空为 null");
        assertEquals(0L, msgU2.unreadCount());
    }

    @Test
    @DisplayName("发送边界：空内容与非成员发送均被拒")
    void send_rejectsBlankContentAndNonMember() {
        long convId = p2p();

        MessageSendRequest blank = new MessageSendRequest();
        blank.setConversationId(convId);
        blank.setContent("   ");
        assertThrows(BusinessException.class, () -> msgU1.send(blank));

        assertThrows(BusinessException.class, () -> send(msgU3, convId, "陌生人消息"));
    }

    private Long lastReadCursorOfUser2() {
        return dsl.select(JooqTables.IM_CONVERSATION_MEMBER.field("last_read_message_id", Long.class))
                .from(JooqTables.IM_CONVERSATION_MEMBER.table())
                .where(JooqTables.IM_CONVERSATION_MEMBER.field("user_id", Long.class).eq(2L))
                .fetchOne(0, Long.class);
    }
}
