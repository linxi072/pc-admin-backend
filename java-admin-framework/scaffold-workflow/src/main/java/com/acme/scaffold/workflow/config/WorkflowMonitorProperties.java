package com.acme.scaffold.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 流程监控配置（{@code app.workflow.monitor.*}，M23）。
 *
 * <p>默认即生效，无需在 {@code application*.yml} 中显式声明。</p>
 */
@ConfigurationProperties(prefix = "app.workflow.monitor")
public class WorkflowMonitorProperties {

    /**
     * 同一任务两次催办的最小间隔（分钟）。防止监控页对同一任务高频点击造成办理人消息轰炸。
     * 设置为 0 或负数表示不限制（由部署方按容忍度决定）。
     */
    private int remindMinIntervalMinutes = 30;

    public int getRemindMinIntervalMinutes() {
        return remindMinIntervalMinutes;
    }

    public void setRemindMinIntervalMinutes(int remindMinIntervalMinutes) {
        // 负值按 0 处理（语义等价「不限制」），避免 Duration 计算出现负数分钟
        this.remindMinIntervalMinutes = Math.max(0, remindMinIntervalMinutes);
    }
}
