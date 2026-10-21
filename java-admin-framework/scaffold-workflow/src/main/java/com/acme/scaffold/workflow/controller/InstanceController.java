package com.acme.scaffold.workflow.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workflow.dto.ApprovalRecordView;
import com.acme.scaffold.workflow.dto.InstanceDetailView;
import com.acme.scaffold.workflow.dto.InstanceView;
import com.acme.scaffold.workflow.dto.StartProcessRequest;
import com.acme.scaffold.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工作流实例")
@RestController
@RequestMapping("/api/workflow/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final WorkflowService workflowService;

    @Operation(summary = "发起审批流程")
    @PostMapping("/start")
    @PreAuthorize("hasAuthority('workflow:instance:start')")
    @AuditOperation(module = "workflow", type = "CREATE", name = "发起审批流程")
    public Result<String> start(@Valid @RequestBody StartProcessRequest request) {
        return Result.success(workflowService.start(request));
    }

    @Operation(summary = "我发起的流程")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('workflow:instance:read')")
    public Result<List<InstanceView>> mine() {
        return Result.success(workflowService.myInstances());
    }

    @Operation(summary = "流程审批记录")
    @GetMapping("/{processInstanceId}/records")
    @PreAuthorize("hasAuthority('workflow:instance:read')")
    public Result<List<ApprovalRecordView>> records(@PathVariable String processInstanceId) {
        return Result.success(workflowService.records(processInstanceId));
    }

    @Operation(summary = "流程实例详情")
    @GetMapping("/{processInstanceId}")
    @PreAuthorize("hasAuthority('workflow:instance:read')")
    public Result<InstanceDetailView> detail(@PathVariable String processInstanceId) {
        return Result.success(workflowService.detail(processInstanceId));
    }
}
