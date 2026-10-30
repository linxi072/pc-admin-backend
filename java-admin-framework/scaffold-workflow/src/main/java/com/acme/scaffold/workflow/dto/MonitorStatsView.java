package com.acme.scaffold.workflow.dto;

/**
 * 流程监控统计摘要（M23）：监控页顶部指标卡，一次拉取避免多次往返。
 *
 * @param runningInstances 运行中实例数（wf_instance_ext.status=RUNNING）
 * @param overdueTasks     已到期未办结任务数（引擎视角，dueDate &lt; now）
 * @param pendingTasks     待办任务数（wf_task_ext.status=PENDING）
 * @param todayStarted     今日发起实例数
 * @param todayFinished    今日办结实例数（APPROVED/REJECTED 均计入）
 */
public record MonitorStatsView(
        long runningInstances,
        long overdueTasks,
        long pendingTasks,
        long todayStarted,
        long todayFinished) {
}
