package com.acme.scaffold.system.rules;

/**
 * 站内信已读回执纯函数规则：已读率推导、催办批量收敛、是否仍有未读判定。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@code MessageReadService} 与视图工厂调用。
 *
 * <p>与公告回执（懒插入模型，分母用发布时活跃用户快照）不同，站内信是
 * <b>eager 投递模型</b>——{@code sys_message_receipt} 在发送时已为每位接收人预插一行，
 * 仅 {@code is_read} 标记 0/1。因此「总数」即回执表行数、「已读/未读」按 {@code is_read}
 * 直接聚合，分母天然存在，无需反连接推导全量未读，统计口径更稳。
 */
public final class MessageReadRules {

    /** 催办单次人数上限：催办是打扰性动作，保守取值（远低于站内信群发上限 5000）。 */
    public static final int MAX_REMIND_LIMIT = 500;

    /** 催办未显式指定批量时的默认值。 */
    public static final int DEFAULT_REMIND_LIMIT = 100;

    private MessageReadRules() {
    }

    /**
     * 已读率（百分比，保留一位小数）。
     *
     * @param readCount   已读人数（回执表中 {@code is_read = 1} 的行数）
     * @param totalCount  接收人总数（回执表全部行数）；{@code <= 0} 表示分母缺失，返回 0.0
     */
    public static double readRate(int readCount, int totalCount) {
        if (totalCount <= 0) {
            return 0.0;
        }
        int clamped = Math.min(Math.max(readCount, 0), totalCount);
        return Math.round(clamped * 1000.0 / totalCount) / 10.0;
    }

    /**
     * 未读人数（eager 模型下 = 回执表中 {@code is_read = 0} 的行数）。
     * 分母缺失时返回 0；分子越界归一到 [0, total]，保证不为负。
     */
    public static int unreadCount(int readCount, int totalCount) {
        if (totalCount <= 0) {
            return 0;
        }
        int clamped = Math.min(Math.max(readCount, 0), totalCount);
        return totalCount - clamped;
    }

    /**
     * 催办批量收敛：{@code null} 取默认值；越界值收敛到 {@code [1, MAX_REMIND_LIMIT]}。
     * 收敛而非报错——催办上限是保护性阈值，调用方传 0/负数/超大值都按最接近的合法值执行。
     */
    public static int resolveRemindLimit(Integer requested) {
        int limit = requested == null ? DEFAULT_REMIND_LIMIT : requested;
        return Math.min(Math.max(limit, 1), MAX_REMIND_LIMIT);
    }

    /** 是否仍有未读：分母存在且已读数小于总数（无未读则无需催办，避免空发打扰）。 */
    public static boolean hasUnread(int readCount, int totalCount) {
        return totalCount > 0 && readCount < totalCount;
    }
}
