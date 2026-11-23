package com.acme.scaffold.config.ops;

import com.acme.scaffold.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一运维控制台聚合接口（E2）：集中返回各 feature-gate 的运行态。
 *
 * <p><b>始终注册</b>（不加 {@code @ConditionalOnProperty}）——运维控制台的前提是即使某特性关闭，
 * 也能看到其开关状态；本接口仅从 {@link Environment} 读配置值，不依赖任何特性 Bean，
 * 故无开关依赖，关闭态也返回完整清单。默认态由 {@link FeatureGateRegistry} 单一事实来源给出。</p>
 *
 * <p>权限码 {@code system:ops:read} 由 {@code ApiResourceAutoSyncRunner} 自动登记并授予 SUPER_ADMIN。</p>
 */
@Tag(name = "统一运维控制台")
@RestController
@RequestMapping("/api/system/ops")
@RequiredArgsConstructor
public class OpsConsoleController {

    private final Environment environment;

    /** 单个开关的运行态。 */
    public record GateStatus(String property, String label, boolean enabled, boolean fromDefault) {
    }

    /** 聚合视图。 */
    public record OpsOverview(List<GateStatus> gates, int enabledCount, int totalCount) {
    }

    @Operation(summary = "各特性开关运行态聚合（统一运维控制台）")
    @GetMapping("/gates")
    @PreAuthorize("hasAuthority('system:ops:read')")
    public Result<OpsOverview> gates() {
        List<GateStatus> statuses = new ArrayList<>(FeatureGateRegistry.size());
        int enabledCount = 0;
        for (FeatureGateRegistry.Gate gate : FeatureGateRegistry.ALL) {
            Boolean configured = environment.getProperty(gate.fullKey(), Boolean.class);
            boolean enabled = configured != null ? configured : gate.defaultEnabled();
            boolean fromDefault = configured == null;
            if (enabled) {
                enabledCount++;
            }
            statuses.add(new GateStatus(gate.fullKey(), gate.label(), enabled, fromDefault));
        }
        return Result.success(new OpsOverview(statuses, enabledCount, statuses.size()));
    }
}
