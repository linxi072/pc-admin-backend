package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.AssignUserPostsRequest;
import com.acme.scaffold.system.dto.CreatePostRequest;
import com.acme.scaffold.system.dto.PostOptionVO;
import com.acme.scaffold.system.dto.PostQuery;
import com.acme.scaffold.system.dto.PostView;
import com.acme.scaffold.system.dto.UpdatePostRequest;
import com.acme.scaffold.system.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位管理接口。
 * <p>权限码：{@code system:post:read/create/update/delete/assign}，
 * 由 ApiResourceAutoSyncRunner 启动扫描自动登记并授予 SUPER_ADMIN。
 * 用户岗位分配独立使用 {@code system:post:assign}，与用户编辑权限解耦。
 */
@Tag(name = "岗位管理")
@RestController
@RequestMapping("/api/system/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "分页查询岗位（支持编码/名称关键词搜索）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:post:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询岗位列表")
    public Result<PageResult<PostView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(postService.list(
                new PostQuery(page, size, keyword, status, sortField, sortDirection)));
    }

    @Operation(summary = "全部启用岗位（下拉选项）")
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('system:post:read')")
    public Result<List<PostOptionVO>> options() {
        return Result.success(postService.options());
    }

    @Operation(summary = "新建岗位")
    @PostMapping
    @PreAuthorize("hasAuthority('system:post:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建岗位")
    public Result<String> create(@Valid @RequestBody CreatePostRequest request) {
        return Result.success(String.valueOf(postService.create(request)));
    }

    @Operation(summary = "编辑岗位")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "编辑岗位")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        postService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除岗位（逻辑删除，并清理用户关联）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除岗位")
    public Result<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return Result.success();
    }

    @Operation(summary = "为指定用户分配岗位（整体替换，空列表清空）")
    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('system:post:assign')")
    @AuditOperation(module = "system", type = "UPDATE", name = "分配用户岗位")
    public Result<Void> assignUserPosts(@PathVariable Long userId,
                                        @Valid @RequestBody AssignUserPostsRequest request) {
        postService.assignUserPosts(userId, request);
        return Result.success();
    }
}
