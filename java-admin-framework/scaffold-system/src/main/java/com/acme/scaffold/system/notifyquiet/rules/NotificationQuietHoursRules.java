package com.acme.scaffold.system.notifyquiet.rules;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 免打扰时段纯函数规则：时间窗解析与命中判定、跨午夜窗口、告警穿透决策、
 * 按接收者过滤与抑制原因。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link com.acme.scaffold.system.notifyquiet.NotificationQuietHoursService}
 * （自服务查询/更新 + 发送前过滤）与
 * {@link com.acme.scaffold.system.notification.service.NotificationService}
 * （dispatch 挂点）复用，保证「存什么、按什么抑制」两处口径同源。</p>
 *
 * <p><b>时间窗模型</b>（半开区间 {@code [start, end)}，服务器本地时间）：
 * <ul>
 *   <li>{@code start < end}：当日窗口（如 09:00–12:00），{@code now ∈ [start, end)} 命中；</li>
 *   <li>{@code start > end}：跨午夜窗口（如 22:00–08:00），{@code now >= start || now < end} 命中；</li>
 *   <li>{@code start == end}：退化窗口，写入侧拒绝（{@code ValidationError}），
 *       纯函数按「不命中」防御（校验先于落库，该分支不可达）。</li>
 * </ul>
 * </p>
 *
 * <p><b>告警穿透</b>：{@code msgType=ALERT} 且 {@code alertBreakthrough=1} 时免打扰不生效
 * （故障告警不应被时间窗吞掉）；其余消息类型（NOTICE/SYSTEM/CC/未知）一律受抑制。</p>
 */
public final class NotificationQuietHoursRules {

    /** 时间格式：{@code HH:mm} 24 小时制（与前端 el-time-picker 默认格式一致）。 */
    public static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /** 默认免打扰开始时间（表单缺省值，用户未配置时展示，不落库）。 */
    public static final String DEFAULT_START = "22:00";

    /** 默认免打扰结束时间（跨午夜：22:00–08:00，覆盖常见睡眠时段）。 */
    public static final String DEFAULT_END = "08:00";

    /** 告警消息类型（穿透免打扰的判定对象，大小写不敏感）。 */
    public static final String MSG_TYPE_ALERT = "ALERT";

    /** 免打扰抑制时的跳过原因（记入通知发送记录 error_message）。 */
    public static final String REASON_QUIET_HOURS = "免打扰时段内已抑制";

    private NotificationQuietHoursRules() {
    }

    // ------------------------------------------------------------------
    // 时间解析与校验
    // ------------------------------------------------------------------

    /**
     * 解析 {@code HH:mm} 时间为当天 {@link LocalTime}；非法格式（含空、越界、秒位）返回 {@code null}。
     */
    public static LocalTime parseTime(String value) {
        if (value == null || !TIME_PATTERN.matcher(value.trim()).matches()) {
            return null;
        }
        String[] parts = value.trim().split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    /** {@link LocalTime} → {@code HH:mm}（null 返回 null）。 */
    public static String formatTime(LocalTime time) {
        return time == null ? null
                : String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }

    /** 是否为合法窗口：起止均可解析且不相同（相同即退化，写入侧拒绝）。 */
    public static boolean isValidWindow(LocalTime start, LocalTime end) {
        return start != null && end != null && !start.equals(end);
    }

    // ------------------------------------------------------------------
    // 命中判定与穿透决策
    // ------------------------------------------------------------------

    /**
     * 当前时刻是否落在免打扰窗口内（半开区间 {@code [start, end)}，支持跨午夜）。
     *
     * <p>退化输入（null 或 {@code start == end}）返回 {@code false}——写入侧已拒绝，
     * 此处防御历史脏数据把全部消息误判为抑制。</p>
     */
    public static boolean isQuiet(LocalTime now, LocalTime start, LocalTime end) {
        if (now == null || !isValidWindow(start, end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // 跨午夜：22:00–08:00 命中「>= 22:00」或「< 08:00」
        return !now.isBefore(start) || now.isBefore(end);
    }

    /** 是否为告警消息类型（trim + 大小写不敏感；null/空白按非告警处理）。 */
    public static boolean isAlert(String msgType) {
        return msgType != null && MSG_TYPE_ALERT.equalsIgnoreCase(msgType.trim());
    }

    /**
     * 免打扰是否应当抑制该消息（纯函数，供批量过滤复用）。
     *
     * @param inQuiet          当前时刻是否命中该用户的免打扰窗口
     * @param alertBreakthrough 该用户是否配置告警穿透
     * @return true = 抑制（不投递）；false = 放行
     */
    public static boolean suppresses(String msgType, boolean inQuiet, boolean alertBreakthrough) {
        if (!inQuiet) {
            return false;
        }
        return !(isAlert(msgType) && alertBreakthrough);
    }

    /**
     * 单用户单类型抑制判定（纯函数，供批量过滤复用）。
     *
     * @param setting 该用户的免打扰配置；{@code null} = 无行（无免打扰，放行）
     */
    public static boolean isSuppressed(String msgType, LocalTime now, QuietSetting setting) {
        if (setting == null || !setting.enabled()) {
            return false;
        }
        return suppresses(msgType, isQuiet(now, setting.startTime(), setting.endTime()),
                setting.alertBreakthrough());
    }

    /**
     * 按免打扰过滤接收者列表（保序过滤，null 元素剔除，不改变保留元素的基数）：
     * 无配置行或未命中窗口的用户保留；ALERT 且配置穿透的用户保留。
     *
     * @param settings userId → 免打扰配置（absent = 无行）
     */
    public static List<Long> filterReceivers(String msgType, LocalTime now, List<Long> receiverIds,
                                             Map<Long, QuietSetting> settings) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return receiverIds == null ? List.of() : receiverIds;
        }
        List<Long> kept = new ArrayList<>(receiverIds.size());
        for (Long id : receiverIds) {
            if (id == null) {
                continue;
            }
            QuietSetting setting = settings == null ? null : settings.get(id);
            if (!isSuppressed(msgType, now, setting)) {
                kept.add(id);
            }
        }
        return kept;
    }

    // ------------------------------------------------------------------
    // 内嵌值对象（H4 不命中 Matrix/Cell 命名，随规则类归档）
    // ------------------------------------------------------------------

    /**
     * 单用户免打扰配置快照（由持久化对象转换而来，供纯函数过滤使用）。
     *
     * @param enabled          是否启用
     * @param startTime        窗口开始（含，服务器本地时间）
     * @param endTime          窗口结束（不含，服务器本地时间）
     * @param alertBreakthrough 告警是否穿透
     */
    public record QuietSetting(boolean enabled, LocalTime startTime, LocalTime endTime,
                               boolean alertBreakthrough) {
    }
}
