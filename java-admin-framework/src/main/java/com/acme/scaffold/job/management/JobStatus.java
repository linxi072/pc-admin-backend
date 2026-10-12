package com.acme.scaffold.job.management;

import java.util.Arrays;

/**
 * 任务调度状态（持久化于 {@code sys_job.status}）。
 * <ul>
 *   <li>{@link #PAUSED}：已暂停，未向调度器注册（新建任务默认此态，避免误触发）；</li>
 *   <li>{@link #RUNNING}：已启动，已注册到调度器并会按触发规则执行；</li>
 *   <li>{@link #COMPLETED}：一次性任务执行完成后的终态（周期任务不会进入此态）。</li>
 * </ul>
 */
public enum JobStatus {

    PAUSED("已暂停"),
    RUNNING("运行中"),
    COMPLETED("已完成");

    private final String label;

    JobStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 宽松解析（忽略大小写），非法值返回 {@link #PAUSED}，用于查询条件兜底。 */
    public static JobStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(PAUSED);
    }
}
