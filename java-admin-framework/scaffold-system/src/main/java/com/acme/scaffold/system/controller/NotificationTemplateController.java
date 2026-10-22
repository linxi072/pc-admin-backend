package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.system.dto.CreateNotificationTemplateRequest;
import com.acme.scaffold.system.dto.NotificationTemplateView;
import com.acme.scaffold.system.dto.PreviewNotificationTemplateRequest;
import com.acme.scaffold.system.dto.UpdateNotificationTemplateRequest;
import com.acme.scaffold.system.service.NotificationTemplateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知模板中心接口（V41 通知模板中心）。
 *
 * <p><b>权限</b>：查询类端点取 {@code system:notification-template:read}，写操作（增/改/删/启停）
 * 取 {@code system:notification-template:manage}——权限码由 {@code ApiResourceAutoSyncRunner}
 * 启动时扫描 {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记。</p>
 *
 * <p><b>与发送链路解耦</b>：本接口只管理模板本身（含渲染预览），具体发送仍走
 * {@code NotificationController#send}，由发送侧取模板、渲染后投递。</p>
 */
@Tag(name = "通知模板中心")
@RestController
@RequestMapping("/api/system/notification-templates")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    @Operation(summary = "分页查询通知模板（支持渠道/关键字/启用状态筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:notification-template:read')")
    public Result<PageResult<NotificationTemplateView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(service.page(page, size, channel, keyword, enabled, sortField, sortDirection));
    }

    @Operation(summary = "查询单个通知模板")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notification-template:read')")
    public Result<NotificationTemplateView> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @Operation(summary = "获取模板变量名列表（${var} 占位解析）")
    @GetMapping("/{id}/variables")
    @PreAuthorize("hasAuthority('system:notification-template:read')")
    public Result<List<String>> variables(@PathVariable Long id) {
        return Result.success(service.variables(id));
    }

    @Operation(summary = "渲染预览：用变量映射渲染模板正文，返回渲染结果与缺失变量")
    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('system:notification-template:read')")
    public Result<Map<String, Object>> preview(@PathVariable Long id,
                                               @Valid @RequestBody PreviewNotificationTemplateRequest request) {
        return Result.success(service.preview(id, request));
    }

    @Operation(summary = "创建通知模板")
    @PostMapping
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public Result<Long> create(@Valid @RequestBody CreateNotificationTemplateRequest request) {
        return Result.success(service.create(request));
    }

    @Operation(summary = "更新通知模板（部分更新，仅传入字段参与）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        service.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除通知模板（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.success();
    }

    @Operation(summary = "启停通知模板")
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggle(id, enabled);
        return Result.success();
    }
}
