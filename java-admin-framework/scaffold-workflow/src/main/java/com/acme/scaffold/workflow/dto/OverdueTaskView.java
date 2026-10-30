package com.acme.scaffold.workflow.dto;

/**
 * 超时任务监控条目（M23）：已过到期时间仍未办结的任务，带办理人与超时期时长，
 * 供监控页催办（remind）与定位卡点。
 */
public record OverdueTaskView(
        String taskId,
        String taskName,
        String processInstanceId,
        String instanceTitle,
        String businessId,
        String activityId,
        Long assigneeUserId,
        String assigneeUsername,
        java.time.LocalDateTime dueAt,
        long overdueMinutes) {
}
