package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.dto.DictDataVO;
import com.acme.scaffold.system.dto.UpdateDictDataRequest;
import com.acme.scaffold.system.service.DictDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典数据管理")
@RestController
@RequestMapping("/api/system/dict-data")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    @Operation(summary = "字典数据列表（按字典类型筛选）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:dict:read')")
    public Result<List<DictDataVO>> list(@RequestParam(required = false) String dictType) {
        return Result.success(dictDataService.listByType(dictType));
    }

    @Operation(summary = "创建字典数据")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建字典数据")
    public Result<Long> create(@Valid @RequestBody CreateDictDataRequest request) {
        return Result.success(dictDataService.create(request));
    }

    @Operation(summary = "更新字典数据")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新字典数据")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateDictDataRequest request) {
        dictDataService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除字典数据")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除字典数据")
    public Result<Void> delete(@PathVariable Long id) {
        dictDataService.delete(id);
        return Result.success();
    }
}
