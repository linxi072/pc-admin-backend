package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.form.dto.FormSubmissionAdminQuery;
import com.acme.scaffold.system.form.dto.FormSubmissionManageView;
import com.acme.scaffold.system.form.dto.FormSubmissionStat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单提交管理后台接口（M47 跨表单统一提交管理）。
 *
 * <p><b>能力</b>：在 {@code FormRenderController} 的「按单表单查询提交记录」之上，提供跨表单统一检索
 * （分页 / 统计 / 详情 / 逻辑删除），是表单提交数据的治理入口。</p>
 *
 * <p><b>权限</b>：读面取 {@code system:form-submission:read}；删除取 {@code system:form-submission:delete}；
 * 均由 {@code ApiResourceAutoSyncRunner} 启动时扫描 {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，
 * 无需手工登记。</p>
 */
@Tag(name = "表单提交管理")
@RestController
@RequestMapping("/api/system/form-submissions")
@RequiredArgsConstructor
public class FormSubmissionAdminController {

    private final FormSubmissionService submissionService;

    @Operation(summary = "跨表单提交记录分页（按表单编码/提交人/关键词检索）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:form-submission:read')")
    public Result<PageResult<FormSubmissionManageView>> page(
            @RequestParam(required = false) String formCode,
            @RequestParam(required = false) String submittedBy,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        FormSubmissionAdminQuery q = FormSubmissionAdminQuery.of(
                formCode, submittedBy, keyword, sortField, sortDir, page, size);
        return Result.success(submissionService.adminPage(q));
    }

    @Operation(summary = "跨表单提交统计（总数 + 按表单聚合）")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system:form-submission:read')")
    public Result<FormSubmissionStat> stats() {
        return Result.success(submissionService.stats());
    }

    @Operation(summary = "表单提交记录详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form-submission:read')")
    public Result<FormSubmissionManageView> detail(@PathVariable Long id) {
        return Result.success(submissionService.getManageById(id));
    }

    @Operation(summary = "删除表单提交记录（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form-submission:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除表单提交记录")
    public Result<Void> remove(@PathVariable Long id) {
        submissionService.removeSubmission(id);
        return Result.success();
    }
}
