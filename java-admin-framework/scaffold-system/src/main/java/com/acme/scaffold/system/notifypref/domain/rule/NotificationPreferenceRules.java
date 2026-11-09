package com.acme.scaffold.system.notifypref.rules;

import com.acme.scaffold.common.notification.Channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通知偏好纯函数规则：渠道/消息类型白名单归一、sys_user 渠道总开关映射、
 * 「细分优先、无行默认开」的生效判定、渠道 × 类型矩阵构建与接收者分区。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link com.acme.scaffold.system.notifypref.NotificationPreferenceService}（矩阵查询/批量 upsert/
 * 发送前过滤）与 {@link com.acme.scaffold.system.notification.service.NotificationService}
 * （dispatch 挂点）复用，保证「存什么、查什么、按什么放行」三处口径同源。</p>
 *
 * <p><b>生效模型</b>（两级）：
 * <ol>
 *   <li>渠道总开关（{@code sys_user.notify_*} 列）：关闭时该渠道一切消息均不投递；</li>
 *   <li>细分偏好（本表行）：无行 = 默认开通，有行按 {@code enabled} 裁决。</li>
 * </ol>
 * 即 {@code effective = channelMaster && (row != null ? row.enabled : true)}。</p>
 */
public final class NotificationPreferenceRules {

    /** 可设偏好的渠道（有序，决定前端矩阵行序）：WEBSOCKET 无 sys_user 列，总开关恒开。 */
    public static final List<Channel> SUPPORTED_CHANNELS =
            List.of(Channel.IN_APP, Channel.EMAIL, Channel.SMS, Channel.WEBSOCKET);

    /** 可设偏好的消息类型白名单（有序，决定前端矩阵列序）：站内信消息类型域。 */
    public static final List<String> MSG_TYPES = List.of("NOTICE", "ALERT", "SYSTEM", "CC");

    /** 消息类型缺省值（与 MessageService 直发默认口径一致）。 */
    public static final String DEFAULT_MSG_TYPE = "NOTICE";

    /** 生效值来源：渠道总开关（总开关关闭时强制关闭，行值被短路）。 */
    public static final String SOURCE_CHANNEL = "CHANNEL";

    /** 生效值来源：用户显式设置的细分偏好行。 */
    public static final String SOURCE_PREFERENCE = "PREFERENCE";

    /** 生效值来源：无行默认开通。 */
    public static final String SOURCE_DEFAULT = "DEFAULT";

    /** 全部接收者均退订时的跳过原因（记入通知发送记录 error_message）。 */
    public static final String REASON_ALL_UNSUBSCRIBED = "全部接收者已退订该类型通知";

    private NotificationPreferenceRules() {
    }

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    /** 该渠道是否支持按用户偏好过滤（WEBHOOK 无个人偏好语义：地址属外部系统，拒绝建行）。 */
    public static boolean supportsPreference(Channel channel) {
        return channel != null && SUPPORTED_CHANNELS.contains(channel);
    }

