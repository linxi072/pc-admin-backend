package com.acme.scaffold.observability.service;

import com.acme.scaffold.observability.dto.TenantMetricView;
import com.acme.scaffold.observability.recorder.TenantMetrics;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * 租户维度聚合服务（R14 租户维度）：读取 {@link TenantMetrics} 快照，向 REST 层提供按租户下钻的
 * 请求量 / 错误率 / 平均时延视图。
 *
 * <p>该聚合由 {@code OtlpTracingFilter} 在请求级 span 产出后自动收集（无需调用方干预）；
 * 本服务仅做读取与视图映射，无副作用。</p>
 */
@Service
@RequiredArgsConstructor
public class TenantMetricsService {

    private final TenantMetrics tenantMetrics;

    /**
     * 查询租户维度聚合快照（按请求量降序）。
     *
     * @return 视图列表（空列表表示尚无租户级观测）
     */
    public List<TenantMetricView> snapshot() {
        return tenantMetrics.snapshot().stream()
                .map(TenantMetricView::from)
                .toList();
    }

    /** @return 是否启用租户维度聚合（运行态开关回显）。 */
    public boolean isEnabled() {
        return tenantMetrics.tenantMetricsEnabled();
    }
}
