package com.acme.scaffold.workbench.controller;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.dto.AuditLogQuery;
import com.acme.scaffold.common.audit.AuditLogService;
import com.acme.scaffold.common.audit.AuditLogView;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.workbench.dto.WorkbenchStatsVO;
import com.acme.scaffold.workbench.service.WorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作台接口。
 */
@Tag(name = "工作台")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final SecurityContextFacade securityContextFacade;
    private final AuditLogService auditLogService;

    /**
     * 工作台统计卡片数据（数量 / 上一周期值 / 趋势）。
     *
     * <p>不加 {@code @PreAuthorize}：工作台是所有登录用户的落地页，且统计口径已按当前登录人收敛
     * （如「我的待办」），与 {@code GET /api/system/menus/mine} 一致——仅要求认证，不额外要求权限点。
     */
    @Operation(summary = "工作台统计卡片数据（按当前登录人收敛）")
    @GetMapping("/stats")
    public Result<WorkbenchStatsVO> stats() {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        return Result.success(workbenchService.stats(userId));
    }

    /**
     * 最近动态：当前登录人自己最近的操作记录（最多 limit 条）。
     *
     * <p>复用审计查询，但按当前登录人收敛且不限权限点——与「我的待办」一致，是落地页的个人信息面板，
     * 不含他人数据，故不加 {@code @PreAuthorize}。摘要按脱敏视图返回。</p>
     */
    @Operation(summary = "最近动态：当前登录人自己最近的操作记录")
    @GetMapping("/recent-activities")
    public Result<List<AuditLogView>> recentActivities(
            @RequestParam(defaultValue = "8") int limit) {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        int size = Math.min(Math.max(limit, 1), 50);
        AuditLogQuery query = new AuditLogQuery(1, size, null, null, userId, null, null, null,
                null, null, null, null, "occurredAt", "DESC");
        PageResult<AuditLogView> page = auditLogService.query(query, false);
        return Result.success(page.records());
    }
}
