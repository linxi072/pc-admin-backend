package com.acme.scaffold.security.antireplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayNonceServiceTest {

    private AntiReplayProperties props() {
        AntiReplayProperties p = new AntiReplayProperties();
        p.setNonceTtlSeconds(1);
        p.setMaxNonceCacheSize(100_000);
        return p;
    }

    @Test
    void firstSeen_notReplay_secondSeen_isReplay() {
        ReplayNonceService svc = new ReplayNonceService(props());
        long now = 5_000L;
        assertFalse(svc.isReplay("n1", now));
        assertTrue(svc.isReplay("n1", now));
    }

    @Test
    void expiredNonce_allowedAgain() {
        ReplayNonceService svc = new ReplayNonceService(props());
        // ttlSeconds = 1 -> ttl = 1000ms
        assertFalse(svc.isReplay("n2", 1000L));
        // 超过 TTL 后再用同一 nonce：应判定为过期，允许再次使用（非重放）
        assertFalse(svc.isReplay("n2", 1000L + (long) props().getNonceTtlSeconds() * 1000L + 10L));
    }

    @Test
    void blankOrNullNonce_neverReplay() {
        ReplayNonceService svc = new ReplayNonceService(props());
        assertFalse(svc.isReplay(null, 1000L));
        assertFalse(svc.isReplay("", 1000L));
        assertFalse(svc.isReplay("   ", 1000L));
    }
}
