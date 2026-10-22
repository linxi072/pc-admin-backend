package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.NotificationItemView;
import com.acme.scaffold.system.dto.NotificationSummaryView;
import com.acme.scaffold.system.service.NotificationCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知中心接口（M9）。
 *
 * <p>贯穿全站的统一未读入口：聚合公告未读与站内信未读，供顶栏统一铃铛/抽屉使用。
 * 端点均为登录用户自身动作（查看自己的未读、标记自己已读），仅登录即可，不挂权限码——
 * 与既有的 {@code /api/system/messages/unread-count}、{@code /mine/unread} 同构，
 * 保证所有登录用户（含非 SUPER_ADMIN）都能用铃铛，无需额外授权。
 *
 * <p>基路径 {@code /api/notification-center} 与 M6 跨渠道通知
 * {@code /api/system/notifications} 字面不同、无碰撞。
 */
@Tag(name = "通知中心")
@RestController
@RequestMapping("/api/notification-center")
@RequiredArgsConstructor
public class NotificationCenterController {

    private final NotificationCenterService centerService;

    @Operation(summary = "未读汇总（公告未读 + 站内信未读 + 合计），供顶栏铃铛角标")
    @GetMapping("/summary")
    public Result<NotificationSummaryView> summary() {
        return Result.success(centerService.summary());
    }

    @Operation(summary = "近期未读条目（公告未读 + 站内信未读，按时间倒序合并截断）")
    @GetMapping("/recent")
    public Result<List<NotificationItemView>> recent(
            @RequestParam(required = false) Integer limit) {
        return Result.success(centerService.recent(limit));
    }

    @Operation(summary = "一键全部已读（公告 + 站内信两类来源）")
    @PostMapping("/mark-all-read")
    @AuditOperation(module = "system", type = "UPDATE", name = "通知中心全部已读")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.success(centerService.markAllRead());
    }
}
