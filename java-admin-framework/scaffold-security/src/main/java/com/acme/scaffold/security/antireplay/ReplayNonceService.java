package com.acme.scaffold.security.antireplay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

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
        long expiry = now + (long) ttlSeconds * 1000L;
        // putIfAbsent 原子完成「检查-占用」：并发同 nonce 只有一个请求能写入成功，
        // 原 get+put 非原子会让并发重放请求双双放行。
        Long existing = seen.putIfAbsent(nonce, expiry);
        if (existing != null) {
            if (existing > now) {
                return true;
            }
            // 已过期：原子覆盖，允许 reuse
            seen.put(nonce, expiry);
            return false;
        }
        if (seen.size() >= maxSize) {
            // 先淘汰已过期条目；若全部仍在 TTL 内（高频请求场景）仍会突破上限，
            // 则按过期时间升序额外淘汰最早过期的条目，保证内存严格有界。
            seen.entrySet().removeIf(e -> e.getValue() < now);
            while (seen.size() >= maxSize) {
                String oldest = null;
                long earliest = Long.MAX_VALUE;
                for (Map.Entry<String, Long> e : seen.entrySet()) {
                    if (e.getKey().equals(nonce)) {
                        continue; // 不淘汰刚写入的条目，否则本次占用立即失效
                    }
                    if (e.getValue() < earliest) {
                        earliest = e.getValue();
                        oldest = e.getKey();
                    }
                }
                if (oldest == null) {
                    break; // 仅剩当前条目，无可淘汰项
                }
                seen.remove(oldest);
            }
        }
        return false;
    }
}
