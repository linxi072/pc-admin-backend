package com.acme.scaffold.system.rules;

/**
 * 公告已读回执纯函数规则：已读率推导、催办批量收敛、可催办状态判定。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@code AnnouncementReadService} 与视图工厂调用。
 *
 * <p>分母口径：已读率以 {@code sys_announcement.audience_count}（发布时活跃用户
 * 快照）为分母。懒插入模型下回执表只含已读行，V40 之前的存量公告该列为 0，
 * 此时一律按「无法计算」处理，返回 0 而不是用 readCount 自造分母，避免假精确。
 */
public final class AnnouncementReadRules {

    /** 催办单次人数上限：催办是打扰性动作，保守取值（远低于站内信群发上限 5000）。 */
    public static final int MAX_REMIND_LIMIT = 500;

    /** 催办未显式指定批量时的默认值。 */
    public static final int DEFAULT_REMIND_LIMIT = 100;

    private AnnouncementReadRules() {
    }

    /**
     * 已读率（百分比，保留一位小数）。
     *
     * @param audienceCount 分母（发布时活跃用户快照）；{@code <= 0} 表示分母缺失，返回 0.0
     * @param readCount     已读人数；负值按 0、超过分母时按分母收敛，保证比率 ∈ [0, 100]
     */
    public static double readRate(int readCount, int audienceCount) {
        if (audienceCount <= 0) {
            return 0.0;
        }
        int clamped = Math.min(Math.max(readCount, 0), audienceCount);
        return Math.round(clamped * 1000.0 / audienceCount) / 10.0;
    }

    /**
     * 未读人数（分母口径）。{@code audienceCount <= 0} 时懒插入模型无法推导全量未读
     * （回执表只含已读行，活跃用户集合需另行统计），返回 0 交由前端按「暂无数据」展示。
     */
    public static int unreadCount(int readCount, int audienceCount) {
        if (audienceCount <= 0) {
            return 0;
        }
        int clamped = Math.min(Math.max(readCount, 0), audienceCount);
        return audienceCount - clamped;
    }

    /**
     * 催办批量收敛：{@code null} 取默认值；越界值收敛到 {@code [1, MAX_REMIND_LIMIT]}。
     * 收敛而非报错——催办上限是保护性阈值，调用方传 0/负数/超大值都按最接近的合法值执行。
     */
    public static int resolveRemindLimit(Integer requested) {
        int limit = requested == null ? DEFAULT_REMIND_LIMIT : requested;
        return Math.min(Math.max(limit, 1), MAX_REMIND_LIMIT);
    }

    /** 仅已发布（PUBLISHED）公告可催办：草稿/已下线/已过期的催办没有业务意义。 */
    public static boolean isRemindable(String status) {
        return "PUBLISHED".equals(status);
    }
}
