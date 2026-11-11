package com.acme.scaffold.cms.category.api;

import com.acme.scaffold.cms.category.application.CmsCategoryService;
import com.acme.scaffold.cms.category.dto.CategoryCreateRequest;
import com.acme.scaffold.cms.category.dto.CategoryNode;
import com.acme.scaffold.cms.category.dto.CategoryUpdateRequest;
import com.acme.scaffold.cms.category.dto.CategoryView;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * 栏目管理接口。统一要求 cms:category:manage 权限。
 */
@Tag(name = "内容管理-栏目")
@RestController
@RequestMapping("/api/cms/category")
@RequiredArgsConstructor
public class CmsCategoryController {

    private final CmsCategoryService categoryService;

    @Operation(summary = "创建栏目")
    @PostMapping
    @PreAuthorize("hasAuthority('cms:category:manage')")
    @AuditOperation(module = "cms", type = "CREATE", name = "创建栏目")
    public Result<Long> create(@Valid @RequestBody CategoryCreateRequest req) {
        return Result.success(categoryService.create(req));
    }

    @Operation(summary = "更新栏目")
    @PutMapping
    @PreAuthorize("hasAuthority('cms:category:manage')")
    @AuditOperation(module = "cms", type = "UPDATE", name = "更新栏目")
    public Result<Void> update(@Valid @RequestBody CategoryUpdateRequest req) {
        categoryService.update(req);
        return Result.success();
    }

    @Operation(summary = "删除栏目（存在子栏目时拒绝）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:category:manage')")
    @AuditOperation(module = "cms", type = "DELETE", name = "删除栏目")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "栏目详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:category:manage')")
    public Result<CategoryView> getById(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    @Operation(summary = "栏目扁平列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('cms:category:manage')")
    public Result<List<CategoryView>> list() {
        return Result.success(categoryService.list());
    }

    @Operation(summary = "栏目树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('cms:category:manage')")
    public Result<List<CategoryNode>> tree() {
        return Result.success(categoryService.tree());
    }
}
