package com.acme.scaffold.im.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.event.domain.ImMessageSentEvent;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.im.ImStatus;
import com.acme.scaffold.im.domain.ImConversationDO;
import com.acme.scaffold.im.domain.ImConversationMemberDO;
import com.acme.scaffold.im.domain.ImMessageDO;
import com.acme.scaffold.im.dto.MessageSendRequest;
import com.acme.scaffold.im.dto.MessageView;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 消息服务：发送、分页查询、撤回、已读回执（标记已读 + 未读总数）。
 *
 * <p>发送落库后经 R2 事件总线发布 {@link ImMessageSentEvent}（解耦实时推送，realtime 订阅后
 * 经 WebSocket 投递给接收方，im 不直连推送通道）。已读回执由成员 last_read_message_id 驱动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageService {

    private static final JooqTables.TableRef CONVERSATION = JooqTables.IM_CONVERSATION;
    private static final JooqTables.TableRef MEMBER = JooqTables.IM_CONVERSATION_MEMBER;
    private static final JooqTables.TableRef MESSAGE = JooqTables.IM_MESSAGE;

    /** 内容预览截断长度（实时推送气泡与通知摘要用）。 */
    private static final int PREVIEW_MAX = 50;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final EventPublisher eventPublisher;

    /** 发送消息：须为会话成员；落库后发布 ImMessageSentEvent（接收方不含发送者本人）。 */
    @Transactional
    public Long send(MessageSendRequest req) {
        Long senderId = currentUserId();
        String operator = currentOperator();
        ImConversationDO conv = requireConversation(req.getConversationId());
        if (!ImStatus.STATUS_ACTIVE.equals(conv.getStatus())) {
            throw BusinessException.badRequest("会话已不可用");
        }
        requireMember(req.getConversationId(), senderId);
        String content = req.getContent();
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("消息内容不能为空");
        }
        ImMessageDO msg = new ImMessageDO();
        msg.setConversationId(req.getConversationId());
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setMsgType(req.getMsgType() == null || req.getMsgType().isBlank() ? ImStatus.MSG_TEXT : req.getMsgType());
        msg.setStatus(ImStatus.MSG_STATUS_SENT);
        msg.setReplyToId(req.getReplyToId());
        msg.setCreatedBy(operator);
        msg.setUpdatedBy(operator);
        JooqWriters.insert(dsl, MESSAGE, msg);
        Long messageId = msg.getId();

        List<ImConversationMemberDO> receivers = JooqWriters.fetchList(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("conversation_id", Long.class).eq(req.getConversationId())
                        .and(JooqWriters.notDeleted(MEMBER))
                        .and(MEMBER.field("status", String.class).eq(ImStatus.STATUS_ACTIVE))
                        .and(MEMBER.field("user_id", Long.class).ne(senderId)));
        List<Long> receiverIds = receivers.stream().map(ImConversationMemberDO::getUserId).toList();

        String preview = content.length() > PREVIEW_MAX ? content.substring(0, PREVIEW_MAX) + "…" : content;
        eventPublisher.publish(new ImMessageSentEvent(conv.getTenantId(), "im", req.getConversationId(),
                messageId, senderId, receiverIds, conv.getConversationType(), preview));
        log.info("消息已发送 messageId={} conversationId={} senderId={} receiverCount={}",
                messageId, req.getConversationId(), senderId, receiverIds.size());
        return messageId;
    }

    /** 会话消息分页（时间正序，聊天场景自顶向下）。须为成员。 */
    @Transactional(readOnly = true)
    public PageResult<MessageView> listMessages(Long conversationId, int page, int size) {
        requireMember(conversationId, currentUserId());
        Condition cond = MESSAGE.field("conversation_id", Long.class).eq(conversationId)
                .and(JooqWriters.notDeleted(MESSAGE));
        PageQuery q = PageQuery.of(page, size, null, null);
        var raw = JooqWriters.page(dsl, MESSAGE, ImMessageDO.class, cond, q,
                MESSAGE.field("created_at", LocalDateTime.class).asc(),
                MESSAGE.field("id", Long.class).asc());
        List<MessageView> views = raw.records().stream().map(this::toView).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /** 消息详情（须为会话成员）。 */
    @Transactional(readOnly = true)
    public MessageView getById(Long id) {
        ImMessageDO msg = JooqWriters.fetchById(dsl, MESSAGE, ImMessageDO.class, id);
        if (msg == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "消息不存在: " + id);
        }
        requireMember(msg.getConversationId(), currentUserId());
        return toView(msg);
    }

    /** 撤回消息：仅本人、仅 SENT 态可撤回，撤回后正文清空。 */
    @Transactional
    public void recall(Long id) {
        Long userId = currentUserId();
        ImMessageDO msg = JooqWriters.fetchById(dsl, MESSAGE, ImMessageDO.class, id);
        if (msg == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "消息不存在: " + id);
        }
        if (!msg.getSenderId().equals(userId)) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN, "仅可撤回本人发送的消息");
        }
        if (ImStatus.MSG_STATUS_RECALLED.equals(msg.getStatus())) {
            throw BusinessException.badRequest("消息已撤回");
        }
        msg.setStatus(ImStatus.MSG_STATUS_RECALLED);
        msg.setContent(null);
        msg.setUpdatedBy(currentOperator());
        JooqWriters.updateByIdWithNulls(dsl, MESSAGE, id, msg, Set.of("content"));
    }

    /** 标记会话已读（当前用户）：把已读游标推进到会话内最大消息 id，且只前进不回退。 */
    @Transactional
    public void markRead(Long conversationId) {
        Long userId = currentUserId();
        requireMember(conversationId, userId);
        Long maxId = dsl.select(DSL.max(MESSAGE.field("id", Long.class)))
                .from(MESSAGE.table())
                .where(MESSAGE.field("conversation_id", Long.class).eq(conversationId)
                        .and(JooqWriters.notDeleted(MESSAGE)))
                .fetchOne(0, Long.class);
        ImConversationMemberDO member = fetchMember(conversationId, userId);
        if (maxId == null) {
            // 会话内已无消息（全部撤回/删除）：保留既有游标，不能把它清空成 null，
            // 否则历史已读状态会回退，旧消息重新变成未读。
            return;
        }
        Long current = member.getLastReadMessageId();
        if (current != null && current >= maxId) {
            // 重复标记已读或游标已超过当前最大消息 id：幂等返回，不回退。
            return;
        }
        member.setLastReadMessageId(maxId);
        member.setUpdatedAt(LocalDateTime.now());
        JooqWriters.updateById(dsl, MEMBER, member.getId(), member);
    }

    /** 当前用户在所有会话中的未读消息总数。 */
    @Transactional(readOnly = true)
    public long unreadCount() {
        Long userId = currentUserId();
        List<ImConversationMemberDO> myMembers = JooqWriters.fetchList(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("user_id", Long.class).eq(userId)
                        .and(JooqWriters.notDeleted(MEMBER))
                        .and(MEMBER.field("status", String.class).eq(ImStatus.STATUS_ACTIVE)));
        long total = 0;
        for (ImConversationMemberDO m : myMembers) {
            total += computeUnread(m.getConversationId(), userId, m.getLastReadMessageId());
        }
        return total;
    }

    // ---------------- 内部辅助 ----------------

    long computeUnread(Long conversationId, Long userId, Long lastReadId) {
        Condition cond = MESSAGE.field("conversation_id", Long.class).eq(conversationId)
                .and(JooqWriters.notDeleted(MESSAGE))
                .and(MESSAGE.field("sender_id", Long.class).ne(userId))
                // 与 ImConversationService 口径一致：已撤回消息不计入未读总数
                .and(MESSAGE.field("status", String.class).ne(ImStatus.MSG_STATUS_RECALLED));
        if (lastReadId != null) {
            cond = cond.and(MESSAGE.field("id", Long.class).gt(lastReadId));
        }
        return JooqWriters.count(dsl, MESSAGE, cond);
    }

    private MessageView toView(ImMessageDO m) {
        MessageView v = new MessageView();
        v.setId(m.getId());
        v.setConversationId(m.getConversationId());
        v.setSenderId(m.getSenderId());
        v.setContent(m.getContent());
        v.setMsgType(m.getMsgType());
        v.setStatus(m.getStatus());
        v.setReplyToId(m.getReplyToId());
        v.setCreatedAt(m.getCreatedAt());
        v.setUpdatedAt(m.getUpdatedAt());
        return v;
    }

    private ImConversationDO requireConversation(Long conversationId) {
        ImConversationDO conv = JooqWriters.fetchById(dsl, CONVERSATION, ImConversationDO.class, conversationId);
        if (conv == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "会话不存在: " + conversationId);
        }
        return conv;
    }

    private void requireMember(Long conversationId, Long userId) {
        if (fetchMember(conversationId, userId) == null) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN, "您不是该会话成员");
        }
    }

    private ImConversationMemberDO fetchMember(Long conversationId, Long userId) {
        return JooqWriters.fetchOne(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("conversation_id", Long.class).eq(conversationId)
                        .and(MEMBER.field("user_id", Long.class).eq(userId))
                        .and(JooqWriters.notDeleted(MEMBER)));
    }

    private Long currentUserId() {
        return securityContextFacade.getCurrentPrincipal()
                .map(CurrentPrincipal::userId)
                .orElse(0L);
    }

    private String currentOperator() {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> p.displayName() != null ? p.displayName() : p.username())
                .orElse("system");
    }
}
