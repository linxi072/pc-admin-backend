package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.service.OrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}
