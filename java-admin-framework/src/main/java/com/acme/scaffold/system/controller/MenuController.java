package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.system.dto.CreateMenuRequest;
import com.acme.scaffold.system.dto.MenuTreeVO;
import com.acme.scaffold.system.dto.UpdateMenuRequest;
import com.acme.scaffold.system.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final SecurityContextFacade securityContextFacade;

    @Operation(summary = "我的菜单（按当前登录用户角色过滤）")
    @GetMapping("/mine")
    public Result<List<MenuTreeVO>> mine() {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        return Result.success(menuService.menusForCurrentUser(userId));
    }

    @Operation(summary = "菜单树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:read')")
    public Result<List<MenuTreeVO>> tree() {
        return Result.success(menuService.tree());
    }

    @Operation(summary = "创建菜单")
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建菜单")
    public Result<Long> create(@Valid @RequestBody CreateMenuRequest request) {
        return Result.success(menuService.create(request));
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新菜单")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateMenuRequest request) {
        menuService.update(id, request);
        return Result.success();
    }
}
