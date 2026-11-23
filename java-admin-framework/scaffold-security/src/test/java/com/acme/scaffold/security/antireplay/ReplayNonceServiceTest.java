package com.acme.scaffold.security.antireplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * nonce 去重服务单测（缺陷修复回归）：
 * 覆盖重放判定、TTL 复用，以及缓存上限在「全部未过期」场景下仍严格有界。
 */
class ReplayNonceServiceTest {

    private static ReplayNonceService service(int ttlSeconds, int maxSize) {
        AntiReplayProperties props = new AntiReplayProperties();
        props.setNonceTtlSeconds(ttlSeconds);
        props.setMaxNonceCacheSize(maxSize);
        return new ReplayNonceService(props);
    }

    private static int cacheSize(ReplayNonceService service) throws Exception {
        Field f = ReplayNonceService.class.getDeclaredField("seen");
        f.setAccessible(true);
        return ((java.util.Map<?, ?>) f.get(service)).size();
    }

    @Test
    void firstUseAllowedThenReplayRejected() {
        ReplayNonceService svc = service(60, 100);
        long now = 1_700_000_000_000L;
        assertFalse(svc.isReplay("nonce-1", now), "首次使用放行");
        assertTrue(svc.isReplay("nonce-1", now + 1_000L), "TTL 内重复提交判重放");
    }

    @Test
    void expiredNonceCanBeReused() {
        ReplayNonceService svc = service(2, 100);
        long now = 1_700_000_000_000L;
        assertFalse(svc.isReplay("n", now));
        assertFalse(svc.isReplay("n", now + 3_000L), "超过 TTL 后允许 reuse");
    }

    @Test
    void blankNonceIsIgnored() {
        ReplayNonceService svc = service(60, 100);
        assertFalse(svc.isReplay(null, 1L));
        assertFalse(svc.isReplay("  ", 1L));
    }

    @Test
    void cacheShouldStayBoundedWhenAllEntriesUnexpired() throws Exception {
        // 回归缺陷：原实现仅在条目过期时淘汰；高频请求下全部条目均在 TTL 内，
        // seen 会持续超过 maxSize 无界增长。修复后严格有界。
        ReplayNonceService svc = service(600, 5);
        long now = 1_700_000_000_000L;
        for (int i = 0; i < 200; i++) {
            svc.isReplay("nonce-" + i, now);
        }
        assertTrue(cacheSize(svc) <= 5, "即使全部未过期，缓存也不得超过 maxSize");
    }
}
