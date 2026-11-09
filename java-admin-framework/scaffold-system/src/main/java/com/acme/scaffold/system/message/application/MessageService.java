package com.acme.scaffold.system.message.application;

import com.acme.scaffold.common.api.PagingRules;
import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.idempotency.Idempotent;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.mybatis.dsl.KeysetPager;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.system.message.dto.MessageView;
import com.acme.scaffold.system.message.dto.SendMessageRequest;
import com.acme.scaffold.system.message.domain.model.SysMessageDO;
import com.acme.scaffold.system.message.domain.model.SysMessageReceiptDO;
import com.acme.scaffold.system.user.domain.model.SysUserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.Record2;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.CaseStep;
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
import com.acme.scaffold.system.support.CurrentUserSupport;

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

    private static final SqlTables.TableRef USER_ROLE = SqlTables.SYS_USER_ROLE;
    private static final SqlTables.TableRef USER_ORG = SqlTables.SYS_USER_ORG;
    private static final SqlTables.TableRef USER = SqlTables.SYS_USER;

    private static final int MAX_BATCH = 5000;

    /** jOOQ 生成层桥接：与手写 DSL 的 {@link SqlTables#SYS_MESSAGE} 等价，列集一致。 */
    private static final com.acme.scaffold.mybatis.dsl.SqlTables.TableRef MSG =
            SqlTables.SYS_MESSAGE;
    /** jOOQ 生成层桥接：与手写 DSL 的 {@link SqlTables#SYS_MESSAGE_RECEIPT} 等价，列集一致。 */
    private static final com.acme.scaffold.mybatis.dsl.SqlTables.TableRef RC =
            SqlTables.SYS_MESSAGE_RECEIPT;

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

    /** 落库实现：对外入口与内部抄送共用，事务由调用方（{@code send} / {@code sendCcNotification}）开启。
     *  <p>包级可见方法升为 public，供通知渠道适配器（{@code InAppNotificationAdapter}）复用同一落库实现，
     *  避免站内信逻辑被复制；仅为可访问性放宽，行为不变。 */
    public int doSend(SendMessageRequest request) {
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
        List<Long> validUserIds = SqlWriters.fetchList(dsl, USER, SysUserDO.class,
                        DSL.and(SqlWriters.notDeleted(USER),
                                USER.field("id", Long.class).in(receivers))).stream()
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
        SqlWriters.insert(dsl, MSG, msg);

        // 批量插入收件明细：先收集全部回执再一次性 batch 写入，避免逐条 insert 的多次往返（M2/M3 批量写入缺陷修复）
        List<SysMessageReceiptDO> receipts = new ArrayList<>(validUserIds.size());
        for (Long userId : validUserIds) {
            SysMessageReceiptDO r = new SysMessageReceiptDO();
            r.setMessageId(msg.getId());
            r.setUserId(userId);
            r.setIsRead(0);
            receipts.add(r);
        }
        SqlWriters.batchInsert(dsl, RC, receipts);
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
            conds.add(USER.field("id", Long.class).in(
                    dsl.select(USER_ROLE.field("user_id", Long.class))
                            .from(USER_ROLE.table())
                            .where(USER_ROLE.field("role_id", Long.class).in(roleIds))));
        }
        if (orgIds != null && !orgIds.isEmpty()) {
            conds.add(USER.field("id", Long.class).in(
                    dsl.select(USER_ORG.field("user_id", Long.class))
                            .from(USER_ORG.table())
                            .where(USER_ORG.field("org_id", Long.class).in(orgIds))));
        }
        if (conds.isEmpty()) {
            return Set.of();
        }
        return SqlWriters.fetchList(dsl, USER, SysUserDO.class,
                        DSL.and(SqlWriters.notDeleted(USER),
                                USER.field("status", String.class).eq("ACTIVE"),
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
        if (PagingRules.exceedsOffset(page, size)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    PagingRules.offsetLimitMessage("站内信", "/api/system/messages/mine-keyset"));
        }
        Long userId = requireCurrentUserId();
        List<Condition> conds = new ArrayList<>();
        conds.add(RC.field("user_id", Long.class).eq(userId));
        if (onlyUnread) {
            conds.add(RC.field("is_read", Integer.class).eq(0));
        }

        // 以收件表为主表做分页查询（仅返回属于自己的消息）
        long total = SqlWriters.count(dsl, RC, DSL.and(conds));
        var q = com.acme.scaffold.common.api.dto.PageQuery.of(page, size);
        List<Record2<Long, Integer>> receipts = dsl.select(
                        RC.field("message_id", Long.class),
                        RC.field("is_read", Integer.class))
                .from(RC.table())
                .where(DSL.and(conds))
                // 未读优先，其次接收时间倒序
                .orderBy(RC.field("is_read", Integer.class).asc(),
                        RC.field("id", Long.class).desc())
                .limit(q.limit()).offset(q.offset())
                .fetch();

        List<Long> msgIds = receipts.stream().map(Record2::value1).toList();
        Map<Long, Integer> isReadMap = receipts.stream()
                .collect(Collectors.toMap(Record2::value1, r -> r.value2() == null ? 0 : r.value2()));
        return new PageResult<>(page, size, total, buildMineViews(msgIds, isReadMap, userId));
    }

    /**
     * 站内信收件箱游标（keyset）分页：深翻页性能稳定，不随偏移退化。
     * 排序键服务端固定为 (is_read ASC, id DESC)，游标不透明编码；不返回 total。
     */
    public KeysetPageResult<MessageView> pageMineKeyset(int size, String cursor, boolean onlyUnread) {
        Long userId = requireCurrentUserId();
        List<Condition> conds = new ArrayList<>();
        conds.add(RC.field("user_id", Long.class).eq(userId));
        if (onlyUnread) {
            conds.add(RC.field("is_read", Integer.class).eq(0));
        }
        KeysetPageQuery q = new KeysetPageQuery(size, cursor == null ? "" : cursor);
        Field<?>[] keyFields = new Field<?>[]{
                RC.field("is_read", Integer.class),
                RC.field("id", Long.class)
        };
        boolean[] ascending = {true, false}; // is_read ASC, id DESC
        KeysetPageResult<SysMessageReceiptDO> raw = KeysetPager.query(dsl, RC, SysMessageReceiptDO.class,
                DSL.and(conds), q,
                r -> new Object[]{r.getIsRead(), r.getId()}, keyFields, ascending);
        List<Long> msgIds = raw.records().stream().map(SysMessageReceiptDO::getMessageId).toList();
        Map<Long, Integer> isReadMap = raw.records().stream()
                .collect(Collectors.toMap(SysMessageReceiptDO::getMessageId,
                        r -> r.getIsRead() == null ? 0 : r.getIsRead()));
        return KeysetPageResult.of(raw.size(), buildMineViews(msgIds, isReadMap, userId), raw.nextCursor());
    }

    /** 由本页消息 id 集合与已读标记构造消息视图（批量装载发件人姓名与已读回执时间，避免 N+1）。 */
    private List<MessageView> buildMineViews(List<Long> orderedMsgIds, Map<Long, Integer> isReadMap, Long userId) {
        List<MessageView> views = new ArrayList<>();
        if (orderedMsgIds.isEmpty()) {
            return views;
        }
        Set<Long> msgIds = new LinkedHashSet<>(orderedMsgIds);
        Map<Long, SysMessageDO> msgMap = SqlWriters.fetchList(dsl, MSG,
                        SysMessageDO.class,
                        DSL.and(SqlWriters.notDeleted(MSG),
                                MSG.field("id", Long.class).in(msgIds)))
                .stream().collect(Collectors.toMap(SysMessageDO::getId, m -> m));

        Set<Long> senderIds = msgMap.values().stream().map(SysMessageDO::getSenderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> senderNames = loadSenderNames(senderIds);

        // 批量装载本页回执（避免逐条查询 readAt 造成 N+1）
        Map<Long, LocalDateTime> readAtMap = loadReadAtMap(msgIds, userId);

        for (Long msgId : orderedMsgIds) {
            SysMessageDO m = msgMap.get(msgId);
            if (m == null) {
                continue;
            }
            Integer flag = isReadMap.get(msgId);
            boolean isRead = flag != null && flag == 1;
            views.add(new MessageView(m.getId(), m.getTitle(), m.getContent(), m.getMsgType(),
                    m.getSenderId(), senderNames.get(m.getSenderId()), m.getSentAt(),
                    isRead, isRead ? readAtMap.get(m.getId()) : null));
        }
        return views;
    }

    /** 我的未读数量（用于顶部提醒徽标）。 */
    public long unreadCount() {
        Long userId = requireCurrentUserId();
        return SqlWriters.count(dsl, RC,
                DSL.and(RC.field("user_id", Long.class).eq(userId),
                        RC.field("is_read", Integer.class).eq(0)));
    }

    /**
     * 标记已读（记录回执时间），并回写发件表的已读计数。
     * 幂等：重复调用不会重复计数。
     */
    @Transactional
    public void markRead(Long messageId) {
        Long userId = requireCurrentUserId();
        SysMessageReceiptDO receipt = SqlWriters.fetchOne(dsl, RC,
                SysMessageReceiptDO.class,
                DSL.and(RC.field("message_id", Long.class).eq(messageId),
                        RC.field("user_id", Long.class).eq(userId)));
        if (receipt == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "消息不存在或非本人接收");
        }
        if (receipt.getIsRead() != null && receipt.getIsRead() == 1) {
            return; // 已读，幂等返回
        }
        LocalDateTime now = LocalDateTime.now();
        // 条件更新而非「先查后更」：并发/重放下只有真正抢到状态跃迁的那一次会生效，
        // 避免两个请求同时读到 is_read=0 后各自 +1，把 read_count 重复累加。
        int changed = dsl.update(RC.table())
                .set(RC.field("is_read", Integer.class), 1)
                .set(RC.field("read_at", LocalDateTime.class), now)
                .where(RC.field("id", Long.class).eq(receipt.getId()))
                .and(DSL.coalesce(RC.field("is_read", Integer.class), 0).ne(1))
                .execute();
        if (changed == 0) {
            return; // 已被并发请求标记，幂等返回且不重复计数
        }

        dsl.update(MSG.table())
                .set(MSG.field("read_count", Integer.class),
                        MSG.field("read_count", Integer.class).coalesce(0).plus(1))
                .where(MSG.field("id", Long.class).eq(messageId))
                .execute();
    }

    /**
     * 一键全部已读。
     *
     * <p>聚合更新：回执表单条语句批量置已读；各消息的 read_count 用单条 CASE 语句按本次
     * 回执数累加。早期实现为逐回执 2N 条 UPDATE（N=未读数），千条未读即两千次写往返，
     * 已在本次测试中定位并修复。</p>
     */
    @Transactional
    public int markAllRead() {
        Long userId = requireCurrentUserId();
        Map<Long, Long> perMessage = SqlWriters.fetchList(dsl, RC,
                        SysMessageReceiptDO.class,
                        DSL.and(RC.field("user_id", Long.class).eq(userId),
                                RC.field("is_read", Integer.class).eq(0)))
                .stream()
                .filter(r -> r.getMessageId() != null)
                .collect(Collectors.groupingBy(SysMessageReceiptDO::getMessageId, Collectors.counting()));
        if (perMessage.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        // 1) 回执批量置已读（单条语句）
        dsl.update(RC.table())
                .set(RC.field("is_read", Integer.class), 1)
                .set(RC.field("read_at", LocalDateTime.class), now)
                .where(RC.field("user_id", Long.class).eq(userId))
                .and(RC.field("is_read", Integer.class).eq(0))
                .execute();
        // 2) 各消息已读数按本次回执数累加（单条 CASE 语句，替代逐消息 UPDATE）
        var msgId = MSG.field("id", Long.class);
        var readCount = MSG.field("read_count", Integer.class);
        CaseStep<Integer> step = null;
        for (Map.Entry<Long, Long> e : perMessage.entrySet()) {
            step = (step == null ? DSL.when(msgId.eq(e.getKey()), e.getValue().intValue())
                    : step.when(msgId.eq(e.getKey()), e.getValue().intValue()));
        }
        // 必须是「历史累计值 + 本次回执增量」。早期实现直接把 CASE 结果 SET 给 read_count，
        // 等于用本次批量已读数覆盖掉此前由 markRead 逐条累计的值，导致发件侧已读统计回退。
        dsl.update(MSG.table())
                .set(readCount, readCount.coalesce(0).plus(step.otherwise(0)))
                .where(msgId.in(perMessage.keySet()))
                .execute();
        return perMessage.values().stream().mapToInt(Long::intValue).sum();
    }

    // ------------------------------------------------------------------
    // 发送方视角
    // ------------------------------------------------------------------

    /** 我发出的消息分页（含已读/未读统计）。 */
    public PageResult<SysMessageDO> pageSent(int page, int size) {
        if (PagingRules.exceedsOffset(page, size)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    PagingRules.offsetLimitMessage("已发送消息", "/api/system/messages/sent-keyset"));
        }
        Long userId = requireCurrentUserId();
        var q = com.acme.scaffold.common.api.dto.PageQuery.of(page, size);
        return SqlWriters.page(dsl, MSG, SysMessageDO.class,
                DSL.and(SqlWriters.notDeleted(MSG),
                        MSG.field("sender_id", Long.class).eq(userId)),
                q, MSG.field("id", Long.class).desc());
    }

    /**
     * 我发出的消息游标（keyset）分页：深翻页性能稳定。排序键服务端固定为 id DESC。
     */
    public KeysetPageResult<SysMessageDO> pageSentKeyset(int size, String cursor) {
        Long userId = requireCurrentUserId();
        KeysetPageQuery q = new KeysetPageQuery(size, cursor == null ? "" : cursor);
        Field<?>[] keyFields = new Field<?>[]{ MSG.field("id", Long.class) };
        boolean[] ascending = {false}; // id DESC
        return KeysetPager.query(dsl, MSG, SysMessageDO.class,
                DSL.and(SqlWriters.notDeleted(MSG),
                        MSG.field("sender_id", Long.class).eq(userId)),
                q, m -> new Object[]{ m.getId() }, keyFields, ascending);
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
        SqlWriters.fetchList(dsl, RC, SysMessageReceiptDO.class,
                        DSL.and(RC.field("message_id", Long.class).in(msgIds),
                                RC.field("user_id", Long.class).eq(userId),
                                RC.field("is_read", Integer.class).eq(1)))
                .forEach(r -> map.put(r.getMessageId(), r.getReadAt()));
        return map;
    }

    private Map<Long, String> loadSenderNames(Set<Long> senderIds) {
        Map<Long, String> map = new HashMap<>();
        if (senderIds.isEmpty()) {
            return map;
        }
        SqlWriters.fetchList(dsl, USER, SysUserDO.class,
                        DSL.and(USER.field("id", Long.class).in(senderIds)))
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
        return CurrentUserSupport.currentUserId(securityContextFacade);
    }

    private Long requireCurrentUserId() {
        return CurrentUserSupport.requireCurrentUserId(securityContextFacade);
    }
}
