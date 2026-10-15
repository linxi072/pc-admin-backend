package com.acme.scaffold.system.log;

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
 * 登录日志接口（V29 登录日志页）。
 *
 * <p><b>与报表域的关系</b>：「报表管理 - 登录统计」是对 {@code sys_login_log} 的聚合视图，
 * 本接口是同一数据源的逐条明细视图，供安全审计场景（异常登录排查、撞库识别）使用。
 *
 * <p><b>权限</b>：独立权限码 {@code system:login-log:read}——登录日志含用户名/IP/UA，
 * 属敏感数据，不与 {@code system:audit:read} 混用，便于按角色最小化授权。
 */
@Tag(name = "登录日志")
@RestController
@RequestMapping("/api/system/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @Operation(summary = "分页查询登录日志（支持用户名/结果/登录类型/失败码/IP/关键字/时间区间）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:login-log:read')")
    public Result<PageResult<LoginLogView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String failureCode,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(loginLogService.page(page, size, username, result, loginType,
                failureCode, ipAddress, keyword, startTime, endTime, sortField, sortDirection));
    }

    @Operation(summary = "登录日志统计摘要（总数/成功/失败/失败率/独立用户/最近登录）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:login-log:read')")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String failureCode,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(loginLogService.summary(username, result, loginType,
                failureCode, ipAddress, keyword, startTime, endTime));
    }
}
