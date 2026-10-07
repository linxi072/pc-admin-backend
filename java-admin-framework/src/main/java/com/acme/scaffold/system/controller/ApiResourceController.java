package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.ApiResourceScanResult;
import com.acme.scaffold.system.dto.ApiResourceSyncResult;
import com.acme.scaffold.system.dto.ApiResourceView;
import com.acme.scaffold.system.dto.CreateApiResourceRequest;
import com.acme.scaffold.system.dto.UpdateApiResourceRequest;
import com.acme.scaffold.system.service.ApiResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "接口资源管理")
@RestController
@RequestMapping("/api/system/api-resources")
@RequiredArgsConstructor
public class ApiResourceController {

    private final ApiResourceService apiResourceService;

    @Operation(summary = "接口资源列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:api:read')")
    public Result<List<ApiResourceView>> list() {
        return Result.success(apiResourceService.list());
    }

    @Operation(summary = "注册接口资源")
    @PostMapping
    @PreAuthorize("hasAuthority('system:api:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "注册接口资源")
    public Result<Long> create(@Valid @RequestBody CreateApiResourceRequest request) {
        return Result.success(apiResourceService.create(request));
    }

    @Operation(summary = "删除接口资源")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:api:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除接口资源")
    public Result<Void> delete(@PathVariable Long id) {
        apiResourceService.delete(id);
        return Result.success();
    }

    @Operation(summary = "更新接口资源")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:api:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新接口资源")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateApiResourceRequest request) {
        apiResourceService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "扫描接口资源并预览差异（不写库）",
            description = "从 Spring MVC 路由表与 @PreAuthorize 注解中解析权限码，与库内现有记录比对")
    @GetMapping("/scan")
    @PreAuthorize("hasAuthority('system:api:read')")
    public Result<ApiResourceScanResult> scanPreview() {
        return Result.success(apiResourceService.scanPreview());
    }

    @Operation(summary = "执行接口资源扫描同步（写库）",
            description = "新增缺失记录、同步变更字段；库中已失效的记录仅统计不删除，避免授权悬空。幂等，可重复执行")
    @PostMapping("/scan/sync")
    @PreAuthorize("hasAuthority('system:api:sync')")
    @AuditOperation(module = "system", type = "UPDATE", name = "扫描同步接口资源", recordResult = true)
    public Result<ApiResourceSyncResult> sync() {
        return Result.success(apiResourceService.sync());
    }
}
