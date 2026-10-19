package com.acme.scaffold.realtime.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.realtime.dto.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.dto.RealtimePushResultView;
import com.acme.scaffold.realtime.dto.RealtimeSessionView;
import com.acme.scaffold.realtime.dto.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.service.RealtimeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实时推送管理接口：暴露在线会话视图与运维侧手动推送能力，补齐 realtime 模块缺失的 REST 入口。
 * <p>与既有 {@code RealtimePushService} / {@code RealtimeSessionManager} / WebSocket 处理器同源，
 * 仅在不改变既有推送语义的前提下提供只读查询与定向 / 广播触发；离线用户本次不推送，
 * 前端进入页面时通过 REST 拉取全量未读 / 待办（与 {@link RealtimePushService} 约定一致）。</p>
 */
@Tag(name = "实时推送管理")
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeAdminService adminService;

    @Operation(summary = "在线会话列表（userId → 活跃会话数）")
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('system:realtime:read')")
    public Result<List<RealtimeSessionView>> sessions() {
        return Result.success(adminService.listSessions());
    }

    @Operation(summary = "在线会话总数")
    @GetMapping("/online-count")
    @PreAuthorize("hasAuthority('system:realtime:read')")
    public Result<Integer> onlineCount() {
        return Result.success(adminService.onlineCount());
    }

    @Operation(summary = "向所有在线会话广播")
    @PostMapping("/push/broadcast")
    @PreAuthorize("hasAuthority('system:realtime:push')")
    public Result<RealtimePushResultView> broadcast(@Valid @RequestBody RealtimeBroadcastRequest request) {
        return Result.success(adminService.broadcast(request));
    }

    @Operation(summary = "向指定用户定向推送")
    @PostMapping("/push/user")
    @PreAuthorize("hasAuthority('system:realtime:push')")
    public Result<RealtimePushResultView> pushToUser(@Valid @RequestBody RealtimeUserPushRequest request) {
        return Result.success(adminService.pushToUsers(request));
    }
}
