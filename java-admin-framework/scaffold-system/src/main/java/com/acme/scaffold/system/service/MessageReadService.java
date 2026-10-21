package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.system.dto.MessageReadStatsView;
import com.acme.scaffold.system.dto.MessageReceiptQuery;
import com.acme.scaffold.system.dto.MessageReceiptView;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.entity.SysMessageDO;
import com.acme.scaffold.system.entity.SysMessageReceiptDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.rules.MessageReadRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内信已读回执中心：已读统计、回执分页、催办未读。
 *
 * <p><b>模型</b>：eager 投递——{@code sys_message_receipt} 在发送时已为每位接收人预插一行，
 * 仅 {@code is_read} 标记 0/1。与公告的懒插入模型（{@code sys_announcement_read} 仅含已读行、
 * 未读靠反连接推导）互为补充。因此：
 * <ul>
 *   <li>统计分母天然 = 回执表行数（{@code total_count}），已读 = {@code is_read = 1} 的行数，
 *       分母绝不会缺失，无需反连接、无需活跃用户快照；</li>
 *   <li>回执分页同时含已读与未读行（{@code isRead} 区分两端）；未读行的 {@code read_at} 为 null；</li>
 *   <li>权限边界：仅消息的<b>发送者</b>可查看其已读情况/催办未读（站内信是私有一对多，
 *       与公告的「全员可见」不同），故服务端校验 sender_id == 当前用户，避免 ID 遍历他人消息。</li>
 * </ul>
 * <p>催办（{@link #remindUnread}）复用 {@link MessageService#doSend} 落库并实时推送，
 * 不复制一套投递逻辑；上限 {@value MessageReadRules#MAX_REMIND_LIMIT} 人（经
 * {@link MessageReadRules#resolveRemindLimit} 收敛）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadService {

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final MessageService messageService;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 单条站内信已读统计（eager 模型：分母来自回执表行数，已读来自 is_read=1 行数，源真值聚合）。
     * 经 {@link MessageReadStatsView#of} 统一推导比率与未读数，保证各调用点口径一致。
     */
    public MessageReadStatsView readStats(Long messageId) {
        SysMessageDO m = requireOwnedMessage(messageId);
        int total = (int) JooqWriters.count(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                DSL.and(JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class).eq(messageId)));
        int read = (int) JooqWriters.count(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                DSL.and(JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class).eq(messageId),
                        JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class).eq(1)));
        String senderName = loadSenderName(m.getSenderId());
        return MessageReadStatsView.of(m, total, read, senderName);
    }

    /** 站内信回执分页（谁已读/未读、何时已读），支持阅读人关键字与回执状态筛选；未读在前。 */
    public PageResult<MessageReceiptView> pageReceipts(MessageReceiptQuery query) {
        requireOwnedMessage(query.messageId());
        var rc = JooqTables.SYS_MESSAGE_RECEIPT;
        var usr = JooqTables.SYS_USER;

        List<Condition> conds = new ArrayList<>();
        conds.add(rc.field("message_id", Long.class).eq(query.messageId()));
        if (query.hasReadStatus()) {
            int flag = "READ".equalsIgnoreCase(query.readStatus()) ? 1 : 0;
            conds.add(rc.field("is_read", Integer.class).eq(flag));
        }
        if (query.hasKeyword()) {
            // 阅读人关键字走子查询限定 user_id 集合：保持主表单表分页，免 join
            String kw = "%" + query.keyword().trim() + "%";
            conds.add(rc.field("user_id", Long.class).in(
                    dsl.select(usr.field("id", Long.class)).from(usr.table())
                            .where(DSL.or(usr.field("display_name", String.class).like(kw),
                                    usr.field("username", String.class).like(kw)))));
        }

        var q = query.toPageQuery();
        PageResult<SysMessageReceiptDO> pageResult = JooqWriters.page(dsl, rc, SysMessageReceiptDO.class,
                DSL.and(conds), q,
                rc.field("is_read", Integer.class).asc(),   // 未读(0) 优先展示
                rc.field("id", Long.class).desc());

        Set<Long> userIds = pageResult.records().stream()
                .map(SysMessageReceiptDO::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = loadDisplayNames(userIds);

        List<MessageReceiptView> views = pageResult.records().stream()
                .map(r -> {
                    boolean isRead = r.getIsRead() != null && r.getIsRead() == 1;
                    return new MessageReceiptView(r.getId(), r.getMessageId(), r.getUserId(),
                            names.get(r.getUserId()),
                            isRead,
                            isRead ? r.getReadAt() : null);
                })
                .toList();
        return new PageResult<>(pageResult.page(), pageResult.size(), pageResult.total(), views);
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /**
     * 催办未读用户：给尚未阅读的用户发一条站内信（复用投递模型 + 实时推送）。
     *
     * @param limit 本次催办人数上限；为空取默认，越界收敛到 {@code [1, MAX_REMIND_LIMIT]}
     * @return 实际催办（投递）人数
     */
    @Transactional
    public int remindUnread(Long messageId, Integer limit) {
        SysMessageDO m = requireOwnedMessage(messageId);
        int resolvedLimit = MessageReadRules.resolveRemindLimit(limit);
        List<Long> unread = unreadUserIds(messageId, resolvedLimit);
        if (unread.isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "当前没有未读用户，无需催办");
        }
        // 走 doSend 而非 send：催办由发送者触发，没有 HTTP 幂等键，不应经过幂等切面
        SendMessageRequest req = new SendMessageRequest(
                "站内信催办：" + m.getTitle(),
                "您有一条站内信尚未阅读：《" + m.getTitle() + "》，请及时查看。",
                "NOTICE", unread, null, null);
        int sent = messageService.doSend(req);
        log.info("站内信催办完成 messageId={} 催办人数={}", messageId, sent);
        return sent;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * 取出发件主表并校验「当前用户是发送者」：
     * 站内信是一对多私信，已读统计/催办仅对发送者开放，避免 ID 遍历他人消息的未读名单。
     */
    private SysMessageDO requireOwnedMessage(Long messageId) {
        SysMessageDO m = JooqWriters.fetchById(dsl, JooqTables.SYS_MESSAGE, SysMessageDO.class, messageId);
        if (m == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "站内信不存在");
        }
        Long me = requireCurrentUserId();
        if (m.getSenderId() == null || !m.getSenderId().equals(me)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能查看自己发出的站内信的已读情况");
        }
        return m;
    }

    /** 未读用户 ID 列表（is_read = 0），按 limit 截断（limit 已收敛到合法区间）。 */
    private List<Long> unreadUserIds(Long messageId, int limit) {
        var rc = JooqTables.SYS_MESSAGE_RECEIPT;
        return JooqWriters.fetchTop(dsl, rc, SysMessageReceiptDO.class,
                DSL.and(rc.field("message_id", Long.class).eq(messageId),
                        rc.field("is_read", Integer.class).eq(0)),
                limit,
                rc.field("id", Long.class).asc()).stream()
                .map(SysMessageReceiptDO::getUserId).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 批量装载用户显示名：userId → displayName（用户被删时缺项，视图降级展示 userId）。 */
    private Map<Long, String> loadDisplayNames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                        JooqTables.SYS_USER.field("id", Long.class).in(userIds)).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(SysUserDO::getId, SysUserDO::getDisplayName, (a, b) -> a));
    }

    /** 单条装载发送者显示名（发件主表 sender_id 可能为 null，做防御）。 */
    private String loadSenderName(Long senderId) {
        if (senderId == null) {
            return null;
        }
        SysUserDO u = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, senderId);
        return u == null ? null : u.getDisplayName();
    }

    private Long currentUserId() {
        return securityContextFacade.getCurrentPrincipal().map(p -> p.userId()).orElse(null);
    }

    private Long requireCurrentUserId() {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未获取到当前登录用户");
        }
        return userId;
    }
}
