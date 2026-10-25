package com.acme.scaffold.workflow.dto;

/**
 * 运行中流程实例监控条目（M23）：在「我发起的」个人面之外补管理员全局视角，
 * 带停留时长与当前节点，供监控页定位滞留流程。
 */
public record RunningInstanceView(
        String processInstanceId,
        String businessType,
        String businessId,
        String title,
        String status,
        java.time.LocalDateTime startedAt,
        long stayedMinutes,
        String currentActivityId,
        Long starterUserId,
        String starterUsername) {
}
