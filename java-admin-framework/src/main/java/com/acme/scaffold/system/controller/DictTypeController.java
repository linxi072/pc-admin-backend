package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.CreateDictTypeRequest;
import com.acme.scaffold.system.dto.DictTypeVO;
import com.acme.scaffold.system.dto.UpdateDictTypeRequest;
import com.acme.scaffold.system.service.DictTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典类型管理")
@RestController
@RequestMapping("/api/system/dict-types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @Operation(summary = "字典类型列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:dict:read')")
    public Result<List<DictTypeVO>> list() {
        return Result.success(dictTypeService.list());
    }

    @Operation(summary = "创建字典类型")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建字典类型")
    public Result<Long> create(@Valid @RequestBody CreateDictTypeRequest request) {
        return Result.success(dictTypeService.create(request));
    }

    @Operation(summary = "更新字典类型")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新字典类型")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateDictTypeRequest request) {
        dictTypeService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除字典类型（存在字典数据时拒绝）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除字典类型")
    public Result<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return Result.success();
    }
}
