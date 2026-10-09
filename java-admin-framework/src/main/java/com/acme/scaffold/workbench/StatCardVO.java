package com.acme.scaffold.workbench;

/**
 * 工作台统计卡片。
 *
 * <p>趋势由「当前值 vs 上一周期值」推导，属纯函数逻辑（见 {@link #of}），便于离线单测。
 * 快照型指标（如「我的待办」）不存在可比的上一周期值，此时 previousValue 取当前值、
 * trend 为 {@code FLAT}、changeRate 为 0，前端据 trend 展示「持平」而非伪造涨跌。
 */
public record StatCardVO(String key, String label, long value, long previousValue,
                         double changeRate, String trend, String unit) {

    public static final String TREND_UP = "UP";
    public static final String TREND_DOWN = "DOWN";
    public static final String TREND_FLAT = "FLAT";

    /**
     * 由当前值与上一周期值构造卡片，自动推导变化率（百分比，保留 1 位）与趋势方向。
     *
     * <p>边界：上一周期为 0 时，若当前值亦为 0 记 0%（持平），否则记 100%（从无到有）。
     */
    public static StatCardVO of(String key, String label, long value, long previousValue, String unit) {
        double rate;
        if (previousValue == 0L) {
            rate = value == 0L ? 0.0 : 100.0;
        } else {
            rate = (value - previousValue) * 100.0 / previousValue;
        }
        String trend = rate > 0 ? TREND_UP : (rate < 0 ? TREND_DOWN : TREND_FLAT);
        return new StatCardVO(key, label, value, previousValue, round1(rate), trend, unit);
    }

    /** 快照型指标：无上一周期可比，趋势恒为持平。 */
    public static StatCardVO snapshot(String key, String label, long value, String unit) {
        return new StatCardVO(key, label, value, value, 0.0, TREND_FLAT, unit);
    }

    private static double round1(double rate) {
        return Math.round(rate * 10.0) / 10.0;
    }
}
