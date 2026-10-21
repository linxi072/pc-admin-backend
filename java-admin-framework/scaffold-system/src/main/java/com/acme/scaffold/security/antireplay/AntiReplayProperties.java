package com.acme.scaffold.security.antireplay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 请求级防重放（anti-replay）开关与参数，绑定自 {@code auth.anti-replay.*}。
 *
 * <p>{@code enabled} 默认 {@code false}：功能完整实现，但默认不开启，避免在没有前端配合
 * （未携带 {@code X-Request-Timestamp} / {@code X-Request-Nonce} 头）时直接阻断现有登录链路
 * （例如 {@code InstanceIntegrationIT} 与既有前端）。运维在对应 profile 的 yml 中显式置
 * {@code auth.anti-replay.enabled: true} 即可启用，前端需为每个登录/刷新请求附带时间戳与一次性 nonce。
 */
@ConfigurationProperties(prefix = "auth.anti-replay")
public class AntiReplayProperties {

    /** 是否启用请求级防重放校验。默认关闭（opt-in）。 */
    private boolean enabled = false;

    /** 允许的服务端/客户端时钟偏差上限（毫秒）。默认 5000ms。 */
    private long maxClockSkewMillis = 5_000L;

    /** nonce 在内存中的存活时长（秒），超过后允许 same nonce 再次使用。默认 60s。 */
    private int nonceTtlSeconds = 60;

    /** nonce 缓存上限，超出后按过期时间惰性清理。默认 100000。 */
    private int maxNonceCacheSize = 100_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxClockSkewMillis() {
        return maxClockSkewMillis;
    }

    public void setMaxClockSkewMillis(long maxClockSkewMillis) {
        this.maxClockSkewMillis = maxClockSkewMillis;
    }

    public int getNonceTtlSeconds() {
        return nonceTtlSeconds;
    }

    public void setNonceTtlSeconds(int nonceTtlSeconds) {
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    public int getMaxNonceCacheSize() {
        return maxNonceCacheSize;
    }

    public void setMaxNonceCacheSize(int maxNonceCacheSize) {
        this.maxNonceCacheSize = maxNonceCacheSize;
    }
}
