package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.ResetPasswordRequest;
import com.acme.scaffold.system.dto.UpdateUserRequest;
import com.acme.scaffold.system.dto.UserQuery;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询用户列表")
    public Result<PageResult<UserView>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long orgId) {
        return Result.success(userService.list(new UserQuery(page, size, username, status, orgId)));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:read')")
    public Result<UserView> get(@PathVariable Long id) {
        return Result.success(userService.getView(id));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建用户")
    public Result<Long> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(userService.create(request));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新用户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        userService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return Result.success();
    }

    @Operation(summary = "删除用户（物理删除/硬删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "物理删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
