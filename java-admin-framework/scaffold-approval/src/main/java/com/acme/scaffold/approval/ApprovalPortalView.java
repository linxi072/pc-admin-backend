package com.acme.scaffold.approval;

import com.acme.scaffold.workflow.dto.InstanceView;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;

import java.util.List;

/**
 * 审批中心首页聚合视图：统一待办/已办/我发起/可发起 + 统计。
 * 复用工作流既有 DTO，门户层不重新定义任务/实例结构。
 */
public record ApprovalPortalView(
        List<TaskView> todoTasks,
        List<TaskView> doneTasks,
        List<InstanceView> myInstances,
        List<WorkflowDesignView> startable,
        ApprovalStats stats) {
}
