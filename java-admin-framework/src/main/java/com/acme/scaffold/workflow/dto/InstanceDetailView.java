package com.acme.scaffold.workflow.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流实例详情视图：聚合实例基础信息、发起用户、当前活跃任务与历史审批记录，
 * 供「流程详情」页一次性拉取，避免多次往返。
 */
public record InstanceDetailView(
        String processInstanceId,
        String businessType,
        String businessId,
        String title,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long starterUserId,
        String starterUsername,
        String currentActivityId,
        List<TaskView> currentTasks,
        List<ApprovalRecordView> records) {
}
