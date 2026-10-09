package com.acme.scaffold.workbench;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.context.SecurityContextFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
