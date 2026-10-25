package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表单渲染接口（V47 低代码表单试点 / M22 spike）。
 *
 * <p><b>spike 收口</b>：只下发「Schema 契约」，前端 {@code DynamicForm.vue} 零依赖渲染；
 * 提交落库与绑定目标（BUSINESS_TABLE / FORM_SUBMISSION）为正式立项决策点，本期不提供提交端点。</p>
 *
 * <p><b>权限</b>：取 {@code system:form-definition:read}——由 {@code ApiResourceAutoSyncRunner}
 * 启动时扫描 {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记。</p>
 */
@Tag(name = "低代码表单试点")
@RestController
@RequestMapping("/api/system/forms")
@RequiredArgsConstructor
public class FormRenderController {

    private final FormDefinitionService service;

    @Operation(summary = "表单定义列表（含停用，供前端选择器）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:form-definition:read')")
    public Result<List<FormDefinitionView>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "按编码取表单 Schema（渲染面入口，仅下发启用中定义）")
    @GetMapping("/schema/{code}")
    @PreAuthorize("hasAuthority('system:form-definition:read')")
    public Result<FormDefinitionView> schema(@PathVariable String code) {
        return Result.success(service.getEnabledByCode(code));
    }
}
