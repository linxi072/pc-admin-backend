package com.acme.scaffold.system.notifylog.rules;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.sensitive.SensitiveMasker;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 通知发送记录纯函数规则：接收方按渠道掩码、标题/正文摘要、筛选值归一。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link com.acme.scaffold.system.notifylog.NotificationRecordRecorder}（落库前脱敏摘要）与
 * {@link com.acme.scaffold.system.notifylog.NotificationRecordService}（筛选值收敛）复用，
 * 保证「存什么」与「查什么」口径同源——与短信记录 {@code SmsRecordRules} 同范式。</p>
 */
public final class NotificationRecordRules {

    /** 结果码：成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 结果码：失败（渠道未启用、校验被拒、适配器或网关异常）。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 渠道缺失时的兜底编码（防御性：{@code NotificationMessage.channel} 理论可为 null）。 */
    public static final String CHANNEL_UNKNOWN = "UNKNOWN";

    /** 标题摘要长度上限（列宽 256，预留省略号空间）。 */
    public static final int MAX_TITLE_LEN = 200;

    /** 正文摘要长度上限（列宽 512，预留省略号空间）。 */
    public static final int MAX_CONTENT_LEN = 400;

    /** 接收方摘要长度上限（列宽 512）。 */
    public static final int MAX_RECEIVER_LEN = 400;

    /** ID 摘要最多展示的条目数，超出折叠为「…共N个」。 */
    private static final int MAX_IDS_SHOWN = 5;

    /** extra 摘要最多展示的键数。 */
    private static final int MAX_EXTRA_KEYS = 5;

    /** extra 单个值摘要长度上限。 */
    private static final int MAX_EXTRA_VALUE_LEN = 32;

