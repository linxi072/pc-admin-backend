package com.acme.scaffold.job.management;

/**
 * 任务触发类型。
 * <ul>
 *   <li>{@link #CRON}：crontab 表达式（支持 5 段分钟精度 / 6 段秒精度），由 JobRunr
 *       {@code scheduleRecurrently(id, cron, lambda)} 注册为周期性任务；</li>
 *   <li>{@link #SIMPLE}：固定间隔（值 + 单位），由 JobRunr
 *       {@code scheduleRecurrently(id, Duration, lambda)} 注册为周期任务；</li>
 *   <li>{@link #ONE_TIME}：单次定时执行，由 JobRunr
 *       {@code schedule(UUID, Instant, lambda)} 注册为一次性任务，执行完成后任务进入 COMPLETED。</li>
 * </ul>
 */
public enum JobTriggerType {

    CRON("cron 表达式"),
    SIMPLE("固定间隔"),
    ONE_TIME("单次执行");

    private final String label;

    JobTriggerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
