package com.acme.scaffold.config.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 网关灰度路由规则（E10）：按显式规则 + 稳定权重哈希把请求分流到 stable / canary。
 *
 * <p>纯函数、零 Spring/Servlet 依赖，便于离线单测；过滤器仅消费本类判定结果。设计要点：</p>
 * <ul>
 *   <li><b>显式规则优先</b>：命中 header / cookie / 客户端 IP 白名单时直接定位目标，不受权重影响；
 *       便于内部测试、指定用户先遣、故障快速回切；</li>
 *   <li><b>稳定哈希分桶</b>：无显式命中时，以 stickyKey（如 userId / deviceId）做 0~99 稳定分桶，
 *       同一 key 永远落同一桶，避免请求间抖动；缺省 stickyKey 时可用请求 IP；</li>
 *   <li><b>权重边界确定</b>：canaryPercent≤0 全走 stable，≥100 全走 canary；</li>
 *   <li><b>配置修复</b>：权重越界钳制，白名单 null 安全（fail-closed 到 stable）。</li>
 * </ul>
 */
public final class GatewayCanaryRules {

    private static final int BUCKETS = 100;

    private GatewayCanaryRules() {
    }

    /** 灰度目标。 */
    public enum Target {
        STABLE,
        CANARY
    }

    /** 灰度决策：目标 + 命中原因（可观测/审计用）。 */
    public record Decision(Target target, String reason, int bucket) {
        public boolean isCanary() {
            return target == Target.CANARY;
        }
    }

    /**
     * 灰度路由决策。
     *
     * @param stickyKey        稳定键（userId/deviceId/IP），为 null/空时权重路径按 0 桶（stable）
     * @param canaryPercent    灰度权重 0~100，越界钳制
     * @param forceCanaryHeader header 名（值非空且等于 {@code forceCanaryValue} 时强制 canary）
     * @param headerValue      实际 header 值
     * @param canaryIps         canary IP 白名单（精确匹配，大小写不敏感）
     * @param clientIp          客户端真实 IP
     */
    public static Decision route(String stickyKey, int canaryPercent,
                                 String forceCanaryHeader, String headerValue,
                                 List<String> canaryIps, String clientIp) {
        int percent = clamp(canaryPercent);
        // 显式规则优先于权重边界：即便权重 0（默认全 stable），也允许 header/IP 定点先遣与故障回切。
        if (forceCanaryHeader != null && !forceCanaryHeader.isBlank()
                && headerValue != null && headerValue.equalsIgnoreCase("true")) {
            return new Decision(Target.CANARY, "header:" + forceCanaryHeader, 0);
        }
        if (clientIp != null && canaryIps != null
                && canaryIps.stream().anyMatch(ip -> ip != null && !ip.isBlank()
                        && ip.trim().equalsIgnoreCase(clientIp.trim()))) {
            return new Decision(Target.CANARY, "ip_whitelist", 0);
        }
        if (percent <= 0) {
            return new Decision(Target.STABLE, "weight_0", 0);
        }
        if (percent >= 100) {
            return new Decision(Target.CANARY, "weight_100", 0);
        }
        int bucket = bucket(stickyKey);
        Target target = bucket < percent ? Target.CANARY : Target.STABLE;
        return new Decision(target, "weight_hash", bucket);
    }

    /** 稳定分桶：FNV-1a 变体哈希后取模，同一 key 恒定同桶。 */
    public static int bucket(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        byte[] bytes = key.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        int hash = 0x811c9dc5;
        for (byte b : bytes) {
            hash ^= b & 0xff;
            hash *= 0x01000193;
        }
        return Math.floorMod(hash, BUCKETS);
    }

    /** 权重钳制到 0~100。 */
    public static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
