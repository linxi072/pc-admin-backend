package com.acme.scaffold.system.notifydigest.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 通知聚合摘要纯函数规则：配置匹配决策、单行摘要生成、条目保留与摘要正文渲染。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link com.acme.scaffold.system.notifydigest.NotificationDigestService}
 * （聚合写入 / 兜底刷新 / 摘要消息构造）与
 * {@link com.acme.scaffold.system.notification.service.NotificationService}
 * （dispatch 挂点）复用，保证「按什么聚合」与「聚成什么」两处口径同源——
 * 与 {@code NotificationQuietHoursRules} / {@code NotificationPreferenceRules} 同范式。</p>
 *
 * <p><b>消息类型口径</b>：被聚合类型由 {@code app.notifications.digest.msg-types} 配置
 * （大小写不敏感、空白按缺省类型归一）；摘要消息自身类型恒为
 * {@value #DIGEST_MSG_TYPE}，刻意置于配置白名单之外——不再被挂点挂起（防递归），
 * 也不在 M13 偏好白名单（NOTICE/ALERT/SYSTEM/CC）内——无偏好行、默认开通，
 * 仅受 {@code sys_user} 渠道总开关约束，天然复用 M13/M14/M16 全链路。</p>
 */
public final class NotificationDigestRules {

    /**
     * 摘要消息自身的消息类型（递归防护 + 偏好透传的双重设计，见类注释）。
     */
    public static final String DIGEST_MSG_TYPE = "DIGEST";

    /** 缺省消息类型（配置或消息 msgType 空白时归一目标，与 M13 偏好口径一致）。 */
    public static final String DEFAULT_MSG_TYPE = "NOTICE";

    /** 聚合挂起时记入通知发送记录的原因（M12 error_message 列复用）。 */
    public static final String REASON_DIGEST_PENDING = "已进入聚合摘要队列，等待摘要投递";

    /** 单行摘要长度上限（防 items_json 无界膨胀；标题/正文均按此截断）。 */
    public static final int MAX_ITEM_LEN = 80;

    private NotificationDigestRules() {
    }

    // ------------------------------------------------------------------
    // 配置匹配
    // ------------------------------------------------------------------

    /**
     * 消息是否命中聚合配置（msgTypes 白名单，大小写不敏感）。
     *
     * @param msgType    消息类型（空白按 {@value #DEFAULT_MSG_TYPE} 归一）
     * @param configured 配置的白名单（如 {@code [NOTICE]}；空白元素忽略）
     * @return {@code true} = 应聚合（调用方仍需校验其余前置条件）
     */
    public static boolean matchesConfigured(String msgType, List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return false;
        }
        String normalized = normalizeMsgType(msgType);
        for (String candidate : configured) {
            if (candidate != null && !candidate.isBlank()
                    && candidate.trim().toUpperCase(Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** 消息类型归一：trim + 大写；空白回退缺省类型（与 M13 {@code filterKey} 口径一致）。 */
    public static String normalizeMsgType(String msgType) {
        if (msgType == null || msgType.isBlank()) {
            return DEFAULT_MSG_TYPE;
        }
        return msgType.trim().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // 条目摘要与保留
    // ------------------------------------------------------------------

    /**
     * 生成单行摘要：标题优先、正文回退（WEBHOOK 等无标题渠道），trim 后截断至
     * {@value #MAX_ITEM_LEN} 字符（不追加省略号——条目在数组内紧凑排列，
     * 截断信息由 item_count 的全量计数补偿）。全空白返回空串（调用方跳过不入库）。
     */
    public static String summarizeItem(String title, String content) {
        String text = title != null && !title.isBlank() ? title : content;
        if (text == null || text.isBlank()) {
            return "";
        }
        String flat = text.trim().replaceAll("\\s+", " ");
        return flat.length() <= MAX_ITEM_LEN ? flat : flat.substring(0, MAX_ITEM_LEN);
    }

    /**
     * 追加条目并保留最近 {@code maxItems} 条（超出丢弃最旧）。
     *
     * @param items    现存条目（调用方持有，方法内不改动入参，返回新列表）
     * @param item     新条目（空白串由调用方先行跳过）
     * @param maxItems 保留上限（&le;0 按 1 处理，至少保留最新一条）
     */
    public static List<String> appendItem(List<String> items, String item, int maxItems) {
        int keep = Math.max(maxItems, 1);
        List<String> merged = new ArrayList<>(items == null ? 1 : items.size() + 1);
        if (items != null) {
            merged.addAll(items);
        }
        merged.add(item);
        while (merged.size() > keep) {
            merged.remove(0);
        }
        return merged;
    }

    // ------------------------------------------------------------------
    // 摘要正文渲染
    // ------------------------------------------------------------------

    /**
     * 渲染条目列表为摘要正文（{@code "1. x\n2. y\n…共N条"}）：仅展示保留的条目，
     * 全量条数由 {@code itemCount} 补偿表达，避免正文无限增长。
     *
     * @param items     保留中的条目（空列表返回空串）
     * @param itemCount 全量聚合条数（&le; items.size() 时省略「共N条」后缀）
     */
    public static String renderItems(List<String> items, int itemCount) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(items.size() * (MAX_ITEM_LEN + 4));
        for (int i = 0; i < items.size(); i++) {
            sb.append(i + 1).append(". ").append(items.get(i));
            if (i < items.size() - 1) {
                sb.append('\n');
            }
        }
        if (itemCount > items.size()) {
            sb.append("\n…共").append(itemCount).append("条");
        }
        return sb.toString();
    }
}
