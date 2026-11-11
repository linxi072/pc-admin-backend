package com.acme.scaffold.workflow.approval.application;

import com.acme.scaffold.workflow.approval.dto.ApprovalPortalView;
import com.acme.scaffold.workflow.approval.dto.ApprovalStats;
import com.acme.scaffold.workflow.definition.application.WorkflowDesignService;
import com.acme.scaffold.workflow.definition.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.instance.application.WorkflowService;
import com.acme.scaffold.workflow.instance.dto.InstanceDetailView;
import com.acme.scaffold.workflow.instance.dto.InstanceView;
import com.acme.scaffold.workflow.instance.dto.TaskView;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审批中心门户应用服务：薄编排层，仅聚合既有工作流能力，不重复实现引擎逻辑。
 *
 * <p>统一出口：待办（myTasks）、已办（myDoneTasks）、我发起（myInstances）、
 * 可发起（designService.list() 过滤 PUBLISHED）、流程详情（detail）与首页统计。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalPortalService {

    private final WorkflowService workflowService;
    private final WorkflowDesignService designService;

    public ApprovalPortalView dashboard() {
        List<TaskView> todo = workflowService.myTasks();
        List<TaskView> done = workflowService.myDoneTasks();
        List<InstanceView> mine = workflowService.myInstances();
        List<WorkflowDesignView> startable = startable();
        ApprovalStats stats = new ApprovalStats(todo.size(), done.size(), mine.size(), startable.size());
        return new ApprovalPortalView(todo, done, mine, startable, stats);
    }

    public List<TaskView> todo() {
        return workflowService.myTasks();
    }

    public List<TaskView> done() {
        return workflowService.myDoneTasks();
    }

    public List<InstanceView> myInstances() {
        return workflowService.myInstances();
    }

    public List<WorkflowDesignView> startable() {
        return designService.list().stream()
                .filter(v -> "PUBLISHED".equals(v.status()))
                .collect(Collectors.toList());
    }

    public InstanceDetailView detail(String processInstanceId) {
        return workflowService.detail(processInstanceId);
    }
}
