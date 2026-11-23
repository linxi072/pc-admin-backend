package com.acme.scaffold.config.gateway;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 轻量网关前置层配置（{@code gateway.*}）。
 *
 * <p><b>默认全关</b>：{@code enabled=false}，与 {@code app.chaos.enabled} / 配置中心一致，
 * 未显式开启时不注册任何网关 Bean，单体直连行为零回归（对齐 PLATFORM-EVOLUTION-PLAN B1 双挂过渡）。</p>
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** 是否启用网关前置层（默认关闭，opt-in）。 */
    private boolean enabled = false;

    /** 全局限流：单 IP 在窗口内允许的最大请求数；<=0 表示关闭限流。 */
    private int rateLimitMax = 120;

    /** 全局限流窗口秒数；<=0 归一为 1 秒（由 RateLimitWindowRules 处理）。 */
    private int rateLimitWindowSeconds = 60;

    /** 限流失败行为：true=fail-closed（拒绝），false=fail-open（放行）。默认 fail-closed。 */
    private boolean failClosed = true;

    /** 允许的来源白名单（CORS）；为空时不处理 CORS。 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** actuator 内网网段前缀（如 10. / 192.168. / 127.）；为空时 actuator 公网不可达。 */
    private List<String> internalCidrs = new ArrayList<>(List.of("127.", "10.", "192.168.", "172.16.", "172.17.", "172.18.", "172.19.", "172.2"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRateLimitMax() {
        return rateLimitMax;
    }

    public void setRateLimitMax(int rateLimitMax) {
        this.rateLimitMax = rateLimitMax;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }

    public List<String> getInternalCidrs() {
        return internalCidrs;
    }

    public void setInternalCidrs(List<String> internalCidrs) {
        this.internalCidrs = internalCidrs == null ? new ArrayList<>() : new ArrayList<>(internalCidrs);
    }
}
