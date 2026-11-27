package com.acme.scaffold.security.antireplay;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * nonce 去重服务：内存维护「已使用 nonce → 过期时刻」映射，识别重放请求。
 *
 * <p>设计要点：
 * <ul>
 *   <li>nonce 在 {@code ttlSeconds} 内视为已占用，重复提交直接判为重放；</li>
 *   <li>超过 TTL 后允许 reuse（被惰性清理）；</li>
 *   <li>缓存达到上限时仅淘汰已过期条目；若全部仍在 TTL 内则 fail-closed 拒绝新请求，
 *       绝不丢弃仍然有效的重放历史（否则攻击者可用噪声 nonce 挤掉证据并绕过检测）；</li>
 *   <li>纯内存存储，适用于单实例；多实例部署建议替换为 Redis（按 nonce 维度共享）。</li>
 * </ul>
 * 算法与 Spring 解耦，便于离线单测。
 */
@Service
public class ReplayNonceService {

    /** nonce 占用结果：放行 / 命中重放历史 / 缓存已满需 fail-closed。 */
    public enum AcquireResult {
        ACQUIRED, REPLAY, CACHE_FULL
    }

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final int ttlSeconds;
    private final int maxSize;

    public ReplayNonceService(AntiReplayProperties props) {
        this.ttlSeconds = props.getNonceTtlSeconds();
        this.maxSize = Math.max(1, props.getMaxNonceCacheSize());
    }

    /** 兼容旧调用方：缓存满也按「不可放行」处理，避免绕过检测。 */
    public boolean isReplay(String nonce) {
        return isReplay(nonce, System.currentTimeMillis());
    }

    boolean isReplay(String nonce, long now) {
        return tryAcquire(nonce, now) != AcquireResult.ACQUIRED;
    }

    /** 带明确结果的 nonce 占用：过滤器据此区分重放与缓存饱和。 */
    public AcquireResult tryAcquire(String nonce) {
        return tryAcquire(nonce, System.currentTimeMillis());
    }

    /**
     * 尝试占用 nonce。
     *
     * @param nonce 客户端提交的一次性随机数
     * @param now   当前毫秒时间戳（注入以便测试）
     */
    AcquireResult tryAcquire(String nonce, long now) {
        if (nonce == null || nonce.isBlank()) {
            return AcquireResult.ACQUIRED;
        }
        long expiry = now + (long) ttlSeconds * 1000L;
        // putIfAbsent 原子完成「检查-占用」：并发同 nonce 只有一个请求能写入成功。
        Long existing = seen.putIfAbsent(nonce, expiry);
        if (existing != null) {
            if (existing > now) {
                return AcquireResult.REPLAY;
            }
            // 已过期：条件覆盖，只有仍持有旧到期值的线程可以 reuse；
            // 覆盖失败说明另一个并发请求已续期，本请求应判为重放。
            if (seen.replace(nonce, existing, expiry)) {
                return AcquireResult.ACQUIRED;
            }
            Long refreshed = seen.get(nonce);
            return refreshed != null && refreshed > now
                    ? AcquireResult.REPLAY : AcquireResult.ACQUIRED;
        }
        // 新插入后只清理过期项；绝不淘汰仍在 TTL 内的记录，否则重放历史可被噪声 nonce 挤出。
        seen.entrySet().removeIf(e -> e.getValue() < now);
        if (seen.size() > maxSize) {
            seen.remove(nonce);
            return AcquireResult.CACHE_FULL;
        }
        return AcquireResult.ACQUIRED;
    }
}
