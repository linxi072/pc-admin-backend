package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.system.form.domain.FormSubmissionDO;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.FormSubmissionView;
import com.acme.scaffold.common.api.dto.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 表单渲染与提交接口（V47 低代码表单试点 / M22 spike + V53 正式立项）。
 *
 * <p><b>M22 正式立项</b>：在 spike 只读面（list / schema）之上，新增提交落库与提交记录查询端点，
 * 承接 V47 的「提交落库与绑定目标」决策点（target_type=FORM_SUBMISSION）——提交值写入独立
 * {@code sys_form_submission} 表，后端按 schema 字段校验后落库。</p>
 *
 * <p><b>权限</b>：读面取 {@code system:form-definition:read}；提交/查询提交记录取
 * {@code system:form-submission:create} / {@code system:form-submission:read}——
 * 均由 {@code ApiResourceAutoSyncRunner} 启动时扫描 {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，
 * 无需手工登记。</p>
 */
@Tag(name = "低代码表单试点")
@RestController
@RequestMapping("/api/system/forms")
@RequiredArgsConstructor
public class FormRenderController {

    private final FormDefinitionService service;
    private final FormSubmissionService submissionService;
    private final SecurityContextFacade securityContextFacade;

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

    @Operation(summary = "提交表单（M22 正式立项：落库 FORM_SUBMISSION 目标）")
    @PostMapping("/{code}/submit")
    @PreAuthorize("hasAuthority('system:form-submission:create')")
    public Result<FormSubmissionView> submit(@PathVariable String code,
                                             @RequestBody Map<String, Object> values) {
        String operator = securityContextFacade.getCurrentPrincipal()
                .map(CurrentPrincipal::username).orElse("unknown");
        return Result.success(submissionService.submit(code, values, operator));
    }

    @Operation(summary = "表单提交记录分页查询（按提交时间倒序）")
    @GetMapping("/{code}/submissions")
    @PreAuthorize("hasAuthority('system:form-submission:read')")
    public Result<PageResult<FormSubmissionView>> submissions(@PathVariable String code,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(submissionService.listByForm(code, page, size));
    }
}
