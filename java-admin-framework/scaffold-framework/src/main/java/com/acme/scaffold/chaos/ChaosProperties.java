package com.acme.scaffold.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 混沌工程开关（R15）。前缀 {@code app.chaos}。
 *
 * <p><b>默认 {@code enabled=false}</b>：完全不注册任何 R15 Bean（注册表 / 过滤器 / 控制器），
 * 与既有行为零回归。仅当显式开启 {@code app.chaos.enabled=true} 时，才在请求链路上注入故障，
 * 用于韧性/混沌演练；演练结束后务必改回 false 或调用 disarm 清除规则。</p>
 */
@ConfigurationProperties(prefix = "app.chaos")
public class ChaosProperties {

    /** 是否启用混沌故障注入。默认关闭。 */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
