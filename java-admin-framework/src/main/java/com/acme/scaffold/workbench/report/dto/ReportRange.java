package com.acme.scaffold.workbench.report.dto;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表管理的时间范围类型。
 *
 * <p>时间窗解析为纯函数（见 {@link #resolve}），不依赖数据库或时钟单例，便于离线单测注入固定「当前时刻」。</p>
 *
 * <ul>
 *   <li>{@code TODAY}：今日 00:00 至今，按<b>小时</b>分桶（24 个整点）；</li>
 *   <li>{@code LAST_7D} / {@code LAST_30D}：含今日的近 7/30 天，按<b>天</b>分桶；</li>
 *   <li>{@code CUSTOM}：自定义起止日期（闭区间，结束日含当天 23:59:59.999），按天分桶，必须同时提供 start/end。</li>
 * </ul>
 */
public enum ReportRange {

    TODAY,
    LAST_7D,
    LAST_30D,
    CUSTOM;

    /** 该范围默认的分桶粒度。 */
    public BucketMode bucketMode() {
        return this == TODAY ? BucketMode.HOUR : BucketMode.DAY;
    }

    /**
     * 解析为具体时间窗。纯函数：传入 {@code now} 即可确定窗口，无副作用。
     *
     * @param now   当前时刻（由调用方注入，便于测试固定时间）
     * @param start 自定义范围起始日期（仅 CUSTOM 需要）
     * @param end   自定义范围结束日期（仅 CUSTOM 需要）
     */
    public ResolvedWindow resolve(LocalDateTime now, LocalDate start, LocalDate end) {
        return switch (this) {
            case TODAY -> new ResolvedWindow(
                    now.toLocalDate().atStartOfDay(),
                    now,
                    BucketMode.HOUR);
            case LAST_7D -> dayWindow(now, 7);
            case LAST_30D -> dayWindow(now, 30);
            case CUSTOM -> {
                if (start == null || end == null) {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                            "自定义时间范围必须同时提供开始与结束日期");
                }
                if (start.isAfter(end)) {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
                }
                yield new ResolvedWindow(
                        start.atStartOfDay(),
                        end.atTime(23, 59, 59, 999_000_000),
                        BucketMode.DAY);
            }
        };
    }

    private static ResolvedWindow dayWindow(LocalDateTime now, int days) {
        LocalDate from = now.toLocalDate().minusDays(days - 1L);
        return new ResolvedWindow(from.atStartOfDay(), now, BucketMode.DAY);
    }

    /** 分桶粒度：小时（0–23）或天（按日期）。 */
    public enum BucketMode {
        HOUR,
        DAY
    }

    /** 解析后的具体时间窗（含分桶粒度）。 */
    public record ResolvedWindow(LocalDateTime from, LocalDateTime to, BucketMode bucketMode) {
    }
}
