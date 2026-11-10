package com.acme.scaffold.im.message.api;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.im.message.dto.MessageSendRequest;
import com.acme.scaffold.im.message.dto.MessageView;
import com.acme.scaffold.im.message.application.ImMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息管理接口。发送要求 im:message:send；查看/已读/未读要求 im:message:view
 * （由 ApiResourceAutoSyncRunner 自动登记并授予 SUPER_ADMIN）。
 */
@Tag(name = "站内消息-消息")
@RestController
@RequestMapping("/api/im/message")
@RequiredArgsConstructor
public class ImMessageController {

    private final ImMessageService messageService;

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('im:message:send')")
    @AuditOperation(module = "im", type = "CREATE", name = "发送消息")
    public Result<Long> send(@Valid @RequestBody MessageSendRequest req) {
        return Result.success(messageService.send(req));
    }

    @Operation(summary = "会话消息分页（时间正序）")
    @GetMapping("/conversation/{conversationId}")
    @PreAuthorize("hasAuthority('im:message:view')")
    public Result<PageResult<MessageView>> list(@PathVariable Long conversationId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "30") int size) {
        return Result.success(messageService.listMessages(conversationId, page, size));
    }

    @Operation(summary = "消息详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('im:message:view')")
    public Result<MessageView> get(@PathVariable Long id) {
        return Result.success(messageService.getById(id));
    }

    @Operation(summary = "撤回消息（仅本人）")
    @PostMapping("/{id}/recall")
    @PreAuthorize("hasAuthority('im:message:send')")
    @AuditOperation(module = "im", type = "UPDATE", name = "撤回消息")
    public Result<Void> recall(@PathVariable Long id) {
        messageService.recall(id);
        return Result.success();
    }

    @Operation(summary = "标记会话已读（当前用户）")
    @PostMapping("/conversation/{conversationId}/read")
    @PreAuthorize("hasAuthority('im:message:view')")
    public Result<Void> markRead(@PathVariable Long conversationId) {
        messageService.markRead(conversationId);
        return Result.success();
    }

    @Operation(summary = "当前用户未读消息总数")
    @GetMapping("/unread/count")
    @PreAuthorize("hasAuthority('im:message:view')")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount());
    }
}
