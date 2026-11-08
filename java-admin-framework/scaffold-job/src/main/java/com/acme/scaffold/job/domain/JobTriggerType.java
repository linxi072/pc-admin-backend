package com.acme.scaffold.job.domain;

/**
 * 任务触发类型。
 * <ul>
 *   <li>{@link #CRON}：crontab 表达式（业务侧 5 段分钟精度，注册时归一化为 Quartz 6 段），
 *       由 {@code QuartzSchedulerPort} 以 CronTrigger 注册；</li>
 *   <li>{@link #SIMPLE}：固定间隔（值 + 单位），以 Quartz SimpleTrigger 重复注册；</li>
 *   <li>{@link #ONE_TIME}：单次定时执行，以 Quartz Trigger 的 startAt 注册，
 *       执行完成后任务进入 COMPLETED。</li>
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
