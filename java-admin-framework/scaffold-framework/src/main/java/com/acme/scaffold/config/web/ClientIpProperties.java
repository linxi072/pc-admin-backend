package com.acme.scaffold.config.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端 IP 解析配置（{@code app.web.*}）。
 *
 * <p>默认即生效，无需在 {@code application*.yml} 中显式声明；仅在部署拓扑与默认值不符时
 * 设置 {@code app.web.trusted-proxy-count}：</p>
 * <ul>
 *   <li>应用直连公网（无反向代理）：设为 {@code 0}，忽略全部 {@code X-Forwarded-For}；</li>
 *   <li>单级 Nginx（默认）：保持 {@code 1}；</li>
 *   <li>多级代理 / CDN：设为实际可信跳数。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.web")
public class ClientIpProperties {

    /**
     * 可信反向代理跳数。决定解析 {@code X-Forwarded-For} 时自右向左跳过几跳，
     * 取值小于实际代理层数会让客户端仍能伪造 IP（最左段永远不可信）。
     */
    private int trustedProxyCount = 1;

    public int getTrustedProxyCount() {
        return trustedProxyCount;
    }

    public void setTrustedProxyCount(int trustedProxyCount) {
        // 防御非法配置（负数），保证下限为 0
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }
}
