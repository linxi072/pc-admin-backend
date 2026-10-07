package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.dto.UpdateOrgRequest;
import com.acme.scaffold.system.service.OrgService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "机构管理")
@RestController
@RequestMapping("/api/system/orgs")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    @Operation(summary = "机构树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:org:read')")
    public Result<List<OrgTreeVO>> tree() {
        return Result.success(orgService.tree());
    }

    @Operation(summary = "创建机构")
    @PostMapping
    @PreAuthorize("hasAuthority('system:org:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建机构")
    public Result<Long> create(@Valid @RequestBody CreateOrgRequest request) {
        return Result.success(orgService.create(request));
    }

    @Operation(summary = "更新机构（支持调整上级与层级移动）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:org:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新机构")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateOrgRequest request) {
        orgService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除机构（存在子机构时拒绝）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:org:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除机构")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.success();
    }
}
