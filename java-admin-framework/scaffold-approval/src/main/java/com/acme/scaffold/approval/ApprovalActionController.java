package com.acme.scaffold.approval;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workflow.dto.CompleteTaskRequest;
import com.acme.scaffold.workflow.dto.DelegateTaskRequest;
import com.acme.scaffold.workflow.dto.ResolveDelegateRequest;
import com.acme.scaffold.workflow.dto.StartProcessRequest;
import com.acme.scaffold.workflow.dto.TransferTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心动作接口：在只读门户之外补齐「可操作」能力，使审批工作台可完成发起与办理。
 *
 * <p>与 {@link ApprovalPortalController} 的分工：门户（{@code /api/approval/portal/**}）只做
 * 只读聚合，本控制器（{@code /api/approval/actions/**}）承接全部写操作，二者共用同一套
 * 工作流引擎，不并行实现业务逻辑。权限码 {@code approval:action:*} 由
 * {@code ApiResourceAutoSyncRunner} 扫描 {@code @PreAuthorize} 自动登记并授予 SUPER_ADMIN。</p>
 *
 * <p>幂等要求：审批/转办/委派/办结委派委托的 {@code WorkflowService} 方法标注
 * {@code @Idempotent(requireKey=true)}，调用方必须携带 {@code Idempotency-Key} 请求头，
 * 并在 body 提供 {@code operationId}（建议同值）。认领无需幂等键。</p>
 */
@Tag(name = "审批中心动作")
@RestController
@RequestMapping("/api/approval/actions")
@RequiredArgsConstructor
public class ApprovalActionController {

    private final ApprovalActionService actionService;

    @Operation(summary = "发起流程（仅可发起的已发布流程）")
    @PostMapping("/start")
    @PreAuthorize("hasAuthority('approval:action:start')")
    @AuditOperation(module = "approval", type = "CREATE", name = "审批中心发起流程")
    public Result<String> start(@Valid @RequestBody StartProcessRequest request) {
        return Result.success(actionService.start(request));
    }

    @Operation(summary = "审批/驳回任务")
    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('approval:action:approve')")
    @AuditOperation(module = "approval", type = "APPROVE", name = "审批中心审批任务")
    public Result<Void> approve(@Valid @RequestBody CompleteTaskRequest request) {
        actionService.approve(request);
        return Result.success();
    }

    @Operation(summary = "转办任务")
    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('approval:action:transfer')")
    @AuditOperation(module = "approval", type = "TRANSFER", name = "审批中心转办任务")
    public Result<Void> transfer(@Valid @RequestBody TransferTaskRequest request) {
        actionService.transfer(request);
        return Result.success();
    }

    @Operation(summary = "委派任务（临时委托他人办理）")
    @PostMapping("/delegate")
    @PreAuthorize("hasAuthority('approval:action:delegate')")
    @AuditOperation(module = "approval", type = "DELEGATE", name = "审批中心委派任务")
    public Result<Void> delegate(@Valid @RequestBody DelegateTaskRequest request) {
        actionService.delegate(request);
        return Result.success();
    }

    @Operation(summary = "办结委派（被委托人交还原审批人）")
    @PostMapping("/resolve-delegate")
    @PreAuthorize("hasAuthority('approval:action:resolve-delegate')")
    @AuditOperation(module = "approval", type = "DELEGATE_RESOLVE", name = "审批中心办结委派")
    public Result<Void> resolveDelegate(@Valid @RequestBody ResolveDelegateRequest request) {
        actionService.resolveDelegate(request);
        return Result.success();
    }

    @Operation(summary = "认领池化任务")
    @PostMapping("/{taskId}/claim")
    @PreAuthorize("hasAuthority('approval:action:claim')")
    @AuditOperation(module = "approval", type = "CLAIM", name = "审批中心认领任务")
    public Result<Void> claim(@PathVariable String taskId) {
        actionService.claim(taskId);
        return Result.success();
    }
}
