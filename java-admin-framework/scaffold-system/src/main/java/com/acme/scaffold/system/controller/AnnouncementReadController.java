package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.AnnouncementReadStatsView;
import com.acme.scaffold.system.dto.AnnouncementReceiptQuery;
import com.acme.scaffold.system.dto.AnnouncementReceiptView;
import com.acme.scaffold.system.dto.AnnouncementView;
import com.acme.scaffold.system.service.AnnouncementReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 公告已读回执接口（V40）。
 *
 * <p>权限分层（与站内信同构）：
 * <ul>
 *   <li>「标记已读 / 我的未读」是用户自身动作，仅需登录（Security 兜底），不挂权限码；</li>
 *   <li>「回执分页 / 已读统计 / 催办」是管理面动作，分别需要
 *       {@code system:announcement:read-receipt} / {@code system:announcement:remind}
 *       （由 ApiResourceAutoSyncRunner 启动时自动登记，无需手工种子）。</li>
 * </ul>
 * 与 {@link AnnouncementController} 共用 {@code /api/system/announcements} 基路径，
 * 完整路径无碰撞（字面路径优先于 {@code /{id}} 模式匹配）。
 */
@Tag(name = "公告已读回执")
@RestController
@RequestMapping("/api/system/announcements")
@RequiredArgsConstructor
public class AnnouncementReadController {

    private final AnnouncementReadService readService;

    @Operation(summary = "标记公告已读（幂等；详情页打开时由前端调用）")
    @PostMapping("/{id}/read")
    @AuditOperation(module = "system", type = "UPDATE", name = "公告标记已读")
    public Result<Void> markRead(@PathVariable Long id) {
        readService.markRead(id);
        return Result.success();
    }

    @Operation(summary = "我的未读公告分页（反连接推导，置顶优先）")
    @GetMapping("/mine/unread")
    @AuditOperation(module = "system", type = "QUERY", name = "查询我的未读公告")
    public Result<PageResult<AnnouncementView>> myUnread(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(readService.myUnread(page, size));
    }

    @Operation(summary = "我的未读公告总数（顶栏统一铃铛/通知中心聚合用）")
    @GetMapping("/mine/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("unread", readService.countMyUnread()));
    }

    @Operation(summary = "一键全部已读（仅标记当前用户的有效未读公告）")
    @PostMapping("/mark-all-read")
    @AuditOperation(module = "system", type = "UPDATE", name = "全部已读公告")
    public Result<Map<String, Integer>> markAllRead() {
        int marked = readService.markAllRead();
        return Result.success(Map.of("marked", marked));
    }

    @Operation(summary = "公告已读回执分页（谁在何时已读，支持阅读人关键字）")
    @GetMapping("/{id}/read-receipts")
    @PreAuthorize("hasAuthority('system:announcement:read-receipt')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询公告已读回执")
    public Result<PageResult<AnnouncementReceiptView>> readReceipts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(readService.pageReceipts(
                new AnnouncementReceiptQuery(id, page, size, keyword)));
    }

    @Operation(summary = "公告已读统计（已读率/已读人数/未读人数）")
    @GetMapping("/{id}/read-stats")
    @PreAuthorize("hasAuthority('system:announcement:read-receipt')")
    public Result<AnnouncementReadStatsView> readStats(@PathVariable Long id) {
        return Result.success(readService.stats(id));
    }

    @Operation(summary = "已读统计分页（公告管理页「已读进度」列数据源）")
    @GetMapping("/read-stats/page")
    @PreAuthorize("hasAuthority('system:announcement:read-receipt')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询公告已读统计")
    public Result<PageResult<AnnouncementReadStatsView>> readStatsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(readService.listStats(page, size, keyword));
    }

    @Operation(summary = "催办未读用户（给未读者发站内信，默认 100 人、上限 500）")
    @PostMapping("/{id}/remind-unread")
    @PreAuthorize("hasAuthority('system:announcement:remind')")
    @AuditOperation(module = "system", type = "UPDATE", name = "催办公告未读")
    public Result<Map<String, Integer>> remindUnread(
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit) {
        int sent = readService.remindUnread(id, limit);
        return Result.success(Map.of("reminded", sent));
    }
}
