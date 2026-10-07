package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.CreateRoleRequest;
import com.acme.scaffold.system.dto.DataScopeRuleView;
import com.acme.scaffold.system.dto.RoleView;
import com.acme.scaffold.system.dto.SaveDataScopeRequest;
import com.acme.scaffold.system.service.RoleDataScopeService;
import com.acme.scaffold.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RoleDataScopeService roleDataScopeService;

    @Operation(summary = "角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:read')")
    public Result<List<RoleView>> list() {
        return Result.success(roleService.list());
    }

    @Operation(summary = "角色详情（含菜单/接口权限）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:read')")
    public Result<RoleView> get(@PathVariable Long id) {
        return Result.success(roleService.getView(id));
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建角色")
    public Result<Long> create(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(roleService.create(request));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CreateRoleRequest request) {
        roleService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "角色数据权限列表")
    @GetMapping("/{id}/data-scopes")
    @PreAuthorize("hasAuthority('system:role:read')")
    public Result<List<DataScopeRuleView>> listDataScopes(@PathVariable Long id) {
        return Result.success(roleDataScopeService.list(id));
    }

    @Operation(summary = "保存角色数据权限（覆盖式）")
    @PutMapping("/{id}/data-scopes")
    @PreAuthorize("hasAuthority('system:role:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "保存角色数据权限")
    public Result<Void> saveDataScope(@PathVariable Long id,
                                       @Valid @RequestBody SaveDataScopeRequest request) {
        roleDataScopeService.save(id, request);
        return Result.success();
    }
}
