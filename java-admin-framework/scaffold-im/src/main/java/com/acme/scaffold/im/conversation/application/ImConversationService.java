package com.acme.scaffold.im.conversation.application;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.im.conversation.domain.model.ImConversationDO;
import com.acme.scaffold.im.conversation.domain.model.ImConversationMemberDO;
import com.acme.scaffold.im.conversation.dto.ConversationCreateRequest;
import com.acme.scaffold.im.conversation.dto.ConversationView;
import com.acme.scaffold.im.message.domain.model.ImMessageDO;
import com.acme.scaffold.im.support.ImStatus;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话服务：点对点/群组会话的创建、成员管理、退出/删除，以及我的会话列表（含未读计数与最近消息预览）。
 *
 * <p>持久化经 {@link SqlWriters}（动态 DSL），租户隔离由框架统一裁决；成员关系与已读游标
 * 承载于 {@code im_conversation_member.last_read_message_id}（见 {@link ImConversationMemberDO}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImConversationService {

    private static final SqlTables.TableRef CONVERSATION = SqlTables.IM_CONVERSATION;
    private static final SqlTables.TableRef MEMBER = SqlTables.IM_CONVERSATION_MEMBER;
    private static final SqlTables.TableRef MESSAGE = SqlTables.IM_MESSAGE;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /** 创建会话：创建者自动作为 OWNER 入会；memberIds 仅填对方成员。 */
    @Transactional
    public Long createConversation(ConversationCreateRequest req) {
        String type = req.getConversationType();
        if (!ImStatus.TYPE_P2P.equals(type) && !ImStatus.TYPE_GROUP.equals(type)) {
            throw BusinessException.badRequest("会话类型必须为 P2P 或 GROUP");
        }
        List<Long> peers = req.getMemberIds();
        if (peers == null || peers.isEmpty()) {
            throw BusinessException.badRequest("会话成员不能为空");
        }
        if (ImStatus.TYPE_P2P.equals(type) && peers.size() != 1) {
            throw BusinessException.badRequest("点对点会话仅支持 1 名对方成员");
        }
        if (ImStatus.TYPE_GROUP.equals(type) && peers.size() < 1) {
            throw BusinessException.badRequest("群组会话至少需要 1 名成员");
        }
        Long ownerId = currentUserId();
        if (peers.contains(ownerId)) {
            // 创建者会自动作为 OWNER 入会；若对端列表又包含自己，去重后会得到「单人 P2P」
            // 或人数少于预期的群，属于无意义的会话数据，前置拦截。
            throw BusinessException.badRequest("会话成员不能包含创建者本人");
        }
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(ownerId);
        userIds.addAll(peers);

        ImConversationDO conv = new ImConversationDO();
        conv.setConversationType(type);
        conv.setTitle(ImStatus.TYPE_GROUP.equals(type) ? req.getTitle() : null);
        conv.setOwnerId(ownerId);
        conv.setStatus(ImStatus.STATUS_ACTIVE);
        conv.setCreatedBy(currentOperator());
        conv.setUpdatedBy(currentOperator());
        SqlWriters.insert(dsl, CONVERSATION, conv);
        Long convId = conv.getId();

        for (Long uid : userIds) {
            ImConversationMemberDO m = new ImConversationMemberDO();
            m.setConversationId(convId);
            m.setUserId(uid);
            m.setMemberRole(uid.equals(ownerId) ? ImStatus.ROLE_OWNER : ImStatus.ROLE_MEMBER);
            m.setStatus(ImStatus.STATUS_ACTIVE);
            SqlWriters.insert(dsl, MEMBER, m);
        }
        log.info("会话已创建 conversationId={} type={} ownerId={} memberCount={}",
                convId, type, ownerId, userIds.size());
        return convId;
    }

    /** 当前用户的会话列表（含成员、未读计数、最近消息预览），按最近消息时间倒序。 */
    @Transactional(readOnly = true)
    public List<ConversationView> listMyConversations() {
        Long userId = currentUserId();
        List<ImConversationMemberDO> myMembers = SqlWriters.fetchList(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("user_id", Long.class).eq(userId)
                        .and(SqlWriters.notDeleted(MEMBER))
                        .and(MEMBER.field("status", String.class).eq(ImStatus.STATUS_ACTIVE)));
        List<ConversationView> views = new ArrayList<>();
        for (ImConversationMemberDO mem : myMembers) {
            ImConversationDO conv = SqlWriters.fetchById(dsl, CONVERSATION, ImConversationDO.class, mem.getConversationId());
            if (conv == null || (conv.getDeleted() != null && conv.getDeleted() == 1)) {
                continue;
            }
            views.add(toView(conv, mem));
        }
        views.sort((a, b) -> {
            LocalDateTime ta = a.getLastMessageAt();
            LocalDateTime tb = b.getLastMessageAt();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        return views;
    }

    /** 会话详情（须为成员）。 */
    @Transactional(readOnly = true)
    public ConversationView getConversation(Long id) {
        Long userId = currentUserId();
        ImConversationDO conv = requireConversation(id);
        requireMember(id, userId);
        return toView(conv, fetchMember(id, userId));
    }

    /**
     * 添加群成员（仅群主）。
     *
     * <p>唯一键 {@code (tenant_id, conversation_id, user_id)} 不区分逻辑删除：曾被移除/退群的
     * 用户会留下 deleted=1 的成员行，若直接 INSERT 会撞唯一键报错。故先按「含已删除」条件查找，
     * 命中则复活该行（恢复 ACTIVE / MEMBER / deleted=0），未命中才新建。</p>
     */
    @Transactional
    public void addMember(Long conversationId, Long userId) {
        Long operator = currentUserId();
        requireGroupConversation(conversationId);
        requireOwner(requireConversation(conversationId), operator);

        ImConversationMemberDO existing = SqlWriters.fetchOne(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("conversation_id", Long.class).eq(conversationId)
                        .and(MEMBER.field("user_id", Long.class).eq(userId)));
        if (existing != null) {
            if (existing.getDeleted() == null || existing.getDeleted() == 0) {
                throw BusinessException.conflict("该用户已是会话成员");
            }
            existing.setMemberRole(ImStatus.ROLE_MEMBER);
            existing.setStatus(ImStatus.STATUS_ACTIVE);
            existing.setDeleted(0);
            existing.setUpdatedAt(LocalDateTime.now());
            SqlWriters.updateById(dsl, MEMBER, existing.getId(), existing);
            log.info("会话成员已复活 conversationId={} userId={}", conversationId, userId);
            return;
        }

        ImConversationMemberDO m = new ImConversationMemberDO();
        m.setConversationId(conversationId);
        m.setUserId(userId);
        m.setMemberRole(ImStatus.ROLE_MEMBER);
        m.setStatus(ImStatus.STATUS_ACTIVE);
        SqlWriters.insert(dsl, MEMBER, m);
    }

    /** 移除群成员：群主移除他人，或本人退群（不可移除群主 / 最后一名成员）。 */
    @Transactional
    public void removeMember(Long conversationId, Long userId) {
        Long operator = currentUserId();
        requireGroupConversation(conversationId);
        ImConversationMemberDO target = fetchMember(conversationId, userId);
        if (target == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "成员不存在");
        }
        if (ImStatus.ROLE_OWNER.equals(target.getMemberRole())) {
            throw BusinessException.badRequest("群主不可被移除");
        }
        if (!target.getUserId().equals(operator)) {
            requireOwner(requireConversation(conversationId), operator);
        }
        SqlWriters.delete(dsl, MEMBER, target.getId(), true);
    }

    /** 本人退出会话：群主退出时把群主转交最早剩余成员；若为最后一名成员则整体逻辑删除。 */
    @Transactional
    public void quitConversation(Long conversationId) {
        Long userId = currentUserId();
        ImConversationDO conv = requireConversation(conversationId);
        ImConversationMemberDO me = fetchMember(conversationId, userId);
        if (me == null) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN, "您不是会话成员");
        }
        long activeCount = SqlWriters.count(dsl, MEMBER,
                MEMBER.field("conversation_id", Long.class).eq(conversationId)
                        .and(SqlWriters.notDeleted(MEMBER))
                        .and(MEMBER.field("status", String.class).eq(ImStatus.STATUS_ACTIVE)));
        if (activeCount <= 1) {
            deleteConversationInternal(conversationId);
            return;
        }
        if (ImStatus.ROLE_OWNER.equals(me.getMemberRole())) {
            List<ImConversationMemberDO> others = SqlWriters.fetchList(dsl, MEMBER, ImConversationMemberDO.class,
                    MEMBER.field("conversation_id", Long.class).eq(conversationId)
                            .and(SqlWriters.notDeleted(MEMBER))
                            .and(MEMBER.field("user_id", Long.class).ne(userId))
                            .and(MEMBER.field("status", String.class).eq(ImStatus.STATUS_ACTIVE)),
                    // 明确按成员行 id 升序取「最早剩余成员」，避免无排序时数据库返回次序不定
                    // 导致群主交接对象不可预期（注释与实现此前不一致）。
                    MEMBER.field("id", Long.class).asc());
            if (!others.isEmpty()) {
                ImConversationMemberDO newOwner = others.get(0);
                newOwner.setMemberRole(ImStatus.ROLE_OWNER);
                newOwner.setUpdatedAt(LocalDateTime.now());
                SqlWriters.updateById(dsl, MEMBER, newOwner.getId(), newOwner);
                conv.setOwnerId(newOwner.getUserId());
                conv.setUpdatedBy(currentOperator());
                SqlWriters.updateById(dsl, CONVERSATION, conv.getId(), conv);
            }
        }
        SqlWriters.delete(dsl, MEMBER, me.getId(), true);
    }

    /** 删除会话（仅群主，逻辑删除会话、成员与消息）。 */
    @Transactional
    public void deleteConversation(Long conversationId) {
        Long operator = currentUserId();
        ImConversationDO conv = requireConversation(conversationId);
        requireOwner(conv, operator);
        deleteConversationInternal(conversationId);
    }

    // ---------------- 内部辅助 ----------------

    private void deleteConversationInternal(Long conversationId) {
        SqlWriters.delete(dsl, CONVERSATION, conversationId, true);
        List<ImConversationMemberDO> members = SqlWriters.fetchList(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("conversation_id", Long.class).eq(conversationId).and(SqlWriters.notDeleted(MEMBER)));
        for (ImConversationMemberDO m : members) {
            SqlWriters.delete(dsl, MEMBER, m.getId(), true);
        }
        List<ImMessageDO> msgs = SqlWriters.fetchList(dsl, MESSAGE, ImMessageDO.class,
                MESSAGE.field("conversation_id", Long.class).eq(conversationId).and(SqlWriters.notDeleted(MESSAGE)));
        for (ImMessageDO m : msgs) {
            SqlWriters.delete(dsl, MESSAGE, m.getId(), true);
        }
    }

    private ConversationView toView(ImConversationDO conv, ImConversationMemberDO myMember) {
        ConversationView v = new ConversationView();
        v.setId(conv.getId());
        v.setConversationType(conv.getConversationType());
        v.setTitle(conv.getTitle());
        v.setOwnerId(conv.getOwnerId());
        v.setCreatedAt(conv.getCreatedAt());
        v.setUpdatedAt(conv.getUpdatedAt());
        List<ImConversationMemberDO> members = SqlWriters.fetchList(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("conversation_id", Long.class).eq(conv.getId()).and(SqlWriters.notDeleted(MEMBER)));
        v.setMemberIds(members.stream().map(ImConversationMemberDO::getUserId).toList());
        v.setMemberCount(members.size());
        v.setUnreadCount(computeUnread(conv.getId(), currentUserId(), myMember.getLastReadMessageId()));
        List<ImMessageDO> last = SqlWriters.fetchTop(dsl, MESSAGE, ImMessageDO.class,
                MESSAGE.field("conversation_id", Long.class).eq(conv.getId()).and(SqlWriters.notDeleted(MESSAGE)),
                1, MESSAGE.field("id", Long.class).desc());
        if (last != null && !last.isEmpty()) {
            ImMessageDO m = last.get(0);
            v.setLastMessagePreview(m.getContent());
            v.setLastMessageAt(m.getCreatedAt());
        }
        return v;
    }

    long computeUnread(Long conversationId, Long userId, Long lastReadId) {
        Condition cond = MESSAGE.field("conversation_id", Long.class).eq(conversationId)
                .and(SqlWriters.notDeleted(MESSAGE))
                .and(MESSAGE.field("sender_id", Long.class).ne(userId))
                // 已撤回消息正文被清空且不应再打扰接收方，不得计入未读数
                .and(MESSAGE.field("status", String.class).ne(ImStatus.MSG_STATUS_RECALLED));
        if (lastReadId != null) {
            cond = cond.and(MESSAGE.field("id", Long.class).gt(lastReadId));
        }
        return SqlWriters.count(dsl, MESSAGE, cond);
    }

    private ImConversationDO requireConversation(Long conversationId) {
        ImConversationDO conv = SqlWriters.fetchById(dsl, CONVERSATION, ImConversationDO.class, conversationId);
        if (conv == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "会话不存在: " + conversationId);
        }
        return conv;
    }

    private ImConversationDO requireGroupConversation(Long conversationId) {
        ImConversationDO conv = requireConversation(conversationId);
        if (!ImStatus.TYPE_GROUP.equals(conv.getConversationType())) {
            throw BusinessException.badRequest("仅群组会话支持该操作");
        }
        return conv;
    }

    private void requireOwner(ImConversationDO conv, Long userId) {
        if (conv.getOwnerId() == null || !conv.getOwnerId().equals(userId)) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN, "仅群主可执行该操作");
        }
    }

    private void requireMember(Long conversationId, Long userId) {
        if (fetchMember(conversationId, userId) == null) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN, "您不是该会话成员");
        }
    }

    private ImConversationMemberDO fetchMember(Long conversationId, Long userId) {
        return SqlWriters.fetchOne(dsl, MEMBER, ImConversationMemberDO.class,
                MEMBER.field("conversation_id", Long.class).eq(conversationId)
                        .and(MEMBER.field("user_id", Long.class).eq(userId))
                        .and(SqlWriters.notDeleted(MEMBER)));
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
