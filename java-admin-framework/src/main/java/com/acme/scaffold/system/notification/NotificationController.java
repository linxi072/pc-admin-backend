package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.idempotency.Idempotent;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.NotificationService;
import com.acme.scaffold.system.notification.dto.SendNotificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 跨渠道通知接口。权限复用站内信发送（system:message:send）；
 * 端点由 ApiResourceAutoSyncRunner 启动扫描自动登记，无需手填接口资源。
 */
@Tag(name = "通知渠道")
@RestController
@RequestMapping("/api/system/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "跨渠道发送通知（channel 指定 IN_APP / WEBHOOK / WEBSOCKET / EMAIL）")
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('system:message:send')")
    @AuditOperation(module = "system", type = "CREATE", name = "跨渠道发送通知")
    @Idempotent(scope = "system:notification:send")
    public Result<Map<String, Object>> send(@Valid @RequestBody SendNotificationRequest request) {
        NotificationMessage msg = new NotificationMessage(
                request.channel(),
                request.title(),
                request.content(),
                request.msgType(),
                request.receiverIds(),
                request.roleIds(),
                request.orgIds(),
                request.webhookUrl(),
                null,
                request.emails());
        int delivered = notificationService.dispatch(msg);
        return Result.success(Map.of("delivered", delivered));
    }
}
