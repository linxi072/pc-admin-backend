package com.acme.scaffold.workflow.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workflow.dto.CompleteTaskRequest;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.dto.TransferTaskRequest;
import com.acme.scaffold.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工作流任务")
@RestController
@RequestMapping("/api/workflow/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final WorkflowService workflowService;

    @Operation(summary = "我的待办任务")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('workflow:task:read')")
    public Result<List<TaskView>> mine() {
        return Result.success(workflowService.myTasks());
    }

    @Operation(summary = "审批/驳回任务")
    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('workflow:task:approve')")
    @AuditOperation(module = "workflow", type = "APPROVE", name = "审批任务")
    public Result<Void> complete(@Valid @RequestBody CompleteTaskRequest request) {
        workflowService.completeTask(request);
        return Result.success();
    }

    @Operation(summary = "转办任务")
    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('workflow:task:transfer')")
    @AuditOperation(module = "workflow", type = "TRANSFER", name = "转办任务")
    public Result<Void> transfer(@Valid @RequestBody TransferTaskRequest request) {
        workflowService.transfer(request);
        return Result.success();
    }

    @Operation(summary = "认领任务")
    @PostMapping("/{taskId}/claim")
    @PreAuthorize("hasAuthority('workflow:task:claim')")
    @AuditOperation(module = "workflow", type = "CLAIM", name = "认领任务")
    public Result<Void> claim(@PathVariable String taskId) {
        workflowService.claim(taskId);
        return Result.success();
    }
}
