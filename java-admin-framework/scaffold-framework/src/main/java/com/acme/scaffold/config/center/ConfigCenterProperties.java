package com.acme.scaffold.config.center;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置中心接入开关与类型（R16），绑定前缀 {@code app.config-center}。
 *
 * <p><b>默认 {@code enabled=false}</b>：不装配任何 R16 Bean（端口 / 服务 / 控制器），
 * 与既有行为零回归。启用后置 {@code type=local} 即使用本地环境适配器；
 * 远端类型（nacos / apollo）需由外部 jar 经 R1 插件机制提供对应的 {@link ConfigCenterPort} 实现。</p>
 */
@ConfigurationProperties(prefix = "app.config-center")
public class ConfigCenterProperties {

    /** 是否启用配置中心接入（默认 false，零回归）。 */
    private boolean enabled = false;

    /** 配置中心类型：local（默认）/ nacos / apollo。 */
    private String type = "local";

    /** 快照暴露的键前缀白名单（逗号分隔）；仅暴露该命名空间下的配置项，避免泄露敏感配置。 */
    private String snapshotPrefix = "app";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSnapshotPrefix() {
        return snapshotPrefix;
    }

    public void setSnapshotPrefix(String snapshotPrefix) {
        this.snapshotPrefix = snapshotPrefix;
    }
}
