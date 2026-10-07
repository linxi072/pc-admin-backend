package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.AnnouncementQuery;
import com.acme.scaffold.system.dto.AnnouncementView;
import com.acme.scaffold.system.dto.CreateAnnouncementRequest;
import com.acme.scaffold.system.dto.UpdateAnnouncementRequest;
import com.acme.scaffold.system.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 系统公告接口。
 * <p>权限码：公告的「管理(写)」与「阅读(读)」分离——普通用户登录后可读已发布公告，
 * 但只有持有 announcement:* 权限的管理员才能创建/编辑/发布/下线/删除。
 */
@Tag(name = "系统公告")
@RestController
@RequestMapping("/api/system/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "分页查询公告（支持标题/内容关键词搜索）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:announcement:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询公告列表")
    public Result<PageResult<AnnouncementView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean onlyValid,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(announcementService.list(
                new AnnouncementQuery(page, size, keyword, status, onlyValid, sortField, sortDirection)));
    }

    @Operation(summary = "公告详情（累加浏览次数）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:announcement:read')")
    public Result<AnnouncementView> get(@PathVariable Long id) {
        return Result.success(announcementService.get(id));
    }

    @Operation(summary = "新建公告（初始为草稿）")
    @PostMapping
    @PreAuthorize("hasAuthority('system:announcement:create')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建公告")
    public Result<Long> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        return Result.success(announcementService.create(request));
    }

    @Operation(summary = "编辑公告（仅草稿/已下线可编辑）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:announcement:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "编辑公告")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateAnnouncementRequest request) {
        announcementService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "发布公告（支持定时生效：publishAt 为空则立即生效）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('system:announcement:publish')")
    @AuditOperation(module = "system", type = "PUBLISH", name = "发布公告")
    public Result<Void> publish(@PathVariable Long id,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishAt,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expireAt) {
        announcementService.publish(id, publishAt, expireAt);
        return Result.success();
    }

    @Operation(summary = "下线公告")
    @PostMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('system:announcement:publish')")
    @AuditOperation(module = "system", type = "OFFLINE", name = "下线公告")
    public Result<Void> offline(@PathVariable Long id) {
        announcementService.offline(id);
        return Result.success();
    }

    @Operation(summary = "切换置顶")
    @PostMapping("/{id}/toggle-top")
    @PreAuthorize("hasAuthority('system:announcement:update')")
    @AuditOperation(module = "system", type = "UPDATE", name = "公告置顶切换")
    public Result<Void> toggleTop(@PathVariable Long id) {
        announcementService.toggleTop(id);
        return Result.success();
    }

    @Operation(summary = "删除公告（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:announcement:delete')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.success();
    }
}
