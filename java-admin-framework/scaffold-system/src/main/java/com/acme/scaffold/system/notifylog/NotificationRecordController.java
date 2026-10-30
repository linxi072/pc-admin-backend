package com.acme.scaffold.system.notifylog;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知发送记录接口（V42 通知记录页）。
 *
 * <p><b>权限</b>：独立权限码 {@code system:notification-record:read}——记录含操作人、
 * 模板来源、失败原因等运营信息，与消息中心 {@code system:message:read}、模板中心
 * {@code system:notification-template:read} 分开，便于按角色最小化授权。</p>
 *
 * <p><b>脱敏</b>：入库前已完成（标题/正文摘要化、接收方按渠道掩码），本接口直出，
 * 不提供「查看原文」入口——通知原文与明文 PII 不落库是该模块的安全前提。</p>
 */
@Tag(name = "通知发送记录")
@RestController
@RequestMapping("/api/system/notification-records")
@RequiredArgsConstructor
public class NotificationRecordController {

    private final NotificationRecordService notificationRecordService;

    @Operation(summary = "分页查询通知发送记录（支持渠道/结果/关键字/时间区间）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:notification-record:read')")
    public Result<PageResult<NotificationRecordView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(notificationRecordService.page(page, size, channel, status, keyword,
                startTime, endTime, sortField, sortDirection));
    }

    @Operation(summary = "通知发送统计摘要（总数/成功/失败/失败率/最近发送）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:notification-record:read')")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(notificationRecordService.summary(channel, status, keyword,
                startTime, endTime));
    }
}
