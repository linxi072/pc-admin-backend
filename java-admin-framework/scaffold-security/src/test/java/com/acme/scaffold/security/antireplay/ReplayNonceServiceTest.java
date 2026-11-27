package com.acme.scaffold.security.antireplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // 缓存严格有界；饱和后新 nonce 被拒绝，而不是挤掉仍有效的重放历史。
        ReplayNonceService svc = service(600, 5);
        long now = 1_700_000_000_000L;
        for (int i = 0; i < 200; i++) {
            svc.isReplay("nonce-" + i, now);
        }
        assertTrue(cacheSize(svc) <= 5, "即使全部未过期，缓存也不得超过 maxSize");
    }

    @Test
    void liveNonceShouldStillBeReplayAfterCacheFull() {
        ReplayNonceService svc = service(60, 5);
        long now = 1_700_000_000_000L;
        for (int i = 0; i < 5; i++) {
            assertEquals(ReplayNonceService.AcquireResult.ACQUIRED, svc.tryAcquire("nonce-" + i, now));
        }

        assertEquals(ReplayNonceService.AcquireResult.CACHE_FULL, svc.tryAcquire("noise", now));
        assertEquals(ReplayNonceService.AcquireResult.REPLAY, svc.tryAcquire("nonce-0", now));
        assertEquals(ReplayNonceService.AcquireResult.REPLAY, svc.tryAcquire("nonce-4", now));
    }

    @Test
    void expiredEntriesShouldBePurgedBeforeCapacityCheck() {
        ReplayNonceService svc = service(1, 2);
        long now = 1_700_000_000_000L;
        assertEquals(ReplayNonceService.AcquireResult.ACQUIRED, svc.tryAcquire("old", now));

        assertEquals(ReplayNonceService.AcquireResult.ACQUIRED, svc.tryAcquire("fresh", now + 2_000L));
        assertEquals(ReplayNonceService.AcquireResult.REPLAY, svc.tryAcquire("fresh", now + 2_000L));
        assertEquals(ReplayNonceService.AcquireResult.ACQUIRED, svc.tryAcquire("old", now + 2_000L));
    }

    @Test
    void isReplayShouldFailClosedWhenCacheFull() {
        ReplayNonceService svc = service(60, 1);
        long now = 1_700_000_000_000L;
        svc.tryAcquire("occupied", now);

        assertTrue(svc.isReplay("brand-new", now), "缓存满时旧接口也必须拒绝放行");
    }
}
