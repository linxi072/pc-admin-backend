package com.acme.scaffold.workflow.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workflow.service.WorkflowInstanceAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程实例治理接口（M23 续）：管理员视角的实例高风险写操作。
 *
 * <p><b>权限</b>：重新指派 {@code workflow:instance:reassign}、挂起/恢复 {@code workflow:instance:suspend}、
 * 迁移 {@code workflow:instance:migrate}——均由 {@code ApiResourceAutoSyncRunner} 启动时扫描
 * {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记。</p>
 */
@Tag(name = "流程实例治理")
@RestController
@RequestMapping("/api/workflow/admin")
@RequiredArgsConstructor
public class WorkflowAdminController {

    private final WorkflowInstanceAdminService adminService;

    @Operation(summary = "重新指派实例的全部活跃任务到目标办理人")
    @PostMapping("/instances/{processInstanceId}/reassign")
    @PreAuthorize("hasAuthority('workflow:instance:reassign')")
    @AuditOperation(module = "workflow", type = "REASSIGN", name = "重新指派流程实例")
    public Result<Void> reassign(@PathVariable String processInstanceId,
                                 @RequestParam Long toUserId) {
        adminService.reassign(processInstanceId, toUserId);
        return Result.success();
    }

    @Operation(summary = "挂起流程实例（冻结在途任务）")
    @PostMapping("/instances/{processInstanceId}/suspend")
    @PreAuthorize("hasAuthority('workflow:instance:suspend')")
    @AuditOperation(module = "workflow", type = "SUSPEND_INSTANCE", name = "挂起流程实例")
    public Result<Void> suspend(@PathVariable String processInstanceId) {
        adminService.suspend(processInstanceId);
        return Result.success();
    }

    @Operation(summary = "恢复（取消挂起）流程实例")
    @PostMapping("/instances/{processInstanceId}/resume")
    @PreAuthorize("hasAuthority('workflow:instance:suspend')")
    @AuditOperation(module = "workflow", type = "RESUME_INSTANCE", name = "恢复流程实例")
    public Result<Void> resume(@PathVariable String processInstanceId) {
        adminService.resume(processInstanceId);
        return Result.success();
    }

    @Operation(summary = "将运行实例迁移到目标流程定义版本（热升级）")
    @PostMapping("/instances/{processInstanceId}/migrate")
    @PreAuthorize("hasAuthority('workflow:instance:migrate')")
    @AuditOperation(module = "workflow", type = "MIGRATE_INSTANCE", name = "迁移流程实例")
    public Result<Void> migrate(@PathVariable String processInstanceId,
                                @RequestParam String toProcessDefinitionId) {
        adminService.migrate(processInstanceId, toProcessDefinitionId);
        return Result.success();
    }
}
