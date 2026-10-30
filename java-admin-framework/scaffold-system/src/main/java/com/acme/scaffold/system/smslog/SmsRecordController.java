package com.acme.scaffold.system.smslog;

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
 * 短信发送记录接口（V37 短信记录页）。
 *
 * <p><b>权限</b>：独立权限码 {@code system:sms-record:read}——记录含操作人、批次号、
 * 失败原因等运营信息，不与消息中心 {@code system:message:read} 混用，便于按角色最小化授权。</p>
 *
 * <p><b>脱敏</b>：入库前已完成（手机号掩码、正文摘要化），本接口直出，
 * 不提供「查看原文」入口——短信原文不落库是该模块的安全前提。</p>
 */
@Tag(name = "短信发送记录")
@RestController
@RequestMapping("/api/system/sms-records")
@RequiredArgsConstructor
public class SmsRecordController {

    private final SmsRecordService smsRecordService;

    @Operation(summary = "分页查询短信发送记录（支持结果/网关/关键字/时间区间）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:sms-record:read')")
    public Result<PageResult<SmsRecordView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(smsRecordService.page(page, size, status, provider, keyword,
                startTime, endTime, sortField, sortDirection));
    }

    @Operation(summary = "短信发送统计摘要（总数/成功/失败/失败率/最近发送）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:sms-record:read')")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(smsRecordService.summary(status, provider, keyword,
                startTime, endTime));
    }
}
