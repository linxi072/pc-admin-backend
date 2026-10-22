package com.acme.scaffold.system.notifypref;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.notifypref.dto.UpdateNotificationPreferenceRequest;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules.NotificationPreferenceMatrix;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知偏好接口（个人中心「通知订阅」页）。
 *
 * <p><b>鉴权约定</b>：与 {@code /api/profile} 同范式——本组接口<b>不加</b>
 * {@code @PreAuthorize} 权限码，任何已登录用户访问自己的订阅即可；服务端一律从
 * 安全上下文取当前用户，接口签名中<b>不存在 userId 参数</b>，不存在越权可能。
 * 因此本模块无菜单、无权限码、无迁移外的 RBAC 播种。</p>
 *
 * <p>路径前缀刻意使用顶级 {@code /api/notification-preferences}（而非 {@code /api/system/...}）：
 * 表达「我的订阅」这一非管理域语义，避免与管理员面的通知模板/记录中心混淆。</p>
 */
@Tag(name = "通知偏好")
@RestController
@RequestMapping("/api/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @Operation(summary = "获取我的通知订阅矩阵（4 渠道 × 4 消息类型）")
    @GetMapping("/mine")
    public Result<NotificationPreferenceMatrix> mine() {
        return Result.success(notificationPreferenceService.mine());
    }

    @Operation(summary = "更新我的通知订阅（批量局部更新，enabled 传 null 恢复默认）")
    @PutMapping("/mine")
    @AuditOperation(module = "profile", type = "UPDATE", name = "更新通知订阅")
    public Result<NotificationPreferenceMatrix> update(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return Result.success(notificationPreferenceService.update(request));
    }
}
