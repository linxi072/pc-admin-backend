package com.acme.scaffold.workflow.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workflow.dto.design.CreateDesignRequest;
import com.acme.scaffold.workflow.dto.design.SaveDesignRequest;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.service.WorkflowDesignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "工作流定义")
@RestController
@RequestMapping("/api/workflow/definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDesignService designService;

    @Operation(summary = "新建工作流草稿")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:definition:create')")
    @AuditOperation(module = "workflow", type = "CREATE", name = "新建工作流草稿")
    public Result<WorkflowDesignView> create(@Valid @RequestBody CreateDesignRequest request) {
        return Result.success(designService.create(request));
    }

    @Operation(summary = "保存工作流草稿（全量覆盖节点与连线）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:definition:update')")
    @AuditOperation(module = "workflow", type = "UPDATE", name = "保存工作流草稿")
    public Result<WorkflowDesignView> save(@PathVariable Long id, @Valid @RequestBody SaveDesignRequest request) {
        return Result.success(designService.save(id, request));
    }

    @Operation(summary = "工作流定义列表")
    @GetMapping
    @PreAuthorize("hasAuthority('workflow:definition:read')")
    public Result<List<WorkflowDesignView>> list() {
        return Result.success(designService.list());
    }

    @Operation(summary = "工作流定义详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:definition:read')")
    public Result<WorkflowDesignView> get(@PathVariable Long id) {
        return Result.success(designService.get(id));
    }

    @Operation(summary = "按 processKey 获取已发布设计（前端发起表单联动：用于判定设计是否已发布）")
    @GetMapping("/published/{processKey}")
    @PreAuthorize("hasAuthority('workflow:definition:read')")
    public Result<WorkflowDesignView> getPublishedByKey(@PathVariable String processKey) {
        // 未发布时返回 null，前端据此隐藏审批人/主管选择框
        return Result.success(designService.getPublished(processKey));
    }

    @Operation(summary = "删除工作流草稿")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:definition:delete')")
    @AuditOperation(module = "workflow", type = "DELETE", name = "删除工作流草稿")
    public Result<Void> delete(@PathVariable Long id) {
        designService.delete(id);
        return Result.success();
    }

    @Operation(summary = "发布工作流（校验->生成BPMN->部署->生效）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('workflow:definition:publish')")
    @AuditOperation(module = "workflow", type = "PUBLISH", name = "发布工作流")
    public Result<WorkflowDesignView> publish(@PathVariable Long id) {
        return Result.success(designService.publish(id));
    }

    @Operation(summary = "取消发布工作流（新发起不再使用该版本，在途实例不受影响）")
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('workflow:definition:publish')")
    @AuditOperation(module = "workflow", type = "UNPUBLISH", name = "取消发布工作流")
    public Result<WorkflowDesignView> unpublish(@PathVariable Long id) {
        return Result.success(designService.unpublish(id));
    }

    @Operation(summary = "查看生成的 BPMN（设计态预览）")
    @GetMapping("/{id}/bpmn")
    @PreAuthorize("hasAuthority('workflow:definition:read')")
    public Result<Map<String, String>> bpmn(@PathVariable Long id) {
        WorkflowDesignView view = designService.get(id);
        return Result.success(Map.of("processKey", view.processKey(), "bpmnXml", view.bpmnXml() == null ? "" : view.bpmnXml()));
    }
}
