package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.MessageReadStatsView;
import com.acme.scaffold.system.dto.MessageReceiptQuery;
import com.acme.scaffold.system.dto.MessageReceiptView;
import com.acme.scaffold.system.service.MessageReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 站内信已读回执接口（M8）。
 *
 * <p>权限分层（与公告回执同构，但站内信是私有一对多，故管理面动作额外校验「当前用户是发送者」）：
 * <ul>
 *   <li>收件箱/已读/未读数等用户自身动作在 {@link MessageController} 中，仅需登录；
 *   <li>「已读统计 / 回执管理 / 催办」是管理面动作，分别需要
 *       {@code system:message:read-receipt} / {@code system:message:remind}
 *       （由 ApiResourceAutoSyncRunner 启动时自动登记，无需手工种子）。
 * </ul>
 * 与 {@link MessageController} 共用 {@code /api/system/messages} 基路径，完整路径无碰撞：
 * {@code /{id}/read-stats} / {@code /{id}/receipts} / {@code /{id}/remind-unread}
 * 与字面端点 {@code /{messageId}/read} 段名不同，Spring 按精确段匹配互不冲突。
 */
@Tag(name = "站内信已读回执")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/messages")
public class MessageReadController {

    private final MessageReadService readService;

    @Operation(summary = "站内信已读统计（已读率/已读人数/未读人数，eager 聚合）")
    @GetMapping("/{id}/read-stats")
    @PreAuthorize("hasAuthority('system:message:read-receipt')")
    public Result<MessageReadStatsView> readStats(@PathVariable Long id) {
        return Result.success(readService.readStats(id));
    }

    @Operation(summary = "站内信回执分页（谁已读/未读、何时已读，支持阅读人关键字与回执状态筛选）")
    @GetMapping("/{id}/receipts")
    @PreAuthorize("hasAuthority('system:message:read-receipt')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询站内信已读回执")
    public Result<PageResult<MessageReceiptView>> receipts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String readStatus) {
        return Result.success(readService.pageReceipts(
                new MessageReceiptQuery(id, page, size, keyword, readStatus)));
    }

    @Operation(summary = "催办未读用户（给未读者发站内信，默认 100 人、上限 500，仅发送者可催办）")
    @PostMapping("/{id}/remind-unread")
    @PreAuthorize("hasAuthority('system:message:remind')")
    @AuditOperation(module = "system", type = "UPDATE", name = "催办站内信未读")
    public Result<Map<String, Integer>> remindUnread(
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit) {
        int sent = readService.remindUnread(id, limit);
        return Result.success(Map.of("reminded", sent));
    }
}
