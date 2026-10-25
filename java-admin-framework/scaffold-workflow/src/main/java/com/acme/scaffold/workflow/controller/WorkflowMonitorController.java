package com.acme.scaffold.workflow.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workflow.dto.DefinitionVersionView;
import com.acme.scaffold.workflow.dto.MonitorStatsView;
import com.acme.scaffold.workflow.dto.OverdueTaskView;
import com.acme.scaffold.workflow.dto.RunningInstanceView;
import com.acme.scaffold.workflow.service.WorkflowMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程监控接口（M23，M4 续）：运行实例 / 超时任务 / 定义版本链 / 统计摘要 + 任务催办。
 *
 * <p><b>权限</b>：查询面取 {@code workflow:monitor:read}，催办取 {@code workflow:task:remind}——
 * 权限码由 {@code ApiResourceAutoSyncRunner} 启动时扫描 {@code @PreAuthorize} 自动落库
 * 并授予 SUPER_ADMIN，无需手工登记。菜单（workflow:monitor）由 V49 迁移播种。</p>
 *
 * <p><b>与个人面互补</b>：{@code InstanceController}/{@code TaskController} 面向「我发起的/我的待办」，
 * 本控制器面向管理员全局视角（他人在途、超时卡点、版本演进），全部只读 + 唯一写操作「催办」。</p>
 */
@Tag(name = "流程监控")
@RestController
@RequestMapping("/api/workflow/monitor")
@RequiredArgsConstructor
public class WorkflowMonitorController {

    private final WorkflowMonitorService monitorService;

    @Operation(summary = "分页查询流程实例（默认运行中，可按状态/关键字筛选）")
    @GetMapping("/running-instances")
    @PreAuthorize("hasAuthority('workflow:monitor:read')")
    public Result<PageResult<RunningInstanceView>> runningInstances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(monitorService.runningInstances(page, size, status, keyword));
    }

    @Operation(summary = "分页查询超时任务（已过到期时间未办结，最紧急在前）")
    @GetMapping("/overdue-tasks")
    @PreAuthorize("hasAuthority('workflow:monitor:read')")
    public Result<PageResult<OverdueTaskView>> overdueTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(monitorService.overdueTasks(page, size));
    }

    @Operation(summary = "查询流程定义版本链（同 processKey 的部署版本，版本号降序）")
    @GetMapping("/definition-versions/{processKey}")
    @PreAuthorize("hasAuthority('workflow:monitor:read')")
    public Result<List<DefinitionVersionView>> definitionVersions(@PathVariable String processKey) {
        return Result.success(monitorService.definitionVersions(processKey));
    }

    @Operation(summary = "流程监控统计摘要（运行实例/超时任务/待办/今日发起/今日办结）")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('workflow:monitor:read')")
    public Result<MonitorStatsView> stats() {
        return Result.success(monitorService.stats());
    }

    @Operation(summary = "催办任务（实时提醒当前办理人，受最小间隔约束防轰炸）")
    @PostMapping("/tasks/{taskId}/remind")
    @PreAuthorize("hasAuthority('workflow:task:remind')")
    @AuditOperation(module = "workflow", type = "REMIND", name = "催办任务")
    public Result<Void> remind(@PathVariable String taskId) {
        monitorService.remind(taskId);
        return Result.success();
    }
}
