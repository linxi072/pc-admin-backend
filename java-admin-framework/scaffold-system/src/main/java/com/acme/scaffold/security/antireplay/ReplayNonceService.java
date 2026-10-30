package com.acme.scaffold.security.antireplay;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * nonce 去重服务：内存维护「已使用 nonce → 过期时刻」映射，识别重放请求。
 *
 * <p>设计要点：
 * <ul>
 *   <li>nonce 在 {@code ttlSeconds} 内视为已占用，重复提交直接判为重放；</li>
 *   <li>超过 TTL 后允许 reuse（被惰性清理）；</li>
 *   <li>缓存达到上限时按过期时间惰性淘汰，避免无界增长；</li>
 *   <li>纯内存存储，适用于单实例；多实例部署建议替换为 Redis（按 nonce 维度共享）。</li>
 * </ul>
 * 算法与 Spring 解耦，便于离线单测。
 */
@Service
public class ReplayNonceService {

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final int ttlSeconds;
    private final int maxSize;

    public ReplayNonceService(AntiReplayProperties props) {
        this.ttlSeconds = props.getNonceTtlSeconds();
        this.maxSize = Math.max(1, props.getMaxNonceCacheSize());
    }

    public boolean isReplay(String nonce) {
        return isReplay(nonce, System.currentTimeMillis());
    }

    /**
     * 判断 nonce 是否为重放。
     *
     * @param nonce 客户端提交的一次性随机数
     * @param now   当前毫秒时间戳（注入以便测试）
     * @return true 表示命中已使用记录（重放），false 表示放行（首次或已过期）
     */
    boolean isReplay(String nonce, long now) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        Long existing = seen.get(nonce);
        if (existing != null) {
            if (existing > now) {
                return true;
            }
            seen.remove(nonce);
        }
        if (seen.size() >= maxSize) {
            seen.entrySet().removeIf(e -> e.getValue() < now);
        }
        seen.put(nonce, now + (long) ttlSeconds * 1000L);
        return false;
    }
}
