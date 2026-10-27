package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.form.dto.CreateFormDefinitionRequest;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.UpdateFormDefinitionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 表单定义管理接口（M22 深化：表单定义 CRUD + JSON-Schema 编辑）。
 *
 * <p><b>权限</b>：列表/详情取 {@code system:form-definition:read}；创建/更新/删除分别取
 * {@code system:form-definition:create} / {@code system:form-definition:update} /
 * {@code system:form-definition:delete}——均由 {@code ApiResourceAutoSyncRunner} 启动时扫描
 * {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记。</p>
 *
 * <p><b>与渲染面解耦</b>：本控制器负责「定义生命周期管理」，{@code FormRenderController} 负责
 * 「按编码取 Schema + 提交 + 提交记录」的运行时渲染链路，二者权限口径一致（读面同源）。</p>
 */
@Tag(name = "表单定义管理")
@RestController
@RequestMapping("/api/system/form-definitions")
@RequiredArgsConstructor
public class FormDefinitionController {

    private final FormDefinitionService service;

    @Operation(summary = "表单定义列表（含停用）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:form-definition:read')")
    public Result<List<FormDefinitionView>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "表单定义详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form-definition:read')")
    public Result<FormDefinitionView> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "创建表单定义（落库前校验 Schema 结构 + 编码唯一性）")
    @PostMapping
    @PreAuthorize("hasAuthority('system:form-definition:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建表单定义")
    public Result<String> create(@Valid @RequestBody CreateFormDefinitionRequest request) {
        return Result.success(String.valueOf(service.create(request)));
    }

    @Operation(summary = "更新表单定义")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form-definition:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新表单定义")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody UpdateFormDefinitionRequest request) {
        service.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除表单定义（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form-definition:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除表单定义")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.success();
    }
}
