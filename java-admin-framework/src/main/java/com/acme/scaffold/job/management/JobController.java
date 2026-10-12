package com.acme.scaffold.job.management;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.job.management.dto.JobCreateDTO;
import com.acme.scaffold.job.management.dto.JobHandlerOptionVO;
import com.acme.scaffold.job.management.dto.JobLogVO;
import com.acme.scaffold.job.management.dto.JobQuery;
import com.acme.scaffold.job.management.dto.JobUpdateDTO;
import com.acme.scaffold.job.management.dto.JobVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 在线定时任务管理接口。
 *
 * <p>能力：任务配置（执行时间 / 触发周期 / 任务参数）、启动、暂停、列表与实时状态、执行日志。
 *
 * <p><b>权限</b>：读接口需 {@code job:read}；写接口需 {@code job:manage}。
 * 权限码由 V21 迁移登记到 {@code sys_api_resource} 并授予 SUPER_ADMIN，同时登记
 * {@code sys_menu} 菜单（route=/job/list）以驱动侧边栏可见性。
 */
@Tag(name = "定时任务管理")
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "任务元信息（调度引擎可用性 / 最小间隔秒数）")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('job:read')")
    public Result<Map<String, Object>> meta() {
        return Result.success(Map.of(
                "schedulerAvailable", jobService.schedulerAvailable(),
                "smallestIntervalSeconds", JobNextFire.smallestIntervalSeconds()));
    }

    @Operation(summary = "可选任务处理器列表")
    @GetMapping("/handlers")
    @PreAuthorize("hasAuthority('job:read')")
    public Result<List<JobHandlerOptionVO>> handlers() {
        return Result.success(jobService.handlerOptions());
    }

    @Operation(summary = "分页查询定时任务（支持名称/分组/状态/触发类型/处理器筛选）")
    @GetMapping
    @PreAuthorize("hasAuthority('job:read')")
    public Result<PageResult<JobVO>> page(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String taskKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(jobService.page(
                new JobQuery(jobName, jobGroup, status, triggerType, taskKey, page, size)));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('job:read')")
    public Result<JobVO> detail(@PathVariable Long id) {
        return Result.success(jobService.detail(id));
    }

    @Operation(summary = "任务执行日志（按触发时间倒序）")
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('job:read')")
    public Result<PageResult<JobLogVO>> logs(@PathVariable Long id,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(jobService.logs(id, page, size));
    }

    @Operation(summary = "新建任务（初始暂停，需显式启动）")
    @PostMapping
    @PreAuthorize("hasAuthority('job:manage')")
    @AuditOperation(module = "job", type = "CREATE", name = "新建定时任务")
    public Result<String> create(@RequestBody JobCreateDTO dto) {
        // 雪花 ID 以字符串返回，避免 18 位数字经 JSON number 上链被前端 round 后无法按 ID 操作
        return Result.success(String.valueOf(jobService.create(dto)));
    }

    @Operation(summary = "编辑任务（运行中会先暂停并按新配置重启，即时生效）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('job:manage')")
    @AuditOperation(module = "job", type = "UPDATE", name = "编辑定时任务")
    public Result<Void> update(@PathVariable Long id, @RequestBody JobUpdateDTO dto) {
        jobService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启动任务（立即注册到调度器，即时生效）")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('job:manage')")
    @AuditOperation(module = "job", type = "UPDATE", name = "启动定时任务")
    public Result<Void> start(@PathVariable Long id) {
        jobService.start(id);
        return Result.success();
    }

    @Operation(summary = "暂停任务（立即取消调度）")
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('job:manage')")
    @AuditOperation(module = "job", type = "UPDATE", name = "暂停定时任务")
    public Result<Void> pause(@PathVariable Long id) {
        jobService.pause(id);
        return Result.success();
    }

    @Operation(summary = "删除任务（逻辑删除，运行中会先取消调度）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('job:manage')")
    @AuditOperation(module = "job", type = "DELETE", name = "删除定时任务")
    public Result<Void> remove(@PathVariable Long id) {
        jobService.remove(id);
        return Result.success();
    }
}
