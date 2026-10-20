package com.acme.scaffold.common.idempotency.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.idempotency.dto.IdempotencyRecordView;
import com.acme.scaffold.common.idempotency.dto.IdempotencyStatsView;
import com.acme.scaffold.common.idempotency.service.IdempotencyRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 幂等记录管理接口（M2 幂等模块的运维侧面，{@code /api/system/idempotency-records}）。
 *
 * <p><b>权限</b>：查询 {@code system:idempotency:read}、清理 {@code system:idempotency:manage}，
 * 读写分离便于按角色最小化授权。权限码无需手工播种——{@code ApiResourceAutoSyncRunner}
 * 启动时扫描 {@code @PreAuthorize} 自动登记 {@code sys_api_resource} 并授予 SUPER_ADMIN。</p>
 *
 * <p><b>与核心写路径的关系</b>：本控制器不触碰 {@code IdempotencyService} 的
 * acquire / mark 语义，也不提供单条删除（见服务类注释中的安全论证）；
 * 清理端点与周期任务 {@code IdempotencyCleanupJob} 同口径，可视为该任务的手动触发。</p>
 */
@Tag(name = "幂等记录管理")
@RestController
@RequestMapping("/api/system/idempotency-records")
@RequiredArgsConstructor
public class IdempotencyRecordController {

    private final IdempotencyRecordService idempotencyRecordService;

    @Operation(summary = "分页查询幂等记录（支持状态/接口域/用户/关键字/时间区间，默认最近 7 天）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:idempotency:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询幂等记录")
    public Result<PageResult<IdempotencyRecordView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apiScope,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(idempotencyRecordService.page(page, size, status, apiScope, userId,
                keyword, startTime, endTime, sortField, sortDirection));
    }

    @Operation(summary = "幂等记录统计摘要（总数/三态分布/已过期待清理/PROCESSING 占比/最近登记）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:idempotency:read')")
    public Result<IdempotencyStatsView> summary(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apiScope,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(idempotencyRecordService.summary(status, apiScope, userId, keyword,
                startTime, endTime));
    }

    @Operation(summary = "手动清理过期幂等记录（与周期任务同口径，返回删除条数）")
    @PostMapping("/purge")
    @PreAuthorize("hasAuthority('system:idempotency:manage')")
    @AuditOperation(module = "system", type = "DELETE", name = "清理过期幂等记录")
    public Result<Integer> purge() {
        return Result.success(idempotencyRecordService.purgeExpired());
    }
}
