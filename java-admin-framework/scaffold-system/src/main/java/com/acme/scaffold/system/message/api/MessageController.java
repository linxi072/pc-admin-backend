package com.acme.scaffold.system.message.api;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.message.application.MessageService;
import com.acme.scaffold.system.message.domain.model.SysMessageDO;
import com.acme.scaffold.system.message.dto.MessageView;
import com.acme.scaffold.system.message.dto.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

/**
 * 站内信接口。
 * <p>权限码：发送需 system:message:send；收件箱/已读/未读数仅需登录（由 Security 兜底），
 * 发送方视角（我发出的）需 system:message:read。
 */
@Tag(name = "站内信")
@RestController
@RequestMapping("/api/system/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "发送站内信（单条指定接收人，或按角色/部门批量发送）")
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('system:message:send')")
    @AuditOperation(module = "system", type = "CREATE", name = "发送站内信")
    public Result<Map<String, Object>> send(@Valid @RequestBody SendMessageRequest request) {
        int count = messageService.send(request);
        return Result.success(Map.of("receiverCount", count));
    }

    @Operation(summary = "我的消息分页（按未读优先排序）")
    @GetMapping("/mine")
    @AuditOperation(module = "system", type = "QUERY", name = "查询我的站内信")
    public Result<PageResult<MessageView>> mine(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(defaultValue = "false") boolean onlyUnread) {
        return Result.success(messageService.pageMine(page, size, onlyUnread));
    }

    @Operation(summary = "我的消息游标（keyset）分页：深翻页性能稳定")
    @GetMapping("/mine-keyset")
    @AuditOperation(module = "system", type = "QUERY", name = "游标查询我的站内信")
    public Result<KeysetPageResult<MessageView>> mineKeyset(@RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String cursor,
                                                           @RequestParam(defaultValue = "false") boolean onlyUnread) {
        return Result.success(messageService.pageMineKeyset(size, cursor, onlyUnread));
    }

    @Operation(summary = "我的未读数量（用于未读提醒徽标）")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("unread", messageService.unreadCount()));
    }

    @Operation(summary = "标记单条已读（记录已读回执时间，幂等）")
    @PostMapping("/{messageId}/read")
    @AuditOperation(module = "system", type = "UPDATE", name = "站内信标记已读")
    public Result<Void> markRead(@PathVariable Long messageId) {
        messageService.markRead(messageId);
        return Result.success();
    }

    @Operation(summary = "一键全部已读")
    @PostMapping("/read-all")
    @AuditOperation(module = "system", type = "UPDATE", name = "站内信全部已读")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.success(Map.of("updated", messageService.markAllRead()));
    }

    @Operation(summary = "我发出的消息分页（含已读统计）")
    @GetMapping("/sent")
    @PreAuthorize("hasAuthority('system:message:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询已发送站内信")
    public Result<PageResult<SysMessageDO>> sent(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageService.pageSent(page, size));
    }

    @Operation(summary = "我发出的消息游标（keyset）分页：深翻页性能稳定")
    @GetMapping("/sent-keyset")
    @PreAuthorize("hasAuthority('system:message:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "游标查询已发送站内信")
    public Result<KeysetPageResult<SysMessageDO>> sentKeyset(@RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String cursor) {
        return Result.success(messageService.pageSentKeyset(size, cursor));
    }
}
