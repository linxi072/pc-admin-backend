package com.acme.scaffold.system.maskstrategy;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.maskstrategy.dto.CreateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.dto.MaskStrategyView;
import com.acme.scaffold.system.maskstrategy.dto.UpdateMaskStrategyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * 脱敏策略中心接口（V46 脱敏策略中心）。
 *
 * <p><b>权限</b>：查询类端点取 {@code system:mask-strategy:read}，写操作（增/改/删/启停/刷新缓存）
 * 取 {@code system:mask-strategy:manage}——权限码由 {@code ApiResourceAutoSyncRunner}
 * 启动时扫描 {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记。</p>
 *
 * <p><b>与脱敏内核解耦</b>：本接口只管理策略本身（CRUD + 缓存刷新），具体脱敏算法仍走
 * {@code common.sensitive.SensitiveMasker}（M1/M20 共用），由 {@link MaskStrategyResolver}
 * 在序列化/导出前统一分派。菜单（system:mask-strategy）由 V46 迁移播种并绑定 SUPER_ADMIN。</p>
 */
@Tag(name = "脱敏策略中心")
@RestController
@RequestMapping("/api/system/mask-strategies")
@RequiredArgsConstructor
public class MaskStrategyController {

    private final MaskStrategyService service;

    @Operation(summary = "分页查询脱敏策略（支持字段键/脱敏类型/启用状态筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:mask-strategy:read')")
    public Result<PageResult<MaskStrategyView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String sensitiveType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(service.page(page, size, fieldKey, sensitiveType, enabled,
                sortField, sortDirection));
    }

    @Operation(summary = "查询全部脱敏策略（含停用）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:mask-strategy:read')")
    public Result<List<MaskStrategyView>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "查询单个脱敏策略")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:mask-strategy:read')")
    public Result<MaskStrategyView> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @Operation(summary = "创建脱敏策略")
    @PostMapping
    @PreAuthorize("hasAuthority('system:mask-strategy:manage')")
    @AuditOperation(module = "system", type = "CREATE", name = "创建脱敏策略")
    public Result<Long> create(@Valid @RequestBody CreateMaskStrategyRequest request) {
        return Result.success(service.create(request));
    }

    @Operation(summary = "更新脱敏策略（部分更新，仅传入字段参与）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:mask-strategy:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新脱敏策略")
    public Result<Void> update(@PathVariable Long id,
                              @Valid @RequestBody UpdateMaskStrategyRequest request) {
        service.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除脱敏策略（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:mask-strategy:manage')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除脱敏策略")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.success();
    }

    @Operation(summary = "启停脱敏策略")
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('system:mask-strategy:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "启停脱敏策略")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggle(id, enabled);
        return Result.success();
    }

    @Operation(summary = "刷新脱敏策略解析缓存（全量失效，下次命中重新加载）")
    @PostMapping("/refresh-cache")
    @PreAuthorize("hasAuthority('system:mask-strategy:manage')")
    public Result<Long> refreshCache() {
        return Result.success(service.refreshCache());
    }
}
