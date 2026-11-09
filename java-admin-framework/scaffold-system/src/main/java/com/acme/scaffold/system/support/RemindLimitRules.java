package com.acme.scaffold.system.support;

/**
 * 催办批量上限收敛共享规则。
 *
 * <p>{@code MessageReadRules} 与 {@code AnnouncementReadRules} 原本各自内联了一份完全相同的
 * 催办批量收敛逻辑（含 {@code MAX_REMIND_LIMIT} / {@code DEFAULT_REMIND_LIMIT} 两个常量）；此处收敛为单一事实来源，
 * 两规则类保留同名公开方法作为委托壳、常量指回本类，对外 API 不变。</p>
 */
public final class RemindLimitRules {

    /** 催办单次人数上限：催办是打扰性动作，保守取值（远低于站内信群发上限 5000）。 */
    public static final int MAX_REMIND_LIMIT = 500;

    /** 催办未显式指定批量时的默认值。 */
    public static final int DEFAULT_REMIND_LIMIT = 100;

    private RemindLimitRules() {
    }

    /**
     * 催办批量收敛：{@code null} 取默认值；越界值收敛到 {@code [1, MAX_REMIND_LIMIT]}。
     * 收敛而非报错——催办上限是保护性阈值，调用方传 0/负数/超大值都按最接近的合法值执行。
     */
    public static int resolveRemindLimit(Integer requested) {
        int limit = requested == null ? DEFAULT_REMIND_LIMIT : requested;
        return Math.min(Math.max(limit, 1), MAX_REMIND_LIMIT);
    }
}
