package com.acme.scaffold.workbench.dashboard.api;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.workbench.dashboard.dto.DashboardTemplateVO;
import com.acme.scaffold.workbench.dashboard.dto.TemplateSaveRequest;
import com.acme.scaffold.workbench.dashboard.dto.TemplateUpdateRequest;
import com.acme.scaffold.workbench.dashboard.application.DashboardTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板模板管理接口。管理类操作统一要求 dashboard:template:manage，
 * 该权限码经 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
 */
@Tag(name = "数据看板-模板")
@RestController
@RequestMapping("/api/dashboard/template")
@RequiredArgsConstructor
public class DashboardTemplateController {

    private final DashboardTemplateService templateService;

    @Operation(summary = "创建看板模板（默认草稿态）")
    @PostMapping
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    @AuditOperation(module = "dashboard", type = "CREATE", name = "创建看板模板")
    public Result<Long> create(@Valid @RequestBody TemplateSaveRequest req) {
        return Result.success(templateService.create(req));
    }

    @Operation(summary = "更新看板模板")
    @PutMapping
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    @AuditOperation(module = "dashboard", type = "UPDATE", name = "更新看板模板")
    public Result<Void> update(@Valid @RequestBody TemplateUpdateRequest req) {
        templateService.update(req);
        return Result.success();
    }

    @Operation(summary = "删除看板模板")
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    @AuditOperation(module = "dashboard", type = "DELETE", name = "删除看板模板")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }

    @Operation(summary = "看板模板详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    public Result<DashboardTemplateVO> getById(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @Operation(summary = "发布看板模板（DRAFT/OFFLINE → PUBLISHED）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    @AuditOperation(module = "dashboard", type = "UPDATE", name = "发布看板模板")
    public Result<Void> publish(@PathVariable Long id) {
        templateService.publish(id);
        return Result.success();
    }

    @Operation(summary = "撤销发布看板模板（PUBLISHED → OFFLINE）")
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    @AuditOperation(module = "dashboard", type = "UPDATE", name = "撤销发布看板模板")
    public Result<Void> unpublish(@PathVariable Long id) {
        templateService.unpublish(id);
        return Result.success();
    }

    @Operation(summary = "看板模板浏览计数 +1")
    @PostMapping("/{id}/view")
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    public Result<Void> view(@PathVariable Long id) {
        templateService.incrementViewCount(id);
        return Result.success();
    }

    @Operation(summary = "看板模板分页（支持编码/名称/状态筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('dashboard:template:manage')")
    public Result<PageResult<DashboardTemplateVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(templateService.page(page, size, templateCode, templateName,
                status, sortField, sortDirection));
    }
}
