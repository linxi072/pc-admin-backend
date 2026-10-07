package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.dto.ConfigVO;
import com.acme.scaffold.system.dto.CreateConfigRequest;
import com.acme.scaffold.system.dto.UpdateConfigRequest;
import com.acme.scaffold.system.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统变量（参数配置）")
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "配置列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:config:read')")
    public Result<List<ConfigVO>> list() {
        return Result.success(configService.list());
    }

    @Operation(summary = "按配置键动态读取")
    @GetMapping("/key/{key}")
    @PreAuthorize("hasAuthority('system:config:read')")
    public Result<ConfigVO> getByKey(@PathVariable String key) {
        ConfigVO vo = configService.getByKey(key);
        if (vo == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "配置不存在");
        }
        return Result.success(vo);
    }

    @Operation(summary = "创建配置")
    @PostMapping
    @PreAuthorize("hasAuthority('system:config:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建系统配置")
    public Result<Long> create(@Valid @RequestBody CreateConfigRequest request) {
        return Result.success(configService.create(request));
    }

    @Operation(summary = "更新配置")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新系统配置")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateConfigRequest request) {
        configService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除系统配置")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.success();
    }
}
