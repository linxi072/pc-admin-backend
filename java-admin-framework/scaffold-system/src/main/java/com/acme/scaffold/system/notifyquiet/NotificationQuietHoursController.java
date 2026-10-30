package com.acme.scaffold.system.notifyquiet;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.notifyquiet.dto.UpdateQuietHoursRequest;
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
 * 免打扰时段接口（个人中心「免打扰」页）。
 *
 * <p><b>鉴权约定</b>：与 {@code /api/profile} / {@code /api/notification-preferences} 同范式——
 * 本组接口<b>不加</b> {@code @PreAuthorize} 权限码，任何已登录用户访问自己的配置即可；
 * 服务端一律从安全上下文取当前用户，接口签名中<b>不存在 userId 参数</b>，不存在越权可能。
 * 因此本模块无菜单、无权限码、无迁移外的 RBAC 播种。</p>
 *
 * <p>路径前缀刻意使用顶级 {@code /api/notification-quiet-hours}（而非 {@code /api/system/...}）：
 * 表达「我的作息」这一非管理域语义，避免与管理员面的通知模板/记录中心混淆。</p>
 *
 * <p><b>时间口径</b>：起止时间为 {@code HH:mm}（服务器本地时间），半开区间 {@code [start, end)}；
 * 结束时间小于开始时间表达跨午夜窗口（如 22:00–08:00）。告警消息（ALERT）可按
 * {@code alertBreakthrough} 穿透免打扰。</p>
 */
@Tag(name = "免打扰时段")
@RestController
@RequestMapping("/api/notification-quiet-hours")
@RequiredArgsConstructor
public class NotificationQuietHoursController {

    private final NotificationQuietHoursService notificationQuietHoursService;

    @Operation(summary = "获取我的免打扰配置（未配置返回默认值：未启用 + 22:00–08:00 + 告警穿透）")
    @GetMapping("/mine")
    public Result<NotificationQuietHoursView> mine() {
        return Result.success(notificationQuietHoursService.mine());
    }

    @Operation(summary = "更新我的免打扰（全量快照：启用开关 + 起止时间 + 告警穿透）")
    @PutMapping("/mine")
    @AuditOperation(module = "profile", type = "UPDATE", name = "更新免打扰时段")
    public Result<NotificationQuietHoursView> update(
            @Valid @RequestBody UpdateQuietHoursRequest request) {
        return Result.success(notificationQuietHoursService.update(request));
    }
}
