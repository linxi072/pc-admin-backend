package com.acme.scaffold.system.notifydigest;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.notification.config.NotificationDigestProperties;
import com.acme.scaffold.system.notifydigest.domain.SysNotificationDigestDO;
import com.acme.scaffold.system.notifydigest.rules.NotificationDigestRules;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.acme.scaffold.system.rules.NotificationTemplateRules;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通知聚合摘要应用服务（V45 聚合中心）：低价值高频通知先聚合、按阈值或最大等待时长
 * 合并为一条摘要再投递，与 M13（谁想收什么）、M14（什么时候收）共同构成接收侧完整口径。
 *
 * <p><b>挂点位置</b>：由 {@code NotificationService.dispatch} 在免打扰（M14）与偏好（M13）
 * 过滤<b>之后</b>、实际路由投递<b>之前</b>调用——只聚合「本来会投递」的消息，
 * 已退订/免打扰抑制的不进摘要（聚合不做二次拦截，只做频次合并）。</p>
 *
 * <p><b>聚合范围</b>（{@link #shouldDigest} 纯预检，不触库）：
 * <ul>
 *   <li>消息类型命中 {@code app.notifications.digest.msg-types} 白名单（默认 NOTICE）；</li>
 *   <li>IN_APP / WEBSOCKET：有明确 {@code receiverIds}（广播无个人投递箱，不聚合）；</li>
 *   <li>EMAIL / SMS：有 {@code emails} / {@code mobiles}（全映射才聚合，见下）；</li>
 *   <li>WEBHOOK：外部系统地址，不聚合（与 M13/M14 广播不过滤口径一致的文档化限制）。</li>
 * </ul>
 * </p>
 *
 * <p><b>全映射才聚合</b>（EMAIL/SMS）：地址经 {@link NotificationPreferenceService#fetchAddressUserIds}
 * 反查 sys_user，任一地址无法归属注册用户（外部收件人）即整体放弃聚合、走正常投递——
 * 防「部分消息进摘要、部分直发」的丢消息风险。已知限制：混合内外收件人的邮件不聚合。</p>
 *
 * <p><b>合并写入</b>（{@link #enqueue}）：逐用户「查现存 → 更新或插入」（本仓动态 DSL
 * 无 upsert API，唯一键 {@code uk_user_channel_type} 兜底并发）；{@code item_count}
 * 累加全量、{@code items_json} 保留最近 maxItems 条、{@code last_at} 前推。
 * 合并后条数达 {@code threshold} 的聚合行随结果返回（{@code flushRows}），
 * 由调用方在同一dispatch 内立即刷新投递——先到先投，防摘要无限囤积。</p>
 *
 * <p><b>兜底刷新</b>（{@link #findDue}，定时任务用）：{@code last_at} 超过
 * {@code max-wait-minutes} 的聚合行强制刷新，防低流量用户「永远差一条」。
 * 投递编排（findDue → buildDigestMessage → dispatch → delete）由
 * {@code NotificationDigestJob} 承担——本服务<b>不依赖</b> NotificationService，
 * 二者经任务层组合，包依赖图保持 DAG（H1）。</p>
 *
 * <p><b>一次性投递语义</b>：摘要行无论投递成功/跳过/失败均删（由调用方删）——
 * 聚合行是待投递缓冲而非档案，反复重投只会制造 FAILED 日志轰炸；审计由 M12 承担
 * （摘要消息本身也走 dispatch 全链路留痕）。瞬时故障的恢复依赖 M16 的投递内重试，
 * 与直发路径同口径；重投增强（attempts 列 + 有界重投）属后续可选项。</p>
 *
 * <p><b>并发说明</b>：「查现存 → 更新」非原子，同一用户并发两条消息可能丢一次计数
 * （item_count  cosmetic、items_json 至多少一条）——与 M13/M14 同范式接受，
 * 唯一键仅兜底插入并发。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDigestService {
    private static final JooqTables.TableRef USER = JooqTables.SYS_USER;

    /** 统计截止时刻格式（摘要正文 {@code ${date}}）。 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 未聚合（直通）结果：{@code digested=false}、无待刷新行。
     * 单例不可变，挂点与任务路径共用。
     */
    public static final EnqueueResult NOT_DIGESTED = new EnqueueResult(false, List.of());

    private final DSLContext dsl;
    private final NotificationDigestProperties properties;
    private final NotificationPreferenceService preferenceService;
    private final ObjectMapper objectMapper;

    /** 桥接自代码生成层 {@code Tables.SYS_NOTIFICATION_DIGEST} 的类型安全表引用（B17）。 */
    private static final JooqTables.TableRef DIGEST = JooqTables.SYS_NOTIFICATION_DIGEST;

    /**
     * 聚合入队结果。
     *
     * @param digested  消息是否已被聚合吞并（true 时调用方不得再投递原消息，记 DIGEST_PENDING 并返回 0）
     * @param flushRows 本次合并后达到阈值的聚合行（待立即刷新投递；投递后由调用方删行）
     */
    public record EnqueueResult(boolean digested, List<SysNotificationDigestDO> flushRows) {
    }

    // ------------------------------------------------------------------
    // 发送前挂点（NotificationService.dispatch 调用）
    // ------------------------------------------------------------------

    /**
     * 聚合功能是否启用（挂点预检与定时任务短路共用同一开关口径）。
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 消息是否应当聚合（纯预检：配置开关 + 类型白名单 + 渠道/目标形态，不触库）。
     *
     * <p>关闭开关、类型未命中、广播/WEBHOOK、无定向目标均为 {@code false}——
     * 调用方按原消息直通投递，行为与 M16 及以前一致。</p>
     */
    public boolean shouldDigest(NotificationMessage message) {
        if (message == null || !properties.isEnabled()) {
            return false;
        }
        if (!NotificationDigestRules.matchesConfigured(message.msgType(), properties.getMsgTypes())) {
            return false;
        }
        return hasDigestibleTargets(message);
    }

    /**
     * 聚合写入：命中预检的消息按目标用户合并进聚合行。
     *
     * @return 聚合结果；未命中预检或目标用户为空时返回 {@link #NOT_DIGESTED}（原消息直通）
     */
    public EnqueueResult enqueue(NotificationMessage message) {
        if (!shouldDigest(message)) {
            return NOT_DIGESTED;
        }
        List<Long> userIds = resolveTargetUserIds(message);
        if (userIds.isEmpty()) {
            return NOT_DIGESTED;
        }
        Channel channel = message.channel();
        String msgType = NotificationDigestRules.normalizeMsgType(message.msgType());
        String item = NotificationDigestRules.summarizeItem(message.title(), message.content());
        LocalDateTime now = LocalDateTime.now();
        int threshold = Math.max(1, properties.getThreshold());
        int maxItems = properties.getMaxItems();
        List<SysNotificationDigestDO> flushRows = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            SysNotificationDigestDO exist = fetchRow(userId, channel, msgType);
            int newCount = (exist != null && exist.getItemCount() != null ? exist.getItemCount() : 0) + 1;
            List<String> items = exist != null ? readItems(exist.getItemsJson()) : new ArrayList<>();
            if (!item.isEmpty()) {
                items = NotificationDigestRules.appendItem(items, item, maxItems);
            }
            String itemsJson = writeItems(items);
            if (exist != null) {
                SysNotificationDigestDO patch = new SysNotificationDigestDO();
                patch.setItemCount(newCount);
                patch.setItemsJson(itemsJson);
                patch.setLastAt(now);
                JooqWriters.updateById(dsl, DIGEST, exist.getId(), patch);
                exist.setItemCount(newCount);
                exist.setItemsJson(itemsJson);
                exist.setLastAt(now);
                if (newCount >= threshold) {
                    flushRows.add(exist);
                }
            } else {
                SysNotificationDigestDO row = new SysNotificationDigestDO();
                row.setUserId(userId);
                row.setChannel(channel.name());
                row.setMsgType(msgType);
                row.setItemCount(newCount);
                row.setItemsJson(itemsJson);
                row.setFirstAt(now);
                row.setLastAt(now);
                JooqWriters.insert(dsl, DIGEST, row);
                if (newCount >= threshold) {
                    flushRows.add(row);
                }
            }
        }
        return new EnqueueResult(true, flushRows);
    }

    // ------------------------------------------------------------------
    // 兜底刷新（NotificationDigestJob 调用）
    // ------------------------------------------------------------------

    /**
     * 取到达刷新时点的聚合行（{@code last_at ≤ now − max-wait-minutes}，最旧优先）。
     *
     * @param limit 单批上限（{@code JooqWriters.fetchTop} 内钳 1..1000，防全表扫描）
     */
    public List<SysNotificationDigestDO> findDue(int limit) {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(Math.max(0, properties.getMaxWaitMinutes()));
        Condition cond = JooqTables.SYS_NOTIFICATION_DIGEST.field("last_at", java.time.LocalDateTime.class).le(cutoff);
        return JooqWriters.fetchTop(dsl, DIGEST,
                SysNotificationDigestDO.class, cond, limit,
                JooqTables.SYS_NOTIFICATION_DIGEST.field("last_at", java.time.LocalDateTime.class).asc());
    }

    /**
     * 由聚合行构造摘要消息（msgType 恒为 {@value NotificationDigestRules#DIGEST_MSG_TYPE}）。
     *
     * <p>渠道字段按行渠道填充：IN_APP / WEBSOCKET 定向 {@code receiverIds=[userId]}；
     * EMAIL / SMS <b>投递时现查</b> {@code sys_user} 的 email/mobile（聚合期间用户可能
     * 改地址，不沿用入队时快照）。</p>
     *
     * @return 摘要消息；用户已删或渠道地址缺失（无处可投）返回 {@code null}——调用方应删行
     */
    public NotificationMessage buildDigestMessage(SysNotificationDigestDO row) {
        if (row == null || row.getUserId() == null) {
            return null;
        }
        Channel channel = parseChannel(row.getChannel());
        if (channel == null) {
            return null;
        }
        String address = null;
        if (channel == Channel.EMAIL || channel == Channel.SMS) {
            SysUserDO user = JooqWriters.fetchById(dsl, USER,
                    SysUserDO.class, row.getUserId());
            if (user == null) {
                return null;
            }
            address = channel == Channel.EMAIL ? user.getEmail() : user.getMobile();
            if (address == null || address.isBlank()) {
                return null;
            }
        }
        int count = row.getItemCount() == null ? 0 : row.getItemCount();
        Map<String, String> vars = new HashMap<>(4);
        vars.put("count", String.valueOf(count));
        vars.put("items", NotificationDigestRules.renderItems(readItems(row.getItemsJson()), count));
        vars.put("date", DATE_FORMAT.format(LocalDateTime.now()));
        String title = NotificationTemplateRules.render(properties.getTitleTemplate(), vars);
        String content = NotificationTemplateRules.render(properties.getContentTemplate(), vars);
        return switch (channel) {
            case IN_APP, WEBSOCKET -> new NotificationMessage(channel, title, content,
                    NotificationDigestRules.DIGEST_MSG_TYPE, List.of(row.getUserId()),
                    null, null, null, null, null, null);
            case EMAIL -> new NotificationMessage(channel, title, content,
                    NotificationDigestRules.DIGEST_MSG_TYPE, null, null, null, null, null,
                    List.of(address), null);
            case SMS -> new NotificationMessage(channel, title, content,
                    NotificationDigestRules.DIGEST_MSG_TYPE, null, null, null, null, null,
                    null, List.of(address));
            case WEBHOOK, WECOM, DINGTALK -> null; // 不可达：机器人渠道不建聚合行，防御性返回
        };
    }

    /**
     * 物理删除聚合行（一次性投递语义：投递尝试完成即删，无逻辑删除列）。
     * 审计由 M12 通知发送记录承担。
     */
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        JooqWriters.delete(dsl, DIGEST, id, false);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 渠道 + 目标形态预检：仅「有个人投递箱」的定向消息可聚合。 */
    private static boolean hasDigestibleTargets(NotificationMessage message) {
        Channel channel = message.channel();
        if (channel == null) {
            return false;
        }
        switch (channel) {
            case IN_APP, WEBSOCKET:
                // 空 receiverIds 即广播语义（受众规模不定，无个人投递箱），不聚合
                return hasElements(message.receiverIds());
            case EMAIL:
                return hasElements(message.emails());
            case SMS:
                return hasElements(message.mobiles());
            default:
                // WEBHOOK：外部系统地址，无个人聚合语义
                return false;
        }
    }

    /**
     * 解析目标用户（保序去重）：IN_APP / WEBSOCKET 取 receiverIds；
     * EMAIL / SMS 反查 sys_user，<b>全映射才聚合</b>——存在外部地址即返回空列表
     * （调用方按未聚合直通，防部分投递丢消息）。
     */
    private List<Long> resolveTargetUserIds(NotificationMessage message) {
        Channel channel = message.channel();
        if (channel == Channel.IN_APP || channel == Channel.WEBSOCKET) {
            return distinctIds(message.receiverIds());
        }
        boolean byEmail = channel == Channel.EMAIL;
        List<String> addresses = byEmail ? message.emails() : message.mobiles();
        Map<String, Long> addressUserIds = preferenceService.fetchAddressUserIds(addresses, byEmail);
        List<Long> ids = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            if (address == null || address.isBlank()) {
                continue;
            }
            Long userId = addressUserIds.get(
                    byEmail ? address.trim().toLowerCase(Locale.ROOT) : address.trim());
            if (userId == null) {
                return List.of();
            }
            ids.add(userId);
        }
        return distinctIds(ids);
    }

    /** 保序去重（剔除 null）；空输入返回空列表。 */
    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        return new ArrayList<>(unique);
    }

    private SysNotificationDigestDO fetchRow(Long userId, Channel channel, String msgType) {
        return JooqWriters.fetchOne(dsl, DIGEST,
                SysNotificationDigestDO.class,
                JooqTables.SYS_NOTIFICATION_DIGEST.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_NOTIFICATION_DIGEST.field("channel", String.class).eq(channel.name()))
                        .and(JooqTables.SYS_NOTIFICATION_DIGEST.field("msg_type", String.class).eq(msgType)));
    }

    /** 解析 items_json；损坏数据按空列表处理（行仍可按 item_count 刷新，不阻断投递）。 */
    private List<String> readItems(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> items = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        } catch (Exception e) {
            log.warn("聚合条目 JSON 解析失败，按空列表处理 err={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 序列化条目为 JSON 字符串数组；序列化失败返回 null（列不写，item_count 仍准）。 */
    private String writeItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("聚合条目 JSON 序列化失败，按空处理 err={}", e.getMessage());
            return null;
        }
    }

    private static Channel parseChannel(String channel) {
        String normalized = NotificationTemplateRules.normalizeChannel(channel);
        return normalized == null ? null : Channel.valueOf(normalized);
    }

    private static boolean hasElements(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
