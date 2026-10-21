package com.acme.scaffold.job.domain;

import java.time.Duration;

/**
 * 固定间隔任务的时间单位（{@link JobTriggerType#SIMPLE} 专用）。
 * <p>最小间隔 5 秒（与 JobRunr {@code Schedule.SMALLEST_SCHEDULE_IN_SECONDS} 保持一致），
 * 由 {@link JobValidator} 在创建/编辑时拦截过小间隔，避免调度器高频空转。
 */
public enum JobIntervalUnit {

    SECONDS("秒", 1L),
    MINUTES("分钟", 60L),
    HOURS("小时", 3600L),
    DAYS("天", 86400L);

    private final String label;
    private final long seconds;

    JobIntervalUnit(String label, long seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    public String getLabel() {
        return label;
    }

    /** 将「值 + 单位」折算为 {@link Duration}，供 JobRunr {@code scheduleRecurrently(id, duration, lambda)} 使用。 */
    public Duration toDuration(int value) {
        return Duration.ofSeconds(Math.multiplyExact(this.seconds, (long) value));
    }
}