    /**
     * 渠道筛选值归一：按 {@link Channel} 枚举名匹配（大小写不敏感）且须在可设偏好白名单内，
     * 否则（含 WEBHOOK、未知值、空）返回 {@code null}——调用方据此拒绝或忽略。
     */
    public static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String c = channel.trim().toUpperCase(Locale.ROOT);
        for (Channel value : SUPPORTED_CHANNELS) {
            if (value.name().equals(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 消息类型归一：空白归一为 {@value #DEFAULT_MSG_TYPE}；白名单内（大小写不敏感）原样返回；
     * 白名单外返回 {@code null}（调用方拒绝写入）。
     */
    public static String normalizeMsgType(String msgType) {
        if (msgType == null || msgType.isBlank()) {
            return DEFAULT_MSG_TYPE;
        }
        String m = msgType.trim().toUpperCase(Locale.ROOT);
        return MSG_TYPES.contains(m) ? m : null;
    }

    /**
     * 发送前过滤用的消息类型键：与 {@link #normalizeMsgType} 一致，但白名单外的非空值
     * 原样透传（trim + 大写）——偏好表只可能存在白名单行，未知类型自然无行匹配、走默认开通，
     * 不会因类型拼写差异把本应放行的消息误判为退订。
     */
    public static String filterKey(String msgType) {
        String normalized = normalizeMsgType(msgType);
        if (normalized != null) {
            return normalized;
        }
        return msgType == null ? DEFAULT_MSG_TYPE : msgType.trim().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // sys_user 渠道总开关映射
    // ------------------------------------------------------------------

    /**
     * 渠道 → sys_user 总开关列名；WEBSOCKET 无对应列，返回 {@code null}（总开关恒开，
     * 仅由细分偏好行裁决）。
     */
    public static String channelColumn(Channel channel) {
        if (channel == null) {
            return null;
        }
        return switch (channel) {
            case IN_APP -> "notify_site_message";
            case EMAIL -> "notify_email";
            case SMS -> "notify_mobile";
            case WEBSOCKET, WEBHOOK, WECOM, DINGTALK -> null;
        };
    }

    /**
     * 渠道总开关的列默认值（与 {@code SysUserDO} 字段默认值一致）：
     * IN_APP/EMAIL 默认开，SMS 默认关（短信为选择性订阅语义），WEBSOCKET 无列恒开。
     * 用户行缺失（理论异常）时以默认值兜底，避免把通知整体误判为退订。
     */
    public static boolean channelDefault(Channel channel) {
        return channel != Channel.SMS;
    }

    /**
     * 把 sys_user 列值（0/1/null）解析为总开关布尔值；null 按 {@link #channelDefault} 兜底。
     */
    public static boolean channelSwitch(Integer raw, Channel channel) {
        return raw == null ? channelDefault(channel) : raw != 0;
    }

    // ------------------------------------------------------------------
    // 生效判定与分区
    // ------------------------------------------------------------------

    /**
     * 单用户单类型生效判定（纯函数，供批量过滤复用）。
     *
     * @param rowEnabled   该用户该 (渠道, 类型) 的偏好行值；{@code null} = 无行（默认开通）
     * @param channelMaster 该用户该渠道的总开关；{@code null} = 用户行缺失，按列默认值兜底
     */
    public static boolean isEnabled(Channel channel, Long userId,
                                    Map<Long, Boolean> rowEnabled, Map<Long, Boolean> channelMaster) {
        if (userId == null) {
            return false;
        }
        boolean master = channelMaster == null
                ? channelDefault(channel)
                : channelMaster.getOrDefault(userId, channelDefault(channel));
        if (!master) {
            return false;
        }
        Boolean row = rowEnabled == null ? null : rowEnabled.get(userId);
        return row == null ? true : row;
    }

    /**
     * 按偏好分区接收者列表（保序过滤，null 元素剔除，不改变保留元素的基数）：
     * 总开关关闭或无行默认开/有行按值裁决，仅保留生效者。
     *
     * @param rowEnabled    userId → 偏好行值（absent = 无行）
     * @param channelMaster userId → 渠道总开关（absent = 按列默认值兜底）
     */
    public static List<Long> filterReceivers(Channel channel, List<Long> receiverIds,
                                             Map<Long, Boolean> rowEnabled,
                                             Map<Long, Boolean> channelMaster) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return receiverIds == null ? List.of() : receiverIds;
        }
        List<Long> kept = new ArrayList<>(receiverIds.size());
        for (Long id : receiverIds) {
            if (isEnabled(channel, id, rowEnabled, channelMaster)) {
                kept.add(id);
            }
        }
        return kept;
    }

    /**
     * 按偏好过滤地址列表（邮件/短信用，保序）：地址无法映射到 sys_user 时无法判定偏好，
     * 按默认开通放行（外部收件人不因注册用户偏好被误拦）；空白地址不过滤，
     * 交由适配器按既有语义处理（保持 FAILED 留痕，不伪装成退订）。
     *
     * @param addressUserIds 归一化地址 → userId；absent = 无匹配用户
     * @param caseInsensitiveKey 地址键是否大小写不敏感（email 为 true：DB collation CI，
     *                           存储值可能与原写大小写不一致，故键统一小写）
     */
    public static List<String> filterAddresses(Channel channel, List<String> addresses,
                                               Map<String, Long> addressUserIds,
                                               Map<Long, Boolean> rowEnabled,
                                               Map<Long, Boolean> channelMaster,
                                               boolean caseInsensitiveKey) {
        if (addresses == null || addresses.isEmpty()) {
            return addresses == null ? List.of() : addresses;
        }
        List<String> kept = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            if (address == null || address.isBlank()) {
                kept.add(address);
                continue;
            }
            String key = caseInsensitiveKey
                    ? address.trim().toLowerCase(Locale.ROOT) : address.trim();
            Long userId = addressUserIds == null ? null : addressUserIds.get(key);
            if (userId == null || isEnabled(channel, userId, rowEnabled, channelMaster)) {
                kept.add(address);
            }
        }
        return kept;
    }

    // ------------------------------------------------------------------
    // 矩阵构建（个人中心「通知订阅」页数据源）
    // ------------------------------------------------------------------

    /**
     * 构建「渠道 × 消息类型」订阅矩阵（{@value #MSG_TYPES#size} 类型 × 4 渠道 = 16 格）。
     *
     * <p>每格携带生效值与来源：总开关关闭时整行强制关闭（source={@value #SOURCE_CHANNEL}）；
     * 否则有行取行值（{@value #SOURCE_PREFERENCE}），无行默认开（{@value #SOURCE_DEFAULT}）。
     * 前端据此把「渠道总开关关闭」的格子置灰，把「默认开」的格子标注可恢复默认。</p>
     *
     * @param channelMaster 渠道 → 总开关（absent 的渠道按列默认值兜底，如 WEBSOCKET）
     * @param rows          渠道 → (消息类型 → 行值)，absent = 无行
     */
    public static NotificationPreferenceMatrix buildMatrix(Map<Channel, Boolean> channelMaster,
                                                           Map<Channel, Map<String, Boolean>> rows) {
        List<ChannelMatrix> channels = new ArrayList<>(SUPPORTED_CHANNELS.size());
        for (Channel ch : SUPPORTED_CHANNELS) {
            boolean master = channelMaster == null
                    ? channelDefault(ch)
                    : channelMaster.getOrDefault(ch, channelDefault(ch));
            Map<String, Boolean> rowMap = rows == null ? null : rows.get(ch);
            List<PreferenceCell> cells = new ArrayList<>(MSG_TYPES.size());
            for (String msgType : MSG_TYPES) {
                Boolean row = rowMap == null ? null : rowMap.get(msgType);
                String source;
                boolean enabled;
                if (!master) {
                    source = SOURCE_CHANNEL;
                    enabled = false;
                } else if (row != null) {
                    source = SOURCE_PREFERENCE;
                    enabled = row;
                } else {
                    source = SOURCE_DEFAULT;
                    enabled = true;
                }
                cells.add(new PreferenceCell(msgType, enabled, source));
            }
            channels.add(new ChannelMatrix(ch.name(), master, cells));
        }
        return new NotificationPreferenceMatrix(channels);
    }

    /** 矩阵中的单个渠道行：总开关 + 各消息类型格子。 */
    public record ChannelMatrix(String channel, boolean channelEnabled, List<PreferenceCell> cells) {
    }

    /** 矩阵中的单个 (渠道 × 消息类型) 格子。 */
    public record PreferenceCell(String msgType, boolean enabled, String source) {
    }

    /** 完整订阅矩阵（4 渠道有序）。 */
    public record NotificationPreferenceMatrix(List<ChannelMatrix> channels) {
    }
}
