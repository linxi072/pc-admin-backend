package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.idempotency.Idempotent;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.realtime.RealtimePushService;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.system.dto.MessageView;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.entity.SysMessageDO;
import com.acme.scaffold.system.entity.SysMessageReceiptDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内信服务：单条发送、按角色/部门批量发送、已读状态跟踪与未读数统计。
 *
 * <p>投递模型：一条 {@link SysMessageDO}（发件）+ N 条 {@link SysMessageReceiptDO}（收件明细）。
 * 收件明细表以 (message_id, user_id) 唯一，批量发送天然幂等——同一消息不会重复投递给同一人。
 *
 * <p>纯 N:N（用户可绑定多角色多部门），批量筛选基于 sys_user_role / sys_user_org 关联表解析，
 * 角色与部门之间为「或」关系，命中任一即投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_BATCH = 5000;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final RealtimePushService realtimePushService;

    // ------------------------------------------------------------------
    // 发送
    // ------------------------------------------------------------------

    /**
     * 发送站内信（单条或批量）——对 HTTP 调用的入口。
     *
     * <p>幂等：支持 {@code Idempotency-Key}，重复提交只发一次。此处 requireKey 保持 false，
     * 因为群发也可能被内部流程复用；不带键时完全按原语义放行，不增加内部调用方负担。
     *
     * <p><b>为什么拆出 {@link #doSend}</b>：抄送（{@link #sendCcNotification}）走的是同一个落库实现，
     * 但它由工作流引擎在流程流转时触发，请求上下文里没有幂等键——若直接复用本方法，
     * 强制键校验会让抄送全部失败。故内部调用一律走 {@code doSend}，绕开幂等切面。
     *
     * @return 实际投递的接收人数量
     */
    @Idempotent(scope = "system:message:send")
    @Transactional
    public int send(SendMessageRequest request) {
        return doSend(request);
    }

    /** 落库实现：对外入口与内部抄送共用，事务由调用方（{@code send} / {@code sendCcNotification}）开启。 */
    protected int doSend(SendMessageRequest request) {
        boolean hasExplicit = request.receiverIds() != null && !request.receiverIds().isEmpty();
        boolean hasFilter = (request.roleIds() != null && !request.roleIds().isEmpty())
                || (request.orgIds() != null && !request.orgIds().isEmpty());
        if (!hasExplicit && !hasFilter) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "请指定接收人，或按角色/部门筛选接收人");
        }

        Set<Long> receivers = hasExplicit
                ? new LinkedHashSet<>(request.receiverIds())
                : resolveReceivers(request.roleIds(), request.orgIds());
        if (receivers.isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "按当前筛选条件未匹配到任何接收人");
        }
        if (receivers.size() > MAX_BATCH) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "单次发送接收人不得超过 " + MAX_BATCH + " 人，请缩小筛选范围");
        }

        // 过滤掉不存在的用户，避免脏投递
        List<Long> validUserIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                                JooqTables.SYS_USER.field("id", Long.class).in(receivers))).stream()
                .map(SysUserDO::getId).collect(Collectors.toList());
        if (validUserIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "接收人不存在");
        }

        SysMessageDO msg = new SysMessageDO();
        msg.setTitle(request.title().trim());
        msg.setContent(request.content());
        msg.setMsgType(request.msgType() == null || request.msgType().isBlank() ? "NOTICE" : request.msgType());
        msg.setSenderId(currentUserId());
        msg.setFilterRoleIds(joinIds(request.roleIds()));
        msg.setFilterOrgIds(joinIds(request.orgIds()));
        msg.setReceiverIds(joinIds(validUserIds));
        msg.setTotalCount(validUserIds.size());
        msg.setReadCount(0);
        msg.setSentAt(LocalDateTime.now());
        JooqWriters.insert(dsl, JooqTables.SYS_MESSAGE, msg);

        // 批量插入收件明细（逐条 insert 以复用主键回填与审计字段填充逻辑）
        for (Long userId : validUserIds) {
            SysMessageReceiptDO r = new SysMessageReceiptDO();
            r.setMessageId(msg.getId());
            r.setUserId(userId);
            r.setIsRead(0);
            JooqWriters.insert(dsl, JooqTables.SYS_MESSAGE_RECEIPT, r);
        }
        log.info("站内信发送完成 messageId={} 接收人数={}", msg.getId(), validUserIds.size());
        // 实时推送未读提醒给在线接收人；离线用户进入页面时通过 REST 拉取全量未读（保证至少一次可见）
        realtimePushService.sendToUsers(validUserIds, RealtimePushService.TYPE_UNREAD_MESSAGE,
                Map.of("messageId", msg.getId(), "title", msg.getTitle(), "msgType", msg.getMsgType()));
        return validUserIds.size();
    }

    /**
     * 工作流抄送 / 执行步骤通知：向显式接收人列表发送一条站内信（msgType=CC）。
     * 供 CC / SERVICE 节点委托类在流程流转时调用，使抄送不再只是结构化日志。
     *
     * @return 实际投递的接收人数量
     */
    @Transactional
    public int sendCcNotification(List<Long> userIds, String title, String content) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        SendMessageRequest req = new SendMessageRequest(
                title == null || title.isBlank() ? "流程抄送通知" : title,
                content == null || content.isBlank() ? "您有一条流程待关注。" : content,
                "CC", userIds, null, null);
        // 走 doSend 而非 send：抄送由引擎触发，没有 HTTP 幂等键，不应经过幂等切面
        return doSend(req);
    }

    /** 按角色/部门解析接收人：角色与部门之间为「或」关系，命中任一即投递（基于多对多关联表）。 */
    private Set<Long> resolveReceivers(List<Long> roleIds, List<Long> orgIds) {
        List<Condition> conds = new ArrayList<>();
        if (roleIds != null && !roleIds.isEmpty()) {
            conds.add(JooqTables.SYS_USER.field("id", Long.class).in(
                    dsl.select(JooqTables.SYS_USER_ROLE.field("user_id", Long.class))
                            .from(JooqTables.SYS_USER_ROLE.table())
                            .where(JooqTables.SYS_USER_ROLE.field("role_id", Long.class).in(roleIds))));
        }
        if (orgIds != null && !orgIds.isEmpty()) {
            conds.add(JooqTables.SYS_USER.field("id", Long.class).in(
                    dsl.select(JooqTables.SYS_USER_ORG.field("user_id", Long.class))
                            .from(JooqTables.SYS_USER_ORG.table())
                            .where(JooqTables.SYS_USER_ORG.field("org_id", Long.class).in(orgIds))));
        }
        if (conds.isEmpty()) {
            return Set.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                                JooqTables.SYS_USER.field("status", String.class).eq("ACTIVE"),
                                DSL.or(conds)))
                .stream().map(SysUserDO::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ------------------------------------------------------------------
    // 收件箱
    // ------------------------------------------------------------------

    /**
     * 我的消息分页（按未读优先、时间倒序）。
     *
     * @param onlyUnread true 时仅返回未读
     */
    public PageResult<MessageView> pageMine(int page, int size, boolean onlyUnread) {
        Long userId = requireCurrentUserId();
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqTables.SYS_MESSAGE_RECEIPT.field("user_id", Long.class).eq(userId));
        if (onlyUnread) {
            conds.add(JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class).eq(0));
        }

        // 以收件表为主表做分页查询（仅返回属于自己的消息）
        long total = JooqWriters.count(dsl, JooqTables.SYS_MESSAGE_RECEIPT, DSL.and(conds));
        var q = com.acme.scaffold.common.api.PageQuery.of(page, size);
        List<Record2<Long, Integer>> receipts = dsl.select(
                        JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class),
                        JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class))
                .from(JooqTables.SYS_MESSAGE_RECEIPT.table())
                .where(DSL.and(conds))
                // 未读优先，其次接收时间倒序
                .orderBy(JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class).asc(),
                        JooqTables.SYS_MESSAGE_RECEIPT.field("id", Long.class).desc())
                .limit(q.limit()).offset(q.offset())
                .fetch();

        List<MessageView> views = new ArrayList<>();
        if (!receipts.isEmpty()) {
            Set<Long> msgIds = receipts.stream().map(Record2::value1).collect(Collectors.toSet());
            Map<Long, SysMessageDO> msgMap = JooqWriters.fetchList(dsl, JooqTables.SYS_MESSAGE,
                            SysMessageDO.class,
                            DSL.and(JooqWriters.notDeleted(JooqTables.SYS_MESSAGE),
                                    JooqTables.SYS_MESSAGE.field("id", Long.class).in(msgIds)))
                    .stream().collect(Collectors.toMap(SysMessageDO::getId, m -> m));

            Set<Long> senderIds = msgMap.values().stream().map(SysMessageDO::getSenderId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> senderNames = loadSenderNames(senderIds);

            // 批量装载本页回执（避免逐条查询 readAt 造成 N+1）
            Map<Long, LocalDateTime> readAtMap = loadReadAtMap(msgIds, userId);

            for (Record2<Long, Integer> r : receipts) {
                SysMessageDO m = msgMap.get(r.value1());
                if (m == null) {
                    continue;
                }
                boolean isRead = r.value2() != null && r.value2() == 1;
                views.add(new MessageView(m.getId(), m.getTitle(), m.getContent(), m.getMsgType(),
                        m.getSenderId(), senderNames.get(m.getSenderId()), m.getSentAt(),
                        isRead, isRead ? readAtMap.get(m.getId()) : null));
            }
        }
        return new PageResult<>(page, size, total, views);
    }

    /** 我的未读数量（用于顶部提醒徽标）。 */
    public long unreadCount() {
        Long userId = requireCurrentUserId();
        return JooqWriters.count(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                DSL.and(JooqTables.SYS_MESSAGE_RECEIPT.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class).eq(0)));
    }

    /**
     * 标记已读（记录回执时间），并回写发件表的已读计数。
     * 幂等：重复调用不会重复计数。
     */
    @Transactional
    public void markRead(Long messageId) {
        Long userId = requireCurrentUserId();
        SysMessageReceiptDO receipt = JooqWriters.fetchOne(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                SysMessageReceiptDO.class,
                DSL.and(JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class).eq(messageId),
                        JooqTables.SYS_MESSAGE_RECEIPT.field("user_id", Long.class).eq(userId)));
        if (receipt == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "消息不存在或非本人接收");
        }
        if (receipt.getIsRead() != null && receipt.getIsRead() == 1) {
            return; // 已读，幂等返回
        }
        LocalDateTime now = LocalDateTime.now();
        dsl.update(JooqTables.SYS_MESSAGE_RECEIPT.table())
                .set(JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class), 1)
                .set(JooqTables.SYS_MESSAGE_RECEIPT.field("read_at", LocalDateTime.class), now)
                .where(JooqTables.SYS_MESSAGE_RECEIPT.field("id", Long.class).eq(receipt.getId()))
                .execute();

        dsl.update(JooqTables.SYS_MESSAGE.table())
                .set(JooqTables.SYS_MESSAGE.field("read_count", Integer.class),
                        JooqTables.SYS_MESSAGE.field("read_count", Integer.class).plus(1))
                .where(JooqTables.SYS_MESSAGE.field("id", Long.class).eq(messageId))
                .execute();
    }

    /** 一键全部已读。 */
    @Transactional
    public int markAllRead() {
        Long userId = requireCurrentUserId();
        List<SysMessageReceiptDO> unread = JooqWriters.fetchList(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                SysMessageReceiptDO.class,
                DSL.and(JooqTables.SYS_MESSAGE_RECEIPT.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class).eq(0)));
        if (unread.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SysMessageReceiptDO r : unread) {
            dsl.update(JooqTables.SYS_MESSAGE_RECEIPT.table())
                    .set(JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class), 1)
                    .set(JooqTables.SYS_MESSAGE_RECEIPT.field("read_at", LocalDateTime.class), now)
                    .where(JooqTables.SYS_MESSAGE_RECEIPT.field("id", Long.class).eq(r.getId()))
                    .execute();
            dsl.update(JooqTables.SYS_MESSAGE.table())
                    .set(JooqTables.SYS_MESSAGE.field("read_count", Integer.class),
                            JooqTables.SYS_MESSAGE.field("read_count", Integer.class).plus(1))
                    .where(JooqTables.SYS_MESSAGE.field("id", Long.class).eq(r.getMessageId()))
                    .execute();
        }
        return unread.size();
    }

    // ------------------------------------------------------------------
    // 发送方视角
    // ------------------------------------------------------------------

    /** 我发出的消息分页（含已读/未读统计）。 */
    public PageResult<SysMessageDO> pageSent(int page, int size) {
        Long userId = requireCurrentUserId();
        var q = com.acme.scaffold.common.api.PageQuery.of(page, size);
        return JooqWriters.page(dsl, JooqTables.SYS_MESSAGE, SysMessageDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_MESSAGE),
                        JooqTables.SYS_MESSAGE.field("sender_id", Long.class).eq(userId)),
                q, JooqTables.SYS_MESSAGE.field("id", Long.class).desc());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 批量装载已读回执时间：messageId → readAt，避免列表查询 N+1。 */
    private Map<Long, LocalDateTime> loadReadAtMap(Set<Long> msgIds, Long userId) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        if (msgIds == null || msgIds.isEmpty()) {
            return map;
        }
        JooqWriters.fetchList(dsl, JooqTables.SYS_MESSAGE_RECEIPT, SysMessageReceiptDO.class,
                        DSL.and(JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class).in(msgIds),
                                JooqTables.SYS_MESSAGE_RECEIPT.field("user_id", Long.class).eq(userId),
                                JooqTables.SYS_MESSAGE_RECEIPT.field("is_read", Integer.class).eq(1)))
                .forEach(r -> map.put(r.getMessageId(), r.getReadAt()));
        return map;
    }

    private Map<Long, String> loadSenderNames(Set<Long> senderIds) {
        Map<Long, String> map = new HashMap<>();
        if (senderIds.isEmpty()) {
            return map;
        }
        JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                        DSL.and(JooqTables.SYS_USER.field("id", Long.class).in(senderIds)))
                .forEach(u -> map.put(u.getId(), u.getDisplayName()));
        return map;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
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