    /** 摘要内折叠的空白/控制符（防日志注入与换行破坏表格）。 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t]");

    private NotificationRecordRules() {
    }

    // ------------------------------------------------------------------
    // 筛选值归一
    // ------------------------------------------------------------------

    /**
     * 渠道筛选值归一：按 {@link Channel} 枚举名匹配（大小写不敏感），
     * 未知渠道返回 {@code null}（调用方忽略该筛选条件，避免把非法值带入 SQL）。
     */
    public static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String c = channel.trim().toUpperCase();
        for (Channel value : Channel.values()) {
            if (value.name().equals(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 结果筛选值归一：仅接受 {@value #STATUS_SUCCESS} / {@value #STATUS_FAILED}（大小写不敏感），
     * 其余（含空）返回 {@code null}——调用方据此忽略该筛选条件。
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        return STATUS_SUCCESS.equals(s) || STATUS_FAILED.equals(s) ? s : null;
    }

    // ------------------------------------------------------------------
    // 摘要
    // ------------------------------------------------------------------

    /**
     * 标题摘要：trim → CR/LF/TAB 折叠为空格 → 连续空格合并 → 超长截断并追加省略号。
     * 空白标题返回空串（短信等无标题渠道不落 {@code "…"} 噪声）。
     */
    public static String summarizeTitle(String title) {
        return summarize(title, MAX_TITLE_LEN);
    }

    /**
     * 正文摘要：与标题同规则（trim → 控制符折叠 → 截断加省略号），上限 {@value #MAX_CONTENT_LEN}。
     */
    public static String summarizeContent(String content) {
        return summarize(content, MAX_CONTENT_LEN);
    }

    /**
     * extra 摘要（WEBHOOK 等无正文渠道的排障替代口径）：{@code {k1=v1, k2=v2, …}}，
     * 最多 {@value #MAX_EXTRA_KEYS} 个键、单值截 {@value #MAX_EXTRA_VALUE_LEN} 字符，整体再截到正文上限。
     * 空 map 返回空串。
     */
    public static String summarizeExtra(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return "";
        }
        String body = extra.entrySet().stream()
                .limit(MAX_EXTRA_KEYS)
                .map(e -> e.getKey() + "=" + truncate(String.valueOf(e.getValue()), MAX_EXTRA_VALUE_LEN))
                .collect(Collectors.joining(", "));
        if (extra.size() > MAX_EXTRA_KEYS) {
            body = body + ", …共" + extra.size() + "项";
        }
        return truncate("{" + body + "}", MAX_CONTENT_LEN);
    }

    // ------------------------------------------------------------------
    // 接收方掩码（按渠道）
    // ------------------------------------------------------------------

    /**
     * 单个手机号脱敏：trim 后复用 {@link SensitiveMasker#maskPhone}（{@code 138****8000}）；
     * 非号码形态原样返回（审计可读性优先），null 归一为空串。
     */
    public static String maskMobile(String mobile) {
        if (mobile == null) {
            return "";
        }
        return SensitiveMasker.maskPhone(mobile.trim());
    }

    /** 手机号列表掩码拼接（保序，逗号分隔）；空列表返回空串。 */
    public static String maskMobiles(List<String> mobiles) {
        if (mobiles == null || mobiles.isEmpty()) {
            return "";
        }
        return mobiles.stream().map(NotificationRecordRules::maskMobile).collect(Collectors.joining(","));
    }

    /** 邮箱列表掩码拼接（保序，逗号分隔；单邮箱形态为 {@code z***@example.com}）。 */
    public static String maskEmails(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return "";
        }
        return emails.stream()
                .map(e -> e == null ? "" : SensitiveMasker.maskEmail(e.trim()))
                .collect(Collectors.joining(","));
    }

    /**
     * webhook 地址掩码：保留 scheme://host/path，query 串整体抹除（{@code ?***}）——
     * 企业微信/钉钉等机器人的 key/token 均在 query 上；path 段无法与正常路径区分，不做处理。
     * null/空白返回空串，超长截断。
     */
    public static String maskWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q) + "?***";
        }
        return truncate(u, MAX_RECEIVER_LEN);
    }

    /**
     * ID 列表摘要：{@code 1,2,3}；超过 {@value #MAX_IDS_SHOWN} 个折叠为 {@code 1,2,3,…共8个}。
     * 空列表返回空串。
     */
    public static String idsSummary(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        String head = ids.stream()
                .limit(MAX_IDS_SHOWN)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return ids.size() > MAX_IDS_SHOWN ? head + ",…共" + ids.size() + "个" : head;
    }

    /**
     * 按渠道生成接收方摘要（全渠道脱敏口径同源）：
     * <ul>
     *   <li>SMS：掩码手机号列表；</li>
     *   <li>EMAIL：掩码邮箱列表；</li>
     *   <li>IN_APP：{@code 用户:…；角色:…；机构:…}（仅展示非空维度，全空为 {@code 未知}）；</li>
     *   <li>WEBHOOK：抹除 query 的地址；</li>
     *   <li>WEBSOCKET：定向 {@code 用户:…} 或 {@code 广播}。</li>
     * </ul>
     */
    public static String receiverSummary(NotificationMessage msg) {
        if (msg == null) {
            return "";
        }
        Channel ch = msg.channel();
        if (ch == null) {
            return "";
        }
        String out = switch (ch) {
            case SMS -> maskMobiles(msg.mobiles());
            case EMAIL -> maskEmails(msg.emails());
            case IN_APP -> inAppSummary(msg);
            case WEBHOOK -> maskWebhookUrl(msg.webhookUrl());
            case WEBSOCKET -> idsSummary(msg.receiverIds()).isEmpty()
                    ? "广播" : "用户:" + idsSummary(msg.receiverIds());
        };
        return truncate(out, MAX_RECEIVER_LEN);
    }

    /**
     * 目标接收数（权威口径，与 {@link #receiverSummary} 同源推导）：
     * SMS/EMAIL 取地址数；IN_APP/WEBSOCKET 取定向用户数（广播记 0）；WEBHOOK 记 1。
     */
    public static int receiverCount(NotificationMessage msg) {
        if (msg == null) {
            return 0;
        }
        Channel ch = msg.channel();
        if (ch == null) {
            return 0;
        }
        return switch (ch) {
            case SMS -> msg.mobiles() == null ? 0 : msg.mobiles().size();
            case EMAIL -> msg.emails() == null ? 0 : msg.emails().size();
            case IN_APP, WEBSOCKET -> msg.receiverIds() == null ? 0 : msg.receiverIds().size();
            case WEBHOOK -> 1;
        };
    }

    /** 通用截断：null 原样返回，超长截取前 {@code max} 字符（不追加省略号，用于定宽列）。 */
    public static String truncate(String text, int max) {
        if (text == null || max <= 0) {
            return max <= 0 ? "" : text;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** IN_APP 接收方摘要：按 用户/角色/机构 三维度拼接，仅展示非空维度。 */
    private static String inAppSummary(NotificationMessage msg) {
        StringBuilder sb = new StringBuilder();
        appendDim(sb, "用户", idsSummary(msg.receiverIds()));
        appendDim(sb, "角色", idsSummary(msg.roleIds()));
        appendDim(sb, "机构", idsSummary(msg.orgIds()));
        return sb.length() == 0 ? "未知" : sb.toString();
    }

    private static void appendDim(StringBuilder sb, String dim, String value) {
        if (value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('；');
        }
        sb.append(dim).append(':').append(value);
    }

    /** 摘要通用流水线：trim → 控制符折叠 → 连续空格合并 → 截断加省略号；空白输入返回空串。 */
    private static String summarize(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String flat = CONTROL_CHARS.matcher(text.trim()).replaceAll(" ").replaceAll(" +", " ").trim();
        if (flat.isEmpty()) {
            return "";
        }
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max) + "…";
    }
}
