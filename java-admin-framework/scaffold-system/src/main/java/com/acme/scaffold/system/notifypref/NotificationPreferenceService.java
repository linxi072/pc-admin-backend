package com.acme.scaffold.system.notifypref;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.notifypref.domain.SysNotificationPreferenceDO;
import com.acme.scaffold.system.notifypref.dto.PreferenceItemRequest;
import com.acme.scaffold.system.notifypref.dto.UpdateNotificationPreferenceRequest;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules.NotificationPreferenceMatrix;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通知偏好应用服务（V43 通知偏好中心）：「个人中心 → 通知订阅」自服务页数据源，
 * 兼作 {@code NotificationService.dispatch} 发送前的偏好过滤挂点。
 *
 * <p><b>自服务边界</b>：与 {@code ProfileService} 同范式——只操作「当前登录用户本人」，
 * 一律从 {@link SecurityContextFacade} 取 userId，不接收前端传入的用户 ID。</p>
 *
 * <p><b>两级生效模型</b>（详见 {@link NotificationPreferenceRules}）：渠道总开关
 * （{@code sys_user.notify_*}）关闭时该渠道一律不投递；否则按本表细分偏好行裁决，
 * 无行默认开通。M13 修复的缺陷即：三个总开关此前唯一读取方是个人中心自身，
 * 发送链路从不读取，是「死开关」——用户关闭站内信通知仍照收。</p>
 *
 * <p><b>过滤口径</b>（{@link #applyPreferences}，供 dispatch 挂点调用）：
 * <ul>
 *   <li>IN_APP / WEBSOCKET：按 {@code receiverIds} 逐用户判定；</li>
 *   <li>EMAIL / SMS：按 {@code emails} / {@code mobiles} 反查 sys_user 后逐用户判定，
 *       无匹配用户的外部地址按默认开通放行；</li>
 *   <li>广播（IN_APP 仅传 roleIds/orgIds、WEBSOCKET 无定向接收者）与 WEBHOOK 不过滤；
 *       定向接收者全部退订时返回 {@code null}，由调用方记 {@code SKIPPED} 并跳过投递。</li>
 * </ul>
 * 定向接收者规模有限（大批量受众走角色/机构广播，不过滤），故反查与行查询均按入参列表
 * 单次 IN 查询，不做分页。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    private static final JooqTables.TableRef USER = JooqTables.SYS_USER;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /** 桥接自代码生成层 {@code Tables.SYS_NOTIFICATION_PREFERENCE} 的类型安全表引用（B19）。 */
    private static final JooqTables.TableRef PREFERENCE =
            JooqTables.SYS_NOTIFICATION_PREFERENCE;

    // ------------------------------------------------------------------
    // 自服务：矩阵查询与批量更新
    // ------------------------------------------------------------------

    /** 我的订阅矩阵（4 渠道 × 4 消息类型 = 16 格，每格带生效值与来源）。 */
    public NotificationPreferenceMatrix mine() {
        Long userId = currentUserId();
        SysUserDO user = JooqWriters.fetchById(dsl, USER, SysUserDO.class, userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        Map<Channel, Boolean> master = new LinkedHashMap<>();
        master.put(Channel.IN_APP, NotificationPreferenceRules.channelSwitch(
                user.getNotifySiteMessage(), Channel.IN_APP));
        master.put(Channel.EMAIL, NotificationPreferenceRules.channelSwitch(
                user.getNotifyEmail(), Channel.EMAIL));
        master.put(Channel.SMS, NotificationPreferenceRules.channelSwitch(
                user.getNotifyMobile(), Channel.SMS));
        // WEBSOCKET 无 sys_user 列：不放入 map，buildMatrix 按列默认值（恒开）兜底

        List<SysNotificationPreferenceDO> rows = JooqWriters.fetchList(dsl,
                PREFERENCE, SysNotificationPreferenceDO.class,
                JooqTables.SYS_NOTIFICATION_PREFERENCE.field("user_id", Long.class).eq(userId));
        return NotificationPreferenceRules.buildMatrix(master, groupRows(rows));
    }

    /**
     * 批量更新我的订阅（局部更新：仅提交的格子生效，未提交的保持原值）。
     *
     * <p>{@code enabled=0/1} 走「查现存 → 更新或插入」（本仓动态 DSL 无 upsert API，
     * 唯一键 {@code uk_user_channel_type} 兜底并发）；{@code enabled=null} 表示恢复默认，
     * 物理删行回到「无行默认开通」。</p>
     *
     * @return 更新后的完整矩阵（与 {@link #mine()} 同口径，省一次前端回查）
     */
    @Transactional
    public NotificationPreferenceMatrix update(UpdateNotificationPreferenceRequest request) {
        Long userId = currentUserId();
        List<PreferenceItemRequest> items = request.items();
        if (items == null || items.isEmpty()) {
            throw BusinessException.badRequest("偏好项不能为空");
        }
        // 先全额校验再落库：避免「前几项写成功、后一项非法」的半成品状态
        List<ResolvedItem> resolved = new ArrayList<>(items.size());
        for (PreferenceItemRequest item : items) {
            Channel channel = parseChannel(item.channel());
            if (channel == null) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "不支持设置偏好的渠道: " + item.channel());
            }
            String msgType = NotificationPreferenceRules.normalizeMsgType(item.msgType());
            if (msgType == null) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "不支持的消息类型: " + item.msgType());
            }
            resolved.add(new ResolvedItem(channel, msgType, item.enabled()));
        }
        for (ResolvedItem item : resolved) {
            applyItem(userId, item);
        }
        return mine();
    }

    /** 校验通过的偏好项（渠道/类型已归一，enabled 三态原样保留）。 */
    private record ResolvedItem(Channel channel, String msgType, Integer enabled) {
    }

    private void applyItem(Long userId, ResolvedItem item) {
        SysNotificationPreferenceDO exist = JooqWriters.fetchOne(dsl,
                PREFERENCE, SysNotificationPreferenceDO.class,
                JooqTables.SYS_NOTIFICATION_PREFERENCE.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_NOTIFICATION_PREFERENCE.field("channel", String.class).eq(item.channel().name()))
                        .and(JooqTables.SYS_NOTIFICATION_PREFERENCE.field("msg_type", String.class).eq(item.msgType())));
        if (item.enabled() == null) {
            // 恢复默认：删行，让「从未设置过」与「设过又还原」在库里同构（均无行）
            if (exist != null) {
                JooqWriters.delete(dsl, PREFERENCE, exist.getId(), false);
            }
            return;
        }
        if (exist != null) {
            SysNotificationPreferenceDO patch = new SysNotificationPreferenceDO();
            patch.setEnabled(item.enabled());
            JooqWriters.updateById(dsl, PREFERENCE,
                    exist.getId(), patch);
            return;
        }
        SysNotificationPreferenceDO row = new SysNotificationPreferenceDO();
        row.setUserId(userId);
        row.setChannel(item.channel().name());
        row.setMsgType(item.msgType());
        row.setEnabled(item.enabled());
        JooqWriters.insert(dsl, PREFERENCE, row);
    }

    // ------------------------------------------------------------------
    // 发送前过滤（NotificationService.dispatch 挂点）
    // ------------------------------------------------------------------

    /**
     * 按接收者偏好过滤消息。
     *
     * @return 过滤后的消息（无退订时原样返回同一实例）；全部定向接收者均退订时返回
     *         {@code null}——调用方应记一条 {@code SKIPPED} 发送记录并跳过投递
     */
    public NotificationMessage applyPreferences(NotificationMessage message) {
        Channel channel = message.channel();
        if (!NotificationPreferenceRules.supportsPreference(channel)) {
            return message;
        }
        String msgTypeKey = NotificationPreferenceRules.filterKey(message.msgType());
        switch (channel) {
            case IN_APP:
                return applyByReceivers(message, msgTypeKey, true);
            case WEBSOCKET:
                // WEBSOCKET 的空 receiverIds 即「广播」语义：定向接收者全退订时必须整体跳过，
                // 绝不能退化成向全部在线用户广播
                return applyByReceivers(message, msgTypeKey, false);
            case EMAIL:
                return applyByAddresses(message, msgTypeKey, true);
            case SMS:
                return applyByAddresses(message, msgTypeKey, false);
            default:
                return message;
        }
    }

    /** 按 userId 列表过滤（IN_APP / WEBSOCKET）。 */
    private NotificationMessage applyByReceivers(NotificationMessage message, String msgTypeKey,
                                                 boolean allowBroadcastFallback) {
        List<Long> ids = message.receiverIds();
        if (ids == null || ids.isEmpty()) {
            return message;
        }
        List<Long> kept = filterReceivers(message.channel(), msgTypeKey, ids);
        if (kept.isEmpty()) {
            // 定向接收者全部退订：IN_APP 若仍带角色/机构广播面则继续（走批量投递），否则整体跳过
            boolean hasBroadcast = allowBroadcastFallback
                    && (hasElements(message.roleIds()) || hasElements(message.orgIds()));
            return hasBroadcast ? withReceivers(message, kept) : null;
        }
        return kept.size() == ids.size() ? message : withReceivers(message, kept);
    }

    /** 按地址列表过滤（EMAIL / SMS）：地址反查 sys_user 后逐用户判定，外部地址默认放行。 */
    private NotificationMessage applyByAddresses(NotificationMessage message, String msgTypeKey,
                                                 boolean byEmail) {
        List<String> addresses = byEmail ? message.emails() : message.mobiles();
        if (addresses == null || addresses.isEmpty()) {
            // 无接收方：原样放行，由适配器按既有语义报错（保持 FAILED 留痕，不伪装成 SKIPPED）
            return message;
        }
        List<String> kept = filterAddresses(message.channel(), msgTypeKey, addresses, byEmail);
        if (kept.isEmpty()) {
            return null;
        }
        return kept.size() == addresses.size() ? message : withAddresses(message, kept, byEmail);
    }

    /**
     * 过滤定向用户列表（保序，null 元素剔除）；渠道不支持偏好时原样返回。
     *
     * <p>两次单次 IN 查询：细分偏好行 + sys_user 渠道总开关列。</p>
     */
    public List<Long> filterReceivers(Channel channel, String msgType, List<Long> receiverIds) {
        if (receiverIds == null || receiverIds.isEmpty()
                || !NotificationPreferenceRules.supportsPreference(channel)) {
            return receiverIds;
        }
        String msgTypeKey = NotificationPreferenceRules.filterKey(msgType);
        List<Long> userIds = new ArrayList<>(receiverIds.size());
        for (Long id : receiverIds) {
            if (id != null) {
                userIds.add(id);
            }
        }
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Boolean> rows = fetchRowEnabled(channel, msgTypeKey, userIds);
        Map<Long, Boolean> master = fetchChannelMaster(channel, userIds);
        return NotificationPreferenceRules.filterReceivers(channel, receiverIds, rows, master);
    }

    /**
     * 过滤地址列表（邮件/短信，保序；空白地址不过滤，交由适配器按既有语义处理）；
     * 渠道不支持偏好或地址无法映射到任何用户时原样返回。
     *
     * @param byEmail true 按 email 列反查（键小写归一），false 按 mobile 列反查
     */
    public List<String> filterAddresses(Channel channel, String msgType, List<String> addresses,
                                        boolean byEmail) {
        if (addresses == null || addresses.isEmpty()
                || !NotificationPreferenceRules.supportsPreference(channel)) {
            return addresses;
        }
        String msgTypeKey = NotificationPreferenceRules.filterKey(msgType);
        Map<String, Long> addressUserIds = fetchAddressUserIds(addresses, byEmail);
        if (addressUserIds.isEmpty()) {
            return addresses;
        }
        List<Long> userIds = new ArrayList<>(addressUserIds.values());
        Map<Long, Boolean> rows = fetchRowEnabled(channel, msgTypeKey, userIds);
        Map<Long, Boolean> master = fetchChannelMaster(channel, userIds);
        return NotificationPreferenceRules.filterAddresses(channel, addresses, addressUserIds,
                rows, master, byEmail);
    }

    // ------------------------------------------------------------------
    // 免登录退订写入口（M15 退订端点复用）
    // ------------------------------------------------------------------

    /**
     * 把指定用户的指定 (渠道 × 消息类型) 置为不退订（{@code enabled=0}）。
     *
     * <p>与 {@link #update} 的分工：本方法userId 由调用方提供（退订令牌承载），
     * <b>不走安全上下文</b>——公开退订端点没有登录主体；写入口径（校验 + 查现存 →
     * 更新或插入）与批量更新完全一致，唯一键 {@code uk_user_channel_type} 兜底并发。</p>
     *
     * <p>渠道须支持偏好（WEBHOOK 拒绝）、消息类型须在白名单内（归一后非空），
     * 否则抛校验异常——令牌只能由本系统铸造，二者不满足即数据异常，不放行。</p>
     *
     * @param userId  退订目标用户（令牌承载，非前端传入）
     * @param channel 退订目标渠道
     * @param msgType 退订目标消息类型（空白按缺省类型归一）
     * @return {@code true} = 本次发生状态变更（此前未退订）；{@code false} = 已是退订态（幂等）
     */
    @Transactional
    public boolean unsubscribe(Long userId, Channel channel, String msgType) {
        if (userId == null || !NotificationPreferenceRules.supportsPreference(channel)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "不支持的退订目标");
        }
        String type = NotificationPreferenceRules.normalizeMsgType(msgType);
        if (type == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "不支持的消息类型: " + msgType);
        }
        SysNotificationPreferenceDO exist = JooqWriters.fetchOne(dsl,
                PREFERENCE, SysNotificationPreferenceDO.class,
                JooqTables.SYS_NOTIFICATION_PREFERENCE.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_NOTIFICATION_PREFERENCE.field("channel", String.class).eq(channel.name()))
                        .and(JooqTables.SYS_NOTIFICATION_PREFERENCE.field("msg_type", String.class).eq(type)));
        if (exist != null) {
            if (exist.getEnabled() != null && exist.getEnabled() == 0) {
                return false;
            }
            SysNotificationPreferenceDO patch = new SysNotificationPreferenceDO();
            patch.setEnabled(0);
            JooqWriters.updateById(dsl, PREFERENCE,
                    exist.getId(), patch);
            return true;
        }
        SysNotificationPreferenceDO row = new SysNotificationPreferenceDO();
        row.setUserId(userId);
        row.setChannel(channel.name());
        row.setMsgType(type);
        row.setEnabled(0);
            JooqWriters.insert(dsl, PREFERENCE, row);
        return true;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 批量取 (渠道, 类型) 偏好行：userId → enabled。absent = 无行（默认开通）。 */
    private Map<Long, Boolean> fetchRowEnabled(Channel channel, String msgTypeKey, List<Long> userIds) {
        List<SysNotificationPreferenceDO> rows = JooqWriters.fetchList(dsl,
                PREFERENCE, SysNotificationPreferenceDO.class,
                JooqTables.SYS_NOTIFICATION_PREFERENCE.field("user_id", Long.class).in(userIds)
                        .and(JooqTables.SYS_NOTIFICATION_PREFERENCE.field("channel", String.class).eq(channel.name()))
                        .and(JooqTables.SYS_NOTIFICATION_PREFERENCE.field("msg_type", String.class).eq(msgTypeKey)));
        Map<Long, Boolean> out = new HashMap<>(rows.size() * 2);
        for (SysNotificationPreferenceDO row : rows) {
            if (row.getUserId() != null) {
                out.put(row.getUserId(), row.getEnabled() == null || row.getEnabled() != 0);
            }
        }
        return out;
    }

    /**
     * 批量取渠道总开关：userId → 开关值（null 列值按列默认值兜底）。
     * WEBSOCKET 无 sys_user 列，返回空 map（判定处按恒开兜底）。
     */
    private Map<Long, Boolean> fetchChannelMaster(Channel channel, List<Long> userIds) {
        String column = NotificationPreferenceRules.channelColumn(channel);
        if (column == null) {
            return Map.of();
        }
        Field<Long> idField = USER.field("id", Long.class);
        Field<Integer> switchField = USER.field(column, Integer.class);
        Map<Long, Boolean> out = new HashMap<>(userIds.size() * 2);
        dsl.select(idField, switchField).from(USER.table())
                .where(DSL.and(idField.in(userIds), JooqWriters.notDeleted(USER)))
                .fetch()
                .forEach(r -> out.put(r.get(idField),
                        NotificationPreferenceRules.channelSwitch(r.get(switchField), channel)));
        return out;
    }

    /**
     * 地址 → userId 反查（soft-deleted 用户除外）。公开供 M15 退订链接注入复用：
     * 邮件/短信适配器按地址列表批量取用户，逐用户铸造退订令牌。
     *
     * <p>键为归一化地址：email 小写（DB collation 大小写不敏感，存储值可能与原写大小写不一致），
     * mobile 原样 trim。absent = 无匹配用户（外部收件人，注入方按「无链接」处理）。</p>
     */
    public Map<String, Long> fetchAddressUserIds(List<String> addresses, boolean byEmail) {
        String column = byEmail ? "email" : "mobile";
        Field<Long> idField = USER.field("id", Long.class);
        Field<String> addrField = USER.field(column, String.class);
        List<String> distinct = new ArrayList<>();
        for (String address : addresses) {
            if (address != null && !address.isBlank() && !distinct.contains(address.trim())) {
                distinct.add(address.trim());
            }
        }
        Map<String, Long> out = new HashMap<>(distinct.size() * 2);
        if (distinct.isEmpty()) {
            return out;
        }
        dsl.select(idField, addrField).from(USER.table())
                .where(DSL.and(addrField.in(distinct), JooqWriters.notDeleted(USER)))
                .fetch()
                .forEach(r -> {
                    String addr = r.get(addrField);
                    if (addr != null && !addr.isBlank()) {
                        out.put(byEmail ? addr.trim().toLowerCase(Locale.ROOT) : addr.trim(),
                                r.get(idField));
                    }
                });
        return out;
    }

    /** 偏好行按 (渠道 → 消息类型 → 值) 分组；非法历史行忽略，不影响矩阵渲染。 */
    private static Map<Channel, Map<String, Boolean>> groupRows(List<SysNotificationPreferenceDO> rows) {
        Map<Channel, Map<String, Boolean>> out = new LinkedHashMap<>();
        for (SysNotificationPreferenceDO row : rows) {
            Channel channel = parseChannel(row.getChannel());
            String msgType = NotificationPreferenceRules.normalizeMsgType(row.getMsgType());
            if (channel == null || msgType == null) {
                continue;
            }
            out.computeIfAbsent(channel, k -> new LinkedHashMap<>())
                    .put(msgType, row.getEnabled() == null || row.getEnabled() != 0);
        }
        return out;
    }

    private static Channel parseChannel(String channel) {
        String normalized = NotificationPreferenceRules.normalizeChannel(channel);
        return normalized == null ? null : Channel.valueOf(normalized);
    }

    private static boolean hasElements(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private Long currentUserId() {
        return securityContextFacade.requireCurrentPrincipal().userId();
    }

    private static NotificationMessage withReceivers(NotificationMessage message, List<Long> kept) {
        return new NotificationMessage(message.channel(), message.title(), message.content(),
                message.msgType(), kept, message.roleIds(), message.orgIds(),
                message.webhookUrl(), message.extra(), message.emails(), message.mobiles());
    }

    private static NotificationMessage withAddresses(NotificationMessage message, List<String> kept,
                                                     boolean byEmail) {
        return byEmail
                ? new NotificationMessage(message.channel(), message.title(), message.content(),
                        message.msgType(), message.receiverIds(), message.roleIds(), message.orgIds(),
                        message.webhookUrl(), message.extra(), kept, message.mobiles())
                : new NotificationMessage(message.channel(), message.title(), message.content(),
                        message.msgType(), message.receiverIds(), message.roleIds(), message.orgIds(),
                        message.webhookUrl(), message.extra(), message.emails(), kept);
    }
}
