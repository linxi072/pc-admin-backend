package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.permission.ColumnPermissionResourceRegistry;
import com.acme.scaffold.security.permission.ColumnPermissionResourceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字段级数据权限（受控资源）接口。
 *
 * <p>只读暴露由 {@link com.acme.scaffold.security.permission.ColumnPermissionResourceRegistry}
 * 启动时扫描 {@code @ColumnPermission} 方法自动归集的受控资源（含字段）清单，供前端
 * 「字段级权限配置」页展示；权限码 {@code system:column-permission:read} 由
 * {@code ApiResourceAutoSyncRunner} 自动登记。</p>
 */
@Tag(name = "字段级数据权限")
@RestController
@RequestMapping("/api/system/column-permissions")
@RequiredArgsConstructor
public class ColumnPermissionController {

    private final ColumnPermissionResourceRegistry resourceRegistry;

    @Operation(summary = "字段级数据权限资源清单")
    @GetMapping("/resources")
    @PreAuthorize("hasAuthority('system:column-permission:read')")
    public Result<List<ColumnPermissionResourceView>> listResources() {
        return Result.success(resourceRegistry.listResources());
    }
}
