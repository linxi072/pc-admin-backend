package com.acme.scaffold.system.form.api;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.form.application.FormVersionService;
import com.acme.scaffold.system.form.domain.model.FormDefinitionVersionDO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单定义版本管理接口（E8 低代码深化）：发布快照、版本列表、回滚、字段 diff。
 *
 * <p>权限沿用 {@code system:form-definition:*} 体系：读面用 read，写面（发布/回滚）用 update，
 * 均由 ApiResourceAutoSyncRunner 按 {@code @PreAuthorize} 自动登记并授予 SUPER_ADMIN。</p>
 */
@Tag(name = "表单定义版本管理")
@RestController
@RequestMapping("/api/system/form-definitions/{id}/versions")
@RequiredArgsConstructor
public class FormVersionController {

    private final FormVersionService service;

    @Operation(summary = "表单版本列表（按版本号升序）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:form-definition:read')")
    public Result<List<FormDefinitionVersionDO>> list(@PathVariable Long id) {
        return Result.success(service.listVersions(id));
    }

    @Operation(summary = "发布当前表单定义为新版本快照")
    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('system:form-definition:update')")
    @AuditOperation(module = "system", type = "CREATE", name = "发布表单版本")
    public Result<FormDefinitionVersionDO> publish(@PathVariable Long id) {
        return Result.success(service.publish(id));
    }

    @Operation(summary = "回滚到指定版本（生成新版本快照，不删除历史）")
    @PostMapping("/{versionNo}/rollback")
    @PreAuthorize("hasAuthority('system:form-definition:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "回滚表单版本")
    public Result<FormDefinitionVersionDO> rollback(@PathVariable Long id, @PathVariable int versionNo) {
        return Result.success(service.rollback(id, versionNo));
    }

    @Operation(summary = "两个版本的字段变更摘要（+新增 / -删除）")
    @GetMapping("/diff")
    @PreAuthorize("hasAuthority('system:form-definition:read')")
    public Result<List<String>> diff(@PathVariable Long id,
                                     @RequestParam int from, @RequestParam int to) {
        return Result.success(service.diffFields(id, from, to));
    }
}
