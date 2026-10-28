package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.permission.DataScopeResourceRegistry;
import com.acme.scaffold.security.permission.DataScopeResourceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据权限（受控资源）接口。
 *
 * <p>只读暴露由 {@link com.acme.scaffold.security.permission.DataScopeResourceRegistry}
 * 启动时扫描 {@code @DataScope} 方法自动归集的受控资源清单，供前端「数据权限配置」页展示；
 * 权限码 {@code system:data-scope:read} 由 {@code ApiResourceAutoSyncRunner} 自动登记。</p>
 */
@Tag(name = "数据权限")
@RestController
@RequestMapping("/api/system/data-scope")
@RequiredArgsConstructor
public class DataScopeController {

    private final DataScopeResourceRegistry resourceRegistry;

    @Operation(summary = "数据权限资源清单")
    @GetMapping("/resources")
    @PreAuthorize("hasAuthority('system:data-scope:read')")
    public Result<List<DataScopeResourceView>> listResources() {
        return Result.success(resourceRegistry.listResources());
    }
}
