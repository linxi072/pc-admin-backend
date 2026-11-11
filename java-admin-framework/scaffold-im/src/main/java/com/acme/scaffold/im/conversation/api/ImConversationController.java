package com.acme.scaffold.im.conversation.api;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.im.conversation.application.ImConversationService;
import com.acme.scaffold.im.conversation.dto.ConversationCreateRequest;
import com.acme.scaffold.im.conversation.dto.ConversationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * 会话管理接口。管理类操作统一要求 im:conversation:manage（更细权限由 ApiResourceAutoSyncRunner 自动登记）。
 */
@Tag(name = "站内消息-会话")
@RestController
@RequestMapping("/api/im/conversation")
@RequiredArgsConstructor
public class ImConversationController {

    private final ImConversationService conversationService;

    @Operation(summary = "创建会话（P2P/GROUP）")
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    @AuditOperation(module = "im", type = "CREATE", name = "创建会话")
    public Result<Long> create(@Valid @RequestBody ConversationCreateRequest req) {
        return Result.success(conversationService.createConversation(req));
    }

    @Operation(summary = "我的会话列表（含成员/未读计数/最近消息预览）")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    public Result<List<ConversationView>> my() {
        return Result.success(conversationService.listMyConversations());
    }

    @Operation(summary = "会话详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    public Result<ConversationView> get(@PathVariable Long id) {
        return Result.success(conversationService.getConversation(id));
    }

    @Operation(summary = "添加群成员（仅群主）")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    @AuditOperation(module = "im", type = "UPDATE", name = "添加群成员")
    public Result<Void> addMember(@PathVariable Long id, @RequestParam Long userId) {
        conversationService.addMember(id, userId);
        return Result.success();
    }

    @Operation(summary = "移除群成员（群主移除他人 / 本人退群）")
    @PostMapping("/{id}/members/{userId}/remove")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    @AuditOperation(module = "im", type = "UPDATE", name = "移除群成员")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        conversationService.removeMember(id, userId);
        return Result.success();
    }

    @Operation(summary = "退出会话（本人）")
    @PostMapping("/{id}/quit")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    @AuditOperation(module = "im", type = "UPDATE", name = "退出会话")
    public Result<Void> quit(@PathVariable Long id) {
        conversationService.quitConversation(id);
        return Result.success();
    }

    @Operation(summary = "删除会话（仅群主，逻辑删除）")
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('im:conversation:manage')")
    @AuditOperation(module = "im", type = "DELETE", name = "删除会话")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }
}
