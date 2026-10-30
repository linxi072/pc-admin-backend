package com.acme.scaffold.config.tenant;

import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 多租户强制隔离启动护栏（M19 生产可用化）。
 *
 * <p>应用启动完成后（所有 Bean 已就绪，{@code JooqConfig} 已注入 {@link TenantEnforcement} 配置快照）
 * 调用 {@link TenantEnforcement#validateConfiguration()} 校验配置自洽性：
 * 当 {@code app.tenant.enforce=true} 时，若 {@code defaultTenantId} 非法或 {@code platform-tables} 为空，
 * 校验抛出 {@link IllegalStateException} 并向上传播，使应用 fail-fast 中止启动，
 * 阻止「配置不全却开启强制隔离」上线导致平台维表不可见或数据越权。</p>
 *
 * <p>{@code enforce=false}（默认）时 {@link TenantEnforcement#validateConfiguration()} 直接放行，
 * 不阻断既有单租户部署的启动，保持零回归。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEnforcementValidator implements CommandLineRunner {

    private final TenantProperties tenantProperties;

    @Override
    public void run(String... args) {
        // 防御：确保快照已注入（JooqConfig 在构建 DSLContext 时也应已调用 configure）
        if (!TenantEnforcement.isConfigured()) {
            TenantEnforcement.configure(tenantProperties);
        }
        try {
            TenantEnforcement.validateConfiguration();
        } catch (IllegalStateException ex) {
            // fail-fast：把校验信息一并抛出，阻止应用带病上线
            throw new IllegalStateException("多租户强制隔离配置校验失败，应用启动中止：" + ex.getMessage(), ex);
        }
        if (TenantEnforcement.isEnabled()) {
            log.warn("多租户强制隔离已启用(enforce=true)：所有业务表读写将追加 tenant_id 过滤，"
                            + "平台共享维表 {} 豁免。请确认历史数据已按租户归位，否则将导致跨租户不可见。",
                    tenantProperties.getPlatformTables());
        } else {
            log.info("多租户强制隔离未启用(enforce=false，默认值)：隔离逻辑就绪但未生效，"
                    + "业务表读写暂不加 tenant_id 约束（保持对既有单租户部署的零回归）。"
                    + "生产启用见 docs/manual/m19-production-enablement.md。");
        }
    }
}
