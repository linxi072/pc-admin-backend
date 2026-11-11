package com.acme.scaffold.workflow.approval.api;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.workflow.approval.application.ApprovalPortalService;
import com.acme.scaffold.workflow.approval.dto.ApprovalPortalView;
import com.acme.scaffold.workflow.definition.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.instance.dto.InstanceDetailView;
import com.acme.scaffold.workflow.instance.dto.InstanceView;
import com.acme.scaffold.workflow.instance.dto.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心门户接口：统一待办、已办、我发起、可发起与统计入口。
 *
 * <p>权限码 {@code approval:portal:view} 由 {@code ApiResourceAutoSyncRunner} 扫描
 * {@code @PreAuthorize} 自动登记并授予 SUPER_ADMIN；门户保持只读聚合，写操作统一收敛到
 * {@link ApprovalActionController}（{@code /api/approval/actions/**}），二者共用同一工作流引擎。</p>
 */
@Tag(name = "审批中心门户")
@RestController
@RequestMapping("/api/approval/portal")
@RequiredArgsConstructor
public class ApprovalPortalController {

    private final ApprovalPortalService portalService;

    @Operation(summary = "审批中心首页（统一待办/已办/我发起/可发起 + 统计）")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('approval:portal:view')")
    public Result<ApprovalPortalView> dashboard() {
        return Result.success(portalService.dashboard());
    }

    @Operation(summary = "我的待办")
    @GetMapping("/todo")
    @PreAuthorize("hasAuthority('approval:portal:view')")
    public Result<List<TaskView>> todo() {
        return Result.success(portalService.todo());
    }

    @Operation(summary = "我的已办")
    @GetMapping("/done")
    @PreAuthorize("hasAuthority('approval:portal:view')")
    public Result<List<TaskView>> done() {
        return Result.success(portalService.done());
    }

    @Operation(summary = "我发起的流程")
    @GetMapping("/instances")
    @PreAuthorize("hasAuthority('approval:portal:view')")
    public Result<List<InstanceView>> myInstances() {
        return Result.success(portalService.myInstances());
    }

    @Operation(summary = "可发起的流程")
    @GetMapping("/startable")
    @PreAuthorize("hasAuthority('approval:portal:view')")
    public Result<List<WorkflowDesignView>> startable() {
        return Result.success(portalService.startable());
    }

    @Operation(summary = "流程详情")
    @GetMapping("/detail/{processInstanceId}")
    @PreAuthorize("hasAuthority('approval:portal:view')")
    public Result<InstanceDetailView> detail(@PathVariable String processInstanceId) {
        return Result.success(portalService.detail(processInstanceId));
    }
}
