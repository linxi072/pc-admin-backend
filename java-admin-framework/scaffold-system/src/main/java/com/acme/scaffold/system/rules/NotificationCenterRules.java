package com.acme.scaffold.system.rules;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 通知中心纯函数规则：最近条目合并排序、未读上限钳制、合计推导。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供 {@code NotificationCenterService}
 * 调用。来源两类条目（公告未读、站内信未读）的时间基准不同（发布时间 / 发送时间），
 * 合并时统一按 {@code occurredAt} 倒序，null 沉底。
 */
public final class NotificationCenterRules {

    /** 近期条目默认返回条数。 */
    public static final int DEFAULT_RECENT = 10;

    /** 近期条目上限：抽屉单次渲染条数护栏，防止超大请求拖垮接口。 */
    public static final int MAX_RECENT = 50;

    private NotificationCenterRules() {
    }

    /**
     * 近期条目数量收敛：{@code null} 取默认；越界值收敛到 {@code [1, MAX_RECENT]}。
     * 收敛而非报错——分页参数是保护性阈值，调用方传 0/负数/超大值都按最接近的合法值执行。
     */
    public static int clampRecentLimit(Integer requested) {
        int limit = requested == null ? DEFAULT_RECENT : requested;
        if (limit < 1) {
            limit = 1;
        }
        return Math.min(limit, MAX_RECENT);
    }

    /** 未读合计：公告未读 + 站内信未读（允许任一方为 0 或正数）。 */
    public static long totalUnread(long announcementUnread, long messageUnread) {
        return announcementUnread + messageUnread;
    }

    /**
     * 合并两类近期未读条目并按时间倒序截断。
     *
     * <p>泛型实现保持 rules 包零向上依赖（不引用 dto 视图类型），调用方传入
     * {@code occurredAt} 取值器即可；本方法只关心排序键与时间方向。
     *
     * @param announcements 公告未读条目（已按置顶+发布时间排序）
     * @param messages      站内信未读条目（已按未读优先+时间倒序排序）
     * @param occurredAt    时间键取值器（用于按时间倒序，null 沉底）
     * @param limit         截断上限（应已先经 {@link #clampRecentLimit} 收敛）
     * @param <T>           条目类型（调用方通常为 dto.NotificationItemView）
     * @return 合并后按 occurredAt 倒序、长度不超过 limit 的列表（原地不可变，返回新列表）
     */
    public static <T> List<T> mergeAndSortRecent(
            List<T> announcements,
            List<T> messages,
            Function<T, LocalDateTime> occurredAt,
            int limit) {
        List<T> merged = new ArrayList<>();
        if (announcements != null) {
            merged.addAll(announcements);
        }
        if (messages != null) {
            merged.addAll(messages);
        }
        merged.sort(Comparator.comparing(
                occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return merged.subList(0, Math.min(merged.size(), limit));
    }
}
