package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.tenant.dto.CreateTenantRequest;
import com.acme.scaffold.system.tenant.dto.TenantProvisioningRequest;
import com.acme.scaffold.system.tenant.dto.TenantProvisioningView;
import com.acme.scaffold.system.tenant.dto.TenantView;
import com.acme.scaffold.system.tenant.dto.UpdateTenantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理接口（V51 租户管理域 / M19 P2）。
 *
 * <p><b>权限</b>：查询类端点取 {@code system:tenant:read}，写操作（注册/更新/删除/启停）
 * 取 {@code system:tenant:manage}——由 {@code ApiResourceAutoSyncRunner} 启动时扫描
 * {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记（与 V51 注释一致）。</p>
 *
 * <p><b>平台治理语义</b>：租户是平台的「被治理对象」，仅平台管理员可维护；
 * 隔离本身由 P3 在 SQL 层强制（本表查询恒不被当前租户过滤）。</p>
 */
@Tag(name = "租户管理")
@RestController
@RequestMapping("/api/system/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService service;
    private final TenantProvisioningService provisioningService;

    @Operation(summary = "分页查询租户（支持 tenantCode 精确 / status 筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:tenant:read')")
    public Result<PageResult<TenantView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(service.page(page, size, tenantCode, status, sortField, sortDirection));
    }

    @Operation(summary = "查询全部租户（含停用）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:tenant:read')")
    public Result<List<TenantView>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "查询单个租户")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:read')")
    public Result<TenantView> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @Operation(summary = "注册租户（tenant_code 创建后不可变更）")
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    @AuditOperation(module = "system", type = "CREATE", name = "注册租户")
    public Result<Long> create(@Valid @RequestBody CreateTenantRequest request) {
        return Result.success(service.create(request));
    }

    @Operation(summary = "更新租户（部分更新；tenant_code 不可变更）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新租户")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody UpdateTenantRequest request) {
        service.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除租户（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除租户")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.success();
    }

    @Operation(summary = "启停租户（ENABLED / DISABLED）")
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "启停租户")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggle(id, enabled);
        return Result.success();
    }

    @Operation(summary = "开通租户（初始化管理员/角色/部门）")
    @PostMapping("/{id}/provision")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    @AuditOperation(module = "system", type = "CREATE", name = "开通租户")
    public Result<TenantProvisioningView> provision(@PathVariable Long id,
                                                   @Valid @RequestBody TenantProvisioningRequest request) {
        return Result.success(provisioningService.provision(id, request));
    }
}
